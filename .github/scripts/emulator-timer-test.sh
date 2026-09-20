#!/bin/bash
# Runs on an Android emulator in CI (never on anybody's phone): does the timer watcher really work?
#
#   home in front            -> no watcher
#   leave home               -> watcher running as a foreground service, no crash
#   inside an app whose one-minute limit runs out, without "display over other apps"
#                            -> a "Time's up" notification, the app stays in front
#   same, with the switch on -> Focus's wall comes up in front of the app
#   back home                -> watcher gone
#
# The limited app is the emulator's own Contacts or Calculator or whatever launchable app is
# there; its limit is written into Focus's settings before the first start (debug build, run-as).
set -uo pipefail
PKG=com.focus.launcher
fail() { echo "FAIL: $*"; adb logcat -d -b crash -t 200 | tail -40; adb shell dumpsys activity services $PKG | head -30; exit 1; }
ok() { echo "ok   $*"; }
front() { adb shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | tr -d '\r'; }
watcher() { adb shell dumpsys activity services $PKG | grep -c "ServiceRecord.*TimerWatchService" | tr -d '\r'; }
wait_for() { # wait_for <seconds> <description> <command...>
  local secs=$1 what=$2; shift 2
  for _ in $(seq 1 "$secs"); do if "$@" >/dev/null 2>&1; then ok "$what"; return 0; fi; sleep 1; done
  fail "$what (not within ${secs}s)"
}

adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null || fail "install"
adb shell appops set $PKG GET_USAGE_STATS allow
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell appops set $PKG SYSTEM_ALERT_WINDOW deny

# An app to put a limit on: any launchable app that is not Focus, not Settings, not a launcher.
TARGET=""
for candidate in com.android.contacts com.google.android.contacts com.android.calculator2 com.google.android.calculator com.android.deskclock com.google.android.deskclock com.android.camera2 com.android.gallery3d com.google.android.apps.messaging com.android.messaging; do
  if adb shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$candidate" 2>/dev/null | grep -q "/"; then TARGET=$candidate; break; fi
done
[ -n "$TARGET" ] || fail "no launchable app found on this emulator image to put a limit on"
echo "limited app for this test: $TARGET (1 minute a day)"

# Focus's settings, before its first start: timers on, one minute for the target, no pause on the wall.
JSON="{&quot;v&quot;:2,&quot;timersEnabled&quot;:true,&quot;appLimits&quot;:{&quot;$TARGET&quot;:1},&quot;warnMinutes&quot;:0}"
adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && cat > shared_prefs/focus_settings.xml'" <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map><string name="settings_json">$JSON</string></map>
XML
adb shell cmd package set-home-activity "$PKG/.MainActivity" >/dev/null 2>&1 || true
adb shell am start -W -n $PKG/.MainActivity >/dev/null
sleep 6
front | grep -q "$PKG/.MainActivity" || fail "Focus's home screen is not in front: $(front)"
[ "$(watcher)" = "0" ] && ok "home in front: no watcher" || fail "the watcher runs while the home screen is in front"

# --- leave home into the limited app
adb shell monkey -p "$TARGET" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
wait_for 20 "left home: the watcher is running" sh -c "[ \"\$(adb shell dumpsys activity services $PKG | grep -c 'ServiceRecord.*TimerWatchService')\" -ge 1 ]"
wait_for 10 "the watcher is a foreground service" sh -c "adb shell dumpsys activity services $PKG | grep -q 'isForeground=true'"
[ "$(adb logcat -d -b crash | grep -c "$PKG")" = "0" ] && ok "no crash so far" || fail "Focus crashed"

# --- the minute runs out, overlay switch off: a notification, the app stays in front
echo "waiting for the one-minute allowance of $TARGET to run out..."
wait_for 120 "time is up: the \"Time's up\" notification is posted" sh -c "adb shell dumpsys notification --noredact | grep -q \"Time's up for\""
front | grep -q "$PKG/.BlockActivity" && fail "the wall came up although Focus may not display over other apps"
ok "without the overlay switch the app stays in front (notification only)"

# --- same visit, overlay switch on: leave and re-enter the app, the wall has to come up
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell input keyevent KEYCODE_HOME; sleep 4
[ "$(watcher)" = "0" ] && ok "back home: the watcher is gone" || fail "the watcher is still running on the home screen"
adb shell monkey -p "$TARGET" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
wait_for 40 "with the overlay switch on the wall comes up in front of the app" sh -c "adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | grep -q '$PKG/.BlockActivity'"

# --- home again, and nothing crashed on the way
adb shell input keyevent KEYCODE_HOME; sleep 5
[ "$(watcher)" = "0" ] && ok "back home: the watcher is gone" || fail "the watcher is still running on the home screen"
[ "$(adb logcat -d -b crash | grep -c "$PKG")" = "0" ] && ok "no crash in the whole run" || fail "Focus crashed"
echo "ALL CHECKS PASSED"
