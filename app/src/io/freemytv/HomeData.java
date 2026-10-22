package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optional crypto prices (Binance, every 5 min) and weather/sunrise/sunset (Open-Meteo, every 30 min), cached
 * so the screen opens with numbers. Only fetched while the home screen or the screensaver is visible (the screen asks).
 * Neither source needs a key. Location and currency come from config.properties.
 */
final class HomeData {
    /** USDT and USDC (the dollar) and Solana, in the configured currency; the 4th slot is spare (never shown). */
    static final String[] COINS = {"USDT", "USDC", "SOL", "BTC"};
    static final String[] COIN_NAMES = {"Dollar (USDT)", "USD Coin", "Solana", "Bitcoin"};
    private static final String CURRENCY = Config.CURRENCY.trim().toUpperCase(Locale.ROOT);
    /** Binance has no pairs in some currencies (INR...) and then refuses the whole request: prices go in dollars instead. */
    private static volatile boolean inDollars;

    /** Binance quotes everything in dollars as USDT; other currencies are converted with the dollar's exchange rate. */
    private static String quote() {
        return "USDT";
    }

    /** Dollars → the configured currency (1 for USD, or until the rate is known and then in dollars). */
    private static volatile double fxRate = Double.NaN;
    private static final String FX_URL = "https://open.er-api.com/v6/latest/USD";

    private static double rate() {
        return CURRENCY.equals("USD") || inDollars || Double.isNaN(fxRate) ? 1 : fxRate;
    }
    private static final String WEATHER_QUERY = "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset"
            + "&hourly=temperature_2m,weather_code,is_day&timezone=auto&forecast_days=2";

    private static String weatherPlace() {
        return Prefs.latitude() + "," + Prefs.longitude();
    }

    private static String weatherUrl() {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + Prefs.latitude() + "&longitude=" + Prefs.longitude() + WEATHER_QUERY;
    }
    private static final long PRICES_EVERY = 60_000L, WEATHER_EVERY = 30 * 60_000L;
    interface Listener { void onData(); }

    /** BTC, ETH and SOL; in Brazilian reais also the dollar (USDT/BRL), which Binance doesn't list for most currencies. */
    static int coinCount() {
        return Prefs.space().equals("crypto") ? active().length : 0;
    }

    /**
     * What the quotes space can show: id and name. To add another one, add a line here, fetch its data into an Info
     * (see refreshIpl / refreshAqi) and give spaceInfo() a case; the Settings drop-down and the card pick it up.
     */
    static final String[][] SPACES = {
        {"off", "Off"},
        {"crypto", "Crypto prices"},
        {"ipl", "IPL scores"},
        {"aqi", "Air quality (my city)"},
    };

    static String spaceName(String id) {
        for (String[] s : SPACES) if (s[0].equals(id)) return Ui.tr(s[1]);
        return Ui.tr("Off");
    }

    static boolean ipl() { return Prefs.space().equals("ipl"); }

    static boolean aqi() { return Prefs.space().equals("aqi"); }

    /** The text-card spaces (everything but crypto): their current Info, or null for crypto / off. */
    Info spaceInfo() {
        switch (Prefs.space()) {
            case "ipl": return ipl;
            case "aqi": return aqi;
            default: return null;
        }
    }

    /** The quotes space has something to show. */
    static boolean quotesOn() {
        String sp = Prefs.space();
        return sp.equals("crypto") ? active().length > 0 : !sp.equals("off");
    }

    // MARK: - IPL (ESPN's public cricket feed, no key)

    /** What the IPL card shows: a heading, up to three lines and a short one-liner for the top row. */
    static final class Info {
        String heading = "IPL", line1 = "", line2 = "", line3 = "", shortLine = "";
        boolean live;
        int dot;        // a colored dot on the card (0 = none; live shows a red one)
        long at;
    }

    volatile Info ipl = new Info(), aqi = new Info();
    private volatile boolean fetchingAqi;
    private volatile long aqiFailedAt;
    private volatile boolean fetchingIpl;
    private volatile long iplFailedAt;
    private static final long IPL_LIVE_EVERY = 30_000L, IPL_IDLE_EVERY = 10 * 60_000L;
    private static final String IPL_BASE = "https://site.api.espn.com/apis/site/v2/sports/cricket/8048/scoreboard";
    private static final String IPL_SEASON = "https://site.web.api.espn.com/apis/v2/sports/cricket/8048/standings";

