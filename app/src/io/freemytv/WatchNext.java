package io.freemytv;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the TV's apps say you're in the middle of: their "Continue watching" entries (Android TV's Watch Next, the row
 * on Google TV's own home screen; Nuvio, YouTube, Prime Video and others fill it). Reading other apps' entries takes
 * READ_TV_LISTINGS, allowed once in Home Settings > Continue Watching; without it the list is empty. The TV shares only
 * the entries each app marked as searchable (the default).
 */
final class WatchNext {
    private WatchNext() {}

    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    /** Watch Next kinds: continue, next episode, new. A watchlist (3) is not something you're in the middle of. */
    private static final int WATCHLIST = 3;
    /** Program types (TvContract.WatchNextPrograms.TYPE_*): series, season and episode are a show. */
    static final int TYPE_MOVIE = 0, TYPE_SERIES = 1, TYPE_SEASON = 2, TYPE_EPISODE = 3;

    static final class Entry {
        long id, position, duration, engaged;
        String pkg, title, episodeTitle, season, episode, image, intent;
        int type;
    }

    // Column names as text: some of TvContract's column interfaces aren't public API.
    private static final String[] COLUMNS = {"_id", "package_name", "title", "episode_title", "season_display_number",
            "episode_display_number", "poster_art_uri", "poster_art_aspect_ratio", "thumbnail_uri", "thumbnail_aspect_ratio",
            "intent_uri", "type", "watch_next_type", "last_playback_position_millis", "duration_millis",
            "last_engagement_time_utc_millis"};

    static boolean allowed(Context c) {
        return c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Every entry the TV shares (each with something to open), most recent first. Background thread. */
    static List<Entry> all(Context c) {
        List<Entry> out = new ArrayList<>();
        if (!allowed(c)) return out;
        try (Cursor cur = c.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, COLUMNS, null, null, null)) {
            while (cur != null && cur.moveToNext()) {
                if (!cur.isNull(12) && cur.getInt(12) == WATCHLIST) continue;
                Entry e = new Entry();
                e.id = cur.getLong(0);
                e.pkg = cur.getString(1);
                e.title = cur.getString(2);
                e.intent = cur.getString(10);
                if (e.pkg == null || e.title == null || e.title.isEmpty() || e.intent == null || e.intent.isEmpty()) continue;
                e.episodeTitle = cur.getString(3);
                e.season = cur.getString(4);
                e.episode = cur.getString(5);
                e.image = landscape(cur.getString(6), cur.isNull(7) ? -1 : cur.getInt(7), cur.getString(8), cur.isNull(9) ? -1 : cur.getInt(9));
                e.type = cur.isNull(11) ? -1 : cur.getInt(11);
                e.position = cur.getLong(13);
                e.duration = cur.getLong(14);
                e.engaged = cur.getLong(15);
                out.add(e);
            }
        } catch (RuntimeException e) {
            AppLog.w("continue watching: " + e.getMessage());
        }
        Collections.sort(out, (a, b) -> Long.compare(b.engaged, a.engaged));
        return out;
    }

    /** How many entries each app shares, for the settings list. Background thread. */
    static Map<String, Integer> counts(Context c) {
        Map<String, Integer> out = new HashMap<>();
        for (Entry e : all(c)) out.put(e.pkg, out.containsKey(e.pkg) ? out.get(e.pkg) + 1 : 1);
        return out;
    }

    /** The wide picture for a card: the poster when it's 16:9, 3:2 or 4:3 (ratios 0 to 2), else the thumbnail. */
    private static String landscape(String poster, int posterRatio, String thumb, int thumbRatio) {
        boolean posterWide = posterRatio >= 0 && posterRatio <= 2, thumbWide = thumbRatio >= 0 && thumbRatio <= 2;
        if (poster != null && !poster.isEmpty() && (posterWide || thumb == null || thumb.isEmpty() || !thumbWide)) return poster;
        return thumb == null || thumb.isEmpty() ? null : thumb;
    }
}
