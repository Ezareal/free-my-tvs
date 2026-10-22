package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.app.role.RoleManager;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Keeps vendor Home priorities from overriding the user's selected Home role. */
public class HomeRedirect extends AccessibilityService {
    private long last = -2000;

    static boolean competingHome(String pkg, String cls) {
        return ("com.google.android.apps.tv.launcherx".equals(pkg)
                && "com.google.android.apps.tv.launcherx.home.HomeActivity".equals(cls))
            || ("com.google.android.tungsten.setupwraith".equals(pkg)
                && "com.google.android.tungsten.setupwraith.RecoveryActivity".equals(cls));
    }

    private boolean ownsHome() {
        if (Build.VERSION.SDK_INT < 29 || Watchdog.crashedTooOften(this)) return false;
        RoleManager roles = getSystemService(RoleManager.class);
        return roles != null && roles.isRoleHeld(RoleManager.ROLE_HOME);
    }

    private void openHome() {
        if (!ownsHome()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - last < 1000) return;
        last = now;
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    @Override protected void onServiceConnected() { openHome(); }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME || !ownsHome()) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            openHome();
            android.util.Log.i("FreeMyTVHome", "Home key handled before stock launcher");
        }
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && competingHome(String.valueOf(event.getPackageName()), String.valueOf(event.getClassName()))) {
            openHome();
        }
    }

    @Override public void onInterrupt() {}
}
