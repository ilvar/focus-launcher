# Tier 3 · Home, drawer, the long-press menu, and Setup

Code: `ui/home/HomeScreen.kt`, `ui/home/HomeWidgets.kt`, `ui/drawer/DrawerScreen.kt`,
`ui/drawer/AppMenu.kt`, `ui/settings/SetupPage.kt`, `ui/settings/LauncherPages.kt`,
`data/Settings.kt`. Layout fitting, touch feedback and motion are in `ui-system.md`.

## Home (page 0), top to bottom, after the owner's sketch
1. **Clock**: ring (`ClockStyle.RING`, arc = battery % or day passed) or plain. Tap = `clockTap`
   (alarms by default, or calendar / screen time / battery / nothing / any app); long-press opens
   the chooser (`ClockTapDialog`).
2. **Screen time** (`ScreenTimeLine`, always there, no setting): "Screen Time", today's total at
   24sp, "N% of today" (of 24 h). Tapping it opens the review, or the usage-access switch if that
   is still missing. The 24-hour bar that used to be a home section now lives only in the review
   (a contributor's change; open decision 14 in `2-overview/user-and-decisions.md`).
   **Section**, a toggle: the calendar agenda (`calendar.md`; off until calendar access is given).
3. **Notices**, only when needed: "Finish setting up Focus →" and "Your weekly review is ready →".
4. **Fast apps**: at most `MAX_FAVORITES = 5`, text only, aligned by `homeAlign`
   (left / center / right). Long-press = the app menu.
5. **Corner shortcuts**: `leftShortcut` / `rightShortcut`. Defaults `auto:phone` → `ACTION_DIAL`
   and `auto:camera` → `INTENT_ACTION_STILL_IMAGE_CAMERA`, so they work whatever dialer or camera
   is installed; either can be any app. Long-press a corner to change it in place;
   `showShortcuts` hides both.
Background gestures (each a setting): swipe down = notifications, swipe up = drawer with search
focused, double-tap = lock (on by default, needs the service), long-press = settings, swipe right
(finger moves right; nothing is to the left of home) = the phone's web search (`ui-system.md`).

## Drawer (page 1, swipe left)
- Search field on top. `autoKeyboard` opens the keyboard on arrival (off by default; when the
  drawer counts as "arrived at" is in `ui-system.md`; dragging the list hides it); `autoLaunch`
  opens the app when ≥ 2 letters leave exactly one result.
- **"Installed in the last 24 hours"**: `firstInstallTime` within 24 h, newest first, Focus itself
  excluded; hidden while searching; `showRecentInstalls` turns it off.
- Under the search field, hidden while searching: Personal / Work tabs (only when a work profile
  has launchable apps; the list and "installed in the last 24 hours" follow the tab, search
  covers both) and "Sort: A–Z / Most used / Recent" (`Settings.drawerSort`; the two usage orders
  come from `UsageRepository.sortStats()`, 7 days).
- Then every visible app in that order; the scrubber only for A–Z. `showUsageInDrawer` adds
  today's time next to apps that have a limit.
- Hidden apps are filtered out of the list and the search; they are managed in
  Settings → Drawer → Hidden apps. Search matches the name shown in Focus (so a renamed app is
  found by its new name, not its system name).
- Work-profile apps sit under the Work tab, carry the drawn briefcase (`WorkBadge`, also on pinned
  apps on the home screen) and are launched through `LauncherApps.startMainActivity` with their user.

## The long-press menu (`AppMenu`): the order is the owner's, do not reorder
Title = the app's name; the subtitle shows the system name if renamed, today's time and the limit.
1. **Uninstall**: `ACTION_DELETE package:…` (+ `EXTRA_USER` for work-profile apps); not offered
   for system apps. The system asks for confirmation; Focus never removes anything itself.
2. **App info**: `LauncherApps.startAppDetailsActivity`.
3. **Move to fast apps** / Remove from fast apps, with "n / 5". When full: "Fast apps are full",
   choose the one to replace.
4. **App timer**: only if `canLimit`. Opens `TimerDialog`: the category default, No limit,
   presets, Custom…; the subtitle says how Focus classified the app (`describeCategory`).
5. **Rename**: inside Focus only; empty restores the system label.
6. **Hide app** / Unhide app.

## Setup page (Settings → Setup; also the "finish setup" notice)
"Three switches make Focus work", each row opens the right system screen and shows its state on
return:
1. **Default home**: `RoleManager.ROLE_HOME` request on Android 10+, else the home settings.
2. **Usage access**: screen time, timers and the review depend on it.
3. **App locking**: the accessibility service. Sideloaded apps hit Android's "restricted setting"
   block, so the row explains the way through (App info → ⋮ → Allow restricted settings).
Optional: **Notifications** (used only for the weekly review) and **Calendar section** (requests
`READ_CALENDAR` and turns the section on in one step).
Focus never flips these switches itself, and neither does an agent over adb
(`2-overview/device-testing.md`).

## Settings, in short
One immutable `Settings` data class stored as JSON; `fromJson` tolerates missing keys. Pages:
main · setup · home · fastapps · drawer · hidden · timers · timerapps · weekly · appearance ·
gestures · about. Open one directly: `am start -n com.focus.launcher/.SettingsActivity --es route
<name>`. Appearance: dark or light, font (sans / serif / mono), text scale, hidden status bar,
launch animation (fast / system).
