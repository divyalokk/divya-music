package com.divyamusic.divyalokk

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
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
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

// ---------- 1. Data ----------
data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    /** Lowercase hex SHA-256 read from the release's "<apk name>.sha256" asset. */
    val sha256: String
)

// ---------- 2. GitHub se latest release check ----------
object UpdateManager {

    private const val OWNER = "divyalokk"
    private const val REPO = "divya-music"
    private const val TAG = "UpdateManager"

    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    /** APK aur .sha256 sirf apne repo ke GitHub release se hi download ho. */
    private const val APK_URL_PREFIX = "https://github.com/$OWNER/$REPO/releases/download/"

    /** Download itna time na complete ho toh receiver hata diya jaata hai. */
    private const val DOWNLOAD_TIMEOUT_MS = 15L * 60_000L

    private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")

    /** Naya version mile toh UpdateInfo, warna null (reason logcat me likha jaata hai). */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val body = httpGet(LATEST_RELEASE_URL, "application/vnd.github+json")
                ?: return@withContext null
            val json = JSONObject(body)

            // "v3.2" aur "V3.2" dono chalein
            val latest = json.getString("tag_name").trimStart('v', 'V')
            val current = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: "0"

            if (!isNewer(latest, current)) return@withContext null

            // .apk wala asset dhoondo
            val assets = json.getJSONArray("assets")
            val apkAsset = findAsset(assets) { it.endsWith(".apk") }
            if (apkAsset == null) {
                Log.e(TAG, "Release $latest me .apk asset nahi mila")
                return@withContext null
            }
            val apkName = apkAsset.first
            val apkUrl = apkAsset.second
            if (!apkUrl.startsWith(APK_URL_PREFIX)) {
                Log.e(TAG, "Unexpected APK URL rejected: $apkUrl")
                return@withContext null
            }

            // Hash asset zaroori hai; bina iske install nahi hoga
            val shaName = "$apkName.sha256"
            val shaAsset = findAsset(assets) { it == shaName }
            if (shaAsset == null) {
                Log.e(TAG, "Release $latest me $shaName asset nahi mila; update offer nahi hoga")
                return@withContext null
            }
            val shaUrl = shaAsset.second
            if (!shaUrl.startsWith(APK_URL_PREFIX)) {
                Log.e(TAG, "Unexpected checksum URL rejected: $shaUrl")
                return@withContext null
            }

            val shaText = httpGet(shaUrl, "application/octet-stream")
                ?: return@withContext null
            val expected = shaText.trim().split(Regex("\\s+"))[0]
            if (!SHA256_REGEX.matches(expected)) {
                Log.e(TAG, "$shaName is not a valid SHA-256 hash")
                return@withContext null
            }

