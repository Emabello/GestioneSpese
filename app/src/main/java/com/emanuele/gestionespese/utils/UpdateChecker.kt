package com.emanuele.gestionespese.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private data class GithubRelease(
    @SerializedName("tag_name") val tagName: String,
    val assets: List<GithubAsset>
)

private data class GithubAsset(
    val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String
)

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean
)

object UpdateChecker {

    private const val RELEASES_API =
        "https://api.github.com/repos/Emabello/GestioneSpese/releases/latest"

    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_API)
                .header("Accept", "application/vnd.github+json")
                .build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string() ?: return@withContext null
            }
            val release = gson.fromJson(body, GithubRelease::class.java)

            // tag_name format: "v1.0.8-abc1234" or "v1.0.8"
            val latestVersion = release.tagName
                .removePrefix("v")
                .substringBefore("-")

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: return@withContext null

            val isNewer = isVersionNewer(latestVersion, currentVersion)

            UpdateInfo(
                latestVersion = latestVersion,
                downloadUrl = apkAsset.downloadUrl,
                isUpdateAvailable = isNewer
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        fileName: String,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(downloadUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext
            val body = response.body ?: return@withContext
            val contentLength = body.contentLength()
            val apkFile = File(context.getExternalFilesDir(null), fileName)

            apkFile.outputStream().use { out ->
                var bytesCopied = 0L
                val buffer = ByteArray(8 * 1024)
                val input = body.byteStream()
                var bytes = input.read(buffer)
                while (bytes >= 0) {
                    out.write(buffer, 0, bytes)
                    bytesCopied += bytes
                    if (contentLength > 0) {
                        val progress = (bytesCopied * 100 / contentLength).toInt()
                        onProgress(progress)
                    }
                    bytes = input.read(buffer)
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(install)
        }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}
