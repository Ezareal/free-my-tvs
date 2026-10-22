# Education

Education is a family mode in the Android/Google TV launcher. The main home screen has a **Kids mode** circle (sparkles icon) beside the Settings circle. Once a parent PIN, viewing controls and at least one app or console are configured, this starts Kids mode directly without requesting the PIN. Entering the restricted mode is unrestricted; leaving it or changing parental controls still needs the PIN. If setup is incomplete, the shortcut opens PIN-protected setup instead. The Education entry screen separately offers Start Kids mode, Parent settings and Back to home screen.

The parent menu offers Start Kids mode, Adult mode, Apps and consoles, Time limits, and More settings. The setup checklist lives under Apps and consoles and appears automatically only while setup is incomplete. Education uses the existing Inter fonts, Material Symbols, compact settings rows and white focus treatment. Adult Home and Education include offline animated backgrounds: blue/teal washes for Adult mode and mint/purple/peach washes for Kids mode, with subtle remote-direction motion. Adult accents vary by time of day. Three cached 128-pixel RGBA_F16 gradient textures (384 KiB) and a shared 64 KiB static dither texture reduce banding. Background updates are capped at 10 Hz, independently of focus animations, and stop when inactive. This is a bounded rendering design, not a measured TV frame-time guarantee. No artwork key is required. The web companion uses moving CSS gradients and a static dither image. Maths uses a fixed two-column question/keypad layout without a ScrollView, checked at the TCL's 1920×1080 / 320-dpi display configuration.

## Configurable time limits

Parent settings → Time limits lists the combined daily allowance, YouTube Kids, ABC Kids, and each approved HDMI port. Select an entry to change either its daily minutes (1–720) or the minutes earned after maths (1–120). Both default to the previous policy: 180 daily minutes and 30 reward minutes. The combined limit is a ceiling shared by all sources; a source also stops when its own limit is reached. For example, a 180-minute combined limit plus a 60-minute YouTube limit permits at most 60 minutes of YouTube before maths even if the other sources have time left.

Changing a limit never clears usage. Only the new calendar day resets used time and earned extensions; limits and PIN stay saved. Per-source accounting starts with this version, while previously recorded combined usage is retained. A five-answer challenge extends the exhausted source and/or the exhausted combined allowance as applicable; it does not reset another source's counter. App and console cards show remaining time subject to both limits. Optional practice does not grant time. `tests/education-source-limits.py` verifies per-app expiry while total time remains, the source reward, and editing a limit without resetting time.

## Family setup

1. Create a four-digit parent PIN and enter it again to confirm. There is no default production PIN.
2. Select **Enable Education controls**, then enable the **Education** accessibility service in the TV's settings. It observes foreground package names, not screen text or video content.
3. In adult mode, set up the child profiles in YouTube and ABC iview. Return to Parent Mode and confirm each app's profile setup. Only installed, approved packages can open from Kids mode.
4. Select **Start Kids mode**. To return to the normal launcher, open **Parent Mode**, enter the PIN, then select **Adult mode**.

FreeMyTV remembers Kids versus Adult mode across activity/process restarts and device reboots. If FreeMyTV is the default HOME launcher, Android's Home route uses that saved choice. Otherwise it applies when FreeMyTV opens; this feature does not silently replace the TV's default launcher. Parent authentication itself is deliberately temporary and expires when the parent leaves the activity. The PIN, usage, rewards and learning progress remain stored locally.

## Watching and maths

