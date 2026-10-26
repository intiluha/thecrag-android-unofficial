plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing. The PKCS12 keystore ./release.p12 is committed (key alias "thecrag"); only its
// password is secret. `just release` prompts for it and passes it in as KEYSTORE_PASSWORD, which is
// read only when a release task was requested, so debug builds, tests and `gradle help` never need it.
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
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
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
