# Tier 3 · F-Droid

Asked by the owner on 2026-09-20: submit the app following F-Droid's quick start guide, and
"create another CI to push it on fdroid and make it run manually".

## How F-Droid works (it is not an upload)
F-Droid builds every app itself, from source, from a recipe in **its** repository
(gitlab.com/fdroid/fdroiddata, `metadata/com.focus.launcher.yml`). The recipe gets there once, as
a merge request from a GitLab account. After it is merged, F-Droid's bot finds new versions from
this repository's tags and builds them; nothing is pushed per release. The listing (name,
descriptions, icon, screenshots, changelogs) is read from `fastlane/metadata/android/en-US/` here.

## What inclusion needed, and what was done
- **A FOSS license.** There was none. The owner chose **GPL-3.0** from three options
  (2026-09-20); recorded as `GPL-3.0-or-later` (LICENSE = GPLv3 text, the "or later" is stated in
  the README). The collaborator's merged code is part of the app: he was asked in the pull request
  to confirm the license for his contributions.
- **Only free dependencies:** AndroidX, Compose, Kotlin, coroutines (all Apache-2.0); `org.json`
  in tests only. No Play services, no analytics, no INTERNET permission. No AntiFeatures apply.
- **No local paths in the build:** the JDK pin left `gradle.properties` (it would have failed on
  F-Droid's server, as it did on any other machine).
- **Fastlane files:** title "Focus Launcher", a 73-character summary, description, 512 px icon
  rendered from the app's own vector icon, four screenshots drawn by `fastlane/screenshots.py`
  with invented content (never captured: rule 4), `changelogs/<versionCode>.txt` (≤ 500 chars).

## Versions without running Gradle
F-Droid finds versions by regex, never by running the build, and this project computes
`versionCode` from the commit count. Both are reconciled by the tag: a release is tagged
`v<base>.<count>`, and the recipe reads both numbers from the tag name:
`UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$` and
`UpdateCheckData: '|^v\d+\.\d+\.(\d+)$||^v([\d.]+)$'` (empty file fields = "use the tag").
The build on F-Droid's side computes the same number, because its clone has the full history.
The workflow refuses a tag whose last component is not the commit count at that commit.
`v1.0` and `v1.1` do not match the pattern and are ignored.

## Reproducible builds: F-Droid ships the APK signed with the project's key
Without this F-Droid signs with its own key, its copy and the website's copy cannot update each
other, and F-Droid says the choice cannot be changed later. So it was tested before submitting:
- Same commit, `assembleRelease`, GitHub's Linux runner (Temurin 21) vs macOS (Homebrew JDK 21):
  119 entries, identical order, **one** difference: `META-INF/version-control-info.textproto`,
  the git stamp AGP 8.3+ adds. → `vcsInfo { include = false }` on the release type.
- `dependenciesInfo { includeInApk = false; includeInBundle = false }`: the Google-only encrypted
  dependency blob lives in the signing block; F-Droid wants it out.
- `-Pfocus.unsigned` makes `release` unsigned. Its contents are identical, entry for entry, to the
  debug-signed `release` and to the release-key `dist` build that is published (checked), so
  F-Droid can build plain `gradle: yes` + `gradleprops: focus.unsigned` and compare with the
  published file.
- Recipe: `Binaries: …/releases/download/v%v/focus-launcher-%v.apk` and
  `AllowedAPKSigningKeys: <release certificate SHA-256>`.
If a release ever fails the comparison, F-Droid does not publish that version: fix and tag again.
- **The first real comparison failed, and the build was not the culprit.** F-Droid's container
  built 1.1.25 fine, but its `classes.dex` had one class fewer than the published APK (a small
  settings enum that R8 had kept), so the baseline profiles differed too. A build of the same tag
  from a **fresh worktree with `--no-build-cache`** on the owner's Mac was identical to F-Droid's,
  file for file. The published APK had been built in the long-lived working folder, with its
  incremental-compilation state and build cache. → **A published APK is always built by
  `site/clean-build.sh`** (clean worktree of HEAD, no build cache, tests, lint, checks the
  certificate and the permissions, prints the APK's path) and `publish.yml` builds with
  `--no-build-cache`. 1.1.22 and 1.1.25 stay as they are (one version, one binary); the first
  reproducible release is the next one.
- **1.1.29 is the first reproducible release, and F-Droid's tools confirm it:** in the buildserver
  container (JDK 21, Gradle 8.14.3) `readmeta`, `rewritemeta`, `checkupdates` (picks `v1.1.29` from
  the tags), `lint` (no warnings) and `build` pass, the published APK is fetched, its signature is
  copied onto F-Droid's own build, and the log ends in "successfully verified". `rewritemeta` only
  moved the `Binaries` URL onto its own line; the template is kept in that canonical form.
- `fdroid build` exits 0 even when the build or the comparison fails: the workflow's first
  "green" run was not green. The verdict is read from its log ("Could not build app", "NOT
  verified"), and the APK it built is kept in the artifact so that a failure can be diffed.

## The manual workflow: `.github/workflows/fdroid.yml` ("F-Droid", `workflow_dispatch` only)
Inputs: `tag` (empty = newest `vX.Y.Z`), `build` (default on), `submit` (default off).
- **check:** verifies the tag/commit-count rule and the Fastlane files at that tag, fills
  `fdroid/com.focus.launcher.yml` (`@VERSION_NAME@`, `@VERSION_CODE@`, `@COMMIT@` = full hash),
  shallow-clones fdroiddata and fdroidserver, and runs in `registry.gitlab.com/fdroid/fdroidserver:
  buildserver`, as the guide does: `fdroid readmeta`, `rewritemeta`, `checkupdates --allow-dirty`,
  `lint`, and `fdroid build`, which with `Binaries` set also compares the result with the
  published APK. The recipe as F-Droid's tools left it, and `fdroid/MERGE_REQUEST.md`, become the
  artifact `fdroid-recipe`.
- **submit:** runs in the protected `release` environment (owner's approval) and calls
  `fdroid/submit.py` with `FDROID_GITLAB_TOKEN` (environment secret) and `FDROID_GITLAB_FORK`
  (repository variable, `<gitlab user>/fdroiddata`). Everything goes through GitLab's API (no git
  push from a shallow clone). **There is only ever one merge request** (the owner asked for this
  on 2026-09-20): the recipe is committed to the fork's branch `com.focus.launcher`; an **open**
  MR from that branch is updated by that commit plus a note for the reviewers, and no second one
  is opened; an unchanged recipe commits nothing; a **closed** MR is reopened and updated; MRs of
  other forks with the same branch name are ignored (matched by `source_project_id`, not by
  author name); if the recipe is already on fdroiddata's `master`, Focus is included and nothing
  is submitted (F-Droid's bot owns the file from then on). Only with none of these is "New app:
  Focus Launcher" opened, with the filled-in checklist. `fdroid/test_submit.py` runs nine such
  scenarios against a fake GitLab at the start of every workflow run. Agents do not create the
  GitLab account or enter the token: the owner does (`gh secret set FDROID_GITLAB_TOKEN --env
  release` prompts for it).

## What only the owner can do
1. A GitLab.com account; fork gitlab.com/fdroid/fdroiddata (public fork).
2. A personal access token with the `api` scope; store it and the fork's path as above.
3. Run the workflow with `submit` ticked, approve it, then answer the reviewers on GitLab.
   If GitLab asks for a phone number or card to run pipelines, F-Droid says not to give one and
   to leave a note in the merge request.
