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
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sports on the Explore tab: the next match or race worth showing, from free sources.
 *  - Serie A and Champions League: football-data.org (needs config.properties "football_key", free);
 *  - Formula 1: Jolpica (the Ergast successor, no key);
 *  - MotoGP and tennis finals: TheSportsDB's free key (partial calendars).
 * One event at most becomes a card (a Recs.Pick of kind "sport") in the 24 hours around it; the rest of Explore stays
 * films and shows. Which app opens it comes from config.properties "sports_apps" (first one installed).
 */
final class Sports {
    private static final long EVERY = 30 * 60_000L, LIVE_FOR = 150 * 60_000L, AHEAD = 30 * 3600_000L;
    private static final int SERIE_A = 0, CHAMPIONS = 1, F1 = 2, MOTO = 3, TENNIS = 4;   // also the priority order
    private static final int[] ACCENT = {0xFF0B5FA5, 0xFF1B2A8A, 0xFFE10600, 0xFFE8590C, 0xFF2E8B57};

    private static Sports instance;

    static synchronized Sports get(Context c) {
        if (instance == null) instance = new Sports(c.getApplicationContext());
        return instance;
    }

    private static final class Event {
        int sport;
        long start;
        String competition, title;
    }

    private final Context context;
    private final SharedPreferences cache;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile List<Event> events = new ArrayList<>();
    private volatile long fetchedAt;
    private volatile boolean fetching;

