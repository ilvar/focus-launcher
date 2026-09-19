# Tier 3 · The weekly review (the reflection mechanism)

Code: `data/WeekSummary.kt`, `service/WeeklyReview.kt` (+ `WeeklyReviewReceiver`),
`ReviewActivity.kt`, `ui/review/ReviewScreen.kt`, `data/AppState.kt`, the weekly block in
`ui/settings/FocusPages.kt`.

## What the owner asked for
At the end of each week, a summary of the week: how many hours on which app, and during what part
of the day, as a way to reflect. So the screen ends with a question, not a score.

## When it appears
- Due at `reviewHour` (default 20:00) on the **last day of the week** (`weekStartsMonday`, default
  true → Sunday evening). Off when `weeklyEnabled` is false.
- `WeeklyReview.schedule()` sets one inexact `setAndAllowWhileIdle` alarm for the next due moment
  (no exact-alarm permission needed). It is re-armed at process start, after every alarm, and when
  the weekly settings change.
- A launcher is resumed dozens of times a day, so `checkDue()` also runs on every home resume
  (`MainActivity`): a missed or delayed alarm only delays the nudge.
- `checkDue()` marks a week pending **once** (`AppState.lastAnnouncedWeek`). First run ever: it
  starts the clock from the previous week instead of announcing a week that was never watched.
- Pending week → a line on the home screen ("Your weekly review is ready →") and, if
  notifications are allowed, one notification with the week's total. Both open `ReviewActivity`
  for that week; opening it clears the notification. That notification is the only one Focus posts.

## What it shows (`ReviewScreen`, tabs Today / Week, `Crossfade` between them)
Today: total, share of the day, unlocks, the 24-hour bar, "Apps today" (top 12, with the limit
next to the time for limited apps). Week (‹ › to move between weeks; "This week", "Last week",
then dates):
1. Total, daily average, change against last week, and the share of waking hours (average ÷ 16 h).
2. **Day by day**: seven bars, the busiest day named.
3. **When you were on your phone**: 24 hourly bars (busiest emphasized), the four periods with
   their share, the busiest hour. This is the "during what time period" the owner asked for.
4. **Where it went**: top 10 apps (≥ 1 min), each with total and share, "mostly <period>",
   "n of m days", and "past its limit n×" when that happened.
5. **Limits and pickups**: how often time ran out, how often it was continued (and for how many
   minutes) or ignored; unlocks per day.
6. **Reflect**: "Was this how you wanted to spend your week?", last week's written intention
   quoted back, and one line to write for next week (`AppState.intention(weekStart)`).
Without usage access the screen explains why it is needed and links to the system switch.

## How the numbers are built (`WeekSummary.build`, off the main thread)
- Input: `UsageRepository.loadDays(weekStart, weekStart+6)` (cached day files, today live) plus the
  seven days before for the comparison, and `LimitManager.stats(from, to)`.
- `DayPeriod`: Morning 5–12, Afternoon 12–17, Evening 17–21, Night 21–5. An app's `peakPeriod` is
  the period with most of its time.
- `daysElapsed` (1..7) is the divisor of the daily average, so a week in progress is not diluted
  by days that have not happened.
- The comparison with last week is shown only if **at least 4 days** of it were recorded, and it
  averages over recorded days only.
- `daysUsed` counts days with ≥ 1 minute in the app. `lightestDay` ignores future days.

## Limits of the data
Android keeps detailed usage events for about a week. Focus saves each finished day as its own
file, so weeks stay available from the install onwards; older weeks show "No screen time recorded
for this week" with that explanation. Days are never cached empty (see `usage-tracking.md`).

## Not done / ideas
Consent opens (after "ignore for today") are not tallied. No export of the week. The review time
is one fixed hour; there is no "remind me later".
