package io.github.thedayapp.update

enum class UpdateSource {
    MANIFEST,
    GITHUB_API,
}

data class GitHubRelease(
    val source: UpdateSource,
    val tagName: String,
    val versionName: String,
    val versionCode: Long?,
    val releaseNotes: String,
    val apkAssetName: String,
    val apkDownloadUrl: String,
    val apkSize: Long,
    val sha256: String,
)

enum class UpdateDownloadState {
    NONE,
    WAITING,
    DOWNLOADING,
    VERIFYING,
    READY,
    FAILED,
}

data class UpdateDownloadStatus(
    val state: UpdateDownloadState,
    val versionName: String? = null,
    val progressPercent: Int? = null,
)

enum class InstallLaunchResult {
    LAUNCHED,
    PERMISSION_REQUIRED,
    NO_VERIFIED_UPDATE,
    INSTALLER_UNAVAILABLE,
    FAILED,
}

sealed interface UpdateCheckResult {
    data class UpdateAvailable(
        val release: GitHubRelease,
    ) : UpdateCheckResult

    data object UpToDate : UpdateCheckResult

    data object CheckFailed : UpdateCheckResult
}