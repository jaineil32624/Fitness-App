package com.repwise.updates

import android.app.DownloadManager
import android.content.Context
import androidx.core.net.toUri
import android.os.Environment
import com.repwise.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(val tag: String, val apkUrl: String, val pageUrl: String) {
    /** Tag reduced to characters that are safe in a file name. */
    val safeTag: String get() = tag.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40).ifBlank { "latest" }
}

class GitHubReleaseClient(private val context: Context) {
    fun latest(owner: String, repo: String): AppRelease {
        require(owner.matches(NAME) && repo.matches(NAME)) { "Enter a valid GitHub owner and repository." }
        val connection = URL("https://api.github.com/repos/$owner/$repo/releases/latest").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Repwise-Android/${BuildConfig.VERSION_NAME}")
            when (val code = connection.responseCode) {
                in 200..299 -> Unit
                404 -> error("No published release was found for $owner/$repo.")
                403, 429 -> error("GitHub is rate-limiting requests. Try again in a few minutes.")
                else -> error("GitHub returned HTTP $code. Check the repository and try again.")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(body)
            val assets = release.optJSONArray("assets") ?: error("This GitHub release has no APK assets.")
            val apk = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?: error("No APK was attached to the latest GitHub release.")
            val url = apk.getString("browser_download_url")
            // Only download over HTTPS from GitHub itself, whatever the API response claims.
            val host = url.toUri().host.orEmpty()
            require(url.startsWith("https://") && (host == "github.com" || host.endsWith(".githubusercontent.com"))) {
                "The release APK isn't hosted on GitHub, so it won't be downloaded."
            }
            return AppRelease(tag = release.optString("tag_name", "latest"), apkUrl = url, pageUrl = release.optString("html_url"))
        } finally {
            connection.disconnect()
        }
    }

    fun download(release: AppRelease): Long {
        val request = DownloadManager.Request(release.apkUrl.toUri())
            .setTitle("Repwise ${release.safeTag}")
            .setDescription("Downloading release APK")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "repwise-${release.safeTag}.apk")
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }

    private companion object {
        val NAME = Regex("[A-Za-z0-9_.-]{1,100}")
    }
}
