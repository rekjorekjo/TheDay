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

class GitHubReleaseClient {
    companion object {
        private const val TAG = "TheDayUpdate"
        private const val API_URL = "https://api.github.com/repos/rekjorekjo/TheDay/releases/latest"
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 15000
        private const val MAX_RESPONSE_SIZE = 1048576 // 1 MiB
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
                    throw Exception("Response too large")
                }
                outputStream.write(buffer, 0, bytes)
                bytes = input.read(buffer)
            }
        }

        return outputStream.toByteArray()
    }

    suspend fun fetchLatestRelease(): GitHubRelease = withContext(Dispatchers.IO) {
        val connection = URL(API_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
        connection.setRequestProperty("User-Agent", "TheDay/${BuildConfig.VERSION_NAME}")

        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP $responseCode")
            }

            val bytes = readLimited(connection, MAX_RESPONSE_SIZE)

            val json = JSONObject(String(bytes, Charsets.UTF_8))

            if (json.optBoolean("draft", false)) {
                throw Exception("Release is draft")
            }

            if (json.optBoolean("prerelease", false)) {
                throw Exception("Release is prerelease")
            }

            val tagName = json.getString("tag_name")
            if (!tagName.matches(Regex("^v\\d+\\.\\d+\\.\\d+$"))) {
                throw Exception("Invalid tagName: $tagName")
            }

            val versionName = tagName.substring(1)
            val releaseNotes = json.optString("body", "").take(20000)

            val assets = json.getJSONArray("assets")
            var apkCount = 0
            var apkAsset: JSONObject? = null

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name == "TheDay-$tagName.apk") {
                    apkCount++
                    apkAsset = asset
                }
            }

            if (apkCount == 0) {
                throw Exception("APK asset not found")
            }

            if (apkCount > 1) {
                throw Exception("Multiple APK assets found")
            }

            val apkName = apkAsset!!.getString("name")
            val apkDownloadUrl = apkAsset.getString("browser_download_url")
            val apkSize = apkAsset.getLong("size")

            if (apkSize <= 0) {
                throw Exception("Invalid APK size")
            }

            val url = URL(apkDownloadUrl)

            if (url.protocol != "https") {
                throw Exception("APK URL must use HTTPS")
            }

            if (!url.host.equals("github.com", ignoreCase = true)) {
                throw Exception("APK URL host must be github.com")
            }

            if (url.query != null) {
                throw Exception("APK URL must not have query")
            }

            if (url.ref != null) {
                throw Exception("APK URL must not have fragment")
            }

            if (url.userInfo != null) {
                throw Exception("APK URL must not have userInfo")
            }

            if (url.port != -1) {
                throw Exception("APK URL must not have explicit port")
            }

            val expectedPath = "/rekjorekjo/TheDay/releases/download/$tagName/$apkName"
            if (url.path != expectedPath) {
                throw Exception("APK URL path mismatch")
            }

            val digest = apkAsset.optString("digest", "")
            if (!digest.startsWith("sha256:")) {
                throw Exception("Missing or invalid digest")
            }

            val sha256 = digest.substring(7).lowercase()
            if (!sha256.matches(Regex("^[a-f0-9]{64}$"))) {
                throw Exception("Invalid SHA-256 digest")
            }

            GitHubRelease(
                source = UpdateSource.GITHUB_API,
                tagName = tagName,
                versionName = versionName,
                versionCode = null,
                releaseNotes = releaseNotes,
                apkAssetName = apkName,
                apkDownloadUrl = apkDownloadUrl,
                apkSize = apkSize,
                sha256 = sha256,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w(TAG, "GitHub API fetch failed", exception)
            throw exception
        } finally {
            connection.disconnect()
        }
    }
}