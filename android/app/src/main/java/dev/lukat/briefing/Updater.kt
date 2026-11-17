package dev.lukat.briefing

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Downloads the newest release APK and hands it to the system installer.
 *
 * The APK is signed with the same key as the running build, so Android accepts it
 * as an update in place. Nothing is installed silently: the package installer still
 * shows its confirmation screen.
 */
object Updater {
    private val http = OkHttpClient()

    suspend fun downloadAndInstall(context: Context, url: String): String? = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "update.apk")
        try {
            http.newCall(Request.Builder().url(url).header("User-Agent", "briefing-app").build())
                .execute().use { response ->
                    if (!response.isSuccessful) return@withContext "HTTP ${response.code}"
                    val body = response.body ?: return@withContext "empty body"
                    // a torn download would fail the signature check anyway; keep it atomic
                    val temp = File(context.cacheDir, "update.apk.part")
                    temp.outputStream().use { out -> body.byteStream().copyTo(out) }
                    if (target.exists()) target.delete()
                    if (!temp.renameTo(target)) return@withContext "could not move the download"
                }
        } catch (e: Exception) {
            return@withContext e.message ?: e.javaClass.simpleName
        }
        withContext(Dispatchers.Main) { install(context, target) }
        null
    }

    private fun install(context: Context, file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