- The combined daily allowance defaults to three hours, shared across approved apps and consoles, with configurable per-source limits as above. Time with the screen off, in the Education menu or solving questions does not consume the allowance. Paused video with its app still visible does count: the service cannot inspect playback.
- At an applicable limit, the guard requests media pause and opens a five-question maths challenge. Five correct answers award the configured extra minutes (30 by default). Another challenge is needed when those minutes run out. Wrong answers give a gentle retry; there is no countdown or penalty.
- The on-screen number pad works with arrows and OK, touch, or a remote's number buttons. Back during an exhausted combined allowance opens the parent PIN screen. With only one source exhausted, Back may return to Kids mode; that source still requires maths before it can reopen.
- Questions are generated locally. They begin with addition up to ten. Each completed practice day adds a small step for the **next** day: slightly larger numbers, subtraction after three practice days, then small multiplication from day fourteen. Repeating challenges on the same day does not accelerate difficulty. Days away do not add difficulty.
- Optional practice does not award viewing time in advance. A parent can reset maths difficulty without resetting today's watching time.
- Usage and earned time persist across switching modes and restarting. A new day resets the allowance and rewards; moving the clock backwards does not create another allowance. This is not tamper-proof against an administrator changing time or app data.

## Gaming consoles

Parent Mode → Gaming consoles lists the TV's HDMI ports and HDMI-CEC device names where available (for example PlayStation 5, Xbox or Nintendo Switch). A console that does not advertise a name is shown by HDMI port; a name is not proof of console type. Parents approve each port explicitly. Approval follows the physical port, so changing the plugged-in device does not require new approval. Kids see approved ports; ports without a reported active signal are dimmed and say to turn the console on, but remain selectable because some TVs report standby until tuned. TV input callbacks refresh connection state.

Gaming and streaming share the existing three-hour daily budget and 30-minute maths rewards. A full-screen Android TvView owns the approved HDMI session and shows the remaining time. The guard counts that foreground session only with a fresh heartbeat and valid port approval. At expiry the activity mutes and releases its HDMI session, then returns to Education's five-question maths challenge. Back/Home or leaving the activity also releases the session. A missing guard closes gaming. Time counts while that screen is open, including connection/no-signal time; it cannot know whether the child is actively playing.

The console keeps running when its TV picture closes. This does not pause a game, save progress or power off the console. The same launcher-level limitations still apply to vendor input menus and HDMI-CEC behaviour outside FreeMyTV.

Each parent approval dialog includes **Test return · 10 seconds**, which exercises HDMI viewing and automatic return without using the daily allowance or granting approval.

Physical TCL Android 11 / V620 validation with PlayStation 5 on HDMI 2 confirmed video availability. TCL's HDR/ALLM status overlay emits a `com.tcl.tv` / `android.widget.RelativeLayout` window event; the guard now retains the existing foreground console session only for that exact event while the approved session heartbeat is fresh. Other TCL activities and unapproved apps remain guarded.

`tests/Build-HdmiProbe.ps1` builds a separate diagnostic APK with two explicit runners. `HdmiProbe` exercises the ten-second parent test without changing allowances. `ConsoleCycleProbe` requires an already approved input and active Kids mode, saves budget/progress fields privately, temporarily sets ten seconds remaining and a one-minute maths reward, then exercises real expiry, five correct answers and resumed play. It restores the saved fields in `finally`; an interrupted run can use `-e action restore` before removing the diagnostic APK. It never changes the PIN or approves an input. Both require an explicit input ID for a test run. These are separate from the emulator-only EducationTests/PlayerFixture APKs, which must never be installed on a real TV.

The physical console cycle passed: ten seconds of remaining allowance expired into maths, five correct answers granted the temporary reward, HDMI video resumed, and the resumed timer decreased over fifteen seconds. Original limits, usage, rewards and maths progress were restored and the diagnostic package removed.

## Streaming profiles

