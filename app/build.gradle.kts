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

android {
    namespace = "io.github.thedayapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.thedayapp"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
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
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