    private void refreshIpl(long now) {
        if (fetchingIpl || !ipl() || failedRecently(iplFailedAt, now)) return;
        long every = ipl.live ? IPL_LIVE_EVERY : IPL_IDLE_EVERY;
        if (now - ipl.at < every && now >= ipl.at) return;
        fetchingIpl = true;
        io.execute(() -> {
            try {
                Info next = parseIpl(get(IPL_BASE));
                if (next == null) next = seasonCountdown();
                next.at = System.currentTimeMillis();
                ipl = next;
                notifyListeners();
            } catch (Exception e) {
                iplFailedAt = System.currentTimeMillis();
                AppLog.w("ipl: " + e.getMessage());
            } finally {
                fetchingIpl = false;
            }
        });
    }

    // MARK: - Air quality (Open-Meteo, no key; US AQI scale)

    private void refreshAqi(long now) {
        if (fetchingAqi || !aqi() || failedRecently(aqiFailedAt, now)) return;
        if (now - aqi.at < 15 * 60_000L && now >= aqi.at && aqi.at != 0) return;
        fetchingAqi = true;
        io.execute(() -> {
            try {
                JSONObject cur = new JSONObject(get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + Prefs.latitude()
                        + "&longitude=" + Prefs.longitude() + "&current=us_aqi,pm2_5,pm10&timezone=auto")).getJSONObject("current");
                int v = (int) Math.round(cur.getDouble("us_aqi"));
                String[] band = aqiBand(v);
                Info out = new Info();
                String city = Prefs.city();
                out.heading = Ui.tr("Air quality") + (city.isEmpty() ? "" : " · " + city);
                out.line1 = "AQI " + v;
                out.line2 = Ui.tr(band[0]);
                out.line3 = "PM2.5 " + Math.round(cur.optDouble("pm2_5", 0)) + "  ·  PM10 " + Math.round(cur.optDouble("pm10", 0)) + " µg/m³";
                out.shortLine = "AQI " + v + " · " + Ui.tr(band[0]);
                out.dot = (int) Long.parseLong(band[1], 16);
                out.at = System.currentTimeMillis();
                aqi = out;
                notifyListeners();
            } catch (Exception e) {
                aqiFailedAt = System.currentTimeMillis();
                AppLog.w("aqi: " + e.getMessage());
            } finally {
                fetchingAqi = false;
            }
        });
    }

    /** US AQI → {words, ARGB color}. */
    private static String[] aqiBand(int v) {
        if (v <= 50) return new String[] {"Good", "FF30D158"};
        if (v <= 100) return new String[] {"Moderate", "FFFFD60A"};
        if (v <= 150) return new String[] {"Unhealthy for sensitive groups", "FFFF9F0A"};
        if (v <= 200) return new String[] {"Unhealthy", "FFFF453A"};
        if (v <= 300) return new String[] {"Very unhealthy", "FFBF5AF2"};
        return new String[] {"Hazardous", "FF8E1B2E"};
    }

    /** The match to show: one in play, else the latest result, else the next one; null when the feed has none. */
    private static Info parseIpl(String body) throws Exception {
        JSONArray events = new JSONObject(body).optJSONArray("events");
        JSONObject pick = null;
        int rank = -1;       // in play (3) > just finished (2) > coming up (1)
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null || e.length() == 0) continue;
            JSONObject type = e.optJSONObject("status") == null ? null : e.getJSONObject("status").optJSONObject("type");
            String state = type == null ? "" : type.optString("state", "");
            int r = state.equals("in") ? 3 : state.equals("post") ? 2 : 1;
            if (r > rank) {
                rank = r;
                pick = e;
            }
        }
        if (pick == null) return null;
        Info out = new Info();
        out.live = rank == 3;
        JSONObject comp = pick.optJSONArray("competitions") == null ? null : pick.getJSONArray("competitions").optJSONObject(0);
        JSONArray teams = comp == null ? null : comp.optJSONArray("competitors");
        String[] abbr = {"", ""}, score = {"", ""};
        for (int i = 0; teams != null && i < teams.length() && i < 2; i++) {
            JSONObject c = teams.getJSONObject(i), t = c.optJSONObject("team");
            abbr[i] = t == null ? "" : t.optString("abbreviation", t.optString("shortDisplayName", ""));
            score[i] = c.optString("score", "");
        }
        JSONObject st = pick.optJSONObject("status");
        JSONObject type = st == null ? null : st.optJSONObject("type");
        String detail = type == null ? "" : type.optString("shortDetail", type.optString("detail", ""));
        String summary = st == null ? "" : st.optString("summary", "");
        String name = pick.optString("shortName", pick.optString("name", abbr[0] + " v " + abbr[1]));
        out.heading = rank == 3 ? "IPL · LIVE" : rank == 2 ? "IPL · RESULT" : "IPL · NEXT MATCH";
        out.line1 = abbr[0].isEmpty() ? name : (abbr[0] + (score[0].isEmpty() ? "" : " " + score[0]));
        out.line2 = abbr[1].isEmpty() ? "" : (abbr[1] + (score[1].isEmpty() ? "" : " " + score[1]));
        out.line3 = !summary.isEmpty() ? summary : detail;
        out.shortLine = "IPL · " + (abbr[0].isEmpty() ? name : abbr[0] + (score[0].isEmpty() ? "" : " " + score[0]) + " v "
                + abbr[1] + (score[1].isEmpty() ? "" : " " + score[1]));
        return out;
    }

    /** Between seasons the feed has no matches: show how long until the next season starts (from the feed itself). */
    private Info seasonCountdown() {
        Info out = new Info();
        out.heading = "IPL";
        try {
            JSONObject season = new JSONObject(get(IPL_SEASON)).getJSONObject("season");
            String start = season.getString("startDate");
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            long days = (f.parse(start).getTime() - System.currentTimeMillis()) / 86_400_000L;
            int year = season.optInt("year", 0);
            if (days >= 0) {
                out.line1 = Ui.trf("IPL {year}", "year", year);
                out.line2 = Ui.trf("starts in {n} days", "n", days);
                out.line3 = Ui.tr("Live scores appear here on match days");
                out.shortLine = "IPL " + year + " · " + Ui.trf("{n} days to go", "n", days);
                return out;
            }
        } catch (Exception e) {
            AppLog.w("ipl season: " + e.getMessage());
        }
        out.line1 = "IPL";
        out.line2 = Ui.tr("No match right now");
        out.line3 = Ui.tr("Live scores appear here on match days");
        out.shortLine = "IPL · " + Ui.tr("No match right now");
        return out;
    }

    private static volatile int[] activeCoins;
    private static volatile int activeVersion = -1;

    /** Indexes in COINS of the coins the user has on (and that Binance lists in this currency). */
    static int[] active() {
        int[] a = activeCoins;
        if (a != null && activeVersion == Prefs.version) return a;
        int n = 0;
        int[] tmp = new int[COINS.length];
        for (int i = 0; i < 3; i++) if (Prefs.coin(i)) tmp[n++] = i;
        a = java.util.Arrays.copyOf(tmp, n);
        activeCoins = a;
        activeVersion = Prefs.version;
        return a;
    }

    /** Settings changed (city, coins): fetch again now. */
    void settingsChanged() {
        aqi = new Info();      // another city: air quality again
        pricesAt = 0;
        weatherAt = 0;
        pricesFailedAt = 0;
        weatherFailedAt = 0;
    }

    static String coinName(int i) {
        return i == 0 ? Ui.tr("Dollar") : i == 1 ? "USDC" : COIN_NAMES[i];
    }

    /** Binance.com refuses the US (HTTP 451); Binance.US answers the same call in dollars. */
    private static final String[] PRICE_HOSTS = {"https://api.binance.com", "https://api.binance.us"};

    /** USDT and USDC are pegged to the dollar: no market lookup, one dollar each (in the configured currency). */
    private static final String[] GECKO_IDS = {"tether", "usd-coin", "solana"};

    private static String geckoUrl() {
        return "https://api.coingecko.com/api/v3/simple/price?ids=tether,usd-coin,solana&vs_currencies="
                + CURRENCY.toLowerCase(Locale.ROOT) + "&include_24hr_change=true";
    }

    /** Live prices and 24 h changes straight in the configured currency (CoinGecko, no key). False if unusable. */
    private boolean parseGecko(String body) {
        try {
            JSONObject o = new JSONObject(body);
            String cur = CURRENCY.toLowerCase(Locale.ROOT);
            int got = 0;
            for (int i = 0; i < GECKO_IDS.length; i++) {
                JSONObject c = o.optJSONObject(GECKO_IDS[i]);
                if (c == null || !c.has(cur)) continue;
                prices[i] = c.getDouble(cur);
                changes[i] = c.has(cur + "_24h_change") ? c.getDouble(cur + "_24h_change") : Double.NaN;
                got++;
            }
            if (got > 0) inDollars = CURRENCY.equals("USD") ? inDollars : false;
            return got == GECKO_IDS.length;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isStable(int coin) {
        return coin < 2;
    }

    /** The Binance request for the active coins that have a market against USDT (USDT itself is the dollar: 1). */
    private static String pricesPath() {
        StringBuilder symbols = new StringBuilder();
        int[] coins = active();
        for (int i = 0; i < coins.length; i++) {
            if (isStable(coins[i])) continue;
            symbols.append(symbols.length() == 0 ? "" : ",").append("%22").append(COINS[coins[i]]).append(quote()).append("%22");
        }
        return symbols.length() == 0 ? null : "/api/v3/ticker/24hr?type=MINI&symbols=%5B" + symbols + "%5D";
    }

    private static String getPrices() throws IOException {
        if (pricesPath() == null) return "[]";      // only the dollar is on: nothing to ask Binance
        IOException failed = null;
        for (String host : PRICE_HOSTS) {
            try {
                return get(host + pricesPath());
            } catch (IOException e) {
                failed = e;
                // A pair Binance doesn't list (HTTP 400) isn't a refusal: the caller falls back to dollars instead.
                if ("HTTP 400".equals(e.getMessage())) break;
            }
        }
        throw failed;
    }

    static final class Weather {
        double temp = Double.NaN, max = Double.NaN, min = Double.NaN;
        int code = -1;
        boolean isDay = true;
        long sunrise, sunset;   // today's, in epoch ms
        // Next hours (starting at the next full hour): time, temperature, code and day/night.
        final List<Long> hours = new ArrayList<>();
        final List<Double> hourTemp = new ArrayList<>();
        final List<Integer> hourCode = new ArrayList<>();
        final List<Boolean> hourDay = new ArrayList<>();
    }

    private static HomeData instance;

    static synchronized HomeData get(Context c) {
        if (instance == null) instance = new HomeData(c.getApplicationContext());
        return instance;
    }

    final double[] prices = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
    /** 24 h change (%), in the same order. */
    final double[] changes = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
    volatile long pricesAt, weatherAt;
    volatile Weather weather = new Weather();

    private final SharedPreferences cache;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private volatile boolean fetchingPrices, fetchingWeather;
    private volatile long pricesFailedAt, weatherFailedAt;

    private HomeData(Context c) {
        cache = c.getSharedPreferences("homedata", Context.MODE_PRIVATE);
        inDollars = CURRENCY.equals(cache.getString("dollarsFor", ""));
        if (CURRENCY.equals(cache.getString("fxFor", ""))) {
            try {
                fxRate = Double.parseDouble(cache.getString("fx", "NaN"));
                inDollars = false;
            } catch (NumberFormatException ignored) {
            }
        } else if (!CURRENCY.equals("USD")) {
            cache.edit().remove("prices").remove("pricesAt").apply();      // saved in dollars: fetch with the rate
        }
        try {
            if (Prefs.quotes()) {
                String g = cache.getString("gecko", null);
                String p = cache.getString("prices", null);
                if (g != null && CURRENCY.equals(cache.getString("geckoFor", "")) && parseGecko(g)) {
                    // live data from CoinGecko, as last seen
                } else if (p != null) parsePrices(p);
                pricesAt = cache.getLong("pricesAt", 0);
            }
            String w = cache.getString("weather", null);
            // The cached forecast is for the city it was fetched for; another city means fetch again.
            if (w != null && weatherPlace().equals(cache.getString("weatherPlace", ""))) {
                weather = parseWeather(w);
                weatherAt = cache.getLong("weatherAt", 0);
            }
        } catch (Exception ignored) {}
    }

    void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    /** Fetches whatever is stale. The screen calls this when it appears and every minute while it's visible. */
    void refreshIfStale() {
        long now = System.currentTimeMillis();
        // A negative age (the TV's clock went back) counts as stale: otherwise the data would freeze.
        refreshIpl(now);
        refreshAqi(now);
        if (!fetchingPrices && coinCount() > 0 && (now - pricesAt > PRICES_EVERY || now < pricesAt) && !failedRecently(pricesFailedAt, now)) {
            fetchingPrices = true;
            io.execute(() -> {
                try {
                    try {
                        String live = get(geckoUrl());
                        if (parseGecko(live)) {
                            pricesAt = System.currentTimeMillis();
                            cache.edit().putString("gecko", live).putString("geckoFor", CURRENCY).putLong("pricesAt", pricesAt).apply();
                            notifyListeners();
                            return;
                        }
                    } catch (Exception e) {
                        AppLog.w("prices: CoinGecko: " + e.getMessage() + " (falling back to Binance)");
                    }
                    // The dollar's exchange rate first (for a currency other than USD); without one, dollars.
                    if (!CURRENCY.equals("USD")) {
                        try {
                            JSONObject fx = new JSONObject(get(FX_URL)).getJSONObject("rates");
                            fxRate = fx.getDouble(CURRENCY);
                            inDollars = false;
                            cache.edit().putString("fx", String.valueOf(fxRate)).putString("fxFor", CURRENCY).remove("dollarsFor").apply();
                        } catch (Exception e) {
                            AppLog.w("prices: exchange rate: " + e.getMessage());
                            if (Double.isNaN(fxRate)) inDollars = true;     // nothing cached either: show dollars
                        }
                    }
                    String body = getPrices();
                    parsePrices(body);
                    pricesAt = System.currentTimeMillis();
                    cache.edit().putString("prices", body).putLong("pricesAt", pricesAt).apply();
                    notifyListeners();
                } catch (Exception e) {
                    pricesFailedAt = System.currentTimeMillis();
                    AppLog.w("prices: " + e.getMessage());
                } finally {
                    fetchingPrices = false;
                }
            });
        }
        if (!fetchingWeather && (now - weatherAt > WEATHER_EVERY || now < weatherAt) && !failedRecently(weatherFailedAt, now)) {
            fetchingWeather = true;
            io.execute(() -> {
                try {
                    String body = get(weatherUrl());
                    weather = parseWeather(body);
                    weatherAt = System.currentTimeMillis();
                    cache.edit().putString("weather", body).putLong("weatherAt", weatherAt).putString("weatherPlace", weatherPlace()).apply();
                    notifyListeners();
                } catch (Exception e) {
                    weatherFailedAt = System.currentTimeMillis();
                    AppLog.w("weather: " + e.getMessage());
                } finally {
                    fetchingWeather = false;
                }
            });
        }
    }

    private static boolean failedRecently(long at, long now) {
        return at != 0 && now >= at && now - at < 2 * 60_000L;
    }

    private void notifyListeners() {
        main.post(() -> { for (Listener l : new ArrayList<>(listeners)) l.onData(); });
    }

    private void parsePrices(String body) throws Exception {
        JSONArray list = new JSONArray(body);
        String quote = quote();
        double rate = rate();
        int found = 0;
        boolean needsBinance = pricesPath() != null;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            String symbol = o.getString("symbol");
            for (int k = 0; k < COINS.length; k++) {
                if (!symbol.equals(COINS[k] + quote)) continue;
                found++;
                double last = Double.parseDouble(o.optString("lastPrice", o.optString("price", "NaN")));
                double open = Double.parseDouble(o.optString("openPrice", "NaN"));
                prices[k] = last * rate;
                changes[k] = open > 0 ? (last - open) / open * 100 : Double.NaN;
            }
        }
        prices[0] = prices[1] = rate;       // USDT and USDC: one dollar each, in the configured currency
        changes[0] = changes[1] = 0;
        if (needsBinance && found == 0) pricesAt = 0;   // cached for other coins (settings changed): fetch again
    }

    private static Weather parseWeather(String body) throws Exception {
        JSONObject o = new JSONObject(body);
        TimeZone zone = TimeZone.getTimeZone(o.optString("timezone", TimeZone.getDefault().getID()));
        Weather w = new Weather();
        JSONObject current = o.getJSONObject("current");
        w.temp = current.getDouble("temperature_2m");
        w.code = current.getInt("weather_code");
        w.isDay = current.optInt("is_day", 1) == 1;
        JSONObject daily = o.getJSONObject("daily");
        w.max = daily.getJSONArray("temperature_2m_max").getDouble(0);
        w.min = daily.getJSONArray("temperature_2m_min").getDouble(0);
        w.sunrise = localTime(daily.getJSONArray("sunrise").getString(0), zone);
        w.sunset = localTime(daily.getJSONArray("sunset").getString(0), zone);
        JSONObject hourly = o.optJSONObject("hourly");
        if (hourly != null) {
            JSONArray times = hourly.getJSONArray("time");
            JSONArray temps = hourly.getJSONArray("temperature_2m");
            JSONArray codes = hourly.getJSONArray("weather_code");
            JSONArray days = hourly.optJSONArray("is_day");
            long now = System.currentTimeMillis();
            for (int i = 0; i < times.length() && w.hours.size() < 6; i++) {
                long at = localTime(times.getString(i), zone);
                if (at <= now) continue;
                w.hours.add(at);
                w.hourTemp.add(temps.getDouble(i));
                w.hourCode.add(codes.getInt(i));
                w.hourDay.add(days == null || days.optInt(i, 1) == 1);
            }
        }
        return w;
    }

    private static long localTime(String iso, TimeZone zone) throws ParseException {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        f.setTimeZone(zone);
        return f.parse(iso).getTime();
    }

    static List<String[]> findCity(String name) throws Exception {
        String lang = Ui.language.replaceAll("-.*", "");   // Open-Meteo takes the language without the country
        JSONObject o = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=10&format=json&language=" + lang
                + "&name=" + URLEncoder.encode(name.trim(), "UTF-8")));
        JSONArray results = o.optJSONArray("results");
        List<String[]> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();   // places with the same name, state and country read the same
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            String city = r.optString("name", "");
            if (city.isEmpty() || !r.has("latitude") || !r.has("longitude")) continue;
            StringBuilder label = new StringBuilder(city);
            for (String part : new String[] {r.optString("admin1", ""), r.optString("country", "")}) {
                if (!part.isEmpty() && !part.equals(city)) label.append(", ").append(part);
            }
            if (!seen.add(label.toString())) continue;
            out.add(new String[] {label.toString(), city,
                    String.format(Locale.US, "%.4f", r.getDouble("latitude")), String.format(Locale.US, "%.4f", r.getDouble("longitude"))});
        }
        return out;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(6000);
        c.setRequestProperty("User-Agent", "FreeMyTV");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }

    /**
     * Night at your location: from sunset to sunrise (like Apple's "Automatic" appearance: the light design darkens).
     * Uses the last weather fetched (it moves ~1 min a day); without data, 6:00 and 18:00.
     */
    boolean isNight(long now) {
        String theme = Prefs.theme();
        if (theme.equals("dark")) return true;
        if (theme.equals("light")) return false;
        long t = Shaders.timeOfDay(now, 0);
        long rise = Shaders.timeOfDay(weather.sunrise, 6 * 60 * 60_000L);
        long set = Shaders.timeOfDay(weather.sunset, 18 * 60 * 60_000L);
        return t < rise || t >= set;
    }

    // MARK: - Formatting

    private static Locale locale() {
        return Ui.locale();
    }

    /** 580.4k · 21.0k · 1.04k · 5.61 (as short as possible; the dollar always with cents). */
    static String price(double v) {
        if (Double.isNaN(v)) return "—";
        if (v >= 10_000) return String.format(locale(), "%.1fk", v / 1000);
        if (v >= 1000) return String.format(locale(), "%.2fk", v / 1000);
        return String.format(locale(), "%.2f", v);
    }

    /** 416.3k · 12.2k · 1.04k · 531 · 5.05: abbreviated, for the price row on the home screen. */
    static String compact(double v) {
        if (Double.isNaN(v)) return "—";
        if (v >= 10_000) return String.format(locale(), "%.1fk", v / 1000);
        if (v >= 1000) return String.format(locale(), "%.2fk", v / 1000);
        if (v >= 100) return String.format(locale(), "%.0f", v);
        return String.format(locale(), "%.2f", v);
    }

    /**
     * $108,234 · $185.20 · R$ 581.234 · 74 264 € (cents only below a thousand), in the configured currency, with the
     * symbol and spaces where the language puts them.
     */
    static String brl(double v) {
        String symbol = currencySymbol();
        if (Double.isNaN(v)) return symbol + "—";
        return Ui.once("brl", v, () -> {
            DecimalFormat f = (DecimalFormat) NumberFormat.getCurrencyInstance(locale());
            DecimalFormatSymbols s = f.getDecimalFormatSymbols();
            s.setCurrencySymbol(symbol.trim());   // our symbols: ICU would write "US$" outside the US
            f.setDecimalFormatSymbols(s);
            f.setMinimumFractionDigits(v >= 1000 ? 0 : 2);
            f.setMaximumFractionDigits(v >= 1000 ? 0 : 2);
            return f.format(v);
        });
    }

    /** The weather in words, from Open-Meteo's WMO code. */
    static String condition(int code, boolean day) {
        if (code < 0) return "";
        if (code == 0) return day ? Ui.tr("Clear") : Ui.tr("night|Clear");
        if (code == 1) return day ? Ui.tr("Mostly Sunny") : Ui.tr("Mostly Clear");
        if (code == 2) return Ui.tr("Partly Cloudy");
        if (code == 3) return Ui.tr("Cloudy");
        if (code == 45 || code == 48) return Ui.tr("Fog");
        if (code >= 51 && code <= 57) return Ui.tr("Drizzle");
        if (code == 65 || code == 67 || code == 82) return Ui.tr("Heavy Rain");
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return Ui.tr("Snow");
        if (code >= 95) return Ui.tr("Thunderstorms");
        return Ui.tr("Rain");
    }

    /** "H:24°  L:15°" (in Portuguese, "Máx. 24°  Mín. 15°"), with the given space between the two. */
    static String highLow(Weather wx, String space) {
        return Ui.trf("H:{max}", "max", degrees(wx.max)) + space + Ui.trf("L:{min}", "min", degrees(wx.min));
    }

    /** "₹", "$"...: the mark before a short price. */
    static String currencyMark() {
        return currencySymbol().trim();
    }

    private static String currencySymbol() {
        switch (inDollars ? "USD" : CURRENCY) {
            case "USD": return "$";
            case "INR": return "₹";
            case "BRL": return "R$ ";
            case "EUR": return "€";
            case "GBP": return "£";
            case "JPY": return "¥";
            default: return CURRENCY + " ";
        }
    }

    /** Celsius or Fahrenheit (config.properties "temperature": C, F, or auto = Fahrenheit in the US). */
    static String degrees(double v) {
        if (Double.isNaN(v)) return "—";
        return Math.round(fahrenheit() ? v * 9 / 5 + 32 : v) + "°";
    }

    private static boolean fahrenheit() {
        String unit = Prefs.temperature();
        if (unit.equals("F")) return true;
        if (unit.equals("C")) return false;
        return "US".equals(Locale.getDefault().getCountry());
    }

    /** "+1.2%" / "−0.8%" (a real minus sign); "+1,2 %" in the languages that space the percent sign. */
    static String change(double v) {
        if (Double.isNaN(v)) return "";
        return Ui.once("change", v, () -> {
            NumberFormat f = NumberFormat.getPercentInstance(locale());
            f.setMinimumFractionDigits(1);
            f.setMaximumFractionDigits(1);
            return (v >= 0 ? "+" : "−") + f.format(Math.abs(v) / 100);
        });
    }

    static String hour(long at) {
        return Ui.once("hour", at, () -> Ui.datePattern("format|h a").format(at));
    }

    /** How long ago the data was fetched, if it's stale (no internet): "12 min ago". */
    static String staleness(long at, long every) {
        if (at == 0) return "";
        long age = System.currentTimeMillis() - at;
        if (age < every * 2) return "";
        long minutes = age / 60_000;
        return minutes < 60 ? Ui.trf("{n} min ago", "n", minutes) : Ui.trf("{n} h ago", "n", minutes / 60);
    }
}
