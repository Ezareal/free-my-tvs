#!/usr/bin/env bash
# Build and deploy Free My TV to the configured Android TV over ADB.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TV_HOST="${1:-192.168.0.187}"
TV_SERIAL="$TV_HOST"
[[ "$TV_SERIAL" == *:* ]] || TV_SERIAL="$TV_SERIAL:5555"

fail() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

ADB_BIN="$(command -v adb || true)"
[[ -n "$ADB_BIN" ]] || fail "adb is not installed or is not available in PATH."
[[ -f "$ROOT/build.sh" ]] || fail "$ROOT/build.sh does not exist."

printf 'Checking TV %s...\n' "$TV_SERIAL"
"$ADB_BIN" start-server >/dev/null
state="$("$ADB_BIN" -s "$TV_SERIAL" get-state 2>/dev/null || true)"
if [[ "$state" != "device" ]]; then
  connect_result="$("$ADB_BIN" connect "$TV_SERIAL" 2>&1 || true)"
  printf '%s\n' "$connect_result"
  state="$("$ADB_BIN" -s "$TV_SERIAL" get-state 2>/dev/null || true)"
fi

if [[ "$state" != "device" ]]; then
  listed_state="$("$ADB_BIN" devices | awk -v target="$TV_SERIAL" '$1 == target { print $2; exit }')"
  case "$listed_state" in
    unauthorized)
      fail "TV detected, but ADB is not authorized. Accept the USB debugging prompt on the TV."
      ;;
    offline)
      fail "TV is offline in ADB. Restart ADB debugging or the TV, then try again."
      ;;
    *)
      fail "TV is powered off, unreachable, or ADB debugging is unavailable at $TV_SERIAL."
      ;;
  esac
fi

power_state="$("$ADB_BIN" -s "$TV_SERIAL" shell dumpsys power 2>/dev/null | tr -d '\r' || true)"
if ! grep -Eq 'mWakefulness=Awake|mInteractive=true|Display Power: state=ON' <<<"$power_state"; then
  fail "TV is connected through ADB, but it is not powered on and awake."
fi

printf 'TV is powered on and connected through ADB. Building...\n'
temporary_build=""
cleanup() {
  if [[ -n "$temporary_build" && -f "$temporary_build" ]]; then
    rm -f -- "$temporary_build"
  fi
}
trap cleanup EXIT

# Some Windows checkouts expose build.sh with CRLF, which Linux cannot execute directly.
if LC_ALL=C grep -q $'\r' "$ROOT/build.sh"; then
  temporary_build="$(mktemp "$ROOT/.build-normalized.XXXXXX.sh")"
  sed $'s/\r$//' "$ROOT/build.sh" > "$temporary_build"
  chmod +x "$temporary_build"
  "$temporary_build"
else
  "$ROOT/build.sh"
fi

[[ -f "$ROOT/FreeMyTV.apk" ]] || fail "The build did not produce FreeMyTV.apk."
[[ "$("$ADB_BIN" -s "$TV_SERIAL" get-state 2>/dev/null || true)" == "device" ]] \
  || fail "The TV lost its ADB connection during the build."

printf 'Installing FreeMyTV.apk...\n'
"$ADB_BIN" -s "$TV_SERIAL" install -r "$ROOT/FreeMyTV.apk"

home_holder="$("$ADB_BIN" -s "$TV_SERIAL" shell cmd role get-role-holders android.app.role.HOME 2>/dev/null \
  | tr -d '\r' | head -n 1)"
if [[ "$home_holder" == "io.freemytv" ]]; then
  "$ADB_BIN" -s "$TV_SERIAL" shell input keyevent KEYCODE_HOME
fi

"$ADB_BIN" -s "$TV_SERIAL" shell pm path io.freemytv >/dev/null \
  || fail "ADB completed, but io.freemytv is not installed."
printf 'Deployment completed on %s.\n' "$TV_SERIAL"
