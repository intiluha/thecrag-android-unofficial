# Lean Android SDK for building in the container (no NDK / emulator / system images).
# Usage (both env vars are required; the licence acceptance was approved by the repo owner):
#   NIXPKGS_ALLOW_UNFREE=1 NIXPKGS_ACCEPT_ANDROID_SDK_LICENSE=1 \
#     nix build --impure --expr 'import ./docs/build-env.nix' -o /tmp/android-sdk
#   export ANDROID_HOME=/tmp/android-sdk/libexec/android-sdk
#   nix shell nixpkgs#jdk17_headless -c ./gradlew assembleDebug
let
  nixpkgs = (builtins.getFlake "nixpkgs").outPath;
  pkgs = import nixpkgs {
    system = builtins.currentSystem;
    config = { allowUnfree = true; android_sdk.accept_license = true; };
  };
  comp = pkgs.androidenv.composeAndroidPackages {
    cmdLineToolsVersion = "latest";
    platformVersions = [ "35" ];
    buildToolsVersions = [ "35.0.0" ];
    includeNDK = false;
    includeEmulator = false;
    includeSystemImages = false;
    includeSources = false;
  };
in comp.androidsdk
