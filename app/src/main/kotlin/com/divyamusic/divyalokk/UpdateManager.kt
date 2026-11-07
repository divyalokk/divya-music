package com.divyamusic.divyalokk

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String)

object UpdateManager {

    private const val OWNER = "divyalokk"
    private const val REPO = "divya-music"
    private const val API_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    /** Naya release ho toh UpdateInfo, warna null. */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(URL(API_URL).readText())

            // Tag "v1.2" ho ya "1.2", dono chalega
            val latest = json.getString("tag_name").trimStart('v', 'V')
            val current = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: "0"

            if (!isNewer(latest, current)) return@withContext null

            // Release ke assets me se pehli .apk file
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
            null // internet nahi / release nahi / koi error -> chup-chaap ignore
        }
    }

    /** "1.10" > "1.9" sahi se compare karta hai. */
    private fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** APK link browser me khol do; browser download karega, user install dabayega. */
    fun openDownload(context: Context, info: UpdateInfo) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** MusicApp ke andar bas ek baar UpdateChecker() call karo. */
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
            title = { Text("Update available: v${info.version}") },
            text = { Text(info.notes.ifBlank { "Naya version aa gaya hai." }) },
            confirmButton = {
                TextButton(onClick = {
                    UpdateManager.openDownload(context, info)
                    update = null
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { update = null }) { Text("Baad me") }
            }
        )
    }
}
