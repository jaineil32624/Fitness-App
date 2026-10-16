package com.repwise.updates

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(val tag: String, val apkUrl: String, val pageUrl: String)

class GitHubReleaseClient(private val context: Context) {
    fun latest(owner: String, repo: String): AppRelease {
        require(owner.matches(Regex("[A-Za-z0-9_.-]+")) && repo.matches(Regex("[A-Za-z0-9_.-]+"))) {
            "Enter a valid GitHub owner and repository."
        }
        val connection = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Repwise-Android")
            if (connection.responseCode !in 200..299) {
                error("GitHub returned HTTP ${connection.responseCode}. Check the repository and try again.")
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = release.optJSONArray("assets") ?: error("This GitHub release has no APK assets.")
            val apk = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?: error("No APK was attached to the latest GitHub release.")
            return AppRelease(
                tag = release.optString("tag_name", "Latest release"),
                apkUrl = apk.getString("browser_download_url"),
                pageUrl = release.optString("html_url"),
            )
        } finally {
            connection.disconnect()
        }
    }

    fun download(release: AppRelease): Long {
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("Repwise ${release.tag}")
            .setDescription("Downloading release APK")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "repwise-${release.tag}.apk")
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }
}
