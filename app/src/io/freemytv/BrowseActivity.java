package io.freemytv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

/**
 * Browse: every title TMDB has on the TV's streaming apps, row by row by genre, without an end (Explore, Movies and
 * Shows tabs of the home screen open it). It runs in the :home process, like the home screen.
 */
public class BrowseActivity extends Activity {
    static final String EXTRA_TAB = "tab", EXTRA_ID = "id", EXTRA_KIND = "kind", EXTRA_TITLE = "title", EXTRA_POSTER = "poster",
            EXTRA_BACKDROP = "backdrop", EXTRA_META = "meta";
    private BrowseView view;

    /** A title's page (description, rating, cast), from the home screen; Back returns there. */
    static void open(Activity from, Recs.Pick p) {
        from.startActivity(new Intent(from, BrowseActivity.class).putExtra(EXTRA_ID, p.id).putExtra(EXTRA_KIND, p.kind)
                .putExtra(EXTRA_TITLE, p.title).putExtra(EXTRA_POSTER, p.poster).putExtra(EXTRA_BACKDROP, p.backdrop).putExtra(EXTRA_META, p.meta));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        Intent in = getIntent();
        BrowseView.Item start = null;
        if (in.hasExtra(EXTRA_ID)) {
            start = new BrowseView.Item();
            start.id = in.getIntExtra(EXTRA_ID, 0);
            start.kind = in.getStringExtra(EXTRA_KIND);
            start.title = in.getStringExtra(EXTRA_TITLE);
            start.poster = in.getStringExtra(EXTRA_POSTER);
            start.backdrop = in.getStringExtra(EXTRA_BACKDROP);
            start.meta = in.getStringExtra(EXTRA_META) == null ? "" : in.getStringExtra(EXTRA_META);
            start.overview = "";
        }
        view = new BrowseView(this, in.getIntExtra(EXTRA_TAB, 0), start);
        setContentView(view);
        getWindow().setBackgroundDrawable(null);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (view != null && view.key(keyCode)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (view != null) view.permissionResult(requestCode, grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED);
    }

    @Override protected void onDestroy() {
        if (view != null) view.release();
        super.onDestroy();
    }
}
