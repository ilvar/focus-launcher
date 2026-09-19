# Tier 3 · Keeping this brain true, complete and publishable

The rules are in `.claude/CLAUDE.md`. This file is the how.

## What "up to date" means here
The owner asked twice on 2026-09-19: first for the brain, then for it to hold **everything done
so far**, not only the last task. So after each piece of work, and whenever asked, check three
things:
1. **Coverage**: every feature and every area of work has a home in some tier. Walk the list in
   `2-overview/app.md` → "Features and where they live" and the five Tier 2 files; anything built
   or decided that appears nowhere gets written down.
2. **Truth**: what is written still matches the code, the repo and the live site (commands below).
3. **Publishability**: nothing that belongs in `private/` (rules in `.claude/CLAUDE.md`).

## Fact-check commands (all read-only; run from the repo root)
```bash
find app/src/main -name '*.kt' | xargs wc -l | tail -1                 # size quoted in Tier 1
grep -c '@Test' app/src/test/java/com/focus/launcher/data/*.kt          # test counts quoted in Tier 1/2/3
grep -rnE "CACHE_VERSION *=|COVER_GRACE_MS *=|SETTLE_MS *=|LOOKBACK_MS *=" app/src/main --include='*.kt'
grep -nE "val (socialDefaultMin|gameDefaultMin|videoDefaultMin|frictionSeconds|askAfterBypass|reviewHour)\b" \
  app/src/main/java/com/focus/launcher/data/Settings.kt                 # defaults quoted in Tier 3
sed -n '1,14p' gradle/libs.versions.toml                                # versions quoted in Tier 2/3
grep -nE 'const val [A-Z_]+ *= *"[a-z]+"' app/src/main/java/com/focus/launcher/ui/settings/SettingsRoot.kt   # routes
ls -l app/build/outputs/apk/*/*.apk                                     # APK sizes
curl -s -o /dev/null -w '%{http_code}\n' https://how2me.me/focusapp/    # site up (and the main site: same with /)
git status -sb && git log --oneline | head -3                           # repo state
```
Two checks worth scripting when many files changed: every back-ticked file path in the brain
exists somewhere in the repo, and every back-ticked symbol (`DayAccumulator`, `launchOptions`, …)
still occurs in `app/src/main`. On 2026-09-19 both passed: 70 paths (the only misses were files
that live on the device or inside an AAR) and 35 symbols.

## Writing for a public brain
- Describe the mechanism, the decision and the size of an error. Leave out whose server, which
  phone, which apps, how many hours, which accounts.
- Examples use invented or generic names (`com.example.app`), never what was seen on the phone.
  Package names that are part of the source code (the curated lists) are fine.
- A fact an agent needs but the public must not read goes in `private/` and the public text only
  points at it. On a machine without `private/`, ask the owner; never reconstruct it from history.
- **When cleaning sensitive text, do not quote it**, not even to explain what is being removed:
  count matches (`grep -c`), copy files mechanically, and rewrite whole files in general terms.

## Before a push
The three-step audit in `2-overview/github-and-release.md`. If the server, the login key, the
phone or the git identity changed, run `private/make-audit-patterns.sh` first. The audit only
finds what it knows to look for; the writing rule above is what actually keeps the brain clean.
A count-only check must also show how much it read: a `0` from a command that read nothing looks
exactly like a clean result (it happened here, see `mistakes-and-lessons.md`).
After the push, check through the API (`gh api repos/<owner>/<repo>/contents/<path>`), because
raw.githubusercontent.com serves a cached copy for a few minutes.
**No journal-only commits.** A push is recorded inside the journal entry of the work it
publishes, written before the commit (what was asked, what the audit showed). The check after
the push is reported to the owner; only a *failed* check earns a new entry. Otherwise every push
would need another push to record it.

## Size discipline
Tier 1 stays within one screen (about 80 lines; 2026-09-19: under 75). A Tier 2 file is about a
page. When a Tier 3 file passes ~120 lines, split it by subsystem and fix the pointers in the
Tier 2 file and in `README.md`.
