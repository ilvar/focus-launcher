# F-Droid

Rkd Launcher is prepared for F-Droid's main repository. F-Droid does not take uploads: it builds every
app itself, from source, following a recipe that lives in its own repository
([fdroiddata](https://gitlab.com/fdroid/fdroiddata), `metadata/pw.rkd.launcher.yml`). The
recipe is proposed once, as a merge request on GitLab. After it is accepted, F-Droid picks up new
versions by itself from this repository's tags; nothing has to be pushed for an update.

- `pw.rkd.launcher.yml` is that recipe, kept here as a template: `@VERSION_NAME@`,
  `@VERSION_CODE@` and `@COMMIT@` are filled in from a tag by the workflow.
- `.github/workflows/fdroid.yml` ("F-Droid", run by hand from the Actions tab) fills the template
  for a tag, runs F-Droid's own tools on it in F-Droid's build container (`fdroid lint`,
  `fdroid rewritemeta`, `fdroid checkupdates`, `fdroid build`), and, only if asked to and only if
  the GitLab secrets exist, runs `submit.py`: it puts the recipe on the owner's fork of fdroiddata
  and makes sure there is exactly **one** merge request. An open one is updated (a new commit on
  the same branch and a note for the reviewers; never a force-push), a closed one is reopened, a
  new one is opened only if there is none, and once Rkd Launcher is in F-Droid nothing is submitted any
  more. `test_submit.py` checks all of that against a fake GitLab at the start of every run.
- The listing (name, descriptions, icon, screenshots, changelogs) is read by F-Droid from
  `fastlane/metadata/android/en-US/` in this repository. The four phone screenshots were supplied by the project owner and show the home screen,
  app list, to-do list, and app limit.

How versions reach F-Droid: a release is tagged `v<version>+<code>` (for example `v1.2.0+57`).
The code is the tagged commit's count and the Android `versionCode`; F-Droid extracts both
values from the tag (`UpdateCheckData`) without running Gradle during update checks.

**New application ID.** The recipe uses `pw.rkd.launcher` and builds from source. It no longer
claims the old Focus release certificate or that an F-Droid APK can update the site's APK.
Verify the first tagged release and submit the new ID separately before enabling automatic
F-Droid submission.

## Submit manually on GitLab

1. Merge the version change into `main`. Tag the exact release commit: from a fresh clone, run
   `code=$(git rev-list --count HEAD)`, then `git tag "v1.2.0+$code"` and
   `git push origin "v1.2.0+$code"`. Do this only after choosing the commit to release.
2. On GitHub, run **Actions → F-Droid → Run workflow** with that tag, **build** enabled and
   **submit** disabled. Download the `fdroid-recipe` artifact after the check passes. It contains
   a filled-in `pw.rkd.launcher.yml` with the release commit and code; use this file, not the
   template with `@...@` placeholders. No GitLab secrets are needed for this check.
3. Sign in to GitLab and [fork fdroiddata](https://gitlab.com/fdroid/fdroiddata/-/forks/new).
   In your fork, create a branch such as `pw.rkd.launcher` from `master` and add the checked
   recipe as `metadata/pw.rkd.launcher.yml`. Commit and open a merge request into
   `fdroid/fdroiddata:master` titled **New app: Rkd Launcher**. Use `MERGE_REQUEST.md` as the
   description, checking off only items actually completed. Let the original author know about
   the fork's submission before filing, as F-Droid asks for fork submissions.
4. Watch the GitLab pipeline and respond to reviewer feedback in that merge request. F-Droid
   will build and sign its own APK from the tagged source after inclusion. Subsequent tagged
   versions are discovered by its update checker.
