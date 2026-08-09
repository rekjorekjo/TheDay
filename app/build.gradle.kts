import java.util.Properties

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionProperties =
    Properties().apply {
        rootProject
            .file("version.properties")
            .inputStream()
            .use { input ->
                load(input)
            }
    }

val appVersionCode =
    versionProperties
        .getProperty("versionCode")
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
        ?: error(
            "version.properties: " +
                "versionCode must be a positive integer",
        )

val appVersionName =
    versionProperties
        .getProperty("versionName")
        ?.takeIf {
            it.matches(
                Regex(
                    "^\\d+\\.\\d+\\.\\d+$",
                ),
            )
        }
        ?: error(
            "version.properties: " +
                "versionName must use X.Y.Z",
        )

val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties()
val hasReleaseSigning = signingPropertiesFile.isFile

if (hasReleaseSigning) {
    signingPropertiesFile.inputStream().use { input ->
        signingProperties.load(input)
    }
}

fun requiredSigningProperty(name: String): String =
    signingProperties
        .getProperty(name)
        ?.takeIf { it.isNotBlank() }
        ?: error("keystore.properties: missing $name")

android {
    namespace = "io.github.thedayapp"

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(requiredSigningProperty("storeFile"))
                storePassword = requiredSigningProperty("storePassword")
                keyAlias = requiredSigningProperty("keyAlias")
                keyPassword = requiredSigningProperty("keyPassword")
            }
        }
    }
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.thedayapp"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    flavorDimensions += "experience"
    productFlavors {
        create("classic") {
            dimension = "experience"
            buildConfigField("String", "EDITION", "\"classic\"")
        }
        create("glass") {
            dimension = "experience"
            // Glass intentionally shares the Classic application id. Installing a
            // Glass APK signed with the same release key replaces Classic in-place
            // and keeps the existing app-private events, images, settings and widgets.
            buildConfigField("String", "EDITION", "\"glass\"")
            // Flutter ships Android native runtime libraries for these ABIs.
            // Keep the restriction on the Glass flavor only so Classic remains
            // unaffected by the Flutter embedding.
            ndk {
                abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86_64")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.github.yalantis:ucrop:2.2.11")

    // Flutter is packaged only into the Glass flavor. Classic stays on the
    // existing Compose stack and does not pull the Flutter engine into its APK.
    add("glassImplementation", project(":flutter"))

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
