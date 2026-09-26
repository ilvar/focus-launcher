Rkd Launcher is a fork of Focus by Chaitany Patel. It is a text-only launcher with app timers, a checklist and optional weather. Weather sends approximate coordinates to Open-Meteo when enabled.

* Source: https://github.com/ilvar/focus-launcher (GPL-3.0-or-later)
* Website: https://how2me.me/focusapp/

## Checklist

### Policy

* [x] The app complies with the [inclusion criteria](https://f-droid.org/docs/Inclusion_Policy).
* [ ] Notify the original author before submitting this new application ID.
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

* [x] Metadata is in `metadata/pw.rkd.launcher.yml`.
* [x] Valid YAML (`fdroid readmeta`, `fdroid rewritemeta`, `fdroid lint` pass).
* [x] LF line endings.
* [x] No summary, description, changelog or images in this MR; they are upstream.
* [x] Releases are tagged (`vX.Y.Z`) and auto update is enabled. The version code is the number of commits and the last component of the tag, so `UpdateCheckData` reads both numbers from the tag name.
* [x] There is an issue tracker, and an AuthorName.
* [x] No srclibs and no submodules are needed.
* [ ] Verify a release build and metadata for the new application ID before submitting.
* [x] No ABI split needed: no native code.
* [x] Only the latest version is in the metadata.
* [x] No disabled versions.
* [x] `commit` is a full hash.

### Pipeline

* [ ] All pipelines should pass.
* [ ] All warnings and errors in the Reports tab are fixed or explained.
