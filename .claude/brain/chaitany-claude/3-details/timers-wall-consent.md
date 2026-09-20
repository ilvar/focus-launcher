# Tier 3 · Timers, the wall, and consent

Code: `ui/Launching.kt`, `BlockActivity.kt`, `ui/block/BlockScreen.kt`,
`service/TimerWatchService.kt`, `data/LimitManager.kt`.

## Two layers, independent on purpose
1. **Launch gate** (`launchApp`), needs only usage access. Runs when an app is opened *from Focus*.
2. **Timer watcher** (`service/TimerWatchService.kt`), for what happens while the user is *inside*
   an app, or opens one from a notification or recents. It also needs only usage access. Since
   2026-09-20 it replaces the accessibility service the app had from 1.0 (why: "No accessibility
   service, no notification listener" below).

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

## The timer watcher (`TimerWatchService`, a foreground service of type `specialUse`)
- **Lifetime:** Focus knows when its home screen is left (`MainActivity` pause → `start()`) and when
  it is back (resume → `stop()`, which also ends the visit's consent). It runs only in between, and
  only if timers are on, usage can be read and something has a limit. It is started with a plain
  `startService` while the home screen is still in front (allowed), and `startForeground` sits in
  a try/catch: if Android says no, there is no watcher this time and the gate still works. A
  launcher must never crash because a service was refused.
- **Which app is in front:** `UsageRepository.lastResumedPackage(since)` every 4 s while the
  screen is on (a query over the last few seconds of the usage log: cheap), nothing with the
  screen off; `SCREEN_ON` / `USER_PRESENT` look again at once. The phone's own launcher comes to
  the front all day as the recent-apps screen: that ends the consent of a visit but not the
  watcher, unless Focus is not the default home app (then nothing else would ever stop it).
- **What to do** is `TimerWatch.decide(...)`, a pure function with ten unit tests: no limit or Focus
  itself → idle · ignored for today → ask consent unless this visit was agreed to · inside a
  continue window → look again when it closes · time left → a timer for exactly that long (+ the
  optional `warnMinutes` toast) · used up → lock. Before locking or asking, the usage log is read
  once more to confirm the app is still in front; if another one is, follow the log instead.
- **Bringing the wall up** is an activity start from the background. Android allows it to an app
  the user let **display over other apps** (`SYSTEM_ALERT_WINDOW`, `Perms.canDrawOverlays`);
  Focus draws nothing else there. Without the switch: one "Time's up for <app>" notification per
  visit (channel `time_up`, high importance; a tap opens the wall), and the gate locks the app the
  next time it is opened. The consent question is only asked in front of the app, never from the
  shade. Without both switches there is no mid-session enforcement, only the gate.
- Its own notification (channel `timer_watch`, low): "<app> locks at 14:32" or "App timers are
  running"; static text, so nothing updates every minute. The user can hide the channel.
- `generation` drops stale async results; `recheck()` after a limit or a pass changed.

## No accessibility service, no notification listener (rule since 2026-09-20)
Google Play Protect **blocks the installation** of an APK that comes from a download (browser,
messenger, file manager) if its manifest declares an accessibility service, a notification
listener, `READ_SMS` or `RECEIVE_SMS` ("This app can request access to sensitive data", no
"install anyway"; active in select markets; source: developers.google.com/android/play-protect/
warning-dev-guidance). Focus had the first since 1.0, and 1.1.34 added the second for the song's
name in the music section. The owner reported the block; both are gone:
- mid-session locking → the timer watcher above; swipe-down notifications → the status-bar service
  (`EXPAND_STATUS_BAR`), which was already the fallback;
- **double tap to lock is gone**: only an accessibility service (or device admin, which is worse)
  can turn the screen off;
- **the music section shows no song name any more**: buttons are media keys
  (`AudioManager.dispatchMediaKeyEvent`), play/pause state comes from `isMusicActive` and an
  `AudioPlaybackCallback`; neither needs a permission. The name needs notification access, full stop.
CI, the Publish workflow and `site/clean-build.sh` refuse an APK whose manifest contains any of the
four. `QUERY_ALL_PACKAGES` stayed: a launcher lists every app and screen time names every app; the
narrower `<queries>` alternative risks hiding usage events of apps without a launcher entry, which
was not worth a permission that blocks nothing.

## Bookkeeping
`focus_limit_state` prefs: `ext_<pkg>` (continue until), `bypass_<pkg>` (date).
`focus_limit_log` prefs: per day, per package `{b,c,m,x}` = blocked, continued, continued minutes,
bypassed → weekly review. Editing a limit calls `clearPasses` so the change bites at once.

## Not done / ideas
Consent opens are not tallied for the weekly review. No friction pause on the consent screen.
An always-on variant of the watcher (apps opened while no watcher runs cannot happen today: every
way out of the home screen starts it).
