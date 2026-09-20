# Tier 2 · The owner, what he asked for, what he decided

## Working agreements
- Requests often arrive several at a time, and follow-ups arrive mid-task. Read for intent, state
  the reading in one line, then do all of it and fold the follow-ups in.
- Lead with the answer. An explanation that was asked for goes first, not after the change log.
- Finish and verify rather than propose. Report honestly what was *not* verified.
- Publishing is a separate step. Every commit and push so far was asked for in so many words
  ("push this"). Work, including brain updates, changes files on disk; it is committed and
  pushed when that is asked, after the audit.
- The phone is in use while you work; surprising state is usually a setting that was changed, not
  a bug. Check before "fixing" (see `device-testing.md`).
- Public identities: the domain `how2me.me` and GitHub `patelchaitany`. Everything else about his
  infrastructure is in `private/`.

## Product decisions (settled; do not re-litigate)
| Decision | Note |
| --- | --- |
| No icons anywhere; black and white only | Includes emoji: stripped from calendar titles, greyscaled in app names |
| Home layout follows his sketch | clock, section(s), ≤5 fast apps, two corner shortcuts; swipe left = drawer |
| Long-press menu order | App name · Uninstall · App info · Move to fast apps · App timer · Rename · Hide |
| **The clock is a split rectangle** (2026-09-20, second sketch): "from the circular to the rectangle with no outside boundary, keep only the center vertical boundary"; left = clock, right = "calendar or mail or any widget" | `ClockStyle.SPLIT`, the default; existing installs that still had the old default ring move over once. Right half = the calendar's next events or today's screen time, chosen by long-press. Ring and plain stay as options. **Mail and "any widget" are not there**: a launcher cannot read mail without notification access, and hosted Android widgets would bring icons and colour (open decision 18) |
| Ring shows **battery %** | Applies to the ring style. "Day passed" kept as an option, not the default. In the split and plain styles the same setting only decides whether the battery is written out |
| Tap on the ring opens a **specific app** | Configurable; long-press the ring to choose (Settings alone was not discoverable) |
| Touch feedback **lights up** | The first version dimmed the pressed item and was rejected |
| Calendar: **one** calendar, **no emoji**, no "which day it is" | Week strip kept as an off-by-default toggle; "Today/Tomorrow" labels kept. Unsure whether those were meant too: ask if it comes up |
| Calendar picker must be **searchable** | Long calendar lists are unusable otherwise. Calendars inside a managed work profile cannot be listed at all unless the organisation allows it |
| After "ignore limit for today", **ask consent on every open** | `askAfterBypass`, default on; one yes covers one visit |
| App launch must feel instant | Ring-tap launches were reported slow; the cause was my clip-reveal animation |
| Small memory, low CPU | Measured and optimized; see `3-details/performance.md` |
| Site headline is his wording | "Reclaim your time. Spend it touching some grass." Never change it |
| Site: black and white, hosted at how2me.me/focusapp, APK download | Done |
| Wants to be found for "minimalist launcher" searches | On-page + technical SEO done; never promise rankings |
| Repo public on GitHub | Done: `patelchaitany/focus-launcher` |
| Three-tier brain, mandatory, updated after every task | This directory |
| **The brain is public** (2026-09-19: "no need to make it private") | Committed with the project. Consequence: server, device and phone specifics live only in git-ignored `private/` |
| **The brain holds everything done so far** (2026-09-19: "update the brain [with] what you have done up till this point") | Not only the last task: every feature and area of work has a home in some tier, checked against the code. How: `3-details/brain-upkeep.md` |
| **Ship the collaborator's work** (2026-09-19: "there are new commit check them and push the new app in my phone as well as on the server download") | PR #1 was reviewed, built, released as **1.1**, installed on his phone and published on the site. "Check them" = a review first (`3-details/ci-and-releases.md`). The points in it that touch his earlier decisions stay open below (10–14): shipping was asked for, those were not answered |
| **A release page with the APK** | GitHub releases, tag per version, the same APK as the website with its checksum; 1.0 and 1.1 are there |
| **Submit to F-Droid, with a manually run CI for it** (2026-09-20) | Repo made F-Droid-ready (GPL-3.0, Fastlane listing, reproducible build so F-Droid ships the APK signed with his own key, recipe). `fdroid.yml` runs only by hand: it checks the recipe with F-Droid's tools and can open the merge request. The GitLab account, fork and token are his to create (open decision 20) |
| **Get past Google Play Protect: only necessary permissions, and the less critical one where there is a choice** (2026-09-20), then the same day: **"revert the merge the changes 11 as it removes the option"** | Play Protect blocks a downloaded APK that declares an accessibility service or a notification listener; Focus has both. PR #11 removed both (published as 1.1.37): locking moved to a usage-log watcher, and double tap to lock and the song's name went away. He had it reverted: **the options matter more to him than the clean download.** The services are back; do not remove an option again without asking him first. What was learned is kept in `3-details/timers-wall-consent.md` |
| **"When nothing is playing the music control should hide automatically"** (2026-09-20) | The music section shows only while something plays, plus one minute after a stop he could see (so play is one tap away and songs do not flicker). On by default for everyone; a switch in Settings → Home screen keeps the contributor's always-there behaviour available |
| **"Push this changes, no need to create new PR"** (2026-09-21) | A change he asked for and has on his phone may go straight to `main` when he says so: fast-forward, audit, push. Tests, lint and a clean build run locally first, because no CI runs before such a merge. A push to `main` is still a release once he approves Publish. Contributors' work keeps its review |
| **Website: "phone model not an iPhone", and show the different screens** (2026-09-21, with four screenshots of his own phone as reference) | The mockups are drawn as an Android phone and rebuilt after his screens: home, app list, wall, plus Today and Week of the review. His screenshots themselves were **not** published (they show his apps, usage and calendar); the content stays invented. If he wants the real ones online he has to say so knowing that |
| **CI that builds an APK on GitHub** | Tests, lint and an APK on every push and pull request; that APK is signed with a throwaway key |
| **The site gets the latest APK when CI has built it** (2026-09-20), **automatically, with his approval** (chosen from three options) | `publish.yml`: signs with the real key, uploads, creates the release, after he approves the run. Keys live in a protected GitHub environment; the server upload key can only deliver site files. He switches it on himself with `site/setup-ci-publishing.sh` |

