# Tier 3 · Deciding what is "social media", a "game", or a tool

Code: `AppRepository.categorize`, `findCommunicationApps`, `describeCategory`;
`LimitManager.limitFor`. The owner asked about this twice; the answer also lives in the README,
the website FAQ and the in-app timer dialog (`describeCategory`).

## Why Android's label is not enough
`ApplicationInfo.CATEGORY_SOCIAL` officially means "messaging, communication, email, or social
network apps". The Play Store maps its Social + Communication + Dating categories onto it (it sets
the hint at install). On a real phone that label covers the browsers, the mail client, contacts,
SMS, video meetings, the messengers and even an accessibility tool. My first classifier trusted it
and put a 30-minute limit on everyday tools, including ones pinned as fast apps. That was noticed
at once, and rightly.

## The order (`categorize`)
1. `KNOWN_SOCIAL` (curated: Instagram, Threads, Facebook, X, Snapchat, TikTok, Reddit, Pinterest,
   LinkedIn, Tumblr, Discord, BeReal, Mastodon, Bluesky, Quora, ShareChat/Moj/Josh, VK, Weibo,
   Hinge, Tinder, Bumble, OkCupid) → **SOCIAL**. Wins over everything; many declare no category.
2. `CATEGORY_GAME` or `FLAG_IS_GAME` → **GAME** (Play sets this reliably).
3. `KNOWN_VIDEO` (YouTube, Netflix, Prime Video, Disney+, Hotstar, JioCinema, SonyLIV, Zee5, Twitch)
   or `CATEGORY_VIDEO` → **VIDEO**. Its automatic limit (`videoDefaultMin`) is **0 = off**.
4. Anything else with `CATEGORY_SOCIAL` → **COMMUNICATION** if in `KNOWN_COMMUNICATION`, or if
   the system says it handles one of these probes (`queryIntentActivities`, `MATCH_ALL`):
   `VIEW http(s)://www.example.com` + `BROWSABLE` (a generic host only matches real browsers),
   `SENDTO mailto:`, `SENDTO smsto:`, `DIAL tel:`; or its package name contains
   browser / mail / messag / .sms / contacts / dialer. Otherwise **UNSURE**.
5. Everything else → **OTHER**.

## What gets a limit (`limitFor`)
Own entry in `appLimits` first (`0` = explicitly unlimited, overriding a default). Otherwise
SOCIAL → `socialDefaultMin` (30), GAME → `gameDefaultMin` (30), VIDEO → `videoDefaultMin` (0).
COMMUNICATION, UNSURE, OTHER → never automatically. Protected packages can never be limited at
all (`canLimit`): this launcher, other launchers, systemui, settings, the default dialer.

## The principle
Wrongly locking a genuine tool costs far more trust than missing a niche social app, so the
classifier **never guesses towards a limit**. Every automatic decision is visible
(Settings → App timers → Limited apps: "With a daily limit", "Excluded", "Not sure about these")
and overridable per app (long-press → App timer).

## How it was validated
Run against a real phone with about two hundred launcher entries (2026-09-19): every browser,
mail client and messenger landed in COMMUNICATION and got no limit; the feed apps landed in
SOCIAL; Play-labelled games in GAME; streaming apps in VIDEO with the limit off; a handful of apps
Android calls "social" that nothing else could confirm landed in UNSURE and were left alone.
The actual lists are somebody's installed apps, so they are not recorded here.

## Known weak spots
A new social network not on the curated list lands in UNSURE until it is added. Play's game label
is trusted, so a utility published in the Games category is treated as a game until the user
exempts it. Apps that are half tool and half feed (chat communities, professional networks) are
judgement calls: they are on the social list, and one long-press exempts them.
