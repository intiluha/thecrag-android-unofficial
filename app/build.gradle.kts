plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing. The PKCS12 keystore ./release.p12 is committed (key alias "thecrag"); only its
// password is secret. `just release` prompts for it and passes it in as KEYSTORE_PASSWORD, which is
// read only when a release task was requested, so debug builds, tests and `gradle help` never need it.
// Debug signing. ./debug.p12 is committed too (alias "androiddebugkey", password "android", nothing
// secret) so every machine signs the .dev build with the same key; with the plugin's default
// per-user ~/.android/debug.keystore each machine's dev build refuses to install over another's.
// Version. `just publish vX.Y.Z` passes the tag as -PappVersion and the APK is stamped X.Y.Z with
// versionCode X*1000000 + Y*10000 + Z, so every release is a real upgrade for Android and for
// Obtainium, which compares the installed versionName with the release tag. Any other build
// (debug, a bare `just release`) is named after `git describe`, versionCode from its tag part or 1.
val appVersion: String = (project.findProperty("appVersion") as String?)?.trim()?.removePrefix("v")?.takeIf { it.isNotEmpty() }
    ?: runCatching {
        providers.exec { commandLine("git", "describe", "--tags", "--always", "--dirty") }.standardOutput.asText.get().trim().removePrefix("v")
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: "0.0.0"
val appVersionCode: Int = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(appVersion)
    ?.destructured?.let { (major, minor, patch) -> major.toInt() * 1_000_000 + minor.toInt() * 10_000 + patch.toInt() } ?: 1

val keystoreFile = rootProject.file("release.p12")
val signingTaskNames = setOf("assembleRelease", "installRelease", "bundleRelease")
val needsSigning = gradle.startParameter.taskNames.any { it.substringAfterLast(':') in signingTaskNames }

fun keystorePassword(): String =
    providers.environmentVariable("KEYSTORE_PASSWORD").orNull?.takeIf { it.isNotEmpty() }
        ?: error("KEYSTORE_PASSWORD is not set: run `just release`, which prompts for the release.p12 password.")

android {
    namespace = "xyz.intiluha.thecrag"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "xyz.intiluha.thecrag"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.p12")
            storeType = "PKCS12"
            keyAlias = "androiddebugkey"
            storePassword = "android"
            keyPassword = "android"
        }
        // Only configured when the keystore is present, so a checkout without it still builds debug.
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storeType = "PKCS12"
                keyAlias = "thecrag"
                if (needsSigning) {
                    val pw = keystorePassword()
                    storePassword = pw
                    keyPassword = pw
                }
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
        }
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
    implementation(libs.maplibre)
    implementation(libs.androidx.work)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
