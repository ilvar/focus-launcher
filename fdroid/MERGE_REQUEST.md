Focus is a text-only, black and white launcher with daily app timers and a weekly screen-time review. It asks for no INTERNET permission. I am the author.

* Source: https://github.com/patelchaitany/focus-launcher (GPL-3.0-or-later)
* Website: https://how2me.me/focusapp/

## Checklist

### Policy

* [x] The app complies with the [inclusion criteria](https://f-droid.org/docs/Inclusion_Policy).
* [x] The original app author has been notified (and does not oppose the inclusion). I am the author.
* [x] The upstream app source code repo contains the app metadata in a Fastlane folder structure (`fastlane/metadata/android/en-US`: title, summary, description, icon, screenshots, changelog).

### Docs

* [x] Read the contributing guide.
* [x] The metadata follows the templates.
* [x] Read the Build Metadata Reference.
* [x] Read the Quick Start Guide.

### Merge Request Setup

* [x] The title follows the "New app: app name" format.
* [x] The fdroiddata fork is public and the branch is not protected.
* [x] No rebase without a conflict.
* [x] No related fdroiddata or RFP issues exist.
* [x] One app in this MR.

### Metadata

* [x] Metadata is in `metadata/com.focus.launcher.yml`.
* [x] Valid YAML (`fdroid readmeta`, `fdroid rewritemeta`, `fdroid lint` pass).
* [x] LF line endings.
* [x] No summary, description, changelog or images in this MR; they are upstream.
* [x] Releases are tagged (`vX.Y.Z`) and auto update is enabled. The version code is the number of commits and the last component of the tag, so `UpdateCheckData` reads both numbers from the tag name.
* [x] There is an issue tracker, and an AuthorName.
* [x] No srclibs and no submodules are needed.
* [x] **Reproducible builds are enabled** (`Binaries` + `AllowedAPKSigningKeys`). The app is Kotlin only; the dependency-info block and AGP's version-control stamp are switched off; the same commit gives identical APK contents on macOS, on GitHub's Linux runners and in the `fdroidserver:buildserver` container, where `fdroid build` was run against the published APK before this MR was opened.
* [x] No ABI split needed: no native code, the APK is 1.3 MB.
* [x] Only the latest version is in the metadata.
* [x] No disabled versions.
* [x] `commit` is a full hash.

### Pipeline

* [ ] All pipelines should pass.
* [ ] All warnings and errors in the Reports tab are fixed or explained.
