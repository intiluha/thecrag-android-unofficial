# thecrag-android

Unofficial, personal-use Android client for [theCrag](https://www.thecrag.com):
browse areas and crags, list routes, view topos, see locations on a map. Everything
comes from the JSON endpoints the website itself uses; no HTML scraping.
Not affiliated with theCrag. Data is theirs and their contributors' (CC BY-NC-SA).

Status: Stage 1 (anonymous online guide) and Stage 2 (login, logbook, ticks) run on a phone, Stage 2
verified against theCrag with a throwaway account; the logbook exports to CSV. Stage 3: star an area to keep it, its routes and topos on the device (foreground download, served offline). Opened pages, topos and
images stay cached and are shown offline. See `docs/PLAN.md` and
`docs/research/api.md`.

## Build and install

The only requirement is [Nix](https://nixos.org). All recipes run inside the project's `nix-shell`,
which provides JDK 17, Gradle, the Android SDK 35, `adb`, `gh` and `just` itself.

```sh
nix-shell
> just           # list all recipes
> just build     # → app/build/outputs/apk/debug/app-debug.apk
> just test      # unit tests: API parsing, session/cache/offline interceptors, topo mapping, logbook, CSV
> just check     # lint + unit tests
> just run       # build, adb install and launch on the USB-connected phone
```

For a one-off from outside the shell, `nix-shell --run 'just check'` does the same.

The dev build installs side by side with any release build as **theCrag Dev** (`xyz.intiluha.thecrag.dev`).

## Release

```sh
just release            # signed app/build/outputs/apk/release/app-release.apk
just release-install    # …and adb install it
just publish v0.1.0     # …and attach it to a new GitHub Release tagged v0.1.0
```

Releases are signed with the committed PKCS12 keystore `release.p12` (key alias `thecrag`). Only its
password is secret: `just release` prompts for it and hands it to Gradle as `KEYSTORE_PASSWORD`, which
is read only when a release task runs, never for debug builds or tests. Set the variable yourself to
skip the prompt. Keeping the keystore in the repo keeps the signing key stable across versions, which
is what lets [Obtainium](https://github.com/ImranR98/Obtainium) update the app in place from this
repository's GitHub Releases. There is no CI: releases are built locally.

Setting up a fresh keystore (once, inside `nix-shell`):

```sh
> just keystore           # keytool prompts for a password; `just release` will ask for the same one
> git add release.p12
```
