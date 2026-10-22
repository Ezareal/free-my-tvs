package io.freemytv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Browse's search and filters, on TMDB: a text search (by name) or, with no text, a discover by genre, year and
 * streaming service; movies, shows or both. One call gives one page of results.
 */
final class SearchEngine {
    private SearchEngine() {}

    static final String ALL = "all", MOVIE = "movie", TV = "tv";

    static final class Filters {
        String query = "";
        String type = ALL;
        String defaultType = ALL;       // follows the tab the screen was opened from
        int genre, year;                // 0 = any
        /** "" = the default (the TV's apps when browsing, anything when searching), "mine", "any" or a TMDB service id. */
        String platform = "";

        boolean active() {
            return !query.isEmpty() || genre != 0 || year != 0 || !platform.isEmpty() || !type.equals(defaultType);
        }

        void reset() {
            query = "";
            type = defaultType;
            genre = 0;
            year = 0;
            platform = "";
        }
    }

    static final class Page {
        final List<BrowseView.Item> items = new ArrayList<>();
        boolean done;
    }

    /** TMDB's show genres that movies spell differently, and the other way round. */
    private static final Map<Integer, Integer> TV_GENRE = new HashMap<>();
    private static final Set<Integer> TV_VALID = new HashSet<>(Arrays.asList(18, 35, 80, 9648, 16, 99, 10751, 37, 10759, 10765, 10762, 10764, 10768));
    static {
        TV_GENRE.put(28, 10759);
        TV_GENRE.put(12, 10759);
        TV_GENRE.put(878, 10765);
        TV_GENRE.put(14, 10765);
        TV_GENRE.put(10752, 10768);
    }

    /** The genre id to ask a kind for, or -1 when that kind has no such genre (no results from it). */
    static int genreFor(String kind, int genre) {
        if (genre == 0 || kind.equals(MOVIE)) return genre;
        int id = TV_GENRE.containsKey(genre) ? TV_GENRE.get(genre) : genre;
        return TV_VALID.contains(id) ? id : -1;
    }

    private static final ExecutorService LOOKUP = Executors.newFixedThreadPool(6);
    private static final Map<String, Set<String>> SERVICES_OF = new HashMap<>();

    static Page fetch(Recs recs, Filters f, int page, String mine) throws Exception {
        List<String> kinds = f.type.equals(ALL) ? Arrays.asList(MOVIE, TV) : Arrays.asList(f.type);
        List<List<BrowseView.Item>> lists = new ArrayList<>();
        Page out = new Page();
        out.done = true;
        for (String kind : kinds) {
            int genre = genreFor(kind, f.genre);
            if (genre < 0) continue;
            String path = f.query.isEmpty() ? discover(f, kind, genre, page, mine) : search(f, kind, page);
            JSONObject o = recs.tmdb(path);
            JSONArray results = o.getJSONArray("results");
            if (page < Math.min(500, o.optInt("total_pages", 1)) && results.length() > 0) out.done = false;
            List<BrowseView.Item> items = new ArrayList<>();
            for (int i = 0; i < results.length(); i++) {
                JSONObject j = results.getJSONObject(i);
                if (!f.query.isEmpty() && genre != 0 && !hasGenre(j, genre)) continue;
                BrowseView.Item it = BrowseView.parseItem(j, kind);
                if (it != null) items.add(it);
            }
            // A text search can't ask TMDB for a service: check each result's services (in parallel) instead.
            if (!f.query.isEmpty()) items = byService(recs, f, kind, items, mine);
            lists.add(items);
        }
        for (int i = 0; ; i++) {
            boolean any = false;
            for (List<BrowseView.Item> l : lists) {
                if (i < l.size()) {
                    out.items.add(l.get(i));
                    any = true;
                }
            }
            if (!any) break;
        }
        return out;
    }

    private static String discover(Filters f, String kind, int genre, int page, String mine) throws Exception {
        StringBuilder p = new StringBuilder("/discover/" + kind + "?language=" + Recs.language() + "&include_adult=false&sort_by=popularity.desc&vote_count.gte=5");
        String services = f.platform.isEmpty() || f.platform.equals("mine") ? mine : f.platform.equals("any") ? "" : f.platform;
        if (!services.isEmpty()) {
            p.append("&watch_region=").append(Recs.region()).append("&with_watch_monetization_types=flatrate")
                    .append("&with_watch_providers=").append(URLEncoder.encode(services, "UTF-8"));
        }
        if (genre > 0) p.append("&with_genres=").append(genre);
        if (f.year > 0) p.append(kind.equals(MOVIE) ? "&primary_release_year=" : "&first_air_date_year=").append(f.year);
        return p.append("&page=").append(page).toString();
    }

    private static String search(Filters f, String kind, int page) throws Exception {
        StringBuilder p = new StringBuilder("/search/" + kind + "?language=" + Recs.language() + "&include_adult=false&query="
                + URLEncoder.encode(f.query, "UTF-8"));
        if (f.year > 0) p.append(kind.equals(MOVIE) ? "&primary_release_year=" : "&first_air_date_year=").append(f.year);
        return p.append("&page=").append(page).toString();
    }

    private static boolean hasGenre(JSONObject j, int genre) {
        JSONArray ids = j.optJSONArray("genre_ids");
        for (int i = 0; ids != null && i < ids.length(); i++) if (ids.optInt(i) == genre) return true;
        return false;
    }

    /** Keeps the results on the chosen service(s); with the default platform a search shows everything. */
    private static List<BrowseView.Item> byService(Recs recs, Filters f, String kind, List<BrowseView.Item> items, String mine) throws Exception {
        if (f.platform.isEmpty() || f.platform.equals("any")) return items;
        final Set<String> wanted = new HashSet<>(Arrays.asList((f.platform.equals("mine") ? mine : f.platform).split("\\|")));
        List<Future<Boolean>> checks = new ArrayList<>();
        for (BrowseView.Item it : items) {
            checks.add(LOOKUP.submit((Callable<Boolean>) () -> {
                for (String id : servicesOf(recs, kind, it.id)) if (wanted.contains(id)) return true;
                return false;
            }));
        }
        List<BrowseView.Item> kept = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) if (checks.get(i).get()) kept.add(items.get(i));
        return kept;
    }

    private static Set<String> servicesOf(Recs recs, String kind, int id) {
        String key = kind + id;
        synchronized (SERVICES_OF) {
            Set<String> known = SERVICES_OF.get(key);
            if (known != null) return known;
        }
        Set<String> ids = new HashSet<>();
        try {
            JSONObject r = recs.tmdb("/" + kind + "/" + id + "/watch/providers").getJSONObject("results");
            JSONObject region = r.optJSONObject(Recs.region());
            JSONArray flat = region == null ? null : region.optJSONArray("flatrate");
            for (int i = 0; flat != null && i < flat.length(); i++) ids.add(String.valueOf(flat.getJSONObject(i).optInt("provider_id")));
        } catch (Exception e) {
            return ids;     // not cached: tried again next time
        }
        synchronized (SERVICES_OF) {
            if (SERVICES_OF.size() > 600) SERVICES_OF.clear();
            SERVICES_OF.put(key, ids);
        }
        return ids;
    }
}
