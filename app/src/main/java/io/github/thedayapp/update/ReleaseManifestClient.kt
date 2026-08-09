package io.github.thedayapp.update

import android.util.Log
import io.github.thedayapp.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ReleaseManifestClient {
    companion object {
        private const val TAG = "TheDayUpdate"
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 15000
        private const val MAX_MANIFEST_SIZE = 65536 // 64 KiB
        private const val MAX_RELEASE_NOTES_SIZE = 20000
    }

    private fun readLimited(connection: HttpURLConnection, maxBytes: Int): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var totalRead = 0

        connection.inputStream.use { input ->
            var bytes = input.read(buffer)
            while (bytes > 0) {
                totalRead += bytes
                if (totalRead > maxBytes) {
                    throw Exception("Manifest too large")
                }
                outputStream.write(buffer, 0, bytes)
                bytes = input.read(buffer)
            }
        }

        return outputStream.toByteArray()
    }

    suspend fun fetchLatestRelease(
        targetEdition: UpdateEdition = UpdateChannel.currentEdition,
    ): GitHubRelease = withContext(Dispatchers.IO) {
        val connection = URL(UpdateChannel.manifestUrl(targetEdition)).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "TheDay/${BuildConfig.VERSION_NAME}")

        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP $responseCode")
            }

            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_MANIFEST_SIZE) {
                throw Exception("Manifest too large")
            }

            val bytes = readLimited(connection, MAX_MANIFEST_SIZE)

            if (bytes.isEmpty()) {
                throw Exception("Empty manifest")
            }

            val json = JSONObject(String(bytes, Charsets.UTF_8))

            val schemaVersion = json.getInt("schemaVersion")
            if (schemaVersion != 1) {
                throw Exception("Unsupported schema version: $schemaVersion")
            }

            UpdateChannel.validateManifestEdition(json.optString("edition", ""), targetEdition)

            val tagName = json.getString("tagName")
            if (!tagName.matches(Regex("^v\\d+\\.\\d+\\.\\d+$"))) {
                throw Exception("Invalid tagName: $tagName")
            }

            val versionName = json.getString("versionName")
            if (!versionName.matches(Regex("^\\d+\\.\\d+\\.\\d+$"))) {
                throw Exception("Invalid versionName: $versionName")
            }

            if (tagName != "v$versionName") {
                throw Exception("tagName and versionName mismatch")
            }

            val versionCode = json.getLong("versionCode")
            if (versionCode <= 0) {
                throw Exception("Invalid versionCode: $versionCode")
            }

            val releaseNotes = json.optString("releaseNotes", "")
                .take(MAX_RELEASE_NOTES_SIZE)

            val apk = json.getJSONObject("apk")
            val apkName = apk.getString("name")

            val expectedApkName = UpdateChannel.apkAssetName(tagName, targetEdition)
            if (apkName != expectedApkName) {
                throw Exception("Invalid APK name: $apkName")
            }

            val apkUrl = apk.getString("url")
            val apkSize = apk.getLong("size")

            if (apkSize <= 0 || apkSize > 200 * 1024 * 1024) {
                throw Exception("Invalid APK size: $apkSize")
            }

            val sha256 = apk.getString("sha256").lowercase()
            if (!sha256.matches(Regex("^[a-f0-9]{64}$"))) {
                throw Exception("Invalid SHA-256: $sha256")
            }

            val apkUrlObj = URL(apkUrl)

            if (apkUrlObj.protocol != "https") {
                throw Exception("APK URL must use HTTPS")
            }

            if (!apkUrlObj.host.equals("github.com", ignoreCase = true)) {
                throw Exception("APK URL host must be github.com")
            }

            if (apkUrlObj.query != null) {
                throw Exception("APK URL must not have query")
            }

            if (apkUrlObj.ref != null) {
                throw Exception("APK URL must not have fragment")
            }

            if (apkUrlObj.userInfo != null) {
                throw Exception("APK URL must not have userInfo")
            }

            if (apkUrlObj.port != -1) {
                throw Exception("APK URL must not have explicit port")
            }

            val expectedPath = "/rekjorekjo/TheDay/releases/download/$tagName/$apkName"
            if (apkUrlObj.path != expectedPath) {
                throw Exception("APK URL path mismatch")
            }

            GitHubRelease(
                source = UpdateSource.MANIFEST,
                tagName = tagName,
                versionName = versionName,
                versionCode = versionCode,
                releaseNotes = releaseNotes,
                apkAssetName = apkName,
                apkDownloadUrl = apkUrl,
                apkSize = apkSize,
                sha256 = sha256,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "Manifest fetch failed", exception)
            throw exception
        } finally {
            connection.disconnect()
        }
    }
}