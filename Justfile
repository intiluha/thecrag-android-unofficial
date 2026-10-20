apk := "app/build/outputs/apk/debug/app-debug.apk"
release_apk := "app/build/outputs/apk/release/app-release.apk"
pkg := "org.thecragunofficial.android"

# Install the debug build ("theCrag unofficial", org.thecragunofficial.android.debug) on the USB-connected phone and launch it
install:
    adb install -r {{apk}}
    adb shell am start -n {{pkg}}.debug/{{pkg}}.ui.MainActivity

# Build the debug APK (needs nix; pulls JDK, Gradle and the Android SDK)
build:
    nix-shell --run 'gradle --no-daemon assembleDebug'

# Build the minified release APK. It is signed with the debug keystore: fine for your own phone, not for distribution
release:
    nix-shell --run 'gradle --no-daemon assembleRelease'

# Install the release build (org.thecragunofficial.android, coexists with the debug one) and launch it
install-release:
    adb install -r {{release_apk}}
    adb shell am start -n {{pkg}}/{{pkg}}.ui.MainActivity

# Run unit tests
test:
    nix-shell --run 'gradle --no-daemon testDebugUnitTest'

# Delete build outputs and Gradle/Kotlin caches (everything `just build` regenerates)
clean:
    rm -rf .gradle .kotlin build app/build
