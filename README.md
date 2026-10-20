# thecrag-android-unofficial

Unofficial, personal-use Android client for [theCrag](https://www.thecrag.com):
browse areas and crags, list routes, view topos, see locations on a map.
Not affiliated with theCrag. Data is theirs and their contributors' (CC BY-NC-SA).

Status: Stage 1 (anonymous online guide) in progress. See `docs/PLAN.md` and
`docs/research/api.md`.

## Build

Needs JDK 17 and an Android SDK with platform 35 / build-tools 35.0.0.
In the dev container both come from nix (`docs/build-env.nix`):

```sh
NIXPKGS_ALLOW_UNFREE=1 NIXPKGS_ACCEPT_ANDROID_SDK_LICENSE=1 \
  nix build --impure --expr 'import ./docs/build-env.nix' -o /tmp/android-sdk
export ANDROID_HOME=/tmp/android-sdk/libexec/android-sdk
nix shell nixpkgs#jdk17_headless -c ./gradlew assembleDebug testDebugUnitTest
```

The debug APK lands in `app/build/outputs/apk/debug/`.
