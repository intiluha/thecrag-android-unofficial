# thecrag-android-unofficial

Unofficial, personal-use Android client for [theCrag](https://www.thecrag.com):
browse areas and crags, list routes, view topos, see locations on a map. Everything
comes from the JSON endpoints the website itself uses; no HTML scraping.
Not affiliated with theCrag. Data is theirs and their contributors' (CC BY-NC-SA).

Status: Stage 1 (anonymous online guide) feature-complete. Opened pages, topos and
images stay cached and are shown offline. See `docs/PLAN.md` and
`docs/research/api.md`.

## Build

Recipes live in the `Justfile`; `shell.nix` provides JDK 17, Gradle and a lean
Android SDK (platform 35, build-tools 35.0.0) through nix.

```sh
just build     # debug APK -> app/build/outputs/apk/debug/app-debug.apk
just release   # minified APK, debug-signed (own phone only) -> app/build/outputs/apk/release/app-release.apk
just test      # unit tests
just install   # adb install + launch the debug build on a connected phone
just install-release   # same for the release build (coexists with the debug one)
just clean
```

Without `just`: `nix-shell --run 'gradle --no-daemon assembleDebug'`.
The Gradle wrapper (`./gradlew`) also works if you bring your own JDK and SDK.
