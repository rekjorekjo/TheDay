package io.github.thedayapp.update

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import io.github.thedayapp.BuildConfig
import io.github.thedayapp.R
import io.github.thedayapp.notification.UpdateNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class AppUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = UpdatePreferences(appContext)
    private val manifestClient = ReleaseManifestClient()
    private val githubClient = GitHubReleaseClient()
    private val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    companion object {
        private const val TAG = "TheDayUpdate"
    }

    init {
        clearInstalledUpdateIfNeeded()
    }

    private fun clearInstalledUpdateIfNeeded() {
        val pendingVersionName = preferences.pendingVersionName ?: return

        val currentVersion = parseSemanticVersion(BuildConfig.VERSION_NAME) ?: return
        val pendingVersion = parseSemanticVersion(pendingVersionName) ?: return

        val versionComparison = compareSemanticVersions(currentVersion, pendingVersion)
        if (versionComparison < 0) {
            return
        }

        val isPendingClassicToGlassReplacement =
            BuildConfig.EDITION == "classic" &&
                preferences.pendingTargetEdition == "glass" &&
                versionComparison == 0
        if (isPendingClassicToGlassReplacement) {
            return
        }

        clearObsoleteUpdate()
    }

    private fun clearObsoleteUpdate() {
        val downloadId = preferences.pendingDownloadId
        val assetName = preferences.pendingAssetName

        if (downloadId != null) {
            runCatching {
                downloadManager.remove(downloadId)
            }
        }

        val downloadDirectory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

        if (downloadDirectory != null && assetName != null) {
            val apkFile = File(downloadDirectory, assetName)

            if (apkFile.exists() && !apkFile.delete()) {
                Log.w(TAG, "Failed to delete obsolete update file")
            }
        }

        preferences.clearPendingUpdate()
        preferences.pendingInstallPermission = false

        UpdateNotifier.cancelUpdateNotifications(appContext)

        Log.i(TAG, "Cleared installed or obsolete update")
    }

    private fun markDownloadFailed(downloadId: Long?) {
        if (downloadId != null) {
            runCatching {
                downloadManager.remove(downloadId)
            }
        }

        preferences.clearPendingUpdate()
        preferences.downloadFailed = true
    }

    private fun parseSemanticVersion(value: String): Triple<Int, Int, Int>? {
        if (!value.matches(Regex("^\\d+\\.\\d+\\.\\d+$"))) {
            return null
        }

        val parts = value.split(".")

        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts[2].toIntOrNull() ?: return null

        return Triple(major, minor, patch)
    }

    private fun compareSemanticVersions(v1: Triple<Int, Int, Int>, v2: Triple<Int, Int, Int>): Int {
        val majorCompare = v1.first.compareTo(v2.first)
        if (majorCompare != 0) return majorCompare

        val minorCompare = v1.second.compareTo(v2.second)
        if (minorCompare != 0) return minorCompare

        return v1.third.compareTo(v2.third)
    }

    suspend fun checkForUpdate(): UpdateCheckResult =
        checkForEdition(
            targetEdition = UpdateChannel.currentEdition,
            allowSameVersion = false,
        )

    /**
     * Classic -> Glass is an edition replacement, not a data import. Glass uses
     * the same application id and signing key, so Android keeps the existing
     * app-private data. Equal version codes are valid for an Android update and
     * are intentionally accepted here so a Classic 3.0.0 build can switch to
     * the Glass 3.0.0 build without manufacturing a fake version bump.
     */
    suspend fun checkForGlassUpgrade(): UpdateCheckResult {
        if (BuildConfig.EDITION == "glass") return UpdateCheckResult.UpToDate
        return checkForEdition(
            targetEdition = UpdateEdition.GLASS,
            allowSameVersion = true,
        )
    }

    private suspend fun checkForEdition(
        targetEdition: UpdateEdition,
        allowSameVersion: Boolean,
    ): UpdateCheckResult {
        return try {
            val release = try {
                manifestClient.fetchLatestRelease(targetEdition)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(TAG, "Manifest failed, falling back to GitHub API", exception)
                githubClient.fetchLatestRelease(targetEdition)
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val currentSemanticVersion = parseSemanticVersion(currentVersion)
            val remoteSemanticVersion = parseSemanticVersion(release.versionName)

            if (currentSemanticVersion == null || remoteSemanticVersion == null) {
                Log.w(TAG, "Invalid semantic version")
                return UpdateCheckResult.CheckFailed
            }

            val versionComparison = compareSemanticVersions(remoteSemanticVersion, currentSemanticVersion)

            val hasUpdate = when (release.source) {
                UpdateSource.MANIFEST -> {
                    val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
                    val remoteVersionCode = release.versionCode ?: return UpdateCheckResult.CheckFailed
                    val versionCodeOk = if (allowSameVersion) {
                        remoteVersionCode >= currentVersionCode
                    } else {
                        remoteVersionCode > currentVersionCode
                    }
                    versionCodeOk && versionComparison >= 0
                }
                UpdateSource.GITHUB_API -> {
                    if (allowSameVersion) versionComparison >= 0 else versionComparison > 0
                }
            }

            if (hasUpdate) {
                UpdateCheckResult.UpdateAvailable(release)
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "Update check failed", exception)
            UpdateCheckResult.CheckFailed
        }
    }

    fun startDownload(release: GitHubRelease): Boolean {
        val existingDownloadId = preferences.pendingDownloadId
        val existingAssetName = preferences.pendingAssetName
        val downloadFailed = preferences.downloadFailed

        if (existingDownloadId != null && existingAssetName == release.apkAssetName) {
            val status = currentStatus()
            if (status.state == UpdateDownloadState.WAITING ||
                status.state == UpdateDownloadState.DOWNLOADING ||
                status.state == UpdateDownloadState.VERIFYING ||
                status.state == UpdateDownloadState.READY) {
                Log.i(TAG, "Same version download already in progress")
                return true
            }

            if (downloadFailed) {
                runCatching {
                    downloadManager.remove(existingDownloadId)
                }
                preferences.clearPendingUpdate()
            }
        } else if (existingDownloadId != null) {
            runCatching {
                downloadManager.remove(existingDownloadId)
            }

            val oldDownloadDirectory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

            if (oldDownloadDirectory != null && existingAssetName != null) {
                val oldApk = File(oldDownloadDirectory, existingAssetName)

                if (oldApk.exists() && !oldApk.delete()) {
                    Log.w(TAG, "Failed to delete old update file")
                }
            }

            preferences.clearPendingUpdate()
        }

        val downloadDirectory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDirectory == null) {
            Log.w(TAG, "Download directory unavailable")
            preferences.downloadFailed = true
            return false
        }

        val targetFile = File(downloadDirectory, release.apkAssetName)
        if (targetFile.exists()) {
            if (!targetFile.delete()) {
                Log.w(TAG, "Failed to delete existing file")
                preferences.downloadFailed = true
                return false
            }
        }

        preferences.clearPendingUpdate()
        preferences.downloadFailed = false
        preferences.verified = false
        preferences.pendingAssetName = release.apkAssetName
        preferences.pendingTargetEdition =
            if (release.apkAssetName.startsWith("TheDay-Glass-")) "glass" else "classic"

        val request = DownloadManager.Request(Uri.parse(release.apkDownloadUrl))
            .setTitle("The Day ${release.versionName}")
            .setDescription(appContext.getString(R.string.app_name))
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, release.apkAssetName)
            .setAllowedOverMetered(!preferences.wifiOnly)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")

        val downloadId = try {
            downloadManager.enqueue(request)
        } catch (exception: SecurityException) {
            Log.w(TAG, "Failed to enqueue update", exception)
            preferences.downloadFailed = true
            return false
        } catch (exception: IllegalArgumentException) {
            Log.w(TAG, "Failed to enqueue update", exception)
            preferences.downloadFailed = true
            return false
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Failed to enqueue update", exception)
            preferences.downloadFailed = true
            return false
        }

        preferences.pendingDownloadId = downloadId
        preferences.pendingVersionName = release.versionName
        preferences.pendingTagName = release.tagName
        preferences.pendingSha256 = release.sha256
        preferences.pendingApkSize = release.apkSize

        Log.i(TAG, "Download started: ${release.versionName}")
        return true
    }

    fun currentStatus(): UpdateDownloadStatus {
        clearInstalledUpdateIfNeeded()

        val downloadId = preferences.pendingDownloadId
        val downloadFailed = preferences.downloadFailed

        if (downloadId == null) {
            return UpdateDownloadStatus(
                state = if (downloadFailed) UpdateDownloadState.FAILED else UpdateDownloadState.NONE,
            )
        }

        val cursor: Cursor? = downloadManager.query(
            android.app.DownloadManager.Query().setFilterById(downloadId)
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val statusIndex = it.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                val bytesSoFarIndex = it.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalBytesIndex = it.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                if (statusIndex >= 0) {
                    val status = it.getInt(statusIndex)
                    val bytesSoFar = if (bytesSoFarIndex >= 0) it.getLong(bytesSoFarIndex) else 0L
                    val totalBytes = if (totalBytesIndex >= 0) it.getLong(totalBytesIndex) else 0L

                    val progressPercent = if (totalBytes > 0) {
                        ((bytesSoFar * 100) / totalBytes).toInt()
                    } else {
                        null
                    }

                    return when (status) {
                        android.app.DownloadManager.STATUS_PENDING -> UpdateDownloadStatus(
                            state = UpdateDownloadState.WAITING,
                            versionName = preferences.pendingVersionName,
                        )
                        android.app.DownloadManager.STATUS_PAUSED -> UpdateDownloadStatus(
                            state = UpdateDownloadState.WAITING,
                            versionName = preferences.pendingVersionName,
                        )
                        android.app.DownloadManager.STATUS_RUNNING -> UpdateDownloadStatus(
                            state = UpdateDownloadState.DOWNLOADING,
                            versionName = preferences.pendingVersionName,
                            progressPercent = progressPercent,
                        )
                        android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                            if (preferences.verified) {
                                UpdateDownloadStatus(
                                    state = UpdateDownloadState.READY,
                                    versionName = preferences.pendingVersionName,
                                )
                            } else {
                                UpdateDownloadStatus(
                                    state = UpdateDownloadState.VERIFYING,
                                    versionName = preferences.pendingVersionName,
                                )
                            }
                        }
                        android.app.DownloadManager.STATUS_FAILED -> {
                            preferences.downloadFailed = true
                            UpdateDownloadStatus(
                                state = UpdateDownloadState.FAILED,
                                versionName = preferences.pendingVersionName,
                            )
                        }
                        else -> UpdateDownloadStatus(state = UpdateDownloadState.NONE)
                    }
                }
            }
        }

        preferences.clearPendingUpdate()
        return UpdateDownloadStatus(
            state = if (downloadFailed) UpdateDownloadState.FAILED else UpdateDownloadState.NONE,
        )
    }

    suspend fun verifyDownload(downloadId: Long): Boolean = withContext(Dispatchers.IO) {
        val verificationStartedAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "APK verification started")

        val pendingId = preferences.pendingDownloadId
        if (downloadId != pendingId) {
            Log.w(TAG, "Download ID mismatch")
            return@withContext false
        }

        val cursor: Cursor? = downloadManager.query(
            android.app.DownloadManager.Query().setFilterById(downloadId)
        )

        var status: Int = -1
        var totalSize: Long = 0

        cursor?.use {
            if (it.moveToFirst()) {
                val statusIndex = it.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                val totalBytesIndex = it.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                if (statusIndex >= 0) {
                    status = it.getInt(statusIndex)
                }
                if (totalBytesIndex >= 0) {
                    totalSize = it.getLong(totalBytesIndex)
                }
            }
        }

        if (status != android.app.DownloadManager.STATUS_SUCCESSFUL) {
            Log.w(TAG, "Download not successful: $status")
            markDownloadFailed(downloadId)
            return@withContext false
        }

        val expectedSize = preferences.pendingApkSize
        if (totalSize != expectedSize) {
            Log.w(TAG, "Size mismatch: $totalSize != $expectedSize")
            markDownloadFailed(downloadId)
            return@withContext false
        }

        val expectedSha256 = preferences.pendingSha256
        if (expectedSha256 == null) {
            Log.w(TAG, "SHA-256 is null")
            markDownloadFailed(downloadId)
            return@withContext false
        }

        val parcelFileDescriptor = try {
            downloadManager.openDownloadedFile(downloadId)
        } catch (exception: Exception) {
            Log.w(TAG, "Cannot open APK file", exception)
            markDownloadFailed(downloadId)
            return@withContext false
        }

        try {
            ParcelFileDescriptor.AutoCloseInputStream(parcelFileDescriptor).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)

                var bytes = input.read(buffer)
                while (bytes > 0) {
                    digest.update(buffer, 0, bytes)
                    bytes = input.read(buffer)
                }

                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }

                if (actualSha256 != expectedSha256) {
                    Log.w(TAG, "SHA-256 mismatch")
                    markDownloadFailed(downloadId)
                    return@withContext false
                }

                preferences.verified = true
                preferences.downloadFailed = false
                Log.i(TAG, "APK verification completed in ${SystemClock.elapsedRealtime() - verificationStartedAt} ms")
                true
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "Verification failed", exception)
            markDownloadFailed(downloadId)
            false
        }
    }

    suspend fun verifyPendingDownloadIfNeeded(): Boolean? {
        val status = currentStatus()

        if (status.state == UpdateDownloadState.READY) {
            return true
        }

        if (status.state != UpdateDownloadState.VERIFYING) {
            return null
        }

        val downloadId = preferences.pendingDownloadId ?: return false

        return verifyDownload(downloadId)
    }

    fun requestInstall(activity: Activity): InstallLaunchResult {
        val status = currentStatus()
        if (status.state != UpdateDownloadState.READY) {
            return InstallLaunchResult.NO_VERIFIED_UPDATE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!appContext.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${appContext.packageName}"),
                )

                return try {
                    activity.startActivity(settingsIntent)
                    preferences.pendingInstallPermission = true
                    InstallLaunchResult.PERMISSION_REQUIRED
                } catch (exception: ActivityNotFoundException) {
                    preferences.pendingInstallPermission = false
                    Log.w(TAG, "Unknown-source settings unavailable", exception)
                    InstallLaunchResult.INSTALLER_UNAVAILABLE
                } catch (exception: SecurityException) {
                    preferences.pendingInstallPermission = false
                    Log.w(TAG, "Cannot open unknown-source settings", exception)
                    InstallLaunchResult.FAILED
                }
            }
        }

        preferences.pendingInstallPermission = false

        val downloadId = preferences.pendingDownloadId ?: return InstallLaunchResult.FAILED
        val apkUri = downloadManager.getUriForDownloadedFile(downloadId)

        if (apkUri == null) {
            return InstallLaunchResult.FAILED
        }

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val resolveInfo = appContext.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfo == null) {
            return InstallLaunchResult.INSTALLER_UNAVAILABLE
        }

        return try {
            activity.startActivity(intent)
            InstallLaunchResult.LAUNCHED
        } catch (exception: ActivityNotFoundException) {
            Log.w(TAG, "Installer not found", exception)
            InstallLaunchResult.INSTALLER_UNAVAILABLE
        } catch (exception: SecurityException) {
            Log.w(TAG, "Security exception", exception)
            InstallLaunchResult.FAILED
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Install failed", exception)
            InstallLaunchResult.FAILED
        }
    }
}