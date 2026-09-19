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
