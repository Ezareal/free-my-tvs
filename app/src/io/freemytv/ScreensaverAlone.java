package io.freemytv;

/**
 * The screensaver on Android 11. There the system's DreamActivity runs in the app's default process and only works
 * with the DreamService in that same process, which can't also hold a home activity (#10). So Android 11 gets this
 * copy, in the main process; every other version keeps Screensaver in :home (app/res/values-v30/bools.xml). Some
 * Android 12 TVs (a TCL) do as Android 11 does: there the first crash switches to this copy (App, HomeMode.useDreamAlone).
 */
public class ScreensaverAlone extends Screensaver {
}