            UpdateInfo(
                version = latest,
                notes = json.optString("body", ""),
                apkUrl = apkUrl,
                apkName = apkName,
                sha256 = expected.lowercase()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            null
        }
    }

    /** Pehla asset jiska name [match] se mile: (name, browser_download_url). */
    private fun findAsset(assets: JSONArray, match: (String) -> Boolean): Pair<String, String>? {
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.getString("name")
            if (match(name)) return name to a.getString("browser_download_url")
        }
        return null
    }

    /** GET request; non-200 par HTTP code log karke null. */
    private fun httpGet(url: String, accept: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Accept", accept)
            conn.setRequestProperty("User-Agent", "DivyaMusic-Updater")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            val code = conn.responseCode
            if (code != 200) {
                // 404 matlab repo me koi published Release nahi hai
                Log.e(TAG, "GET $url failed: HTTP $code")
                return null
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    internal fun isNewer(latest: String, current: String): Boolean {
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
     * false -> permission chahiye (Settings khol di gayi) ya download start nahi hua
     */
    fun downloadAndInstall(context: Context, info: UpdateInfo): Boolean {
        val appContext = context.applicationContext

        // Defense in depth: checkForUpdate already validates these
        if (!info.apkUrl.startsWith(APK_URL_PREFIX) || !SHA256_REGEX.matches(info.sha256)) {
            Log.e(TAG, "Refusing to download update ${info.version}: unverified source or hash")
            Toast.makeText(
                appContext,
                appContext.getString(R.string.update_checksum_failed),
                Toast.LENGTH_LONG
            ).show()
            return false
        }

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
                appContext.getString(R.string.update_allow_unknown_sources),
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (dir == null) {
            Log.e(TAG, "External files dir unavailable; cannot download update")
            Toast.makeText(appContext, appContext.getString(R.string.update_download_failed), Toast.LENGTH_LONG).show()
            return false
        }

        val fileName = "update-${info.version}.apk"
        val file = File(dir, fileName)
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle(appContext.getString(R.string.update_download_title, info.version))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                appContext, Environment.DIRECTORY_DOWNLOADS, fileName
            )

        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = try {
            dm.enqueue(request)
        } catch (e: Exception) {
            Log.e(TAG, "Could not enqueue update download", e)
            Toast.makeText(appContext, appContext.getString(R.string.update_download_failed), Toast.LENGTH_LONG).show()
            return false
        }
        Toast.makeText(appContext, appContext.getString(R.string.update_download_started), Toast.LENGTH_SHORT).show()

        DownloadWatcher(appContext, dm, downloadId, file, info.sha256).start()
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

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Ek download ko watch karta hai: completion broadcast ya timeout, jo pehle aaye.
     * Receiver ek hi baar unregister hota hai, isliye dobara call safe hai.
     */
    private class DownloadWatcher(
        private val appContext: Context,
        private val dm: DownloadManager,
        private val downloadId: Long,
        private val file: File,
        private val expectedSha256: String
    ) : BroadcastReceiver() {

        private val handler = Handler(Looper.getMainLooper())
        private val finished = AtomicBoolean(false)

        private val timeout = Runnable {
            if (unregister()) {
                Log.e(TAG, "Update download timed out (id=$downloadId)")
                failed()
            }
        }

        fun start() {
            ContextCompat.registerReceiver(
                appContext,
                this,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )
            handler.postDelayed(timeout, DOWNLOAD_TIMEOUT_MS)
        }

        /** true sirf pehli baar; baaki calls no-op. */
        private fun unregister(): Boolean {
            if (!finished.compareAndSet(false, true)) return false
            handler.removeCallbacks(timeout)
            try {
                appContext.unregisterReceiver(this)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver already unregistered", e)
            }
            return true
        }

        override fun onReceive(ctx: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id != downloadId) return
            if (!unregister()) return

            // Download sach mein successful hua ya nahi
            val success = dm.query(DownloadManager.Query().setFilterById(downloadId))
                ?.use { c ->
                    c.moveToFirst() && c.getInt(
                        c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    ) == DownloadManager.STATUS_SUCCESSFUL
                } ?: false

            if (!success || !file.exists()) {
                Log.e(TAG, "Update download failed (id=$downloadId)")
                failed()
                return
            }

            // Hashing bada file hai, isliye main thread se bahar
            Thread {
                val actual = try {
                    sha256Of(file)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not hash downloaded update", e)
                    null
                }
                handler.post {
                    if (actual != null && actual.equals(expectedSha256, ignoreCase = true)) {
                        installApk(appContext, file)
                    } else {
                        Log.e(TAG, "SHA-256 mismatch: expected $expectedSha256, got $actual")
                        file.delete()
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.update_checksum_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.start()
        }

        private fun failed() {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.update_download_failed),
                Toast.LENGTH_LONG
            ).show()
        }
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
            title = { Text(stringResource(R.string.update_dialog_title, info.version)) },
            text = { Text(info.notes.ifBlank { stringResource(R.string.update_no_notes) }) },
            confirmButton = {
                TextButton(onClick = {
                    // Permission missing ho toh dialog khula rahe
                    if (UpdateManager.downloadAndInstall(context, info)) {
                        update = null
                    }
                }) { Text(stringResource(R.string.update_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { update = null }) { Text(stringResource(R.string.update_cancel)) }
            }
        )
    }
}