    private Sports(Context c) {
        context = c;
        cache = c.getSharedPreferences("sports", Context.MODE_PRIVATE);
        try {
            fetchedAt = cache.getLong("at", 0);
            JSONArray a = new JSONArray(cache.getString("events", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                events.add(event(o.getInt("sport"), o.getLong("start"), o.getString("competition"), o.getString("title")));
            }
        } catch (Exception ignored) {
        }
    }

    private static Event event(int sport, long start, String competition, String title) {
        Event e = new Event();
        e.sport = sport;
        e.start = start;
        e.competition = competition;
        e.title = title;
        return e;
    }

    /** The Explore card for the event that is on now or soon, or null. */
    Recs.Pick pick() {
        if (!Config.SPORTS) return null;
        long now = System.currentTimeMillis();
        Event best = null;
        for (Event e : events) {
            if (e.start < now - LIVE_FOR || e.start > now + AHEAD) continue;
            boolean live = e.start <= now;
            boolean bestLive = best != null && best.start <= now;
            if (best == null || (live && !bestLive) || (live == bestLive && (e.start < best.start
                    || (e.start == best.start && e.sport < best.sport)))) best = e;
        }
        if (best == null) return null;
        String pkg = null, app = "";
        android.content.pm.PackageManager pm = context.getPackageManager();
        for (String candidate : Config.SPORTS_APPS.split(",")) {
            String name = candidate.trim();
            if (!name.isEmpty() && HomeApps.launchIntent(pm, name) != null) {
                pkg = name;
                try {
                    app = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(name, 0)));
                } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
                }
                break;
            }
        }
        Recs.Pick p = new Recs.Pick();
        p.kind = "sport";
        p.title = best.title;
        p.accent = ACCENT[best.sport];
        p.label = best.competition;
        p.pkg = pkg;
        p.detailed = true;
        p.meta = when(best.start, now) + (app.isEmpty() ? "" : " · " + app);
        p.id = (p.title + p.meta).hashCode();
        return p;
    }

    private static String when(long start, long now) {
        if (start <= now) return Ui.tr("Ao vivo", "Live now", "En vivo", "In diretta");
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        a.setTimeInMillis(now);
        b.setTimeInMillis(start);
        String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date(start));
        if (a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR) && a.get(Calendar.YEAR) == b.get(Calendar.YEAR)) {
            return Ui.tr("Hoje", "Today", "Hoy", "Oggi") + " " + time;
        }
        a.add(Calendar.DAY_OF_YEAR, 1);
        if (a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR) && a.get(Calendar.YEAR) == b.get(Calendar.YEAR)) {
            return Ui.tr("Amanhã", "Tomorrow", "Mañana", "Domani") + " " + time;
        }
        return new SimpleDateFormat("EEE", Ui.locale()).format(new Date(start)) + " " + time;
    }

    /** Fetches again if the last look is older than 30 minutes; tells the home screen when it has something new. */
    void refreshIfStale() {
        if (!Config.SPORTS || fetching) return;
        long now = System.currentTimeMillis();
        if (now - fetchedAt < EVERY && now >= fetchedAt) return;
        fetching = true;
        io.execute(() -> {
            List<Event> found = new ArrayList<>();
            long from = System.currentTimeMillis() - LIVE_FOR;
            String key = Config.FOOTBALL_KEY.trim();
            if (!key.isEmpty()) {
                try {
                    football(found, key, from);
                } catch (Exception e) {
                    AppLog.w("football: " + e.getMessage());
                }
            }
            try {
                formula1(found);
            } catch (Exception e) {
                AppLog.w("formula 1: " + e.getMessage());
            }
            for (String sport : new String[] {"Motorsport", "Tennis"}) {
                try {
                    sportsDb(found, sport);
                } catch (Exception e) {
                    AppLog.w("sports (" + sport + "): " + e.getMessage());
                }
            }
            events = found;
            fetchedAt = System.currentTimeMillis();
            save(found);
            fetching = false;
            main.post(() -> Recs.get(context).changed());
        });
    }

    private void save(List<Event> list) {
        try {
            JSONArray a = new JSONArray();
            for (Event e : list) {
                a.put(new JSONObject().put("sport", e.sport).put("start", e.start).put("competition", e.competition)
                        .put("title", e.title));
            }
            cache.edit().putString("events", a.toString()).putLong("at", fetchedAt).apply();
        } catch (Exception ignored) {
        }
    }

    // MARK: - Sources

    private void football(List<Event> out, String key, long from) throws Exception {
        String url = "https://api.football-data.org/v4/matches?competitions=SA,CL&dateFrom=" + day(from - 86_400_000L)
                + "&dateTo=" + day(from + 3 * 86_400_000L);
        JSONArray matches = new JSONObject(get(url, key)).getJSONArray("matches");
        for (int i = 0; i < matches.length(); i++) {
            JSONObject m = matches.getJSONObject(i);
            String code = m.getJSONObject("competition").optString("code");
            if (!code.equals("SA") && !code.equals("CL")) continue;
            long start = utc(m.getString("utcDate"));
            if (start == 0) continue;
            out.add(event(code.equals("SA") ? SERIE_A : CHAMPIONS, start,
                    code.equals("SA") ? "Serie A" : "Champions League",
                    team(m.getJSONObject("homeTeam")) + " – " + team(m.getJSONObject("awayTeam"))));
        }
    }

    private static String team(JSONObject t) {
        String name = t.optString("shortName", "");
        return name.isEmpty() || "null".equals(name) ? t.optString("name") : name;
    }

    private void formula1(List<Event> out) throws Exception {
        JSONObject race = new JSONObject(get("https://api.jolpi.ca/ergast/f1/current/next.json", null))
                .getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races").getJSONObject(0);
        String name = race.optString("raceName");
        String[][] sessions = {{"Qualifying", Ui.tr("Classificação", "Qualifying", "Clasificación", "Qualifiche")},
                {"Sprint", "Sprint"}, {"", Ui.tr("Corrida", "Race", "Carrera", "Gara")}};
        for (String[] s : sessions) {
            JSONObject o = s[0].isEmpty() ? race : race.optJSONObject(s[0]);
            if (o == null) continue;
            long start = utc(o.optString("date") + "T" + o.optString("time", "12:00:00Z"));
            if (start != 0) out.add(event(F1, start, "Formula 1", name + " · " + s[1]));
        }
    }

    /** TheSportsDB's day listing for a sport over the next days: MotoGP, and tennis finals. */
    private void sportsDb(List<Event> out, String sport) throws Exception {
        boolean tennis = sport.equals("Tennis");
        for (int d = 0; d < 3; d++) {
            long when = System.currentTimeMillis() + d * 86_400_000L;
            JSONObject o = new JSONObject(get("https://www.thesportsdb.com/api/v1/json/3/eventsday.php?d=" + day(when)
                    + "&s=" + sport, null));
            JSONArray list = o.optJSONArray("events");
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject e = list.getJSONObject(i);
                String league = e.optString("strLeague"), title = e.optString("strEvent");
                String text = (league + " " + title + " " + e.optString("strRound")).toLowerCase(Locale.ROOT);
                if (tennis ? !(text.contains("final") && (text.contains("atp") || text.contains("wta")
                        || text.contains("grand slam"))) : !league.toLowerCase(Locale.ROOT).contains("motogp")) continue;
                long start = utc(e.optString("strTimestamp"));
                if (start != 0) out.add(event(tennis ? TENNIS : MOTO, start, tennis ? "Tennis" : "MotoGP", title));
            }
        }
    }

    // MARK: - Helpers

    private static String get(String url, String token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("Accept", "application/json");
        if (token != null) c.setRequestProperty("X-Auth-Token", token);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }

    private static String day(long at) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(at));
    }

    /** "2026-10-21T19:00:00Z" (or without the Z, or with fractions) in UTC, as milliseconds; 0 if unreadable. */
    private static long utc(String text) {
        if (text == null || text.length() < 19) return 0;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(text.substring(0, 19)).getTime();
        } catch (java.text.ParseException e) {
            return 0;
        }
    }
}
