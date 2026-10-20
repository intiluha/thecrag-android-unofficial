apk := "app/build/outputs/apk/debug/app-debug.apk"

# Install the debug build ("theCrag unofficial", org.thecragunofficial.android.debug) on the USB-connected phone and launch it
install:
    adb install -r {{apk}}
    adb shell am start -n org.thecragunofficial.android.debug/org.thecragunofficial.android.ui.MainActivity

# Build the debug APK (needs nix; pulls JDK, Gradle and the Android SDK)
build:
    nix-shell --run 'gradle --no-daemon assembleDebug'

# Run unit tests
test:
    nix-shell --run 'gradle --no-daemon testDebugUnitTest'

# Delete build outputs and Gradle/Kotlin caches (everything `just build` regenerates)
clean:
    rm -rf .gradle .kotlin build app/build
