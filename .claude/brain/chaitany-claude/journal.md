# Journal (append only, newest at the bottom, absolute dates)

One entry per finished piece of work: what was asked, what was done, what was verified and how,
what is still open. Facts that stay true belong in the tiers; this is the record of *when* and
*why*. This file is public: no server, device or phone specifics (see `.claude/CLAUDE.md`).

---

## 2026-09-19 · Session 1 (one long day)

**Asked:** build a focus launcher for Android from an Excalidraw sketch: no icons, black and
white, search + app list, per-app timers that lock social apps and games with "continue for N
minutes" or bypass, 5 important apps on the home screen, a calendar or a bar showing hours of the
24 spent on the phone, a weekly summary for reflection, settings. Phone attached.

**Built and installed** (Kotlin + Compose without Material; first build passed):
home / drawer / long-press menu / wall / review / settings / accessibility service / weekly alarm.
Verified on the phone by guarded screenshots. The owner granted usage access and made Focus his
default launcher while I was still testing, and the wall locked a real app for the first time that
afternoon.

**Fixes found by verifying, not by being told:** an under-count of about a third for an app that
stacks activity instances → new `ForegroundTracker`, validated against the system event log for 40
apps; app list disk cache for instant cold start; 6 Compose lint errors; home layout that always
fits; 10 then 13 tracker tests.

**Round 2 (owner feedback):** smooth animations + performance (release build, profileinstaller,
AOT compile, refresh rate, transitions); ring shows battery; tap action on the ring configurable;
"calendar not working" → permission now turns the section on; **classifier rewrite** after finding
it limited mail, browser and messenger apps. Then: no emoji in calendar, one calendar with a
picker, no week strip; work calendars inside a managed work profile turned out to be unreachable
by policy → API supported, limit explained, not worked around. Then: light-up touch feedback,
long-press the ring, searchable calendar picker.

**Round 3:** memory/CPU pass, measured first. Incremental usage accumulator, label reuse,
per-package refresh, cached agenda, ModulateAlpha, one thread fewer. Cold start 0.57 s CPU, idle
0 ms. Verification exposed the Settings/FallbackHome bug (fixed; the total then matched ground
truth to the minute) and that work-profile usage is invisible to apps.

**Round 4:** "launch from the ring is slow" → my clip-reveal animation; replaced with scale-up
from 94% after realising `makeCustomAnimation` is ignored for task opens. Launcher hand-over
measured from real taps: 29–65 ms. Consent before every open after "ignore for today"
(`askAfterBypass`, `sessionConsent`); the log showed the consent screen being used.

**Round 5:** landing page, black and white, no JS, at https://how2me.me/focusapp/ with APK
download. Created a release signing key + `dist` build (the phone keeps the debug-key build).
Deployed to the owner's server with one `include` line in the domain's existing nginx site
(backup, `nginx -t`, verified main site untouched). Headline changed to his wording mid-deploy.
Asked whether it was uploaded: showed the served files and checksums.

**Round 6:** SEO (title/h1 eyebrow/section/FAQ, JSON-LD, sitemap, domain robots.txt, IndexNow
accepted). **Round 7:** audited, made the repo public, pushed
(`patelchaitany/focus-launcher`), linked site ↔ repo. **Round 8:** this brain.

**Verified:** 19 unit tests, lint 0 errors, release + dist builds, live site (routes, headers, CSP,
served APK sha256 == built == printed), repo (sensitive paths 404 from outside), screen time vs
ground truth, idle/cold-start CPU.
**Not verified:** mid-session locking and service-side consent (accessibility service never
enabled on the phone); the enterprise-calendar path (blocked by policy); how the new launch
animation and light-up feedback *feel* to him; the searchable calendar picker on device.

**Open with the owner:** see `2-overview/user-and-decisions.md` → "Open decisions".

## 2026-09-19 · The brain goes public

**Asked:** push the brain to the project's GitHub repo; "no need to make it private".

**Done:** the brain had been written on the assumption that `.claude/` stays ignored, so it held
things that must not be published: where the server is and what else it hosts, device
identifiers, and what I had seen on the owner's phone while validating (apps, hours, calendars).
Before pushing:
- copied the brain unedited to git-ignored `private/originals-2026-09-19/`, and assembled
  `private/server.md`, `device.md`, `phone.md` from it by copying;
- rewrote twelve public files in general terms (mechanisms, decisions, error sizes; no names,
  addresses, inventories, app lists or usage figures);
- `.gitignore` now tracks only `.claude/CLAUDE.md` and `.claude/brain/`, and ignores the rest of
  `.claude/` and every `.claude/brain/*/private/`;
- `.claude/CLAUDE.md` gained "The brain is public. Write it that way.";
- the pre-push audit no longer contains owner-specific strings: they are generated into
  `private/audit-patterns.txt` by `private/make-audit-patterns.sh` and used with `grep -c`.
Commit `1ace437`, pushed to `main`.

