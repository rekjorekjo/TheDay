pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    val flutterStorageUrl =
        System.getenv("FLUTTER_STORAGE_BASE_URL")
            ?: "https://storage.googleapis.com"

    repositories {
        google()
        mavenCentral()
        maven("$flutterStorageUrl/download.flutter.io")
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.yalantis")
            }
        }
    }
}

rootProject.name = "TheDay"
include(":app")

val flutterInclude =
    settingsDir.resolve("glass_flutter/.android/include_flutter.groovy")

if (!flutterInclude.isFile) {
    throw GradleException(
        "Flutter Glass module is not bootstrapped. Run scripts/setup_flutter_glass.ps1 " +
            "from the repository root, then sync Gradle again.",
    )
}

apply(from = flutterInclude)
