# Tier 3 · How screen time is computed (and how to prove it is right)

Code: `data/ForegroundTracker.kt` (pure, unit tested), `data/UsageRepository.kt`.
These numbers drive the day bar, the review **and when apps get locked**, and users compare them
with Digital Wellbeing. Treat accuracy as a feature.

## The trap that shaped the design
`UsageStatsManager.queryEvents` yields ACTIVITY_RESUMED / PAUSED / STOPPED per activity. Android
pairs them by a hidden **instanceId**. Some apps (big social apps in particular) stack several
instances of one activity class, so pairing by *class name* breaks: the late STOPPED of an old
instance closes the newer instance of the same class. Measured on a real day, one such app was
under-counted by about a third.

## The algorithm (`ForegroundTracker`)
- Per package: a counter of resumed-but-not-yet-paused activities; the package is "open" while > 0.
- **STOPPED is never fed in.**
- Safety net for a PAUSED that never comes (crash, split-screen): when another package resumes,
  open packages are marked *covered at t*. If their own PAUSED does not arrive within
  `COVER_GRACE_MS = 3000`, they are closed at the cover time. A covered package that resumes again
  loses the mark.
- SCREEN_NON_INTERACTIVE / DEVICE_SHUTDOWN close everything. KEYGUARD_HIDDEN counts an unlock.
- `openIntervals(now)` reports what `finish()` would emit without ending anything.
Validated against instance-aware ground truth for 40 apps on a real day: every app within 10 s,
the day's total within 30 s.

## Today is a running total (`UsageRepository.DayAccumulator`)
A launcher refreshes on every return home (~100×/day). Re-reading the whole day each time meant
tens of thousands of events per refresh. Now each event is consumed **exactly once**:
- `consumedUntil` starts at `dayStart − 3h` (so a session across midnight is seen whole) and each
  refresh queries `[consumedUntil, now − SETTLE_MS)`, `SETTLE_MS = 1500`. The newest moment is
  left for next time because the OS writes its log from another thread.
- `snapshot(now)` = closed totals (deep-copied) + open intervals up to now. A snapshot taken right
  after coming home may over-count the last app by ≤1.5 s; the next refresh corrects it.
- If nothing changed and no *counted* app is in front, the **same `DayUsage` object** is returned,
  so Compose does not recompose. `version` bumps on any change to totals or unlocks.
- Rebuilt from scratch on a new day, a time-zone change, a backwards clock jump, or process start.
- Hour buckets by division on normal days; calendar math only on DST days (`plainDay`).
History (`compute(from,to)`) still does one full pass; finished days are cached as
`files/usage/YYYY-MM-DD.json`. **Bump `DayUsage.CACHE_VERSION` (now 3) when the math changes.**
Empty days are never cached (the OS may simply have dropped the events: retention ≈ a week).

## What is excluded, and two traps
- Real launchers and `com.android.systemui` are left out of totals but still flow through the
  tracker (coming home "covers" the app that was open).
- **Trap:** `com.android.settings` declares a HOME activity (`FallbackHome`, priority −1000).
  Filtering home apps by category alone erased all time in Settings. `homePackages()` keeps only
  `ResolveInfo.priority >= 0`.
- **Platform limit:** usage inside a managed work profile is invisible to a normal app, so Focus
  can read a little lower than Digital Wellbeing on a phone that has one. Do not work around it.
- "Today" = since local midnight. Android's own "daily" bucket does not start at local midnight,
  so its `totalTimeUsed` legitimately differs.

## Proving it
`adb shell dumpsys usagestats > dump.txt` prints events as
`time="…" type=ACTIVITY_RESUMED package=… class=… instanceId=…`. Write a short script that:
splits the dump on `user=N` lines and keeps **user 0**; de-duplicates events (the dump repeats
them across sections); pairs by `instanceId`; clips to local midnight; ignores the launchers and
systemui; sums. Compare with the home screen at the same moment.
Last check (2026-09-19, evening): the app's total matched ground truth **to the minute**, and the
two most-used apps matched to the minute as well. Timestamps in the dump have 1 s resolution:
expect ±seconds.
Keep the dump and the script output in the scratchpad. They describe a person's day: record the
size of the error here, never the apps or the hours.
