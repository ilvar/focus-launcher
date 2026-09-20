# Tier 3 · Memory and CPU: what was measured, what was changed

The owner asked for a small footprint and low CPU. Everything here was measured on his phone
with passive probes. A launcher is always resident, so **per-resume work is multiplied ~100×/day**
and idle cost matters most.

## Probes (none of them disturbs the user)
```bash
PID=$(adb shell pidof com.focus.launcher)
adb shell cat /proc/$PID/stat | awk '{print ($14+$15)/100 " cpu-seconds"}'      # utime+stime
adb shell "for t in /proc/$PID/task/*; do echo \"\$(cat \$t/comm)|\$(cat \$t/stat)\"; done"   # per thread
adb shell dumpsys gfxinfo com.focus.launcher | grep "Total frames rendered"   # sample twice, 5-10 s apart
adb shell dumpsys meminfo com.focus.launcher | grep -E "Java Heap:|Native Heap:|Code:|Graphics:|TOTAL PSS:"
adb logcat -s FocusLaunch                                                       # launcher's own hand-over time
```

## Findings
- **Idle was already free:** home screen visible → 0 frames, CPU 80→20→0 ms over three 5 s
  windows (the first includes resume work). Screen off → 0. No looping animations exist.
- The real costs: (1) today's usage recomputed from the **whole day's event log** on every resume
  and every minute; (2) each process start re-loaded ~200 app labels (opening each app's
  resources: seconds) although the list was cached, and every package change rescanned everything;
  (3) three calendar provider queries per resume.
- First process observed: 32.9 s CPU in 7m39s (main 10.3, RenderThread 8.6, workers 5.9). The UI
  part was active swiping on a *debuggable, un-compiled* build, so it says little about the app.

## Changes
`DayAccumulator` (events consumed once; same object returned when unchanged) · label reuse keyed
by package `lastUpdateTime` + locale, per-package refresh instead of full rescans, apps cache v2
(`files/apps.json`) · cached `Agenda` + ContentObserver · `CompositingStrategy.ModulateAlpha` on
the pager · no dedicated thread in the accessibility service · search labels normalized once ·
instant launch path for limited apps far from their limit · `profileinstaller` so Compose's
baseline profiles apply to sideloaded builds · trim caches on `TRIM_MEMORY_BACKGROUND` only.

## Results (release build, `speed-profile`, screen off, nobody touching the phone)
| | Before | After |
| --- | --- | --- |
| Cold start, total CPU | not isolated | **0.57 s** (main 0.22, workers 0.13, render 0.08); launch ≈ 535–660 ms |
| Background-thread CPU | 5.9 s over ~7 min | 0.13 s at start |
| Idle, 10 s | 10 ms | 0 ms |
| Threads | 45 | 38 |
| Launcher hand-over on app open | — | 29–65 ms (so slow launches are the target app + animation) |

## Memory, honestly
App-owned data is small (Java heap 6–10 MB): no icons, images, Material or database. Total PSS
swings with process age: **94.8 MB** seconds after a cold start (every code page just touched),
**44.5 MB** settled after ~4.5 min (of which 30.9 MB swapped to zRAM, ≈14 MB resident). Most of it
is shared runtime, framework and GPU-driver code. **Never claim a memory win from two PSS
readings.** `speed-profile` was chosen over `speed`: nearly the same speed, less mapped code
(with `speed`, "Code" read 17.8 MB), and it is what Android converges to for store installs.

## Battery, measured over a real day (2026-09-20, release build, about 14.5 h on battery)
Asked by the owner after half a day of use: which apps use the battery, and what does Focus use.
```bash
adb shell dumpsys batterystats --charged > stats.txt        # since the last full charge; read-only
adb shell cmd package list packages -U com.focus.launcher   # uid 10NNN  ->  "u0aNNN" in the dump
grep -n "Estimated power use" stats.txt                     # global numbers, then one "UID …: mAh" line per app
awk '/^  u0aNNN:$/{on=1} on' stats.txt | head -20           # the app's block: Top for, Total cpu time, wake locks, alarms, jobs
```
- Focus: about **0.6% of the battery's capacity** in 14.5 h, around one percent of what was
  discharged, and a small fraction of what the biggest app took. **89 s of CPU in total** (half of
  it kernel time: binder calls for usage events, packages and the calendar), of which 22 s with
  the screen off; **no alarms, no jobs, no wake locks of its own**; 2 process starts.
- Most of a launcher's "mAh" is not its work: Android's model books the *screen's* power to
  whichever app is on top, and the home screen was on top for about ten minutes in total.
- **Why the stock launcher keeps running although Focus is the home app** (the owner asked):
  Android keeps gesture navigation and the recent-apps screen *inside the stock launcher*. System
  UI binds its `QUICKSTEP_SERVICE` (`…quickstep.TouchInteractionService`), and
  `dumpsys activity recents` names its `RecentsActivity` as `mRecentsComponent`. It therefore runs
  as a foreground service the whole time, comes to the front every time recents opens, used more
  CPU than Focus over the same day, and is booked a similar amount of battery without ever being
  the home screen. No launcher can take that role over, and switching the package off breaks
  gestures and recents: never suggest it. Check: `dumpsys activity services <stock launcher>`.
- **Why Focus has a battery figure at all:** it is in front for a few seconds at every unlock,
  every return home and every app switch (dozens of visits a day, about ten minutes in total),
  and the display's power for those seconds is booked to it.
- The per-app numbers are estimates from a power model (their sum exceeded the measured
  discharge): good for ranking, not for absolute claims. Which apps ranked where is the owner's
  business and is not recorded here; he got the list in the session.

## Traps
`TRIM_MEMORY_UI_HIDDEN` fires on every app launch from a launcher: clearing caches there defeats
them. After `adb install`, run `compile -m speed-profile -f` only once the app has been running
~10 s (profileinstaller writes the profile after first launch), then restart the launcher.
