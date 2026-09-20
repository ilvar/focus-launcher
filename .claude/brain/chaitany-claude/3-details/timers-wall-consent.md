# Tier 3 · Timers, the wall, and consent

Code: `ui/Launching.kt`, `BlockActivity.kt`, `ui/block/BlockScreen.kt`,
`service/FocusAccessibilityService.kt`, `data/LimitManager.kt`.

## Two layers, independent on purpose
1. **Launch gate** (`launchApp`), needs only usage access. Runs when an app is opened *from Focus*.
2. **Accessibility service**, needed to act while the user is *inside* an app, or when an app is
   opened from a notification or recents. Not enabled on the owner's phone yet → **untested on
   device**.

## The gate, in order
no limit or no usage access → open · **bypassed today** → consent screen if `askAfterBypass`,
else open · inside a "continue" window → open · fast path: cached usage + time since it was
computed + 5 s < limit → open without touching the usage log · else ask the accumulator; if
used ≥ limit → record `blocked`, show the wall; else open.
`start()` launches with `launchOptions()` and logs `FocusLaunch: <pkg> requested in N ms`
(measured 29–65 ms on the phone).

## The wall (`BlockScreen`, request.consent = false)
"Close <app>" is the primary button. "Continue for" 1/5/15 min (configurable, ≤4 shown) unlock
after `frictionSeconds` (5). "Ignore the limit for today" if `allowBypass`. Both off = strict mode.
Shows how often the limit was passed this week. Back = close (go home). `onStop` → `finish()`: the
wall never lingers; if the app is still over its limit it comes back.
- `midSession = true` (raised by the service): leaving by "continue" just `finish()`es and reveals
  the app underneath. `false` (raised by the gate): "continue" must start the app.
- `preview = true` (Settings → App timers → Preview): buttons change nothing.

## Consent after "ignore for today" (`request.consent = true`)
Owner's requirement: ignoring a limit must not make the app free for the day. Every open asks
"Over today's limit … open anyway?". **"Not now" is the big button**; "Open <app> anyway";
"Bring the limit back for today" (`clearPasses`). No countdown.
- One yes covers **one visit**: `LimitManager.sessionConsent = pkg` (in memory, never persisted).
- The service clears it when a launcher comes to the front (`isHomeApp`, but not for
  `BlockActivity`, which shares the launcher's package) or the screen turns off. While set, the
  service does not ask again for that package. Choosing "ignore for today" on the wall sets it too.
- Without the service, only the gate asks, which is every open from Focus: correct.

## The service
Listens to `TYPE_WINDOW_STATE_CHANGED` only; `canRetrieveWindowContent=false` (it learns the
package and class name, nothing on screen). `isActivity(pkg, cls)` (LRU-cached
`getActivityInfo`) filters out keyboards, dialogs, the shade. On a real switch:
`evaluate()` → limit? → bypassed → maybe consent · in a continue window → re-check when it closes ·
else compute remaining on `Dispatchers.IO` → schedule a check for exactly that long (+ optional
`warnMinutes` toast) → when it fires, **re-read usage** and confirm with `lastResumedPackage` that
the app is really still in front (if the log says another app is, follow the log instead of
blocking the wrong one) → `startActivity(BlockActivity)` with NEW_TASK | CLEAR_TOP.
Being a system-bound accessibility service is what permits that background activity start and
keeps the process from being frozen. `generation` drops stale async results. SCREEN_OFF cancels
timers; USER_PRESENT re-evaluates (no window event fires after unlock).
Also provides `openNotifications()` and `lockScreen()` for home gestures.

## Play Protect, and the variant without the service (built, published, reverted 2026-09-20)
Google Play Protect **blocks the installation** of an APK that comes from a download (browser,
messenger, file manager) if its manifest declares an accessibility service, a notification
listener, `READ_SMS` or `RECEIVE_SMS` ("This app can request access to sensitive data", no
"install anyway"; select markets; installs over adb are exempt; source:
developers.google.com/android/play-protect/warning-dev-guidance). Focus has the first since 1.0
and the second since 1.1.34 (the song's name). The owner met the block and asked to get past it.
- **PR #11 (1.1.37)** removed both: a foreground service of type `specialUse`
  (`TimerWatchService`) ran only between `MainActivity` pause and resume, read
  `UsageRepository.lastResumedPackage` every 4 s while the screen was on, decided with a pure
  function (`TimerWatch.decide`, 10 unit tests) and brought the wall up through the
  "display over other apps" exemption for background activity starts, else a "Time's up"
  notification. It passed an emulator test on Android 14 and 15. Its price: **no double tap to
  lock** (only an accessibility service or device admin can turn the screen off), **no song name**
  (needs notification access), one more switch to explain.
- **The owner had it reverted the same day: the options matter more to him.** The services are
  back, the CI guard against them is gone, and Play Protect blocks the download again where it
  enforces this. Do not remove them again without his yes.
- The variant is whole in git: `git show 5ebb0fd`; reverting the revert commit brings it back.
  If it ever returns, it should come as a second build variant or behind his explicit choice,
  not as a replacement.

## Bookkeeping
`focus_limit_state` prefs: `ext_<pkg>` (continue until), `bypass_<pkg>` (date).
`focus_limit_log` prefs: per day, per package `{b,c,m,x}` = blocked, continued, continued minutes,
bypassed → weekly review. Editing a limit calls `clearPasses` so the change bites at once.

## Not done / ideas
Consent opens are not tallied for the weekly review. No friction pause on the consent screen.
