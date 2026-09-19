# Tier 3 · The calendar section

Code: `data/CalendarRepository.kt`, `ui/home/HomeWidgets.kt` (`CalendarWidget`, `WeekStrip`),
`ui/settings/CalendarPickerDialog.kt`, the calendar block in `LauncherPages.kt`.

## What the owner asked for
One calendar only, with a way to pick it (including a work calendar); no emoji in titles; no
"which day it is" (the week strip, now an off-by-default toggle); a searchable picker.

## How it works
- `calendars()` lists every calendar in the personal profile (no visibility filter), primaries
  first, plus any work-profile calendars the platform lets through.
- Setting `calendarKey`: `"auto"` | `"all"` | `CalendarInfo.key` = `"p:<id>"` personal /
  `"w:<id>"` work. (`fromJson` migrates a short-lived earlier `calendarId` long.)
- `choose()`: explicit key → that calendar; `"auto"` → `busiest()`: among **primary** personal
  calendars, the one with the most instances in the next 14 days; else any personal calendar with
  events; else the first primary. Reason: on a phone with several accounts the first primary is
  often an empty "local account" or an address that is no longer used, and the section then shows
  "Nothing in the next 7 days" although the calendar app is full. That is exactly what happened
  in the first version.
- `upcoming()`: `CalendarContract.Instances`, next 7 days, max 3; all-day events read in UTC;
  `visible = 1` is applied only for "all calendars" (a calendar the user picked by name is shown
  even if hidden in the calendar app).
- `stripEmoji()` removes pictographs, dingbats, symbols, variation selectors, ZWJ, keycap marks
  and tag characters, then collapses spaces (unit tested, including Devanagari and accents).
- `agenda(context, key)` caches the chosen calendar + events: invalid when a `ContentObserver` on
  `CalendarContract.CONTENT_URI` fires (it only flips a flag, so background syncs cost nothing),
  when an event in it has ended, or after 10 minutes. Before this, every return home ran three
  provider queries.
- The picker shows each calendar's name, account, and "N upcoming" (`upcomingCounts`), which is the
  quickest way to recognise the right one; "All calendars together" is last. It has a search
  field because a phone with several accounts lists dozens of calendars.
- Permission UX: granting calendar access in Setup also turns the section on; turning the section
  on in Home settings requests the permission. (Originally granting did nothing visible, which
  was reported as "calendar not working".)

## A work calendar inside a managed work profile
When a work account lives in an Android **work profile** managed by an organisation, its calendars
are not in the personal profile's provider at all. The only sanctioned door is the enterprise API
(`Calendars.ENTERPRISE_CONTENT_URI`, `Instances.ENTERPRISE_CONTENT_URI`, a restricted column set;
calendar names are never exposed, so they appear as "Work calendar"), and it returns rows **only
if the organisation's device policy allows cross-profile calendar access** for the calling
package. `adb shell dumpsys device_policy` shows the allow-list (`mCrossProfileCalendarPackages`).
- Focus implements that API. Where the allow-list is empty the list comes back empty, the picker
  says so in place, and nothing else can be done from the app's side.
- **That path has never returned data on a test device, so it is untested.**
- The sanctioned route for a user: share the work calendar with a personal account from the
  calendar's own sharing settings, if the organisation permits it.
- Never try to get around the policy (no adb tricks, no accessibility scraping, no second app
  inside the profile). Explain the limit instead.
