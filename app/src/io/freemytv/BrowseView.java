package io.freemytv;

import android.app.Activity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The browse screen: a hero with the focused title (photo, name, details, synopsis) and, under it, rows of posters by
 * genre. ← → move along a row, which loads more titles as you get near its end; ↑ ↓ move between rows, and new rows
 * keep appearing (popular, top rated, new and Indian, for every genre), so there is no bottom. OK opens the title in
 * the streaming app that has it. Everything comes from TMDB, only titles on the apps installed on this TV.
 */
final class BrowseView extends View {
    private static final String IMG = "https://image.tmdb.org/t/p/";

    static final class Item {
        int id;
        String kind, title, poster, backdrop, overview, meta;
    }

    private static final class Row {
        final String title, kind, params;
        final List<Item> items = new ArrayList<>();
        final Set<Integer> seen = new HashSet<>();
        final Spring sx = new Spring(0.4f, 0.9f, 0);
        int page, col;
        boolean loading, done;
        long retryAt;

        Row(String title, String kind, String params) {
            this.title = title;
            this.kind = kind;
            this.params = params;
        }
    }

    private static final class Img {
        final Bitmap bitmap;
        BitmapShader shader;

        Img(Bitmap b) {
            bitmap = b;
        }
    }

    /** TMDB genre ids and names. */
    private static final Object[][] MOVIE_GENRES = {
        {28, "Action"}, {35, "Comedy"}, {18, "Drama"}, {53, "Thriller"}, {10749, "Romance"}, {878, "Sci-Fi"},
        {27, "Horror"}, {80, "Crime"}, {12, "Adventure"}, {14, "Fantasy"}, {9648, "Mystery"}, {16, "Animation"},
        {10751, "Family"}, {99, "Documentary"}, {36, "History"}, {10752, "War"}, {10402, "Music"}, {37, "Western"}};
    private static final Object[][] TV_GENRES = {
        {18, "Drama"}, {35, "Comedy"}, {10759, "Action & Adventure"}, {80, "Crime"}, {9648, "Mystery"},
        {10765, "Sci-Fi & Fantasy"}, {16, "Animation"}, {99, "Documentary"}, {10751, "Family"}, {10764, "Reality"},
        {10768, "War & Politics"}, {10762, "Kids"}, {37, "Western"}};
    private static final Map<Integer, String> GENRE_NAME = new HashMap<>();
    static {
        for (Object[] g : MOVIE_GENRES) GENRE_NAME.put((Integer) g[0], (String) g[1]);
        for (Object[] g : TV_GENRES) GENRE_NAME.putIfAbsent((Integer) g[0], (String) g[1]);
    }

    // Layout, against 1920×1080.
    private static final float MARGIN = 80, HERO_H = 520, ROWS_TOP = 584, CARD_W = 190, CARD_H = 285, GAP = 26,
            PITCH = 450, GROW = 0.1f;

