# Tier 2 · The public repository, signing, releasing

Repo: **https://github.com/patelchaitany/focus-launcher** · public since 2026-09-19 · branch `main`
· `gh` is authenticated as the owner on his machine. Homepage field and README point to the site;
topics set (minimalist-launcher, android-launcher, app-blocker, …).

## What is deliberately not in the repo
All git-ignored and verified absent from raw.githubusercontent.com:
`keystore.properties` · `local.properties` · `site/deploy.env` · `site/public/` (generated, holds
the APK) · everything in `.claude/` **except** `CLAUDE.md` and the brain · every `private/` folder
inside a brain · build output. The committed template for the deploy target is
`site/deploy.env.example`.

The brain itself **is** committed (owner's decision, 2026-09-19). That is why it must never contain
what the list in `.claude/CLAUDE.md` → "The brain is public" forbids.

## Before every push (publishing cannot be undone)
```bash
git add -A
# 1. no forbidden files
git diff --cached --name-only | grep -E "keystore\.properties|local\.properties|deploy\.env$|\.jks$|\.apk$|/private/|^\.claude/launch\.json|^site/public/" && echo STOP
# 2. no forbidden content: generic patterns ...
git diff --cached | grep -cE "^\+.*(/Users[/]|storePassword[=]|keyPassword[=]|PRIVATE[ ]KEY)"   # brackets: so this line does not match itself
# 3. ... and the owner-specific ones, kept out of the repo on purpose (count only, never print matches)
git diff --cached | grep -cEf .claude/brain/chaitany-claude/private/audit-patterns.txt
```
Step 1 must print nothing; steps 2 and 3 must print `0`. Step 3 uses `-c` so that a hit is never
echoed into a log. `private/audit-patterns.txt` is generated from the machine's own config by
`private/make-audit-patterns.sh`; regenerate it when the server, key or device changes. Then
commit and push. No force-pushes to `main` without being asked.

## Commits
Use the owner's global git identity as configured on his machine (the author address is visible
in public history; he was told about GitHub's noreply alternative). End every commit message with
the `Co-Authored-By:` line the session instructions specify. Commit or push only when asked, or as
the closing step of a change he asked to have published.

## Signing and the three builds
| Build | Signed with | For |
| --- | --- | --- |
| `debug` | debug key | short verification (`run-as`, exported test activities) |
| `release` | **debug key**, R8-optimized | the owner's own phone (installs over debug, keeps data) |
| `dist` | **release key**, same optimized code | the website; the only build that may be published |

The release key lives outside the repo. Its location and password are in git-ignored
`keystore.properties` (never print it). If key or password is lost, no published build can ever be
updated: the owner was told to back both up. A `dist` APK cannot be installed over
`release`/`debug` (or the reverse).

## Releasing a new version
1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. `./gradlew :app:testDebugUnitTest :app:lintDebug` clean.
3. Owner's phone: `assembleRelease` → `adb install --user 0 -r` → `compile -m speed-profile -f`.
4. Public: `./gradlew :app:assembleDist && site/deploy.sh` (the APK file name carries the version;
   older APKs stay on the server so old links keep working).
5. Audit, commit, push.

## Open
No LICENSE · no GitHub Release with the APK · `gradle.properties` pins a local JDK path.

## Tier 3 pointers
`signing-keys.md` · `toolchain-and-build.md`
