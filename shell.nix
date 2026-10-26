# Dev environment: everything the Justfile needs. Usage: `nix-shell`, then `just`.
{ pkgs ? import (fetchTarball "https://channels.nixos.org/nixos-26.05/nixexprs.tar.xz") {
    config = { allowUnfree = true; android_sdk.accept_license = true; };
  }
}:
let
  android = pkgs.androidenv.composeAndroidPackages {
    platformVersions = [ "35" ];
    buildToolsVersions = [ "35.0.0" ];
    includeEmulator = false;
    includeSources = false;
    includeSystemImages = false;
  };
  sdk = "${android.androidsdk}/libexec/android-sdk";
in
pkgs.mkShell {
  packages = [ pkgs.jdk17 pkgs.gradle android.androidsdk pkgs.just pkgs.gh ];
  ANDROID_HOME = sdk;
  ANDROID_SDK_ROOT = sdk;
  JAVA_HOME = pkgs.jdk17.home;
  # Maven's aapt2 is a generic-linux binary; use the nix-patched one from the SDK instead.
  GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/35.0.0/aapt2";
}
