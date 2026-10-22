package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import org.json.JSONObject;

/** Observes package names only. No screen contents, browsing history or answers are collected. */
public class EducationGuard extends AccessibilityService {
    private final Handler handler = new Handler();
    private String foreground = "";
    private long last, redirected;
    private boolean watching;
    private String watchingKey="";
    private final Runnable tick = new Runnable() {
        @Override public void run() { update(true); handler.postDelayed(this, 1000); }
    };
    @Override protected void onServiceConnected() {
        last = SystemClock.elapsedRealtime();
        // Charge a pending viewing interval after a service/process restart, capped at the allowance.
        EducationStore.access(this, s -> {
            EducationStore.day(s);
            if (s.optBoolean("watching") && s.optBoolean("kids")) {
                long missed = Math.max(0, System.currentTimeMillis() - s.optLong("seen", System.currentTimeMillis()));
                EducationBudgets.charge(s,s.optString("watchingKey","youtube"),Math.min(missed,EducationStore.remaining(s)));
            }
            s.put("watching", false); s.remove("gaming");
        });
        handler.post(tick);
        if (EducationStore.kids(this)) EducationStore.open(this);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        update(false); // account for the previous app without redirecting a stale window
        String pkg = e.getPackageName().toString();
        if(EducationInputs.statusOverlay(EducationStore.read(this),foreground,getPackageName(),
                SystemClock.elapsedRealtime(),pkg,String.valueOf(e.getClassName()))) {
            android.util.Log.i("FreeMyTVGuard","HDMI status overlay; retaining timed console session");
            update(true); return;
        }
        // Volume/power overlays do not end a viewing session.
        if (!pkg.equals("com.android.systemui") && !pkg.equals("android")) foreground = pkg;
        update(true);
    }
    private void update(boolean enforce) {
        long now = SystemClock.elapsedRealtime(), elapsed = Math.max(0, now - last); last = now;
        boolean interactive = ((PowerManager)getSystemService(POWER_SERVICE)).isInteractive();
        JSONObject s = EducationStore.access(this, state -> {
            EducationStore.day(state);
            if (watching && state.optBoolean("kids")) EducationBudgets.charge(state,watchingKey,elapsed);
            state.put("heartbeat", now).put("guard", true).put("seen", System.currentTimeMillis());
            watchingKey=EducationBudgets.source(state,foreground,getPackageName(),now);
            watching = interactive && state.optBoolean("kids") && !watchingKey.isEmpty()
                    && EducationBudgets.remaining(state,watchingKey)>0;
            state.put("watching", watching).put("watchingKey",watchingKey);
            if(!watchingKey.isEmpty()&&EducationBudgets.remaining(state,watchingKey)==0)state.put("challengeKey",watchingKey);
        });
        if (enforce && interactive && s.optBoolean("kids") && !foreground.isEmpty() && !foreground.equals(getPackageName())
                && (!EducationStore.allowed(s, foreground) || (!watchingKey.isEmpty()&&EducationBudgets.remaining(s,watchingKey)==0))) {
            if (now - redirected > 1500) {
                redirected = now;
                android.util.Log.i("FreeMyTVGuard","Redirect package="+foreground+" source="+watchingKey);
                // Stop the active media session as well as hiding its activity (background audio may continue).
                android.media.AudioManager audio = (android.media.AudioManager)getSystemService(AUDIO_SERVICE);
                audio.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE));
                audio.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE));
                EducationStore.open(this);
            }
        }
    }
    @Override public void onInterrupt() { watching = false; }
    @Override public void onDestroy() {
        handler.removeCallbacks(tick);
        EducationStore.access(this, s -> s.put("guard", false).put("watching", false));
        super.onDestroy();
    }
}
