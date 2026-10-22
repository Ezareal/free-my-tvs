package io.freemytv;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The dock's streaming apps, by use. Once a day (never while someone is navigating: it runs when the home screen
 * starts), the streaming app used most over the last weeks can take the place of the one used least, one swap a day at
 * most, and only when it is clearly ahead: a single open never moves anything. Use comes from Android's usage
 * statistics when the permission is there, plus the opens made from this launcher; both fade with a 14-day half-life.
 * Apps that lose their place stay in "More apps". In manual mode (Settings > dock order, or after moving an app
 * yourself) nothing here runs.
 */
final class DockAuto {
    private static final int DOCK_VISIBLE = 6;
    private static final double HALF_LIFE_DAYS = 14, WIN_MARGIN = 1.35, OPEN_MINUTES = 4;
    private static final long DAY = 86_400_000L;

    /** Services that can be on the dock: each row is one service (its packages, the first installed one counts). */
    private static final String[][] BASE_SERVICES = {
        {"com.netflix.ninja"},
        {"com.amazon.firetv.youtube", "com.google.android.youtube.tv"},
        {"com.hbo.hbonow", "com.wbd.stream"},
        {"com.disney.disneyplus"},
        {"com.univision.prendetv"},
        {"org.smarttube.stable", "org.smarttube.beta", "com.teamsmart.videomanager.tv"},
        {"com.apple.atve.amazon.appletv", "com.apple.atve.androidtv.appletv", "com.apple.atve.sony.appletv"},
        {"com.mercadolibre.android.mplay_25"},
        {"tv.pluto.android"},
        {"com.amazon.amazonvideo.livingroom"},
        {"com.crunchyroll.crunchyroid"},
        {"com.cbs.ca", "com.cbs.ott"},
        {"com.hulu.livingroomplus"},
        {"com.peacocktv.peacockandroid"},
        {"com.globo.globotv"},
    };

    /** The known services plus the ones found on this TV (see looksStreaming) or pinned by hand. */
    private static volatile String[][] SERVICES = BASE_SERVICES;

    private DockAuto() {}

    private static final java.util.regex.Pattern AUDIO = java.util.regex.Pattern.compile("(?i)spotify|music|audio|radio|podcast|deezer|tunein|karaoke");

