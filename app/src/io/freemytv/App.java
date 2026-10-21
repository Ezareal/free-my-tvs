package io.freemytv;

import android.app.Application;

/** Gives Prefs a context in every process. */
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        if (getPackageName().equals(getProcessName()) && !HomeMode.dreamAlone(this)) watchDream();
    }

    /**
     * Some TVs' Android 12 (a TCL) start the screensaver's DreamActivity in this, the main process, where it only works
     * with the dream in this same process, as on Android 11; with Screensaver in :home it crashes on a cast. That crash
     * moves the screensaver here for good (HomeMode.useDreamAlone): the next one opens.
     */
    private void watchDream() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                if (dreamCrash(error)) {
                    AppLog.w("screensaver crashed in the main process: moving it there");
                    HomeMode.useDreamAlone(this);
                }
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    private static boolean dreamCrash(Throwable t) {
        for (int depth = 0; t != null && depth < 8; t = t.getCause(), depth++) {
            if (t instanceof ClassCastException && String.valueOf(t.getMessage()).contains("DreamActivityCallback")) return true;
        }
        return false;
    }
}