## Asked for by a contributor (2026-09-19, on a clone); shipped in 1.1 at the owner's request
| Request | What was done |
| --- | --- |
| Media controls and a note on the home screen, "like [the owner's first sketch]: no icons rendering, no fancy UI, separation is by lines not boxes" (2026-09-20) | `MusicSection` and `NoteSection`, plain text, a thin line between sections. He had first asked for cards, hosted widgets and drag-to-arrange (PR #5), then had all of it dropped in favour of `main`; do not bring any of that back unasked. Not yet seen by the owner |
| Double tap locks the screen | Existed already; the default is now on. Needs the accessibility service; without it a toast, and Settings → Gestures points to Setup |
| "Swiping left should open the Google search widget" | Read as the page *left* of home on a stock launcher: the finger moves right on the home page. Opens the phone's search app by intent; toggle in Settings → Gestures. Finger-left stays the drawer. No embedded widget: it would break "no icons, black and white" |
| Sort the drawer | A–Z (default) / Most used / Recent (7 days), chosen from "Sort: …" under the search bar |
| Separate work apps | Personal / Work tabs in the drawer, shown only when a work profile has launchable apps; search covers both |
| Work apps carry a work **icon**, not the word | Asked twice ("not the words work"). `WorkBadge`: a drawn, monochrome briefcase outline, drawer rows and pinned apps. The one exception to "no icons"; still no colour, no assets |
| Does not like the screen time bar; wants only "2h 41m" under the battery line, "the default, no setting for it" | The home bar, its `showScreenTime` setting and `ScreenTimeWidget` are gone. A line inside the ring was tried and rejected the same hour ("keep it outside the clock, make it more explicit"): now `ScreenTimeLine` sits below the clock: the title "Screen Time" at 15sp (his wording and size; not the small-caps `Label`), the total at 24sp, "N% of today" at 13sp. This removes a feature of the owner's design from the home screen: his call whether to take it (open decision 14) |
| Keyboard opens by itself in the drawer, as a toggle, smooth and not buggy | The toggle existed (Settings → App drawer → "Open the keyboard right away", default off). Reworked when the drawer counts as open; dragging the list hides the keyboard |
| "Do not push or commit anything" | Changes were left in the working tree |

## How apps are classified (asked twice, so keep the answer short and first)
Android's "social" label also covers mail, browsers and messengers, so it is not trusted alone.
Order: curated social list → Play's game label → curated/labelled video (limit off by default) →
anything else Android calls social is a *tool* if the system says it can browse, mail, SMS or dial,
else *unsure* and left alone. Details: `3-details/app-classification.md`.

## Open decisions (raised, waiting for him)
1. ~~License.~~ Decided 2026-09-20: **GPL-3.0** (recorded as GPL-3.0-or-later), chosen from three
   options when F-Droid required one. The collaborator's merged code is part of the app: he was
   asked in the pull request to confirm the license for his contributions; not answered yet.
2. ~~His phone runs the debug-key `release` build.~~ 2026-09-20: he uninstalled it himself and
   installed the website's APK (settings reset, as predicted; see 15 for what blocked it at first).
   His phone is on the release key now; updates over adb come from `site/clean-build.sh`.
3. **Google Search Console** verification (needs his Google account).
4. A link to `/focusapp/` from the how2me.me homepage (his other site; offered, not edited).
5. Commit author address: his global git identity is used; GitHub's noreply alternative offered.
6. ~~`gradle.properties` pins a local JDK path.~~ Removed 2026-09-20 (F-Droid's server would have
   failed on it). On the owner's Mac every `./gradlew` now takes `-Dorg.gradle.java.home=<JDK 21>`.
7. ~~No GitHub Release with the APK attached.~~ Done 2026-09-19 (1.0 and 1.1).
8. The accessibility service has never been switched on on his phone, so mid-session locking and
   double tap to lock are unseen there. (Between 1.1.37 and its revert on 2026-09-20 there was none.)
   After the last fresh install of 2026-09-20 (1.1.40 over adb) he made Focus the default home
   and gave it usage access; the accessibility switch and notification access were still off.
9. One observation about the main site's configuration, unrelated to Focus: `private/server.md`.
10. **The work marker is now a drawn briefcase glyph**, at the contributor's repeated request: the
    first exception to the owner's "no icons". Owner's call whether it stays.
11. **"Swipe left = Google search" was interpreted** as finger-right (the page left of home).
    Confirm; the other reading would take the drawer's gesture.
12. The owner asked for the contributor's changes to be shipped (1.1), so they are wanted as a
    whole. Still unconfirmed one by one: the new defaults (double tap to lock on, swipe right on).
    His own phone keeps its stored `doubleTapLock` value; swipe right is new and therefore on.
13. Drawer tabs: pausing / resuming the work profile from the drawer is not implemented, and the
    tabs cannot be swiped between (the horizontal swipe belongs to the pager).
14. **The 24-hour bar is no longer on the home screen** (contributor's request, no setting). It
    was part of the owner's original sketch. The site's home mockup and copy were changed to match
    1.1; the review still has the bar. Keep, revert, or bring back as an option: owner's call.
15. Early installs used plain `adb install`, which installs for **every** Android user on a
    phone. 2026-09-20: he uninstalled Focus from his own profile to install the website's APK,
    and the copies left in other users of the phone (debug key) blocked it with a package conflict. He ran
    the removal for all users himself (`adb uninstall <pkg>`; verified afterwards that no user
    and no leftover record has the package). **Closed.** Removing an app from his phone stays
    his action, not an agent's; installs over adb use `--user 0`.
16. **CI publishing waits for one command from him**: `site/setup-ci-publishing.sh` (it handles
    his signing key and a new server key, so an agent must not run it, **also not when he says
    "run it"**: he did, 2026-09-20, and was given the reason and the Run button instead). Until
    then new versions are still published by hand. After it: `gh workflow run publish.yml`, then
    his Approve click; the agent can verify the result (environment, secret *names*, site,
    release) without touching a secret.
17. `main` has no branch protection: anyone with write access can push to it directly. With the
    approval gate nothing gets published without him, so this is about history, not about safety.
    Offered: require a pull request for `main`.
18. **"Mail or any widget" next to the clock.** His sketch names calendar, mail or any widget for
    the right half. Built: calendar and screen time. Mail as text (unread counts, senders) needs
    notification access, a new special permission and a listener service; hosted Android widgets
    are out by his own rule (icons, colour). Offered: a text-only "unread" section behind
    notification access, if he wants it.
19. **The split clock renders as designed on his phone (checked 2026-09-20); whether he likes it
    is his to say.** Both halves hug the line (clock text right-aligned, section text
    left-aligned) and the row sits near the top. That is a reading of the sketch, easy to change:
    alignment, sizes, which section is on the right by default.
20. **The F-Droid merge request needs him:** a GitLab.com account, a public fork of
    fdroid/fdroiddata, a token (`api` scope) stored with `gh secret set FDROID_GITLAB_TOKEN --env
    release`, and `gh variable set FDROID_GITLAB_FORK`. Then: run "F-Droid" with `submit` ticked,
    approve it, and answer the reviewers on GitLab. Agents do not create accounts or enter tokens.
21. ~~What the Play Protect fix cost.~~ He weighed it on 2026-09-20: the options stay, PR #11 was
    reverted. What is left open is the other half: **Play Protect blocks the download again**
    wherever it enforces this (his own report started it; 1.1.37, without the services, did
    install from a browser download on his phone, so the diagnosis was right). Ways out that keep the options, none
    built, all his call: installs over adb (exempt, what his phone can use); F-Droid, once the
    merge request is through (whether its installs are exempt is not verified); two variants,
    "full" and one without the two services (two builds to publish and explain).
22. "Unknown developer" warnings, as opposed to the sensitive-data block, are reputation, not
    permissions: only time, or Google's appeal form filed by him, changes them.
