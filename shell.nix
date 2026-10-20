# Dev shell: JDK 17, Gradle and a lean Android SDK (platform 35, build-tools 35.0.0;
# no NDK, emulator or system images). Used by the Justfile recipes.
# Accepting the Android SDK licence here was approved by the repo owner.
{ pkgs ? import <nixpkgs> {
    config = {
      allowUnfree = true;
      android_sdk.accept_license = true;
    };
  }
}:
let
  android = pkgs.androidenv.composeAndroidPackages {
    cmdLineToolsVersion = "latest";
    platformVersions = [ "35" ];
    buildToolsVersions = [ "35.0.0" ];
    includeNDK = false;
    includeEmulator = false;
    includeSystemImages = false;
    includeSources = false;
  };
  sdk = "${android.androidsdk}/libexec/android-sdk";
in
pkgs.mkShell {
  packages = [ pkgs.jdk17_headless pkgs.gradle pkgs.android-tools ];
  ANDROID_HOME = sdk;
  ANDROID_SDK_ROOT = sdk;
  JAVA_HOME = pkgs.jdk17_headless;
  # Gradle downloads its own aapt2 from Maven; that binary does not run on NixOS,
  # so point it at the SDK's patched one.
  GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/35.0.0/aapt2";
}
