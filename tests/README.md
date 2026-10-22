# Education verification

Build production first with `./build.sh`. On Windows the helper scripts use the Android SDK under LOCALAPPDATA and Android Studio's bundled JDK; set JAVA_HOME for Test-Education.ps1 if needed.

## Disposable emulator only

1. Run `tests/Test-Education.ps1` (budget boundaries and 10,000 generated questions).
2. Run `tests/Build-Instrumentation.ps1` and `tests/Build-PlayerFixture.ps1`.
3. Install FreeMyTV.apk, out/instrumentation/EducationTests.apk, and the player fixture APK on a disposable emulator.
4. Run `python tests/education-ui-smoke.py --serial emulator-5580` and `python tests/education-source-limits.py --serial emulator-5580`, substituting the emulator serial.

These scripts replace emulator family settings and use fixture PIN 4826. Never install EducationTests or the player fixture on a physical TV. The fixture deliberately uses YouTube's package name and is not the real app. UI observation uses FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES to keep the guard running.

## Explicit physical HDMI checks

`tests/Build-HdmiProbe.ps1` creates out/hdmi-probe/HdmiProbe.apk, signed with the same local key as production. Install that separate package only for an authorized test.

- `adb -s SERIAL shell am instrument -w -e input INPUT_ID io.freemytv.hdmiprobe/io.freemytv.HdmiProbe`: the parent ten-second HDMI diagnostic, without budget changes.
- `adb -s SERIAL shell am instrument -w -e input INPUT_ID io.freemytv.hdmiprobe/io.freemytv.ConsoleCycleProbe`: an existing approved PlayStation entry in English Kids mode, at least two minutes overall allowance, and the enabled guard are required. Saves the budget/progress fields privately, sets ten seconds of console time remaining and a one-minute reward, answers five questions, verifies resumed counting for fifteen seconds, and restores saved fields in finally. It never sets a PIN or approves a port.
- If interrupted, run `adb -s SERIAL shell am instrument -w -e action restore io.freemytv.hdmiprobe/io.freemytv.ConsoleCycleProbe` before removing the diagnostic. The backup remains in app-private storage until restoration succeeds.

Instrumentation can restart app processes. Reconnect the already-enabled FreeMyTV accessibility services after testing, preserving all unrelated entries, and verify both are bound. Remove the helper with `adb -s SERIAL uninstall io.freemytv.hdmiprobe`. Never clear production data.

Physical TCL Android 11 V620 / PlayStation 5 HDMI 2: video available, real budget expiry into maths, five-answer reward, resumed video and decreasing time all passed. Original budgets/progress were restored. Other TVs, cold boot and actual streaming-app profile restrictions are not covered by this result.