    private final Activity activity;
    private final Recs recs;
    private final HomeData data;
    private final Click click;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newFixedThreadPool(2), images = Executors.newFixedThreadPool(3);
    private final int tab;
    private final List<Row> rows = new ArrayList<>();
    private final Spring scroll = new Spring(0.42f, 0.9f, 0), heroIn = new Spring(0.5f, 1f, 1);
    private final LruCache<String, Img> posters = new LruCache<>(70);
    private final LruCache<String, Bitmap> backdrops = new LruCache<>(4);
    private final Set<String> pending = new HashSet<>();
    private final Map<Integer, StaticLayout> synopsis = new HashMap<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG),
            rim = new Paint(Paint.ANTI_ALIAS_FLAG), shaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG),
            shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint heroTitle, heroMeta, rowTitle, label, hint, empty;
    private final TextPaint bodyPaint;
    private final RectF cell = new RectF();
    private final Matrix matrix = new Matrix();
    private String providers;
    private boolean released;
    private int rowIdx;
    private Item heroItem;
    private float k = 1;
    private long last;
    private boolean night;
    private LinearGradient sideFade, bottomFade;
    private int fadeColor, fadeW, fadeH;
    private int opening;
    private final SearchEngine.Filters filters = new SearchEngine.Filters();

    private final boolean detailOnly;

    BrowseView(Activity a, int tab, Item startItem) {
        super(a);
        activity = a;
        this.tab = tab;
        detailOnly = startItem != null;
        filters.defaultType = tab == 1 ? SearchEngine.MOVIE : tab == 2 ? SearchEngine.TV : SearchEngine.ALL;
        filters.type = filters.defaultType;
        recs = Recs.get(a);
        data = HomeData.get(a);
        click = new Click(a);
        heroTitle = Ui.text(Ui.displaySemibold, 64, 0xFFFFFFFF);
        heroMeta = Ui.text(Ui.medium, 30, 0xFFFFFFFF);
        rowTitle = Ui.text(Ui.semibold, 36, 0xFFFFFFFF);
        label = Ui.text(Ui.medium, 28, 0xFFFFFFFF);
        hint = Ui.text(Ui.regular, 24, 0xFFFFFFFF);
        empty = Ui.text(Ui.medium, 32, 0xFFFFFFFF);
        bodyPaint = new TextPaint(Ui.text(Ui.regular, 28, 0xFFFFFFFF));
        rim.setStyle(Paint.Style.STROKE);
        setFocusable(true);
        setKeepScreenOn(false);
        if (detailOnly) {
            ensureRows(0);
            showDetail(startItem);      // from the home screen: the title's page, and Back goes home
            return;
        }
        new Thread(() -> {
            providers = recs.providerIds();
            main.post(() -> {
                if (released) return;
                for (Row r : rows) load(r);
                invalidate();
            });
        }).start();
        ensureRows(5);
    }

    void release() {
        released = true;
        stopVoice();
        io.shutdownNow();
        images.shutdownNow();
        click.release();
    }

    // MARK: - Rows

    private static String today() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static String newest(String kind) {
        return kind.equals("movie")
                ? "&sort_by=primary_release_date.desc&primary_release_date.lte=" + today() + "&vote_count.gte=15"
                : "&sort_by=first_air_date.desc&first_air_date.lte=" + today() + "&vote_count.gte=10";
    }

    private static final String POPULAR = "&sort_by=popularity.desc", TOP = "&sort_by=vote_average.desc&vote_count.gte=400",
            INDIA = "&with_origin_country=IN";
    private static final boolean IN = Recs.region().equals("IN");

    /** The n-th row of this tab: a few fixed ones, then every genre in four flavors, again and again. */
    private Row rowAt(int n) {
        List<Row> intro = new ArrayList<>();
        if (tab == 0) {
            intro.add(new Row("Popular movies", "movie", POPULAR));
            intro.add(new Row("Popular shows", "tv", POPULAR));
            if (IN) intro.add(new Row("Made in India · Movies", "movie", POPULAR + INDIA));
            if (IN) intro.add(new Row("Made in India · Shows", "tv", POPULAR + INDIA));
            intro.add(new Row("Top rated movies", "movie", TOP));
            intro.add(new Row("Top rated shows", "tv", TOP));
        } else {
            String kind = tab == 1 ? "movie" : "tv", noun = tab == 1 ? "movies" : "shows";
            intro.add(new Row("Popular " + noun, kind, POPULAR));
            if (IN) intro.add(new Row("Made in India", kind, POPULAR + INDIA));
            intro.add(new Row("Top rated", kind, TOP));
            intro.add(new Row("New releases", kind, newest(kind)));
        }
        if (n < intro.size()) return intro.get(n);
        // Genres: Explore alternates movie and show genres; Movies and Shows use their own list.
        List<Object[]> genres = new ArrayList<>();
        for (int i = 0; i < Math.max(MOVIE_GENRES.length, TV_GENRES.length); i++) {
            if (tab != 2 && i < MOVIE_GENRES.length) genres.add(new Object[] {"movie", MOVIE_GENRES[i]});
            if (tab != 1 && i < TV_GENRES.length) genres.add(new Object[] {"tv", TV_GENRES[i]});
        }
        int m = n - intro.size(), g = m % genres.size(), cycle = (m / genres.size()) % 4;
        String kind = (String) genres.get(g)[0];
        Object[] genre = (Object[]) genres.get(g)[1];
        String name = (String) genre[1], with = "&with_genres=" + genre[0];
        String suffix = tab == 0 ? (kind.equals("movie") ? " movies" : " shows") : "";
        switch (cycle) {
            case 0: return new Row(name + suffix, kind, POPULAR + with);
            case 1: return new Row("Top rated · " + name + suffix, kind, TOP + with);
            case 2: return new Row("New · " + name + suffix, kind, newest(kind) + with);
            default:
                return IN ? new Row("Indian · " + name + suffix, kind, POPULAR + INDIA + with)
                        : new Row("Hidden gems · " + name + suffix, kind, "&sort_by=vote_average.desc&vote_count.gte=100&vote_count.lte=2000" + with);
        }
    }

    private void ensureRows(int upTo) {
        while (rows.size() <= upTo) rows.add(rowAt(rows.size()));
    }

    private void load(Row r) {
        if (r.loading || r.done || providers == null || released) return;
        if (r.retryAt > SystemClock.uptimeMillis()) return;
        r.loading = true;
        final int page = r.page + 1;
        io.execute(() -> {
            List<Item> got = new ArrayList<>();
            boolean ok = true, end = false;
            try {
                String path = "/discover/" + r.kind + "?language=" + Recs.language() + "&watch_region=" + Recs.region()
                        + "&with_watch_monetization_types=flatrate&include_adult=false"
                        + (providers.isEmpty() ? "" : "&with_watch_providers=" + java.net.URLEncoder.encode(providers, "UTF-8"))
                        + r.params + "&page=" + page;
                JSONObject o = recs.tmdb(path);
                JSONArray results = o.getJSONArray("results");
                for (int i = 0; i < results.length(); i++) {
                    Item it = parseItem(results.getJSONObject(i), r.kind);
                    if (it != null) got.add(it);
                }
                end = page >= Math.min(500, o.optInt("total_pages", 1)) || results.length() == 0;
            } catch (Exception e) {
                ok = false;
                AppLog.w("browse: " + e.getMessage());
            }
            final boolean fine = ok, last = end;
            main.post(() -> {
                r.loading = false;
                if (released) return;
                if (!fine) {
                    r.retryAt = SystemClock.uptimeMillis() + 8000;
                    postInvalidateDelayed(8100);
                    return;
                }
                r.page = page;
                r.done = last;
                for (Item it : got) if (r.seen.add(it.id)) r.items.add(it);
                // A page that gave nothing new (all filtered out) asks for the next one.
                if (got.isEmpty() && !r.done) load(r);
                invalidate();
            });
        });
    }

    /** A TMDB result as a card, or null without a poster. */
    static Item parseItem(JSONObject j, String kind) {
        String poster = j.optString("poster_path", "");
        if (poster.isEmpty() || "null".equals(poster)) return null;
        Item it = new Item();
        it.id = j.optInt("id");
        it.kind = kind;
        it.title = j.optString(kind.equals("movie") ? "title" : "name");
        it.poster = poster;
        String b = j.optString("backdrop_path", "");
        it.backdrop = b.isEmpty() || "null".equals(b) ? null : b;
        it.overview = j.optString("overview", "");
        it.meta = meta(j, kind);
        return it;
    }

    private static String meta(JSONObject j, String kind) {
        List<String> parts = new ArrayList<>();
        if (kind.equals("tv")) parts.add(Ui.tr("Series"));
        JSONArray ids = j.optJSONArray("genre_ids");
        for (int i = 0; ids != null && i < ids.length() && i < 2; i++) {
            String g = GENRE_NAME.get(ids.optInt(i));
            if (g != null) parts.add(g);
        }
        String date = j.optString(kind.equals("movie") ? "release_date" : "first_air_date", "");
        if (date.length() >= 4) parts.add(date.substring(0, 4));
        double rating = j.optDouble("vote_average", 0);
        if (rating > 0) parts.add(String.format(Locale.US, "★ %.1f", rating));
        return TextUtils.join(" · ", parts);
    }

    // MARK: - Images

    private Img poster(String path) {
        Img img = posters.get(path);
        if (img != null) return img;
        fetch("p" + path, IMG + "w342" + path, Math.round(CARD_W * k * (1 + GROW)), Math.round(CARD_H * k * (1 + GROW)), true);
        return null;
    }

    private Bitmap backdrop(String path) {
        if (path == null) return null;
        Bitmap b = backdrops.get(path);
        if (b != null) return b;
        fetch("b" + path, IMG + "w780" + path, 0, 0, false);
        return null;
    }

    private void fetch(String key, String url, int w, int h, boolean isPoster) {
        synchronized (pending) {
            if (!pending.add(key)) return;
        }
        try {
            images.execute(() -> {
                Bitmap out = null;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    try (InputStream in = c.getInputStream()) {
                        Bitmap b = BitmapFactory.decodeStream(in);
                        if (b != null && w > 0 && b.getWidth() > w) {
                            Bitmap s = Bitmap.createScaledBitmap(b, w, h, true);
                            if (s != b) b.recycle();
                            b = s;
                        }
                        out = b;
                    } finally {
                        c.disconnect();
                    }
                } catch (Exception e) {
                    AppLog.w("browse image: " + e.getMessage());
                }
                final Bitmap result = out;
                main.post(() -> {
                    synchronized (pending) {
                        pending.remove(key);
                    }
                    if (released || result == null) return;
                    if (isPoster) posters.put(key.substring(1), new Img(result)); else backdrops.put(key.substring(1), result);
                    invalidate();
                });
            });
        } catch (RuntimeException rejected) {
            synchronized (pending) {
                pending.remove(key);
            }
        }
    }

    // MARK: - Keys

    private Row row() {
        return rows.get(rowIdx);
    }

    private int colIn(Row r) {
        return r.items.isEmpty() ? 0 : Math.min(r.col, r.items.size() - 1);
    }

    boolean key(int code) {
        if (detail != null) return detailKey(code);
        if (listening) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_DPAD_CENTER) stopVoice();
            return true;
        }
        if (picker != null) return pickerKey(code);
        if (code == KeyEvent.KEYCODE_SEARCH || code == KeyEvent.KEYCODE_VOICE_ASSIST || code == KeyEvent.KEYCODE_ASSIST) {
            startVoice();
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK && filters.active()) {     // Back leaves the results first
            clearFilters();
            return true;
        }
        if (barFocus >= 0) return barKey(code);
        if (resultsMode) return gridKey(code);
        Row r = row();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (colIn(r) > 0) {
                    r.col = colIn(r) - 1;
                    moved();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (colIn(r) < r.items.size() - 1) {
                    r.col = colIn(r) + 1;
                    moved();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (rowIdx > 0) {
                    rowIdx--;
                    moved();
                } else {
                    barFocus = 0;       // up from the first row: the search bar
                    click.move();
                    invalidate();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                rowIdx++;
                ensureRows(rowIdx + 4);
                moved();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (!r.items.isEmpty()) showDetail(r.items.get(colIn(r)));
                return true;
            default:
                return false;
        }
    }

    private void moved() {
        click.move();
        Row r = row();
        scroll.target = rowIdx;
        r.sx.target = Math.max(0, colIn(r) - 3) * (CARD_W + GAP) * k;
        if (r.items.size() - colIn(r) < 8) load(r);
        invalidate();
    }

    // MARK: - A title's page

    private static final class Detail {
        final Item item;
        String tagline = "", facts = "", cast = "", director = "", imdb = "", tmdb = "", service = "";
        StaticLayout overview;
        Recs.Pick pick;
        boolean loaded, resolved;
        int button;     // 0 = open in the app (when there is one), then close

        Detail(Item item) {
            this.item = item;
        }

        boolean canOpen() {
            return pick != null && pick.pkg != null;
        }
    }

    private Detail detail;
    private final Spring detailIn = new Spring(0.45f, 1f, 0);
    private TextPaint overviewPaint;
    private Paint chipText, buttonText, factsPaint, tagPaint;

    private void showDetail(Item it) {
        click.open();
        final Detail d = new Detail(it);
        detail = d;
        detailIn.value = 0;
        detailIn.velocity = 0;
        detailIn.target = 1;
        new Thread(() -> {
            String line = "", tagline = "", cast = "", director = "", imdb = "", tmdb = "";
            String overview = it.overview;
            try {
                JSONObject o = recs.tmdb("/" + it.kind + "/" + it.id + "?language=" + Recs.language()
                        + "&append_to_response=credits,external_ids");
                tagline = o.optString("tagline", "");
                String full = o.optString("overview", "");
                if (!full.isEmpty()) overview = full;
                List<String> facts = new ArrayList<>();
                String date = o.optString(it.kind.equals("movie") ? "release_date" : "first_air_date", "");
                if (date.length() >= 4) facts.add(date.substring(0, 4));
                if (it.kind.equals("movie")) {
                    int min = o.optInt("runtime");
                    if (min > 0) facts.add(min >= 60 ? (min / 60) + "h " + (min % 60) + "m" : min + "m");
                } else {
                    int seasons = o.optInt("number_of_seasons");
                    if (seasons > 0) facts.add(Ui.plural("plural|one:{n} season|other:{n} seasons", seasons));
                }
                JSONArray genres = o.optJSONArray("genres");
                for (int i = 0; genres != null && i < genres.length() && i < 3; i++) facts.add(genres.getJSONObject(i).optString("name"));
                line = TextUtils.join("  ·  ", facts);
                double v = o.optDouble("vote_average", 0);
                if (v > 0) tmdb = String.format(Locale.US, "%.1f", v);
                JSONObject credits = o.optJSONObject("credits");
                if (credits != null) {
                    JSONArray cs = credits.optJSONArray("cast");
                    List<String> names = new ArrayList<>();
                    for (int i = 0; cs != null && i < cs.length() && i < 5; i++) names.add(cs.getJSONObject(i).optString("name"));
                    cast = TextUtils.join(", ", names);
                    JSONArray crew = credits.optJSONArray("crew");
                    for (int i = 0; crew != null && i < crew.length() && director.isEmpty(); i++) {
                        if ("Director".equals(crew.getJSONObject(i).optString("job"))) director = crew.getJSONObject(i).optString("name");
                    }
                }
                if (director.isEmpty()) {
                    JSONArray by = o.optJSONArray("created_by");
                    List<String> names = new ArrayList<>();
                    for (int i = 0; by != null && i < by.length() && i < 2; i++) names.add(by.getJSONObject(i).optString("name"));
                    director = TextUtils.join(", ", names);
                }
                JSONObject ext = o.optJSONObject("external_ids");
                String imdbId = ext == null ? "" : ext.optString("imdb_id", "");
                if (!imdbId.isEmpty() && !Config.OMDB_KEY.trim().isEmpty()) imdb = imdbRating(imdbId);
            } catch (Exception e) {
                AppLog.w("browse detail: " + e.getMessage());
            }
            final String fl = line, ft = tagline, fc = cast, fd = director, fi = imdb, fm = tmdb, fo = overview;
            main.post(() -> {
                if (released || detail != d) return;
                d.facts = fl;
                d.tagline = ft;
                d.cast = fc;
                d.director = fd;
                d.imdb = fi;
                d.tmdb = fm.isEmpty() ? "" : fm;
                d.overview = layoutOverview(fo);
                d.loaded = true;
                invalidate();
            });
        }).start();
        new Thread(() -> {
            Recs.Pick p = recs.resolve(it.id, it.kind, it.title);
            main.post(() -> {
                if (released || detail != d) return;
                d.pick = p;
                String name = Recs.serviceName(p);
                d.service = name == null ? "" : name;
                d.resolved = true;
                invalidate();
            });
        }).start();
        invalidate();
    }

    /** IMDb's rating of a title, through OMDb (needs the optional omdb_key). Empty when unknown. */
    private static String imdbRating(String imdbId) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://www.omdbapi.com/?i="
                    + java.net.URLEncoder.encode(imdbId, "UTF-8") + "&apikey=" + java.net.URLEncoder.encode(Config.OMDB_KEY.trim(), "UTF-8")).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            try (InputStream in = c.getInputStream()) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                String r = new JSONObject(out.toString("UTF-8")).optString("imdbRating", "");
                return "N/A".equals(r) ? "" : r;
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            AppLog.w("omdb: " + e.getMessage());
            return "";
        }
    }

    private StaticLayout layoutOverview(String text) {
        if (overviewPaint == null) overviewPaint = new TextPaint(Ui.text(Ui.regular, 30, 0xFFFFFFFF));
        overviewPaint.setTextSize(30 * k);
        return StaticLayout.Builder.obtain(text, 0, text.length(), overviewPaint, Math.round(1180 * k))
                .setMaxLines(7).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0, 1.2f).build();
    }

    private boolean detailKey(int code) {
        Detail d = detail;
        switch (code) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                closeDetail();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (d.canOpen()) {
                    d.button = 1 - d.button;
                    click.move();
                    invalidate();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (d.canOpen() && d.button == 0) {
                    openApp(d);
                } else {
                    closeDetail();
                }
                return true;
            default:
                return true;
        }
    }

    private void closeDetail() {
        detail = null;
        click.move();
        invalidate();
        if (detailOnly) activity.finish();
    }

    private void openApp(Detail d) {
        click.open();
        Recs.Pick p = d.pick;
        String service = d.service.isEmpty() ? p.pkg : d.service;
        boolean ok = HomeApps.open(activity, HomeApps.app(p.pkg, service, p.link), this, getWidth() / 2, getHeight() / 2, 1, 1);
        if (!ok) Toast.makeText(activity, Ui.trf("Couldn't open {app}.", "app", service), Toast.LENGTH_LONG).show();
    }

    private void drawChip(Canvas c, String label, String value, float x, float y, int bgColor, int textColor) {
        if (chipText == null) chipText = Ui.text(Ui.semibold, 28, 0xFF000000);
        chipText.setTextSize(28 * k);
        chipText.setColor(textColor);
        String text = label + "  " + value;
        float pad = 20 * k, w = chipText.measureText(text) + 2 * pad, h = 52 * k;
        fill.setColor(bgColor);
        c.drawRoundRect(x, y, x + w, y + h, 12 * k, 12 * k, fill);
        c.drawText(text, x + pad, y + h / 2 + 10 * k, chipText);
        chipX = x + w + 16 * k;
    }

    private float chipX;

    private void drawDetail(Canvas c, int w, int h) {
        Detail d = detail;
        float a = Ui.clamp(detailIn.value);
        Item it = d.item;
        c.drawColor(Ui.alpha(bg(), a));
        Bitmap b = backdrop(it.backdrop);
        if (b != null) {
            float bw = w * 0.8f, bh = bw * b.getHeight() / b.getWidth();
            cell.set(w - bw, 0, w, bh);
            bitmapPaint.setAlpha(Math.round(255 * a));
            c.drawBitmap(b, null, cell, bitmapPaint);
            bitmapPaint.setAlpha(255);
        }
        int bg = bg();
        shaderPaint.setShader(new LinearGradient(0, 0, w * 0.95f, 0, new int[] {bg, bg, Ui.alpha(bg, 0.7f), Ui.alpha(bg, 0.5f)},
                new float[] {0, 0.42f, 0.75f, 1}, Shader.TileMode.CLAMP));
        shaderPaint.setAlpha(Math.round(255 * a));
        c.drawRect(0, 0, w, h, shaderPaint);
        shaderPaint.setShader(new LinearGradient(0, h * 0.4f, 0, h * 0.78f, bg & 0x00FFFFFF, bg, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.4f, w, h, shaderPaint);
        shaderPaint.setShader(null);
        shaderPaint.setAlpha(255);

        // Poster at the left.
        float px = MARGIN * k, py = 150 * k, pw = 300 * k, ph = 450 * k;
        Img img = poster(it.poster);
        cell.set(px, py, px + pw, py + ph);
        if (img != null) {
            if (img.shader == null) img.shader = new BitmapShader(img.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            matrix.setScale(pw / img.bitmap.getWidth(), ph / img.bitmap.getHeight());
            matrix.postTranslate(px, py);
            img.shader.setLocalMatrix(matrix);
            shaderPaint.setShader(img.shader);
            shaderPaint.setAlpha(Math.round(255 * a));
            c.drawRoundRect(cell, 18 * k, 18 * k, shaderPaint);
            shaderPaint.setShader(null);
            shaderPaint.setAlpha(255);
        } else {
            fill.setColor(night ? 0xFF2C2C2E : 0xFFDADADF);
            c.drawRoundRect(cell, 18 * k, 18 * k, fill);
        }

        float x = px + pw + 60 * k, y = 215 * k;
        heroTitle.setColor(Ui.alpha(ink(), a));
        heroTitle.setTextSize(64 * k);
        c.drawText(TextUtils.ellipsize(it.title, new TextPaint(heroTitle), w - x - MARGIN * k, TextUtils.TruncateAt.END).toString(), x, y, heroTitle);
        if (factsPaint == null) {
            factsPaint = Ui.text(Ui.medium, 30, 0xFFFFFFFF);
            tagPaint = Ui.text(Ui.regular, 30, 0xFFFFFFFF);
            buttonText = Ui.text(Ui.semibold, 32, 0xFFFFFFFF);
        }
        factsPaint.setTextSize(30 * k);
        factsPaint.setColor(Ui.alpha(dim(), a));
        float cursor = y + 56 * k;
        c.drawText((it.kind.equals("tv") ? Ui.tr("Series") + "  ·  " : "") + (d.facts.isEmpty() ? it.meta : d.facts), x, cursor, factsPaint);
        cursor += 28 * k;
        // Ratings.
        if (!d.tmdb.isEmpty() || !d.imdb.isEmpty()) {
            chipX = x;
            if (!d.imdb.isEmpty()) drawChip(c, "IMDb", d.imdb, chipX, cursor, 0xFFF5C518, 0xFF000000);
            if (!d.tmdb.isEmpty()) drawChip(c, "TMDB", "★ " + d.tmdb, chipX, cursor, night ? 0xFF1B2B3A : 0xFF0D253F, 0xFF90CEA1);
            cursor += 52 * k + 36 * k;
        } else {
            cursor += 20 * k;
        }
        if (!d.tagline.isEmpty()) {
            tagPaint.setTextSize(30 * k);
            tagPaint.setColor(Ui.alpha(ink(), a * 0.9f));
            c.drawText("“" + d.tagline + "”", x, cursor + 20 * k, tagPaint);
            cursor += 56 * k;
        }
        if (d.overview != null) {
            overviewPaint.setColor(Ui.alpha(night ? 0xFFD8D8DC : 0xFF3A3A3C, a));
            c.save();
            c.translate(x, cursor);
            d.overview.draw(c);
            c.restore();
            cursor += d.overview.getHeight() + 30 * k;
        } else {
            factsPaint.setColor(Ui.alpha(dim(), a));
            c.drawText(Ui.tr("Loading…"), x, cursor + 20 * k, factsPaint);
            cursor += 56 * k;
        }
        factsPaint.setTextSize(26 * k);
        factsPaint.setColor(Ui.alpha(dim(), a));
        if (!d.director.isEmpty()) {
            c.drawText((it.kind.equals("tv") ? Ui.tr("Created by") : Ui.tr("Director")) + ":  " + d.director, x, cursor + 16 * k, factsPaint);
            cursor += 40 * k;
        }
        if (!d.cast.isEmpty()) {
            c.drawText(TextUtils.ellipsize(Ui.tr("Cast") + ":  " + d.cast, new TextPaint(factsPaint), w - x - MARGIN * k, TextUtils.TruncateAt.END).toString(), x, cursor + 16 * k, factsPaint);
        }

        // Buttons at the bottom.
        float by = h - 190 * k, bh = 84 * k, bx = x;
        buttonText.setTextSize(32 * k);
        if (!d.resolved) {
            factsPaint.setTextSize(28 * k);
            c.drawText(Ui.tr("Checking your apps…"), bx, by + bh / 2 + 10 * k, factsPaint);
            bx += 360 * k;
        } else if (d.canOpen()) {
            bx = drawButton(c, Ui.trf("Open in {app}", "app", d.service.isEmpty() ? d.pick.pkg : d.service), bx, by, bh, d.button == 0) + 24 * k;
        } else {
            factsPaint.setTextSize(28 * k);
            c.drawText(Ui.tr("Not on your apps right now"), bx, by + bh / 2 + 10 * k, factsPaint);
            bx += factsPaint.measureText(Ui.tr("Not on your apps right now")) + 40 * k;
        }
        drawButton(c, Ui.tr("Back"), bx, by, bh, !d.canOpen() || d.button == 1);
    }

    private float drawButton(Canvas c, String text, float x, float y, float h, boolean focused) {
        float pad = 44 * k, w = buttonText.measureText(text) + 2 * pad;
        fill.setColor(focused ? (night ? 0xFFFFFFFF : 0xFF1C1C1E) : (night ? 0x33FFFFFF : 0x1F000000));
        c.drawRoundRect(x, y, x + w, y + h, h / 2, h / 2, fill);
        buttonText.setColor(focused ? (night ? 0xFF1C1C1E : 0xFFFFFFFF) : ink());
        c.drawText(text, x + pad, y + h / 2 + 11 * k, buttonText);
        return x + w;
    }

    // MARK: - Search, filters and voice

    private static final int GRID_COLS = 8;
    private static final int BAR_SEARCH = 0, BAR_MIC = 1, BAR_TYPE = 2, BAR_GENRE = 3, BAR_YEAR = 4, BAR_PLATFORM = 5, BAR_CLEAR = 6;
    private boolean resultsMode, resultLoading, resultDone, resultFailed;
    private final List<Item> results = new ArrayList<>();
    private final Set<String> resultSeen = new HashSet<>();
    private int resultPage, searchToken, gridIdx, barFocus = -1;
    private long resultRetryAt;
    private final Spring gridScroll = new Spring(0.42f, 0.9f, 0);
    private final RectF[] barRects = {new RectF(), new RectF(), new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
    private Paint chipPaint, summaryPaint;
    private LinearGradient barFade;
    private int barFadeColor;

    private static final class Picker {
        final String title;
        final int bar;
        final List<String> labels = new ArrayList<>(), values = new ArrayList<>();
        int cursor;

        Picker(String title, int bar) {
            this.title = title;
            this.bar = bar;
        }
    }

    private Picker picker;

    // Voice
    private boolean listening;
    private String partial = "";
    private float voiceLevel;
    private SpeechRecognizer recognizer;
    private static final int PERMISSION_MIC = 41;

    private void filtersChanged() {
        resultsMode = filters.active();
        if (resultsMode) {
            startSearch();
        } else {
            searchToken++;
            results.clear();
            resultSeen.clear();
            resultLoading = false;
        }
        invalidate();
    }

    private void clearFilters() {
        filters.reset();
        barFocus = -1;
        click.move();
        filtersChanged();
    }

    private void submitQuery(String text) {
        filters.query = text == null ? "" : text.trim();
        barFocus = filters.query.isEmpty() ? barFocus : -1;
        filtersChanged();
    }

    private void startSearch() {
        searchToken++;
        results.clear();
        resultSeen.clear();
        resultPage = 0;
        resultDone = false;
        resultFailed = false;
        resultLoading = false;
        resultRetryAt = 0;
        gridIdx = 0;
        gridScroll.snap(0);
        loadResults();
    }

    private void loadResults() {
        if (resultLoading || resultDone || released || providers == null) return;
        if (resultRetryAt > SystemClock.uptimeMillis()) return;
        resultLoading = true;
        final int token = searchToken, page = resultPage + 1;
        final SearchEngine.Filters f = new SearchEngine.Filters();
        f.query = filters.query;
        f.type = filters.type;
        f.genre = filters.genre;
        f.year = filters.year;
        f.platform = filters.platform;
        final String mine = providers;
        io.execute(() -> {
            SearchEngine.Page got = null;
            try {
                got = SearchEngine.fetch(recs, f, page, mine);
            } catch (Exception e) {
                AppLog.w("browse search: " + e.getMessage());
            }
            final SearchEngine.Page result = got;
            main.post(() -> {
                if (released || token != searchToken) return;
                resultLoading = false;
                if (result == null) {
                    resultFailed = true;
                    resultRetryAt = SystemClock.uptimeMillis() + 6000;
                    postInvalidateDelayed(6100);
                    return;
                }
                resultFailed = false;
                resultPage = page;
                resultDone = result.done;
                for (Item it : result.items) if (resultSeen.add(it.kind + it.id)) results.add(it);
                // A page the filters emptied out asks for the next one.
                if (result.items.isEmpty() && !resultDone) loadResults();
                invalidate();
            });
        });
    }

    private boolean barKey(int code) {
        int last = filters.active() ? BAR_CLEAR : BAR_PLATFORM;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (barFocus > 0) {
                    barFocus--;
                    click.move();
                    invalidate();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (barFocus < last) {
                    barFocus++;
                    click.move();
                    invalidate();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                barFocus = -1;
                click.move();
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                click.open();
                switch (barFocus) {
                    case BAR_SEARCH: typeQuery(); break;
                    case BAR_MIC: startVoice(); break;
                    case BAR_CLEAR: clearFilters(); break;
                    default: openPicker(barFocus);
                }
                return true;
            default:
                return code != KeyEvent.KEYCODE_BACK;
        }
    }

    private boolean gridKey(int code) {
        int n = results.size();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (gridIdx > 0) gridMoved(gridIdx - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (gridIdx < n - 1) gridMoved(gridIdx + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (gridIdx >= GRID_COLS) {
                    gridMoved(gridIdx - GRID_COLS);
                } else {
                    barFocus = 0;
                    click.move();
                    invalidate();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (gridIdx + GRID_COLS < n) {
                    gridMoved(gridIdx + GRID_COLS);
                } else if (n > 0 && gridIdx / GRID_COLS < (n - 1) / GRID_COLS) {
                    gridMoved(n - 1);       // the last row is shorter: land on its last poster
                } else {
                    loadResults();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (n > 0) showDetail(results.get(gridIdx));
                return true;
            default:
                return false;
        }
    }

    private void gridMoved(int to) {
        gridIdx = to;
        gridScroll.target = gridIdx / GRID_COLS;
        click.move();
        if (results.size() - gridIdx < GRID_COLS * 2) loadResults();
        invalidate();
    }

    // Typing

    private void typeQuery() {
        final EditText input = new EditText(activity);
        input.setSingleLine();
        input.setText(filters.query);
        input.setSelection(input.getText().length());
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setHint(Ui.tr(tab == 1 ? "Search movies" : tab == 2 ? "Search shows" : "Search movies & shows"));
        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(Ui.tr("Search"))
                .setView(input)
                .setPositiveButton(Ui.tr("Search"), (d, which) -> submitQuery(input.getText().toString()))
                .setNegativeButton(Ui.tr("Cancel"), null)
                .create();
        input.setOnEditorActionListener((v, action, event) -> {
            submitQuery(input.getText().toString());
            dialog.dismiss();
            return true;
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    // Voice

    private void startVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            Toast.makeText(activity, Ui.tr("Voice search isn't available on this TV. Type instead."), Toast.LENGTH_LONG).show();
            typeQuery();
            return;
        }
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, PERMISSION_MIC);
            return;
        }
        beginListening();
    }

    void permissionResult(int request, boolean granted) {
        if (request != PERMISSION_MIC) return;
        if (granted) beginListening();
        else Toast.makeText(activity, Ui.tr("Voice search needs the microphone. You can type instead."), Toast.LENGTH_LONG).show();
    }

    private void beginListening() {
        stopVoice();
        listening = true;
        partial = "";
        voiceLevel = 0;
        recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rms) {
                voiceLevel = Math.max(0, Math.min(1, (rms + 2) / 12f));
                invalidate();
            }
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {
                boolean heard = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
                stopVoice();
                Toast.makeText(activity, Ui.tr(heard ? "Didn't catch that. Try again." : "Voice search isn't working right now."), Toast.LENGTH_SHORT).show();
            }
            @Override public void onResults(Bundle results) {
                List<String> text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String said = text == null || text.isEmpty() ? "" : text.get(0);
                stopVoice();
                if (!said.isEmpty()) submitQuery(said);
            }
            @Override public void onPartialResults(Bundle partialResults) {
                List<String> text = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (text != null && !text.isEmpty()) {
                    partial = text.get(0);
                    invalidate();
                }
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.getPackageName());
        recognizer.startListening(intent);
        invalidate();
    }

    private void stopVoice() {
        listening = false;
        if (recognizer != null) {
            try {
                recognizer.cancel();
                recognizer.destroy();
            } catch (RuntimeException ignored) {
            }
            recognizer = null;
        }
        invalidate();
    }

    // Filter lists

    private String typeLabel() {
        return Ui.tr(filters.type.equals(SearchEngine.MOVIE) ? "Movies" : filters.type.equals(SearchEngine.TV) ? "Shows" : "All");
    }

    private String platformName(String id) {
        for (String[] s : recs.providerList()) if (s[0].equals(id)) return s[1];
        return id;
    }

    private String[] chipTexts() {
        String platform = filters.platform.isEmpty() ? Ui.tr("Platform")
                : filters.platform.equals("any") ? Ui.tr("Any platform") : filters.platform.equals("mine") ? Ui.tr("My apps") : platformName(filters.platform);
        return new String[] {typeLabel(), filters.genre == 0 ? Ui.tr("Genre") : Ui.tr(GENRE_NAME.get(filters.genre)),
                filters.year == 0 ? Ui.tr("Year") : String.valueOf(filters.year), platform};
    }

    private void openPicker(int bar) {
        Picker p;
        if (bar == BAR_TYPE) {
            p = new Picker(Ui.tr("Show"), bar);
            p.labels.add(Ui.tr("All"));
            p.values.add(SearchEngine.ALL);
            p.labels.add(Ui.tr("Movies"));
            p.values.add(SearchEngine.MOVIE);
            p.labels.add(Ui.tr("Shows"));
            p.values.add(SearchEngine.TV);
            p.cursor = p.values.indexOf(filters.type);
        } else if (bar == BAR_GENRE) {
            p = new Picker(Ui.tr("Genre"), bar);
            p.labels.add(Ui.tr("Any genre"));
            p.values.add("0");
            for (Object[] g : filters.type.equals(SearchEngine.TV) ? TV_GENRES : MOVIE_GENRES) {
                p.labels.add(Ui.tr((String) g[1]));
                p.values.add(String.valueOf(g[0]));
            }
            p.cursor = Math.max(0, p.values.indexOf(String.valueOf(filters.genre)));
        } else if (bar == BAR_YEAR) {
            p = new Picker(Ui.tr("Year"), bar);
            p.labels.add(Ui.tr("Any year"));
            p.values.add("0");
            for (int y = Calendar.getInstance().get(Calendar.YEAR); y >= 1970; y--) {
                p.labels.add(String.valueOf(y));
                p.values.add(String.valueOf(y));
            }
            p.cursor = Math.max(0, p.values.indexOf(String.valueOf(filters.year)));
        } else {
            if (providers == null) {
                Toast.makeText(activity, Ui.tr("Loading…"), Toast.LENGTH_SHORT).show();
                return;
            }
            p = new Picker(Ui.tr("Platform"), bar);
            p.labels.add(Ui.tr("Default"));
            p.values.add("");
            p.labels.add(Ui.tr("Any platform"));
            p.values.add("any");
            p.labels.add(Ui.tr("All my apps"));
            p.values.add("mine");
            for (String[] s : recs.providerList()) {
                p.labels.add(s[1]);
                p.values.add(s[0]);
            }
            p.cursor = Math.max(0, p.values.indexOf(filters.platform));
        }
        picker = p;
        invalidate();
    }

    private boolean pickerKey(int code) {
        Picker p = picker;
        switch (code) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                picker = null;
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                p.cursor = Math.max(0, p.cursor - 1);
                click.move();
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                p.cursor = Math.min(p.labels.size() - 1, p.cursor + 1);
                click.move();
                invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                String v = p.values.get(p.cursor);
                if (p.bar == BAR_TYPE) {
                    filters.type = v;
                    // A genre the new type doesn't have is dropped.
                    if (filters.genre != 0 && SearchEngine.genreFor(v.equals(SearchEngine.ALL) ? SearchEngine.MOVIE : v, filters.genre) < 0
                            && !(v.equals(SearchEngine.TV) && GENRE_NAME.containsKey(filters.genre))) filters.genre = 0;
                } else if (p.bar == BAR_GENRE) {
                    filters.genre = Integer.parseInt(v);
                } else if (p.bar == BAR_YEAR) {
                    filters.year = Integer.parseInt(v);
                } else {
                    filters.platform = v;
                }
                picker = null;
                click.open();
                filtersChanged();
                return true;
            default:
                return true;
        }
    }

    // MARK: - Drawing

    private int bg() {
        return night ? 0xFF0B0B0D : 0xFFF2F2F7;
    }

    private int ink() {
        return night ? 0xFFFFFFFF : 0xFF1C1C1E;
    }

    private int dim() {
        return night ? 0xFF98989D : 0xFF6C6C70;
    }

    // MARK: - Search bar, results and overlays

    /**
     * Same look as the home tabs: at rest a soft translucent capsule (no outline), selected = the white pill with a
     * shadow; a chip with a filter on has a stronger fill.
     */
    private void drawPill(Canvas c, RectF r, boolean focused, boolean active) {
        float rad = r.height() / 2;
        fill.setShader(null);
        if (focused) {
            shadowPaint.setColor(night ? 0x66000000 : 0x2E000000);
            c.drawRoundRect(r.left, r.top + 7 * k, r.right, r.bottom + 7 * k, rad, rad, shadowPaint);
            fill.setColor(0xFFFFFFFF);
            c.drawRoundRect(r, rad, rad, fill);
            return;
        }
        fill.setColor(night ? (active ? 0x33FFFFFF : 0x1AFFFFFF) : (active ? 0x24000000 : 0x10000000));
        c.drawRoundRect(r, rad, rad, fill);
    }

    private int pillInk(boolean focused) {
        return focused ? 0xFF1C1C1E : ink();
    }

    private void drawBar(Canvas c, int w) {
        if (chipPaint == null) {
            chipPaint = Ui.text(Ui.semibold, 26, 0xFFFFFFFF);
            summaryPaint = Ui.text(Ui.medium, 28, 0xFFFFFFFF);
        }
        if (!resultsMode) {
            int bg = bg();
            if (barFade == null || barFadeColor != bg) {
                barFadeColor = bg;
                barFade = new LinearGradient(0, 0, 0, 170 * k, Ui.alpha(bg, 0.92f), bg & 0x00FFFFFF, Shader.TileMode.CLAMP);
            }
            shaderPaint.setShader(barFade);
            c.drawRect(0, 0, w, 170 * k, shaderPaint);
            shaderPaint.setShader(null);
        }
        float top = 34 * k, h = 68 * k, x = MARGIN * k;
        barRects[BAR_SEARCH].set(x, top, x + 560 * k, top + h);
        float mic = 52 * k;      // a small round button at the right end of the search field
        barRects[BAR_MIC].set(barRects[BAR_SEARCH].right - 8 * k - mic, top + (h - mic) / 2, barRects[BAR_SEARCH].right - 8 * k, top + (h + mic) / 2);
        x = barRects[BAR_SEARCH].right + 36 * k;
        chipPaint.setTextSize(26 * k);
        String[] chips = chipTexts();
        for (int i = 0; i < 4; i++) {
            float tw = chipPaint.measureText(chips[i]) + 56 * k;
            barRects[BAR_TYPE + i].set(x, top + 8 * k, x + tw, top + h - 8 * k);
            x += tw + 12 * k;
        }
        String clear = Ui.tr("Clear");
        barRects[BAR_CLEAR].set(x, top + 8 * k, x + chipPaint.measureText(clear) + 56 * k, top + h - 8 * k);

        // Search field: a magnifier and what was typed or said (or a hint).
        RectF s = barRects[BAR_SEARCH];
        boolean sf = barFocus == BAR_SEARCH;
        drawPill(c, s, sf, !filters.query.isEmpty());
        int fg = pillInk(sf);
        rim.setColor(fg);
        rim.setStrokeWidth(3.5f * k);
        float ix = s.left + 38 * k, iy = s.centerY() - 3 * k;
        c.drawCircle(ix, iy, 10 * k, rim);
        c.drawLine(ix + 7.5f * k, iy + 7.5f * k, ix + 15 * k, iy + 15 * k, rim);
        boolean has = !filters.query.isEmpty();
        chipPaint.setTextSize(28 * k);
        chipPaint.setColor(has ? fg : Ui.alpha(fg, 0.55f));
        String shown = has ? filters.query : Ui.tr(tab == 1 ? "Search movies" : tab == 2 ? "Search shows" : "Search movies & shows");
        c.drawText(TextUtils.ellipsize(shown, new TextPaint(chipPaint), s.width() - 170 * k, TextUtils.TruncateAt.END).toString(),
                s.left + 68 * k, s.centerY() + 10 * k, chipPaint);

        // Mic: a soft round button inside the search field, with a thin microphone (like YouTube's).
        RectF m = barRects[BAR_MIC];
        boolean mf = barFocus == BAR_MIC;
        fill.setShader(null);
        if (mf) fill.setColor(0xFFFFFFFF);
        else if (listening) fill.setColor(night ? 0x55FFFFFF : 0x33000000);
        else fill.setColor(night ? 0x26FFFFFF : 0x14000000);
        c.drawCircle(m.centerX(), m.centerY(), m.width() / 2, fill);
        int micInk = mf ? 0xFF1C1C1E : sf ? fg : ink();
        drawMic(c, m.centerX(), m.centerY() - 1 * k, 0.3f * k, micInk);

        // Filters.
        chipPaint.setTextSize(26 * k);
        boolean[] activeChip = {!filters.type.equals(filters.defaultType), filters.genre != 0, filters.year != 0, !filters.platform.isEmpty()};
        for (int i = 0; i < 4; i++) {
            RectF r = barRects[BAR_TYPE + i];
            boolean f = barFocus == BAR_TYPE + i;
            drawPill(c, r, f, activeChip[i]);
            chipPaint.setColor(pillInk(f));
            c.drawText(chips[i], r.left + 28 * k, r.centerY() + 9 * k, chipPaint);
        }
        if (filters.active()) {
            RectF r = barRects[BAR_CLEAR];
            boolean f = barFocus == BAR_CLEAR;
            drawPill(c, r, f, false);
            chipPaint.setColor(pillInk(f));
            c.drawText(clear, r.left + 28 * k, r.centerY() + 9 * k, chipPaint);
        }
    }

    private void drawMic(Canvas c, float cx, float cy, float s, int color) {
        fill.setShader(null);
        fill.setColor(color);
        c.drawRoundRect(cx - 17 * s, cy - 52 * s, cx + 17 * s, cy + 12 * s, 17 * s, 17 * s, fill);
        rim.setColor(color);
        rim.setStrokeWidth(Math.max(1.6f, 6 * s));
        rim.setStrokeCap(Paint.Cap.ROUND);
        cell.set(cx - 34 * s, cy - 30 * s, cx + 34 * s, cy + 38 * s);
        c.drawArc(cell, 0, 180, false, rim);
        c.drawLine(cx, cy + 38 * s, cx, cy + 58 * s, rim);
        rim.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawResults(Canvas c, int w, int h) {
        summaryPaint.setTextSize(28 * k);
        summaryPaint.setColor(dim());
        String summary;
        if (results.isEmpty()) summary = resultLoading || (!resultDone && !resultFailed) ? Ui.tr("Searching…") : resultFailed ? Ui.tr("Couldn't reach TMDB. Trying again…") : Ui.tr("No titles found. Try other words or filters.");
        else summary = Ui.plural("plural|one:{n} title|other:{n} titles", results.size()) + (resultDone ? "" : "+");
        c.drawText(summary, MARGIN * k, 172 * k, summaryPaint);
        float cw = CARD_W * k, ch = CARD_H * k, gap = GAP * k, pitch = (CARD_H + 84) * k;
        c.save();
        c.clipRect(0, 196 * k, w, h);
        int total = results.isEmpty() && (resultLoading || !resultDone) ? GRID_COLS * 2 : results.size();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < total; i++) {
                boolean on = barFocus < 0 && i == gridIdx;
                if (on != (pass == 1)) continue;
                float x = MARGIN * k + (i % GRID_COLS) * (cw + gap), y = 224 * k + (i / GRID_COLS - gridScroll.value) * pitch;
                if (y > h) continue;
                if (y + ch + 60 * k < 196 * k) continue;
                if (i >= results.size()) {
                    fill.setShader(null);
                    fill.setColor(night ? 0xFF1C1C1E : 0xFFE3E3E8);
                    c.drawRoundRect(x, y, x + cw, y + ch, 14 * k, 14 * k, fill);
                    continue;
                }
                Item it = results.get(i);
                drawCard(c, it, x, y, cw, ch, on);
                chipPaint.setTextSize(24 * k);
                chipPaint.setColor(on ? ink() : dim());
                c.drawText(TextUtils.ellipsize(it.title, new TextPaint(chipPaint), cw + gap - 26 * k, TextUtils.TruncateAt.END).toString(), x, y + ch + 52 * k, chipPaint);
            }
        }
        c.restore();
    }

    private void drawPicker(Canvas c, int w, int h) {
        Picker p = picker;
        fill.setShader(null);
        fill.setColor(0x80000000);
        c.drawRect(0, 0, w, h, fill);
        RectF anchor = barRects[p.bar];
        float rowH = 66 * k, panelW = 380 * k, pad = 18 * k, titleH = 70 * k;
        int visible = Math.min(p.labels.size(), 8);
        float panelH = titleH + visible * rowH + pad;
        float px = Math.max(MARGIN * k, Math.min(anchor.left, w - MARGIN * k - panelW)), py = 120 * k;
        cell.set(px, py, px + panelW, py + panelH);
        fill.setColor(night ? 0xF2232326 : 0xF5F7F7FA);
        c.drawRoundRect(cell, 30 * k, 30 * k, fill);
        rim.setColor(night ? 0x44FFFFFF : 0x33000000);
        rim.setStrokeWidth(2 * k);
        c.drawRoundRect(cell, 30 * k, 30 * k, rim);
        chipPaint.setTextSize(28 * k);
        chipPaint.setColor(dim());
        c.drawText(p.title, px + 30 * k, py + 46 * k, chipPaint);
        int first = Math.max(0, Math.min(p.cursor - visible / 2, p.labels.size() - visible));
        String current = p.bar == BAR_TYPE ? filters.type : p.bar == BAR_GENRE ? String.valueOf(filters.genre) : p.bar == BAR_YEAR ? String.valueOf(filters.year) : filters.platform;
        for (int i = 0; i < visible; i++) {
            int idx = first + i;
            float ry = py + titleH + i * rowH;
            boolean on = idx == p.cursor;
            if (on) {
                fill.setColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
                c.drawRoundRect(px + 12 * k, ry, px + panelW - 12 * k, ry + rowH - 6 * k, 22 * k, 22 * k, fill);
            }
            chipPaint.setTextSize(30 * k);
            chipPaint.setColor(on ? (night ? 0xFF1C1C1E : 0xFFFFFFFF) : ink());
            boolean chosen = p.values.get(idx).equals(current) || (p.bar == BAR_YEAR && p.values.get(idx).equals("0") && filters.year == 0);
            c.drawText(TextUtils.ellipsize(p.labels.get(idx), new TextPaint(chipPaint), panelW - 110 * k, TextUtils.TruncateAt.END).toString(), px + 36 * k, ry + 43 * k, chipPaint);
            if (chosen) c.drawText("✓", px + panelW - 62 * k, ry + 43 * k, chipPaint);
        }
        if (p.labels.size() > visible) {
            chipPaint.setTextSize(22 * k);
            chipPaint.setColor(dim());
            c.drawText("▲▼", px + panelW - 70 * k, py + 44 * k, chipPaint);
        }
    }

    private void drawVoice(Canvas c, int w, int h) {
        fill.setShader(null);
        fill.setColor(0xB3000000);
        c.drawRect(0, 0, w, h, fill);
        float pw = 820 * k, ph = 440 * k, px = (w - pw) / 2, py = (h - ph) / 2;
        cell.set(px, py, px + pw, py + ph);
        fill.setColor(night ? 0xF2232326 : 0xF7F7F7FA);
        c.drawRoundRect(cell, 48 * k, 48 * k, fill);
        float cx = w / 2f, cy = py + 150 * k;
        // The ring breathes with the voice.
        fill.setColor(night ? 0x33FFFFFF : 0x22000000);
        c.drawCircle(cx, cy, (74 + 46 * voiceLevel) * k, fill);
        fill.setColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
        c.drawCircle(cx, cy, 66 * k, fill);
        drawMic(c, cx, cy - 4 * k, 0.9f * k, night ? 0xFF1C1C1E : 0xFFFFFFFF);
        chipPaint.setTextSize(34 * k);
        chipPaint.setColor(ink());
        chipPaint.setTextAlign(Paint.Align.CENTER);
        String line = partial.isEmpty() ? Ui.tr("Listening…") : partial;
        c.drawText(TextUtils.ellipsize(line, new TextPaint(chipPaint), pw - 100 * k, TextUtils.TruncateAt.END).toString(), cx, py + 300 * k, chipPaint);
        chipPaint.setTextSize(24 * k);
        chipPaint.setColor(dim());
        c.drawText(Ui.tr(partial.isEmpty() ? "Say a title, an actor or a genre" : "Back to cancel"), cx, py + 360 * k, chipPaint);
        chipPaint.setTextAlign(Paint.Align.LEFT);
        postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        k = w / 1920f;
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        night = data.isNight(System.currentTimeMillis());
        boolean moving = scroll.step(dt);
        moving |= heroIn.step(dt) | gridScroll.step(dt);
        if (detail != null) moving |= detailIn.step(dt);
        for (Row r : rows) moving |= r.sx.step(dt);
        c.drawColor(bg());

        if (detailOnly) {      // opened from the home screen: just the title's page
            if (detail != null) drawDetail(c, w, h);
            if (moving) postInvalidateOnAnimation();
            return;
        }
        if (resultsMode) {
            drawResults(c, w, h);
            if (!results.isEmpty() || resultFailed) loadResults();
        } else {
            Row focusRow = row();
            Item focus = focusRow.items.isEmpty() ? null : focusRow.items.get(colIn(focusRow));
            if (focus != heroItem) {
                heroItem = focus;
                heroIn.value = 0;
                heroIn.velocity = 0;
                heroIn.target = 1;
                moving = true;
            }
            drawHero(c, w, focus);

            // Rows: from the one scrolling out to the ones below; each loads when it is about to show.
            c.save();
            c.clipRect(0, HERO_H * k, w, h);
            for (int r = Math.max(0, (int) Math.floor(scroll.value)); r < rows.size(); r++) {
                float top = (ROWS_TOP + (r - scroll.value) * PITCH) * k;
                if (top > h) break;
                drawRow(c, rows.get(r), r, top, w);
            }
            c.restore();
            ensureRows(rowIdx + 4);
            for (int r = rowIdx; r < rows.size() && r <= rowIdx + 2; r++) load(rows.get(r));
        }
        drawBar(c, w);
        if (picker != null) drawPicker(c, w, h);
        if (listening) drawVoice(c, w, h);
        if (detail != null) drawDetail(c, w, h);
        if (moving) postInvalidateOnAnimation();
    }

    private void drawHero(Canvas c, int w, Item it) {
        float hh = HERO_H * k;
        if (it == null) {
            empty.setColor(dim());
            empty.setTextSize(32 * k);
            c.drawText(Ui.tr("Loading…"), MARGIN * k, 300 * k, empty);
            return;
        }
        float a = Ui.clamp(heroIn.value);
        Bitmap b = backdrop(it.backdrop);
        if (b != null) {
            // The photo covers the right side of the hero, 16:9, then fades into the background.
            float bw = Math.max(w * 0.72f, hh * b.getWidth() / b.getHeight()), bh = bw * b.getHeight() / b.getWidth();
            cell.set(w - bw, 0, w, bh);
            bitmapPaint.setAlpha(Math.round(255 * a));
            c.save();
            c.clipRect(0, 0, w, hh);
            c.drawBitmap(b, null, cell, bitmapPaint);
            c.restore();
            bitmapPaint.setAlpha(255);
        }
        int bg = bg();
        if (sideFade == null || fadeColor != bg || fadeW != w || fadeH != (int) hh) {
            fadeColor = bg;
            fadeW = w;
            fadeH = (int) hh;
            sideFade = new LinearGradient(0, 0, w * 0.86f, 0, new int[] {bg, bg, Ui.alpha(bg, 0.4f), 0},
                    new float[] {0, 0.46f, 0.72f, 1}, Shader.TileMode.CLAMP);
            bottomFade = new LinearGradient(0, hh * 0.55f, 0, hh, bg & 0x00FFFFFF, bg, Shader.TileMode.CLAMP);
        }
        shaderPaint.setShader(sideFade);
        c.drawRect(0, 0, w, hh, shaderPaint);
        shaderPaint.setShader(bottomFade);
        c.drawRect(0, hh * 0.5f, w, hh, shaderPaint);
        shaderPaint.setShader(null);

        float x = MARGIN * k;
        heroTitle.setColor(Ui.alpha(ink(), a));
        heroTitle.setTextSize(64 * k);
        String title = TextUtils.ellipsize(it.title, new TextPaint(heroTitle), w * 0.56f, TextUtils.TruncateAt.END).toString();
        c.drawText(title, x, 250 * k, heroTitle);
        heroMeta.setColor(Ui.alpha(dim(), a));
        heroMeta.setTextSize(30 * k);
        c.drawText(it.meta, x, 306 * k, heroMeta);
        if (!it.overview.isEmpty()) {
            StaticLayout layout = synopsis.get(it.id);
            if (layout == null) {
                bodyPaint.setTextSize(28 * k);
                layout = StaticLayout.Builder.obtain(it.overview, 0, it.overview.length(), bodyPaint, Math.round(w * 0.5f))
                        .setMaxLines(4).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0, 1.18f).build();
                if (synopsis.size() > 40) synopsis.clear();
                synopsis.put(it.id, layout);
            }
            bodyPaint.setColor(Ui.alpha(night ? 0xFFD1D1D6 : 0xFF3A3A3C, a));
            c.save();
            c.translate(x, 340 * k);
            layout.draw(c);
            c.restore();
        }
    }

    private void drawRow(Canvas c, Row r, int index, float top, int w) {
        rowTitle.setColor(ink());
        rowTitle.setTextSize(36 * k);
        c.drawText(Ui.tr(r.title), MARGIN * k, top, rowTitle);
        float cw = CARD_W * k, ch = CARD_H * k, gap = GAP * k, y = top + 52 * k;
        boolean focused = index == rowIdx && barFocus < 0;
        int sel = colIn(r);
        if (r.items.isEmpty()) {
            // Gray placeholders while the first page loads.
            fill.setColor(night ? 0xFF1C1C1E : 0xFFE3E3E8);
            for (int i = 0; i < 9; i++) {
                float x = MARGIN * k + i * (cw + gap);
                c.drawRoundRect(x, y, x + cw, y + ch, 14 * k, 14 * k, fill);
            }
            return;
        }
        // The focused card is drawn last (on top of its neighbors).
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < r.items.size(); i++) {
                boolean on = focused && i == sel;
                if (on != (pass == 1)) continue;
                float x = MARGIN * k + i * (cw + gap) - r.sx.value;
                if (x > w) break;
                if (x + cw < 0) continue;
                drawCard(c, r.items.get(i), x, y, cw, ch, on);
            }
        }
    }

    private void drawCard(Canvas c, Item it, float x, float y, float cw, float ch, boolean focused) {
        float s = focused ? 1 + GROW : 1;
        float cx = x + cw / 2, cy = y + ch / 2;
        cell.set(cx - cw * s / 2, cy - ch * s / 2, cx + cw * s / 2, cy + ch * s / 2);
        float rad = 14 * k;
        Img img = poster(it.poster);
        // A soft shadow under every card, deeper under the selected one.
        shadowPaint.setColor(focused ? (night ? 0x73000000 : 0x40000000) : (night ? 0x40000000 : 0x1F000000));
        float lift = focused ? 20 * k : 8 * k, blur = focused ? 22 * k : 12 * k;
        shadowPaint.setMaskFilter(new android.graphics.BlurMaskFilter(Math.max(1, blur), android.graphics.BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(cell.left + 4 * k, cell.top + lift, cell.right - 4 * k, cell.bottom + lift - 6 * k, rad, rad, shadowPaint);
        shadowPaint.setMaskFilter(null);
        if (img == null) {
            fill.setColor(night ? 0xFF2C2C2E : 0xFFE4E4EA);
            c.drawRoundRect(cell, rad, rad, fill);
        } else {
            if (img.shader == null) img.shader = new BitmapShader(img.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            matrix.setScale(cell.width() / img.bitmap.getWidth(), cell.height() / img.bitmap.getHeight());
            matrix.postTranslate(cell.left, cell.top);
            img.shader.setLocalMatrix(matrix);
            shaderPaint.setShader(img.shader);
            c.drawRoundRect(cell, rad, rad, shaderPaint);
            shaderPaint.setShader(null);
        }
        // A hairline edge keeps light posters from melting into the light background; the selected one gets a soft ring.
        rim.setColor(focused ? (night ? 0x66FFFFFF : 0x661D1D1F) : (night ? 0x14FFFFFF : 0x14000000));
        rim.setStrokeWidth((focused ? 2.5f : 1.5f) * k);
        c.drawRoundRect(cell, rad, rad, rim);
    }
}
