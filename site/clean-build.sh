#!/bin/bash
# Builds the APK that gets published, from a CLEAN checkout of the current commit.
#
#   site/clean-build.sh              prints the path of the signed APK as its last line
#   FOCUS_APK=$(site/clean-build.sh | tail -1) site/deploy.sh
#
# Why not just ./gradlew :app:assembleDist in the working folder: that folder carries months of
# incremental-compilation state and a build cache, and a build made there came out with one class
# more than the same commit built anywhere else (F-Droid's container, a fresh clone). F-Droid only
# ships our signed APK if its own build of the tag is identical to it, so a published APK has to be
# what a clean checkout produces. Same commit, clean checkout, no build cache = same bytes.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD

[ -z "$(git status --porcelain)" ] || { echo "The working tree has uncommitted changes: commit them first, a release is built from a commit." >&2; exit 1; }
[ -f keystore.properties ] || { echo "keystore.properties is missing: this machine cannot sign releases." >&2; exit 1; }
COMMIT=$(git rev-parse HEAD)
WORK=$(mktemp -d)
cleanup() { git -C "$ROOT" worktree remove --force "$WORK/src" >/dev/null 2>&1 || true; rm -rf "$WORK"; git -C "$ROOT" worktree prune; }
trap cleanup EXIT

git worktree add --quiet --detach "$WORK/src" "$COMMIT"
# The two untracked files a build needs are linked, not copied: the key's password stays in one place.
ln -s "$ROOT/keystore.properties" "$WORK/src/keystore.properties"
[ -f local.properties ] && ln -s "$ROOT/local.properties" "$WORK/src/local.properties"

JDK_ARGS=()
if [ -n "${FOCUS_JDK:-}" ]; then JDK_ARGS=(-Dorg.gradle.java.home="$FOCUS_JDK")
elif [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then JDK_ARGS=(-Dorg.gradle.java.home=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home); fi

( cd "$WORK/src" && ./gradlew "${JDK_ARGS[@]}" --console=plain -q --no-build-cache :app:testDebugUnitTest :app:lintDebug :app:assembleDist ) >&2

SDK="${ANDROID_HOME:-$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null || true)}"
TOOLS=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
APK="$WORK/src/app/build/outputs/apk/dist/app-dist.apk"
VERSION=$("$TOOLS/aapt2" dump badging "$APK" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)
CERT=$("$TOOLS/apksigner" verify --print-certs "$APK" 2>/dev/null | sed -n 's/.*certificate SHA-256 digest: *//p' | sort -u)
[ "$CERT" = "526a00b874660af4266699d5795a457ddebe958a78484820fac4b61b2a4852a2" ] || { echo "not signed with the release key" >&2; exit 1; }
! "$TOOLS/aapt2" dump badging "$APK" | grep -q "android.permission.INTERNET" || { echo "the APK asks for the INTERNET permission" >&2; exit 1; }
# Play Protect blocks a downloaded APK that declares any of these; see the README.
! "$TOOLS/aapt2" dump xmltree --file AndroidManifest.xml "$APK" | grep -qE "BIND_ACCESSIBILITY_SERVICE|BIND_NOTIFICATION_LISTENER_SERVICE|android\.permission\.(READ|RECEIVE)_SMS" \
  || { echo "the APK declares an accessibility service, a notification listener or an SMS permission: Play Protect would block it" >&2; exit 1; }

mkdir -p "$ROOT/build/clean"
OUT="$ROOT/build/clean/focus-launcher-$VERSION.apk"
cp "$APK" "$OUT"
echo "clean build of $COMMIT: version $VERSION, release key, no INTERNET permission, $(wc -c < "$OUT" | tr -d ' ') bytes" >&2
echo "$OUT"