YouTube Kids on TVs is generally a child profile within YouTube. Configure YouTube's own **parent code** to protect adult profiles and signed-out viewing. Education opens the approved package; it cannot choose or lock a profile inside another company's app. See [YouTube Kids on TV](https://support.google.com/youtubekids/answer/9618031?hl=en) and [YouTube parental controls / parent code](https://support.google.com/youtubekids/answer/6172308?hl=en-GB).

ABC Kids is available through an ABC iview child profile. Education likewise cannot prevent profile switching within iview. See [ABC child profiles](https://help.abc.net.au/hc/en-us/articles/8551863438223-How-do-I-create-a-child-s-ABC-iview-profile). App availability and the supported profile controls must be checked on the actual TV before treating the setup as suitable for unattended use.

While its service is running, Education sends unapproved foreground apps back to its home and gates FreeMyTV's settings and launcher entry points. It is a launcher-level family aid, not Android device-owner/kiosk management. Hardware HDMI changes, firmware menus, uninstall/clear-data, disabling the accessibility service, vendor service killing, and changes within an approved app are outside a complete security boundary. The existing Original-mode recovery and watchdog remain intact. No root, firmware change or package disabling is added by Education. A missing service prevents launches through the Kids screen; a removed service cannot enforce rules elsewhere on the TV.

PINs use a random salt and PBKDF2-HMAC-SHA256 (120,000 iterations). Five incorrect attempts impose a 30-second retry delay. State is in app-private storage with an OS file lock and atomic replacement so the UI and guard processes do not overwrite each other's updates. No PIN or answer is sent over the network.

## Platforms

### TCL Home priority compatibility

On the tested TCL Android 11 firmware, Android records FreeMyTV as the Home role holder but still resolves Home to Google TV because of the system activity's priority. The optional **Free My TV Home** accessibility service redirects only Google TV's HomeActivity and the setup app's RecoveryActivity to FreeMyTV. It does not read screen contents, disable system packages or alter Wi-Fi networks. It acts only while FreeMyTV holds Android's Home role, and stops retrying if the existing crash safeguard trips. Disable this service to stop the redirect. A brief stock-home flash is possible; this is a compatibility redirect, not a firmware-level change to activity priorities.

The service also requests key filtering to handle physical Home presses before the stock launcher opens. All other keys pass through untouched. The TV reports the key-filter capability bound, but the user confirmed that physical Home opens FreeMyTV. No direct key-handler log was observed, so this confirms the route rather than proving interception before the stock launcher. No text or key history is stored; a diagnostic log records only that Home was handled.

The service was enabled on the TCL over ADB after installation. Home from TV settings was verified, changing the Home role back to Google TV stopped the redirect, and restoring FreeMyTV's role restored the redirect. The saved-network list was identical before and after. A cold power-on test has not yet been performed. The helper `tools/Restore-TclHome.ps1` restores Google TV's Home role and removes only this redirect service from the enabled accessibility list.

The working mode and time guard are Android-only. The webOS/Tizen drawer includes an explicitly labelled Education **preview** with the matching design and practice keypad. It never launches streaming apps or claims to enforce their viewing time; those platforms do not expose this Android guard.

Education text is available in English, Portuguese, Spanish, German and Russian. The Android activity follows FreeMyTV's selected language. Regenerate translations with `py -3.11 tools/education-translations.py` after updating their source.

## Verification

Build the normal APK with `./build.sh`. `tests/Test-Education.ps1` checks time boundaries and 10,000 generated questions. Android instrumentation in `tests/` checks persisted day transitions, PIN hashing/verification and retry lockout. `tests/education-ui-smoke.py` exercises the APK, foreground-app expiry, maths reward, parent gate, settings redirects and mode persistence on a **disposable emulator**.

The player fixture uses a known allowlisted package name but displays “Education test fixture — not YouTube.” Never install it on a real TV or over a real YouTube installation. The separate instrumentation APK and fixture are not included in `FreeMyTV.apk`.

The production APK was installed and tested on the TCL, including the physical HDMI/maths cycle above. Actual YouTube/ABC playback and profile restrictions, cold power-on behaviour, other manufacturers, and physical webOS/Tizen devices remain outside the verified coverage. Emulator UI tests cover app expiry, rewards, PIN protection, the Kids shortcut and per-source settings; their fixture is not evidence of real streaming-service behaviour.
