# Tier 2 · The owner, what he asked for, what he decided

## Working agreements
- Requests often arrive several at a time, and follow-ups arrive mid-task. Read for intent, state
  the reading in one line, then do all of it and fold the follow-ups in.
- Lead with the answer. An explanation that was asked for goes first, not after the change log.
- Finish and verify rather than propose. Report honestly what was *not* verified.
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
| Ring shows **battery %** | "Day passed" kept as an option, not the default |
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

## Asked for by a contributor (2026-09-19, on a clone; not yet seen by the owner)
| Request | What was done |
| --- | --- |
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
1. **License** for the public repo (none yet = all rights reserved). Site says "source is public".
2. **His phone still runs the debug-key `release` build.** Moving to the public `dist` build needs
   one uninstall (settings reset). Offered, not done. Never uninstall without asking.
3. **Google Search Console** verification (needs his Google account).
4. A link to `/focusapp/` from the how2me.me homepage (his other site; offered, not edited).
5. Commit author address: his global git identity is used; GitHub's noreply alternative offered.
6. `gradle.properties` pins a local JDK path: a fresh clone elsewhere fails until it is removed.
7. No GitHub Release with the APK attached.
8. The accessibility service is not enabled on his phone, so mid-session locking is untested.
9. One observation about the main site's configuration, unrelated to Focus: `private/server.md`.
10. **The work marker is now a drawn briefcase glyph**, at the contributor's repeated request: the
    first exception to the owner's "no icons". Owner's call whether it stays.
11. **"Swipe left = Google search" was interpreted** as finger-right (the page left of home).
    Confirm; the other reading would take the drawer's gesture.
12. Whether the owner wants the contributor's changes and the new defaults (double tap to lock on,
    swipe right on) at all: he has not seen them.
13. Drawer tabs: pausing / resuming the work profile from the drawer is not implemented, and the
    tabs cannot be swiped between (the horizontal swipe belongs to the pager).
14. **The 24-hour bar is no longer on the home screen** (contributor's request, no setting). The
    site and its mockups still show it. Keep, revert, or bring back as an option: owner's call.
