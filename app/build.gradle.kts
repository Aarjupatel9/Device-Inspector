/*
 * This file declares the application's dependencies.
 * Location: app/build.gradle.kts
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Add the Compose Compiler plugin, which is now required.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mhk.deviceinspector"
    compileSdk = 34 // Updated to latest stable SDK

    defaultConfig {
        applicationId = "com.example.deviceinspector"
        minSdk = 23
        targetSdk = 34 // Updated to latest stable SDK
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // Updated to Java 11
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        // Updated to JVM target 11
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    // The composeOptions block is no longer needed when using the plugin.
    // The Compose BOM manages the compiler version.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android & Jetpack - Updated to latest versions
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Jetpack Compose - BOM (Bill of Materials) - Updated to latest version
    // The BOM ensures that all Compose libraries are on compatible versions.
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation for Compose
    implementation("androidx.navigation:navigation-compose:2.7.7") // This is still the latest stable

    // Accompanist for system UI control (status bar color)
    // NOTE: Accompanist is largely deprecated. This is the latest version, but consider
    // migrating to the official APIs built into Compose Foundation.
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")
    implementation("com.google.accompanist:accompanist-drawablepainter:0.34.0")

    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // Testing - Updated to latest versions
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
