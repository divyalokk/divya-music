package com.divyamusic.divyalokk

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ---------- 1. Data ----------
data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String
)

// ---------- 2. GitHub se latest release check ----------
object UpdateManager {

    private const val OWNER = "divyalokk"
    private const val REPO = "divya-music"

    /** Naya version mile toh UpdateInfo, warna null. */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
                .openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "DivyaMusic-Updater")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            if (conn.responseCode != 200) return@withContext null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })

            val latest = json.getString("tag_name").removePrefix("v")
            val current = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: "0"

            if (!isNewer(latest, current)) return@withContext null

            // .apk wala asset dhoondo
            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    apkUrl = a.getString("browser_download_url")
                    break
                }
            }
            if (apkUrl == null) return@withContext null

            UpdateInfo(latest, json.optString("body", ""), apkUrl)
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    // ---------- 3. Download + install ----------

    /**
     * true  -> download shuru ho gaya
     * false -> "Install unknown apps" permission chahiye, Settings khol di gayi
     */
    fun downloadAndInstall(context: Context, info: UpdateInfo): Boolean {
        val appContext = context.applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            appContext.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${appContext.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Toast.makeText(
                appContext,
                "Allow karke wapas aao aur dobara Update dabao",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val fileName = "update-${info.version}.apk"
        val file = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("DivyaMusic update ${info.version}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                appContext, Environment.DIRECTORY_DOWNLOADS, fileName
            )

        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)
        Toast.makeText(appContext, "Download shuru ho gaya...", Toast.LENGTH_SHORT).show()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return
                appContext.unregisterReceiver(this)

                // Download sach mein successful hua ya nahi
                val success = dm.query(DownloadManager.Query().setFilterById(downloadId))
                    ?.use { c ->
                        c.moveToFirst() && c.getInt(
                            c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        ) == DownloadManager.STATUS_SUCCESSFUL
                    } ?: false

                if (success && file.exists()) {
                    installApk(appContext, file)
                } else {
                    Toast.makeText(appContext, "Update download fail ho gaya", Toast.LENGTH_LONG).show()
                }
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
        return true
    }

    private fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

// ---------- 4. Compose UI ----------
/** MusicApp ke andar call hota hai. */
@Composable
fun UpdateChecker() {
    val context = LocalContext.current
    var update by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        update = UpdateManager.checkForUpdate(context)
    }

    update?.let { info ->
        AlertDialog(
            onDismissRequest = { update = null },
            title = { Text("Update available (v${info.version})") },
            text = { Text(info.notes.ifBlank { "Naya version available hai." }) },
            confirmButton = {
                TextButton(onClick = {
                    // Permission missing ho toh dialog khula rahe
                    if (UpdateManager.downloadAndInstall(context, info)) {
                        update = null
                    }
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { update = null }) { Text("Cancel") }
            }
        )
    }
}
