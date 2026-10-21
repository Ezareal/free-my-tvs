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
    private static final long EVERY = 30 * 60_000L, FAST = 5 * 60_000L, AHEAD = 30 * 3600_000L, WEEK = 7 * 86_400_000L;
    private static final int SERIE_A = 0, CHAMPIONS = 1, EUROPA = 2, F1 = 3, MOTO = 4, TENNIS = 5;   // also the priority order
    private static final int[] ACCENT = {0xFF0B5FA5, 0xFF1B2A8A, 0xFFE8650A, 0xFFE10600, 0xFFB5470B, 0xFF2E8B57};

    private static Sports instance;

    static synchronized Sports get(Context c) {
        if (instance == null) instance = new Sports(c.getApplicationContext());
        return instance;
    }

    private static final class Event {
        int sport, italian;      // italian: 3 for an Italian club, rider, driver or player (the screen is in Italian)
        long start, minutes;     // minutes: how long it lasts, to move on to the next one when it is over
        String competition, title, home = "", away = "", logoHome = "", logoAway = "", logoComp = "", art = "";
        boolean live, done;
    }

    private final Context context;
    private final SharedPreferences cache;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService art = Executors.newSingleThreadExecutor();     // logos, apart from the schedule fetch
    private final java.util.Map<String, android.graphics.Bitmap> bitmaps = new java.util.LinkedHashMap<>();
    private final java.util.Set<String> loading = new java.util.HashSet<>();
    private final java.util.Map<String, Long> failedAtMap = new java.util.HashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile List<Event> events = new ArrayList<>();
    private volatile long fetchedAt;
    private volatile boolean fetching;

    private Sports(Context c) {
        context = c;
        cache = c.getSharedPreferences("sports", Context.MODE_PRIVATE);
        try {
            fetchedAt = cache.getLong("at2", 0);
            JSONArray a = new JSONArray(cache.getString("events2", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Event e = event(o.getInt("sport"), o.getLong("start"), o.getString("competition"), o.getString("title"));
                e.minutes = o.optLong("minutes", 120);
                e.italian = o.optInt("italian");
                e.live = o.optBoolean("live");
                e.home = o.optString("home");
                e.away = o.optString("away");
                e.logoHome = o.optString("logoHome");
                e.logoAway = o.optString("logoAway");
                e.logoComp = o.optString("logoComp");
                e.art = o.optString("art");
                events.add(e);
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

    /**
     * The Explore card for the event that is on now or next, or null. One that has finished gives way to the next at
     * once. With the screen in Italian (or the country Italy), Italian clubs, riders, drivers and players come first
     * among events at the same time.
     */
    Recs.Pick pick() {
        if (!Config.SPORTS) return null;
        long now = System.currentTimeMillis();
        boolean italy = Ui.it || "IT".equalsIgnoreCase(Config.TMDB_REGION.trim());
        Event best = null;
        for (Event e : events) {
            if (e.done || now >= e.start + e.minutes * 60_000L || e.start > now + AHEAD) continue;
            if (best == null || before(e, best, now, italy)) best = e;
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
        p.home = best.home;
        p.away = best.away;
        p.logoHome = best.logoHome;
        p.logoAway = best.logoAway;
        p.logoComp = best.logoComp;
        p.art = best.art;
        p.title = best.title;
        p.accent = ACCENT[best.sport];
        p.label = best.competition;
        p.pkg = pkg;
        p.detailed = true;
        p.meta = when(best.start, now) + (app.isEmpty() ? "" : " · " + app);
        p.id = (p.title + p.meta).hashCode();
        return p;
    }

    /** Whether `a` should be shown before `b`: what is on now first, then by start (half-hour slots), then by who plays. */
    private static boolean before(Event a, Event b, long now, boolean italy) {
        boolean aLive = a.start <= now, bLive = b.start <= now;
        if (aLive != bLive) return aLive;
        if (!aLive) {
            long slotA = a.start / (30 * 60_000L), slotB = b.start / (30 * 60_000L);
            if (slotA != slotB) return slotA < slotB;
        }
        int ia = italy ? a.italian : 0, ib = italy ? b.italian : 0;
        if (ia != ib) return ia > ib;
        if (a.sport != b.sport) return a.sport < b.sport;
        return a.start < b.start;
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
        boolean close = false;     // something is about to start or on: look more often (scores, finished matches)
        for (Event e : events) close |= now >= e.start - 15 * 60_000L && now < e.start + e.minutes * 60_000L;
        long every = events.isEmpty() ? 2 * 60_000L : close ? FAST : EVERY;    // nothing yet: keep trying
        if (now - fetchedAt < every && now >= fetchedAt) return;
        fetching = true;
        io.execute(() -> {
            try {
            List<Event> found = new ArrayList<>();
            long from = System.currentTimeMillis();
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
            for (String sport : new String[] {"Motorsport", "Tennis", "Soccer"}) {
                try {
                    sportsDb(found, sport);
                } catch (Exception e) {
                    AppLog.w("sports (" + sport + "): " + e.getMessage());
                }
            }
            if (!found.isEmpty()) {     // all sources failing (no internet) keeps what we had
                events = found;
                save(found);
            }
            fetchedAt = System.currentTimeMillis();
            main.post(() -> Recs.get(context).changed());
            } finally {
                fetching = false;
                fetchedAt = Math.max(fetchedAt, System.currentTimeMillis());
            }
        });
    }

    private void save(List<Event> list) {
        try {
            JSONArray a = new JSONArray();
            for (Event e : list) {
                a.put(new JSONObject().put("sport", e.sport).put("start", e.start).put("competition", e.competition)
                        .put("title", e.title).put("minutes", e.minutes).put("italian", e.italian).put("live", e.live)
                        .put("home", e.home).put("away", e.away).put("logoHome", e.logoHome).put("logoAway", e.logoAway)
                        .put("logoComp", e.logoComp).put("art", e.art));
            }
            cache.edit().putString("events2", a.toString()).putLong("at2", System.currentTimeMillis()).apply();
        } catch (Exception ignored) {
        }
    }

    // MARK: - Sources

    private static final String[] ITALIAN_PLAYERS = {"sinner", "musetti", "berrettini", "paolini", "cobolli", "arnaldi",
            "darderi", "errani", "cocciaretto", "sonego", "fognini", "bellucci", "nardi", "passaro", "bronzetti", "trevisan"};

    private void football(List<Event> out, String key, long from) throws Exception {
        java.util.Set<String> italian = serieATeams(key);
        String url = "https://api.football-data.org/v4/matches?competitions=SA,CL&dateFrom=" + day(from - 86_400_000L)
                + "&dateTo=" + day(from + 3 * 86_400_000L);
        JSONArray matches = new JSONObject(get(url, key)).getJSONArray("matches");
        for (int i = 0; i < matches.length(); i++) {
            JSONObject m = matches.getJSONObject(i);
            JSONObject comp = m.getJSONObject("competition");
            String code = comp.optString("code");
            if (!code.equals("SA") && !code.equals("CL")) continue;
            String status = m.optString("status");
            long start = utc(m.getString("utcDate"));
            if (start == 0 || status.equals("POSTPONED") || status.equals("CANCELLED") || status.equals("SUSPENDED")) continue;
            JSONObject home = m.getJSONObject("homeTeam"), away = m.getJSONObject("awayTeam");
            Event e = event(code.equals("SA") ? SERIE_A : CHAMPIONS, start,
                    code.equals("SA") ? "Serie A" : "Champions League", team(home) + " – " + team(away));
            e.minutes = 115;
            e.done = status.equals("FINISHED") || status.equals("AWARDED");
            e.live = status.equals("IN_PLAY") || status.equals("PAUSED");
            e.home = team(home);
            e.away = team(away);
            e.logoHome = home.optString("crest");
            e.logoAway = away.optString("crest");
            String[] info = code.equals("SA") ? leagueInfo(4332, "serie a") : leagueInfo(4480, "champions");
            e.logoComp = info[0].isEmpty() ? comp.optString("emblem") : info[0];
            e.art = code.equals("CL") && !Config.CHAMPIONS_ART.trim().isEmpty() ? Config.CHAMPIONS_ART.trim() : info[1];
            e.italian = italian.contains(home.optString("id")) || italian.contains(away.optString("id")) ? 3 : 0;
            out.add(e);
        }
    }

    /** Ids of the Serie A clubs (the Italian ones in the Champions League too), kept for a week. */
    private java.util.Set<String> serieATeams(String key) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        long saved = cache.getLong("teamsAt", 0);
        String list = cache.getString("teams", "");
        if (list.isEmpty() || System.currentTimeMillis() - saved > WEEK) {
            try {
                JSONArray teams = new JSONObject(get("https://api.football-data.org/v4/competitions/SA/teams", key)).getJSONArray("teams");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < teams.length(); i++) sb.append(i == 0 ? "" : ",").append(teams.getJSONObject(i).optInt("id"));
                list = sb.toString();
                cache.edit().putString("teams", list).putLong("teamsAt", System.currentTimeMillis()).apply();
            } catch (Exception e) {
                AppLog.w("Serie A teams: " + e.getMessage());
            }
        }
        for (String id : list.split(",")) if (!id.isEmpty()) ids.add(id);
        return ids;
    }

    private static String team(JSONObject t) {
        String name = t.optString("shortName", "");
        return name.isEmpty() || "null".equals(name) ? t.optString("name") : name;
    }

    private void formula1(List<Event> out) throws Exception {
        JSONObject race = new JSONObject(get("https://api.jolpi.ca/ergast/f1/current/next.json", null))
                .getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races").getJSONObject(0);
        String name = race.optString("raceName");
        String[] info = leagueInfo(4370, "formula");
        Object[][] sessions = {{"Qualifying", Ui.tr("Classificação", "Qualifying", "Clasificación", "Qualifiche"), 75L},
                {"Sprint", "Sprint", 60L}, {"", Ui.tr("Corrida", "Race", "Carrera", "Gara"), 120L}};
        for (Object[] s : sessions) {
            String part = (String) s[0];
            JSONObject o = part.isEmpty() ? race : race.optJSONObject(part);
            if (o == null) continue;
            long start = utc(o.optString("date") + "T" + o.optString("time", "12:00:00Z"));
            if (start == 0) continue;
            Event e = event(F1, start, "Formula 1", name + " · " + s[1]);
            e.minutes = (Long) s[2];
            e.italian = 3;      // Ferrari (and an Italian driver on the grid)
            e.logoComp = info[0];
            e.art = info[1];
            out.add(e);
        }
    }

    private static final String[] ITALIAN_CLUBS = {"inter", "milan", "juventus", "napoli", "roma", "lazio", "atalanta",
            "fiorentina", "bologna", "torino", "genoa", "udinese"};

    /** TheSportsDB's day listing for a sport over the next days: MotoGP, tennis finals and the Europa League. */
    private void sportsDb(List<Event> out, String sport) throws Exception {
        boolean tennis = sport.equals("Tennis"), soccer = sport.equals("Soccer");
        String[] moto = sport.equals("Motorsport") ? leagueInfo(4407, "moto") : new String[] {"", ""};
        String[] europa = soccer ? leagueInfo(4481, "europa") : new String[] {"", ""};
        for (int d = 0; d < 3; d++) {
            long when = System.currentTimeMillis() + d * 86_400_000L;
            JSONObject o = new JSONObject(get("https://www.thesportsdb.com/api/v1/json/3/eventsday.php?d=" + day(when)
                    + "&s=" + sport, null));
            JSONArray list = o.optJSONArray("events");
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject j = list.getJSONObject(i);
                String league = j.optString("strLeague"), title = j.optString("strEvent");
                String lower = league.toLowerCase(Locale.ROOT);
                String text = (league + " " + title + " " + j.optString("strRound")).toLowerCase(Locale.ROOT);
                if (soccer ? !(lower.contains("europa league") && !lower.contains("conference") && !lower.contains("women"))
                        : tennis ? !(text.contains("final") && (text.contains("atp") || text.contains("wta")
                        || text.contains("grand slam"))) : !lower.contains("motogp")) continue;
                long start = utc(j.optString("strTimestamp"));
                if (start == 0) continue;
                Event e = event(soccer ? EUROPA : tennis ? TENNIS : MOTO, start,
                        soccer ? "Europa League" : tennis ? "Tennis" : "MotoGP", title);
                e.minutes = soccer ? 115 : tennis ? 180 : 75;
                String[] info = soccer ? europa : moto;
                e.logoComp = !info[0].isEmpty() ? info[0] : clean(j.optString("strLeagueBadge"));
                e.art = soccer && !Config.EUROPA_ART.trim().isEmpty() ? Config.EUROPA_ART.trim()
                        : !info[1].isEmpty() ? info[1] : clean(j.optString("strThumb"));
                if (soccer || tennis) {
                    e.home = j.optString("strHomeTeam");
                    e.away = j.optString("strAwayTeam");
                    e.logoHome = clean(j.optString("strHomeTeamBadge"));
                    e.logoAway = clean(j.optString("strAwayTeamBadge"));
                    e.italian = soccer ? (italianClub(e.home + " " + e.away) ? 3 : 0) : (italianPlayer(e.home + " " + e.away) ? 3 : 0);
                } else {
                    e.italian = 3;     // Ducati and the Italian riders
                }
                out.add(e);
            }
        }
    }

    private static boolean italianClub(String names) {
        String n = names.toLowerCase(Locale.ROOT);
        for (String p : ITALIAN_CLUBS) if (n.contains(p)) return true;
        return false;
    }

    private static boolean italianPlayer(String names) {
        String n = names.toLowerCase(Locale.ROOT);
        for (String p : ITALIAN_PLAYERS) if (n.contains(p)) return true;
        return false;
    }

    private static String clean(String url) {
        return url == null || url.isEmpty() || "null".equals(url) ? "" : url;
    }

    /**
     * {logo, picture} of a league from TheSportsDB by id, only if its name contains `must` (so a wrong id gives
     * nothing); kept for a week. The picture is the league's wide artwork, the card's background.
     */
    private String[] leagueInfo(int id, String must) {
        String key = "league" + id;
        String saved = cache.getString(key, null);
        if (saved != null && System.currentTimeMillis() - cache.getLong(key + "At", 0) < WEEK) return split(saved);
        String logo = "", art = "";
        try {
            JSONArray leagues = new JSONObject(get("https://www.thesportsdb.com/api/v1/json/3/lookupleague.php?id=" + id, null))
                    .optJSONArray("leagues");
            JSONObject l = leagues == null ? null : leagues.optJSONObject(0);
            if (l != null && l.optString("strLeague").toLowerCase(Locale.ROOT).contains(must)) {
                logo = clean(l.optString("strBadge"));
                if (logo.isEmpty()) logo = clean(l.optString("strLogo"));
                art = clean(l.optString("strFanart1"));
                if (art.isEmpty()) art = clean(l.optString("strBanner"));
            }
        } catch (Exception e) {
            AppLog.w("league " + id + ": " + e.getMessage());
            return saved == null ? new String[] {"", ""} : split(saved);
        }
        cache.edit().putString(key, logo + "|" + art).putLong(key + "At", System.currentTimeMillis()).apply();
        return new String[] {logo, art};
    }

    private static String[] split(String saved) {
        String[] parts = saved.split("\\|", -1);
        return new String[] {parts.length > 0 ? parts[0] : "", parts.length > 1 ? parts[1] : ""};
    }

    /** A club's badge (PNG) from TheSportsDB by name: the way back when its crest won't load. */
    private String badgeFor(String name) {
        String key = "badge:" + name.toLowerCase(Locale.ROOT);
        String saved = cache.getString(key, null);
        if (saved != null) return saved;
        String badge = "";
        try {
            JSONArray teams = new JSONObject(get("https://www.thesportsdb.com/api/v1/json/3/searchteams.php?t="
                    + java.net.URLEncoder.encode(name, "UTF-8"), null)).optJSONArray("teams");
            for (int i = 0; teams != null && i < teams.length() && badge.isEmpty(); i++) {
                JSONObject t = teams.getJSONObject(i);
                if (t.optString("strSport").equals("Soccer")) badge = clean(t.optString("strBadge"));
            }
        } catch (Exception e) {
            AppLog.w("badge " + name + ": " + e.getMessage());
            return "";
        }
        cache.edit().putString(key, badge).apply();
        return badge;
    }

    // MARK: - Logos

    /** The logo at `url` if it is ready (otherwise starts loading it and the home screen redraws when it arrives). */
    android.graphics.Bitmap bitmap(String url, String club) {
        if (url == null || url.isEmpty()) return null;
        synchronized (bitmaps) {
            android.graphics.Bitmap b = bitmaps.get(url);
            if (b != null) return b;
            Long failedAt = failedAtMap.get(url);
            if ((failedAt != null && System.currentTimeMillis() - failedAt < 10 * 60_000L) || !loading.add(url)) return null;
        }
        art.execute(() -> {
            android.graphics.Bitmap b = null;
            try {
                b = loadLogo(url);
            } catch (IOException | RuntimeException e) {
                AppLog.w("logo " + url + ": " + e.getMessage());
                String other = club == null || club.isEmpty() ? "" : badgeFor(club);
                if (!other.isEmpty()) {
                    try {
                        b = loadLogo(other);
                    } catch (IOException | RuntimeException again) {
                        AppLog.w("badge " + other + ": " + again.getMessage());
                    }
                }
            }
            synchronized (bitmaps) {
                loading.remove(url);
                if (b == null) {
                    failedAtMap.put(url, System.currentTimeMillis());
                } else {
                    bitmaps.put(url, b);
                    while (bitmaps.size() > 16) bitmaps.remove(bitmaps.keySet().iterator().next());
                }
            }
            if (b != null) main.post(() -> Recs.get(context).changed());
        });
        return null;
    }

    /** Downloads (SVG crests go through an image proxy that returns a PNG) and decodes, keeping the file for later. */
    private android.graphics.Bitmap loadLogo(String url) throws IOException {
        java.io.File dir = new java.io.File(context.getCacheDir(), "sports");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        java.io.File f = new java.io.File(dir, Integer.toHexString(url.hashCode()) + ".png");
        if (!f.exists()) {
            String from = url.toLowerCase(Locale.ROOT).endsWith(".svg")
                    ? "https://wsrv.nl/?w=256&h=256&fit=inside&output=png&url=" + java.net.URLEncoder.encode(url, "UTF-8") : url;
            HttpURLConnection c = (HttpURLConnection) new URL(from).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            java.io.File part = new java.io.File(f.getPath() + ".part");
            try (InputStream in = c.getInputStream(); java.io.FileOutputStream o = new java.io.FileOutputStream(part)) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) > 0) o.write(buffer, 0, n);
            } finally {
                c.disconnect();
            }
            if (!part.renameTo(f)) throw new IOException("couldn't save the logo");
        }
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(f.getPath(), bounds);
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inSampleSize = 1;
        while (bounds.outWidth / (opts.inSampleSize * 2) >= 1280) opts.inSampleSize *= 2;    // artwork can be huge
        android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeFile(f.getPath(), opts);
        if (b == null) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            throw new IOException("unreadable logo");
        }
        return b;
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