    /**
     * An app that looks like a streaming or video service, with no list to maintain: a user app that publishes its
     * programs to the TV's home (the WRITE_EPG_DATA permission every Android TV streaming app asks for), is not audio-only
     * (by name) and is not a tool. Anything else it gets wrong is one long press away in "More apps".
     */
    static boolean looksStreaming(Context c, String pkg) {
        if (pkg.equals(c.getPackageName()) || HomeApps.isHiddenOrTool(pkg) || AUDIO.matcher(pkg).find()) return false;
        PackageManager pm = c.getPackageManager();
        try {
            android.content.pm.PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
            if ((info.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) return false;
            if (info.requestedPermissions == null) return false;
            for (String perm : info.requestedPermissions) {
                if (perm.endsWith("WRITE_EPG_DATA")) return !AUDIO.matcher(String.valueOf(pm.getApplicationLabel(info.applicationInfo))).find();
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
        }
        return false;
    }

    /** Hold OK on an app in "More apps": it joins the dock (and stays a streaming app for the daily order). */
    static void pin(Context c, String pkg) {
        java.util.Set<String> pinned = new java.util.HashSet<>(Prefs.dockPinned()), out = Prefs.dockUnpinned();
        pinned.add(pkg);
        out.remove(pkg);
        Prefs.setDockPinned(pinned);
        Prefs.setDockUnpinned(out);
        List<String> shelf = new ArrayList<>(HomeApps.shelfPackages(c));
        shelf.remove(HomeApps.PIN_LAST);
        if (!shelf.contains(pkg)) shelf.add(pkg);
        Prefs.setShelfAuto(shelf);
    }

    /** Reorder mode, down: the app leaves the dock for "More apps" and the daily update leaves it there. */
    static void unpin(Context c, String pkg) {
        java.util.Set<String> pinned = Prefs.dockPinned(), out = Prefs.dockUnpinned();
        pinned.remove(pkg);
        out.add(pkg);
        Prefs.setDockPinned(pinned);
        Prefs.setDockUnpinned(out);
        List<String> shelf = new ArrayList<>(HomeApps.shelfPackages(c));
        shelf.remove(pkg);
        shelf.remove(HomeApps.PIN_LAST);
        Prefs.setShelfAuto(shelf);
    }

    private static void rebuildServices(Context c) {
        List<String[]> rows = new ArrayList<>(java.util.Arrays.asList(BASE_SERVICES));
        java.util.Set<String> known = new HashSet<>();
        for (String[] r : BASE_SERVICES) known.addAll(java.util.Arrays.asList(r));
        java.util.Set<String> pinned = Prefs.dockPinned();
        for (String pkg : pinned) if (!known.contains(pkg) && HomeApps.launchIntent(c.getPackageManager(), pkg) != null) { rows.add(new String[] {pkg}); known.add(pkg); }
        for (String[] app : HomeApps.launchable(c)) {
            if (!known.contains(app[0]) && looksStreaming(c, app[0])) { rows.add(new String[] {app[0]}); known.add(app[0]); }
        }
        SERVICES = rows.toArray(new String[0][]);
    }

    static boolean isStreaming(String pkg) {
        return service(pkg) >= 0;
    }

    private static int service(String pkg) {
        for (int i = 0; i < SERVICES.length; i++) for (String p : SERVICES[i]) if (p.equals(pkg)) return i;
        return -1;
    }

    /** Runs the daily update if today's has not happened (off the main thread). */
    static void updateIfDue(Context c) {
        if (!Prefs.dockAuto()) return;
        final String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        if (today.equals(Prefs.dockDay())) return;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                update(app, today);
            } catch (RuntimeException e) {
                AppLog.w("dock update: " + e.getMessage());
            }
        }, "dock-auto").start();
    }

    private static synchronized void update(Context c, String today) {
        if (!Prefs.dockAuto() || today.equals(Prefs.dockDay())) return;
        PackageManager pm = c.getPackageManager();
        rebuildServices(c);
        // The installed package of each service (the first of its list).
        Map<Integer, String> installed = new HashMap<>();
        java.util.Set<String> left = Prefs.dockUnpinned();
        for (int i = 0; i < SERVICES.length; i++) {
            for (String p : SERVICES[i]) {
                if (!left.contains(p) && HomeApps.launchIntent(pm, p) != null) {
                    installed.put(i, p);
                    break;
                }
            }
        }
        List<String> shelf = new ArrayList<>(HomeApps.shelfPackages(c));
        shelf.remove(HomeApps.PIN_LAST);   // the pinned app is added at the end when the dock is read, not ranked here
        // An app that was uninstalled leaves the dock (no holes); the rest keep their places.
        List<String> kept = new ArrayList<>();
        for (String p : shelf) if (HomeApps.launchIntent(pm, p) != null && !kept.contains(p)) kept.add(p);
        shelf = kept;
        Map<Integer, Double> score = scores(c, installed);
        Set<Integer> onDock = new HashSet<>();
        for (String p : shelf) onDock.add(service(p));
        // Every streaming app on the TV is on the dock: the six first are what shows, the rest are a scroll away.
        List<Integer> missing = new ArrayList<>();
        for (int sv : installed.keySet()) if (!onDock.contains(sv)) missing.add(sv);
        java.util.Collections.sort(missing, (x, y) -> Double.compare(value(score, y), value(score, x)));
        StringBuilder change = new StringBuilder();
        for (int sv : missing) {
            shelf.add(installed.get(sv));
            change.append(" +").append(installed.get(sv));
        }
        // One swap a day at most, and only when clearly ahead: the best app beyond the window against the weakest one
        // inside it (apps that are not streaming are never touched).
        int visible = Math.min(DOCK_VISIBLE, shelf.size());
        int best = -1, weakest = -1;
        for (int i = visible; i < shelf.size(); i++) {
            int sv = service(shelf.get(i));
            if (sv >= 0 && (best < 0 || value(score, sv) > value(score, service(shelf.get(best))))) best = i;
        }
        for (int i = 0; i < visible; i++) {
            int sv = service(shelf.get(i));
            if (sv >= 0 && (weakest < 0 || value(score, sv) < value(score, service(shelf.get(weakest))))) weakest = i;
        }
        if (best >= 0 && weakest >= 0) {
            double ahead = value(score, service(shelf.get(best))), incumbent = value(score, service(shelf.get(weakest)));
            if (ahead > incumbent * WIN_MARGIN && ahead - incumbent >= 10) {
                change.append(" swap ").append(shelf.get(weakest)).append(" <-> ").append(shelf.get(best));
                String t = shelf.get(weakest);
                shelf.set(weakest, shelf.get(best));
                shelf.set(best, t);
            }
        }
        // Beyond the window, the streaming apps go by use (it moves nothing that shows).
        List<Integer> tailSlots = new ArrayList<>();
        List<String> tailApps = new ArrayList<>();
        for (int i = visible; i < shelf.size(); i++) {
            if (service(shelf.get(i)) >= 0) {
                tailSlots.add(i);
                tailApps.add(shelf.get(i));
            }
        }
        java.util.Collections.sort(tailApps, (x, y) -> Double.compare(value(score, service(y)), value(score, service(x))));
        for (int i = 0; i < tailSlots.size(); i++) shelf.set(tailSlots.get(i), tailApps.get(i));
        String changed = change.length() == 0 ? "none" : change.toString();
        change = new StringBuilder(changed);
        Prefs.setShelfAuto(shelf);
        Prefs.setDockDay(today);
        AppLog.w("dock auto " + today + ": " + change + " dock=" + shelf + " scores=" + score);
    }

    private static double value(Map<Integer, Double> score, int s) {
        Double v = score.get(s);
        return v == null ? 0 : v;
    }

    /** Minutes of use, faded by age: Android's statistics (30 days) plus the opens from this launcher. */
    private static Map<Integer, Double> scores(Context c, Map<Integer, String> installed) {
        Map<Integer, Double> out = new HashMap<>();
        long now = System.currentTimeMillis();
        Map<String, Double> byPackage = new HashMap<>();
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            boolean allowed = ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            if (allowed && usm != null) {
                List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 30 * DAY, now);
                if (stats != null) {
                    for (UsageStats u : stats) {
                        double age = Math.max(0, now - u.getLastTimeUsed()) / (double) DAY;
                        double minutes = u.getTotalTimeInForeground() / 60_000.0;
                        Double old = byPackage.get(u.getPackageName());
                        byPackage.put(u.getPackageName(), (old == null ? 0 : old) + minutes * Math.pow(0.5, age / HALF_LIFE_DAYS));
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        for (Map.Entry<Integer, String> e : installed.entrySet()) {
            double total = 0;
            for (String p : SERVICES[e.getKey()]) {
                Double u = byPackage.get(p);
                if (u != null) total += u;
                total += Prefs.openScore(p, now) * OPEN_MINUTES;
            }
            out.put(e.getKey(), total);
        }
        return out;
    }
}
