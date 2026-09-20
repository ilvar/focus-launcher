# F-Droid

Focus is submitted to F-Droid's main repository. F-Droid does not take uploads: it builds every
app itself, from source, following a recipe that lives in its own repository
([fdroiddata](https://gitlab.com/fdroid/fdroiddata), `metadata/com.focus.launcher.yml`). The
recipe is proposed once, as a merge request on GitLab. After it is accepted, F-Droid picks up new
versions by itself from this repository's tags; nothing has to be pushed for an update.

- `com.focus.launcher.yml` is that recipe, kept here as a template: `@VERSION_NAME@`,
  `@VERSION_CODE@` and `@COMMIT@` are filled in from a tag by the workflow.
- `.github/workflows/fdroid.yml` ("F-Droid", run by hand from the Actions tab) fills the template
  for a tag, runs F-Droid's own tools on it in F-Droid's build container (`fdroid lint`,
  `fdroid rewritemeta`, `fdroid checkupdates`, `fdroid build`), and, only if asked to and only if
  the GitLab secrets exist, runs `submit.py`: it puts the recipe on the owner's fork of fdroiddata
  and makes sure there is exactly **one** merge request. An open one is updated (a new commit on
  the same branch and a note for the reviewers; never a force-push), a closed one is reopened, a
  new one is opened only if there is none, and once Focus is in F-Droid nothing is submitted any
  more. `test_submit.py` checks all of that against a fake GitLab at the start of every run.
- The listing (name, descriptions, icon, screenshots, changelogs) is read by F-Droid from
  `fastlane/metadata/android/en-US/` in this repository. The screenshots are drawn by
  `fastlane/screenshots.py`, not captured: a real screenshot shows somebody's apps and calendar.

How versions reach F-Droid: a release is a tag `v<base>.<build>` (for example `v1.1.22`). The
build number is the number of commits, which is also the `versionCode`; F-Droid reads both
numbers from the tag name (`UpdateCheckData`), because it never runs Gradle to find a version.

**Reproducible builds.** The recipe names the published APK (`Binaries`) and the release
certificate (`AllowedAPKSigningKeys`). F-Droid builds Focus unsigned (`-Pfocus.unsigned`), checks
that the result is identical to that published APK, and then ships the published one, signed
with the project's own key. So the copy from F-Droid and the copy from the website are the same
file, and one can update the other. This works because the build is deterministic: the same
commit gives the same APK contents on macOS, on GitHub's Linux runners and in F-Droid's
container (no Google dependency blob, no version-control stamp, version taken from git).
If a release ever fails that comparison, F-Droid simply does not publish it; fix the cause and
tag again.
