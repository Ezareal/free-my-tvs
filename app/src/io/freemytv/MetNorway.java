package io.freemytv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The backup weather source, with weather_backup=true: MET Norway's Locationforecast (api.met.no, no key), asked only
 * when Open-Meteo doesn't answer (some ISPs block it). Its answer is rewritten in Open-Meteo's shape, so HomeData parses
 * and caches it as it does Open-Meteo's. MET has no sunrise or sunset in the forecast; they're worked out here.
 * MET's terms: an identifying User-Agent, coordinates cut to 4 decimals, and credit (README).
 */
final class MetNorway {
    private MetNorway() {}

    static final String USER_AGENT = "FreeMyTV github.com/simbasjungles/free-my-tv";

    static String url(String latitude, String longitude) {
        return "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=" + fourDecimals(latitude)
                + "&lon=" + fourDecimals(longitude);
    }

    /** MET refuses coordinates with more than 4 decimals (cut, not rounded: "40.71284" → "40.7128"). */
    static String fourDecimals(String v) {
        int dot = v.indexOf('.');
        return dot < 0 || v.length() - dot - 1 <= 4 ? v : v.substring(0, dot + 5);
    }

    /** MET's forecast as Open-Meteo would have answered it, in the TV's time zone. */
    static String asOpenMeteo(String body, double lat, double lon, TimeZone zone, long now) throws JSONException, ParseException {
        JSONArray series = new JSONObject(body).getJSONObject("properties").getJSONArray("timeseries");
        SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat local = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        local.setTimeZone(zone);

        long[] sun = sun(lat, lon, now, zone);
        Calendar day = Calendar.getInstance(zone);
        day.setTimeInMillis(now);
        int today = day.get(Calendar.DAY_OF_YEAR);

        JSONArray times = new JSONArray(), temps = new JSONArray(), codes = new JSONArray(), days = new JSONArray();
        JSONObject current = null;
        double max = Double.NaN, min = Double.NaN;
        for (int i = 0; i < series.length(); i++) {
            JSONObject step = series.getJSONObject(i), data = step.getJSONObject("data");
            JSONObject hour = data.optJSONObject("next_1_hours");
            if (hour == null) break;   // past the first ~2 days MET steps by 6 hours
            long at = utc.parse(step.getString("time")).getTime();
            double temp = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");
            String symbol = hour.getJSONObject("summary").getString("symbol_code");
            boolean isDay = isDay(symbol, at, sun, zone);
            int code = code(symbol);
            if (at <= now || current == null) {
                current = new JSONObject().put("temperature_2m", temp).put("weather_code", code).put("is_day", isDay ? 1 : 0);
            }
            day.setTimeInMillis(at);
            if (day.get(Calendar.DAY_OF_YEAR) == today) {   // today's high and low, from the hours left
                max = Double.isNaN(max) ? temp : Math.max(max, temp);
                min = Double.isNaN(min) ? temp : Math.min(min, temp);
            }
            times.put(local.format(at));
            temps.put(temp);
            codes.put(code);
            days.put(isDay ? 1 : 0);
        }
        if (current == null) throw new JSONException("no hourly forecast");
        if (Double.isNaN(max)) max = min = current.getDouble("temperature_2m");
        JSONObject daily = new JSONObject()
                .put("temperature_2m_max", new JSONArray().put(max))
                .put("temperature_2m_min", new JSONArray().put(min))
                .put("sunrise", new JSONArray().put(local.format(sun[0])))
                .put("sunset", new JSONArray().put(local.format(sun[1])));
        JSONObject hourly = new JSONObject().put("time", times).put("temperature_2m", temps).put("weather_code", codes)
                .put("is_day", days);
        return new JSONObject().put("timezone", zone.getID()).put("source", "MET Norway").put("current", current)
                .put("daily", daily).put("hourly", hourly).toString();
    }

    /** "partlycloudy_night" → day or night; symbols without one (rain, fog…) go by the sun. */
    private static boolean isDay(String symbol, long at, long[] sun, TimeZone zone) {
        if (symbol.endsWith("_day")) return true;
        if (symbol.endsWith("_night") || symbol.endsWith("_polartwilight")) return false;
        long t = minuteOfDay(at, zone);
        return t >= minuteOfDay(sun[0], zone) && t < minuteOfDay(sun[1], zone);
    }

    private static long minuteOfDay(long at, TimeZone zone) {
        return ((at + zone.getOffset(at)) / 60_000L) % (24 * 60);
    }

    /** MET's symbol as the WMO code Open-Meteo uses, close enough for the words and the icon (HomeData, Symbols). */
    static int code(String symbol) {
        String s = symbol.replaceAll("_(day|night|polartwilight)$", "");
        if (s.contains("thunder")) return 95;
        if (s.contains("snow")) return s.startsWith("heavy") ? 75 : s.startsWith("light") ? 71 : 73;
        if (s.contains("sleet")) return s.startsWith("heavy") ? 67 : 66;
        if (s.contains("rain")) return s.startsWith("heavy") ? 65 : s.startsWith("light") ? 61 : 63;
        switch (s) {
            case "clearsky": return 0;
            case "fair": return 1;
            case "partlycloudy": return 2;
            case "fog": return 45;
            default: return 3;   // cloudy
        }
    }

    /**
     * Today's sunrise and sunset at lat/lon (epoch ms), from the sunrise equation (about a minute off at most). Where the
     * sun doesn't set, midnight to midnight; where it doesn't rise, both at noon (night all day).
     */
    static long[] sun(double lat, double lon, long now, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(now);
        Calendar noon = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        noon.clear();
        noon.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), 12, 0, 0);
        double n = Math.round(noon.getTimeInMillis() / 86_400_000.0 + 2440587.5 - 2451545.0);
        double meanNoon = n - lon / 360;
        double m = Math.toRadians((357.5291 + 0.98560028 * meanNoon) % 360);
        double center = 1.9148 * Math.sin(m) + 0.02 * Math.sin(2 * m) + 0.0003 * Math.sin(3 * m);
        double ecliptic = Math.toRadians((Math.toDegrees(m) + center + 180 + 102.9372) % 360);
        double transit = 2451545.0 + meanNoon + 0.0053 * Math.sin(m) - 0.0069 * Math.sin(2 * ecliptic);
        double declination = Math.asin(Math.sin(ecliptic) * Math.sin(Math.toRadians(23.4397)));
        double phi = Math.toRadians(lat);
        double cosHour = (Math.sin(Math.toRadians(-0.833)) - Math.sin(phi) * Math.sin(declination))
                / (Math.cos(phi) * Math.cos(declination));
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long midnight = c.getTimeInMillis();
        if (cosHour < -1) return new long[] {midnight, midnight + 24 * 3_600_000L - 60_000L};
        if (cosHour > 1) return new long[] {midnight + 12 * 3_600_000L, midnight + 12 * 3_600_000L};
        double half = Math.toDegrees(Math.acos(cosHour)) / 360;
        return new long[] {julianToMillis(transit - half), julianToMillis(transit + half)};
    }

    private static long julianToMillis(double julian) {
        return Math.round((julian - 2440587.5) * 86_400_000.0);
    }
}