**Verified:** audit before the push: no forbidden file names, 0 generic hits, 0 owner-specific
hits in the staged diff and in the whole tracked tree (the same patterns do find the originals,
so the patterns work). After the push, from outside: six brain files return 200 on
raw.githubusercontent.com; `launch.json`, seven paths under `private/`, `keystore.properties`,
`local.properties` and `site/deploy.env` return 404; the tarball GitHub serves has 0 files
matching the private patterns. The first audit run reported one generic hit: the audit command
matching its own text; the documented regex now uses bracket forms so it cannot match itself.

**Open:** unchanged (see `2-overview/user-and-decisions.md`). The app and the site were not
touched. Note for later: a pattern audit only finds what it knows to look for; the rule that
keeps this brain clean is the writing rule in `.claude/CLAUDE.md`, not the grep.

## 2026-09-19 · Gestures and drawer: five requests from a contributor

**Asked** (by a contributor working on a clone of the repo, not by the owner): double tap locks
the screen; "swiping left should open the Google search widget"; sort the drawer by usage; keep
work apps apart; a "work logo" on pinned work apps. Also: "do not push or commit anything", and
"update the brain too".

**Done** (left uncommitted in the working tree):
1. Double tap to lock existed (`Settings.doubleTapLock`, accessibility service,
   `GLOBAL_ACTION_LOCK_SCREEN`); its default is now `true`. An existing install keeps its stored
   value, so there it is switched on in Settings → Gestures.
2. Swipe right on the home page (the page *left* of home, as on a stock launcher) opens the
   phone's web search: `Settings.swipeRightSearch` (default on, toggle in Gestures), a
   non-consuming `PointerEventPass.Initial` watcher on the pager, `openWebSearch()` with three
   intent fallbacks. Finger-left stays the drawer. No embedded AppWidget: it would bring colour
   and icons onto the home screen.
3. Drawer sort A–Z / Most used / Recent (`DrawerSort`, `Settings.drawerSort`, "Sort: …" under the
   search bar), ordered by `UsageRepository.sortStats()` = the system's 7-day aggregates. The A–Z
   scrubber shows only for A–Z; work-profile entries count as zero.
4. Drawer Personal / Work tabs, only when a work profile has launchable apps; search covers both.
   `TabChip` moved from `ReviewScreen.kt` to `ui/components/Basics.kt` and is shared.
5. Pinned work apps on the home screen carry the small dim word "work", as drawer rows do. The
   request said "logo"; the settled no-icons rule was kept.
Brain and README updated; `3-details/toolchain-and-build.md` gained how to build on a machine
without the pinned JDK without editing tracked files.

**Verified:** on the finished tree: `:app:testDebugUnitTest` 19 tests, 0 failures; `:app:lintDebug` 0 errors (9 "newer version available" warnings, expected); `:app:assembleRelease` builds (1.37 MB). Built on a machine without the pinned JDK, using the command-line override in `3-details/toolchain-and-build.md`; no tracked build file was changed.
**Installed** on the contributor's own phone afterwards. It had the website (`dist`) build, so
`adb install -r` failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, as `signing-keys.md` predicts;
they were asked, agreed to lose Focus's data, and the `release` build went on after an uninstall.
Opened by intent (Setup page); the process started and logged no crash. No special access was
granted over adb.
The first install used plain `adb install`, which also put Focus into the phone's work profile;
reinstalled with `--user 0` and confirmed per user (lesson recorded).
**Not verified:** nothing was exercised on a device yet: the swipe-right gesture next to the pager's own
drag, which search app each intent reaches, the lock, the tabs with a real work profile, the two
usage orders.

**Open:** the owner has not seen any of this, including the two new defaults. "Swipe left" read
as finger-right is an interpretation to confirm. The contributor may still want a glyph rather
than the word "work". Pausing / resuming the work profile from the drawer is not implemented;
the tabs cannot be swiped between. See `2-overview/user-and-decisions.md` → "Open decisions"
10–13.

## 2026-09-19 · Work icon, keyboard in the drawer (same contributor, on their phone)

**Asked:** work apps should have a work icon, "not the words work"; a setting for the keyboard to
come up automatically in the app drawer, "not buggy and smooth". Also: install to the personal
profile only, and give the install command.

**Done:** `WorkBadge` (drawn briefcase outline, `Basics.kt`) replaces the word on drawer rows and
pinned apps. The keyboard toggle already existed (`autoKeyboard`, Settings → App drawer); the bug
was the signal behind it: `currentPage == 1` flips mid-drag. `MainActivity` now passes
`drawerActive` (dragged → `settledPage`, else `targetPage`), and dragging the list hides the
keyboard. Details in `3-details/ui-system.md`. Install commands now use `--user 0` everywhere.

**Verified:** 19 unit tests pass, lint 0 errors, release build; installed with
`adb install --user 0 -r` and confirmed present for user 0 only.
Afterwards the contributor could not find the keyboard toggle (it sat only under App drawer →
Search). The same toggle now also appears on the Gestures page ("Keyboard opens with the
drawer"), and the main settings page names the keyboard in both rows' subtitles. Same lesson as
before: a setting alone is not discoverable. Rebuilt (lint 0 errors), reinstalled for user 0.
**Not verified:** how the keyboard timing and the badge look and feel on the device; that is the
contributor's to judge. **Open:** unchanged, plus decision 10 (the icon exception).

