package com.example.dukaan.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val publishedAt: String,
    val isNewer: Boolean
)

object AppUpdateHelper {
    private const val TAG = "AppUpdateHelper"
    private const val PREFS_NAME = "orakle_dukaan_updater"
    private const val KEY_CUSTOM_REPO = "github_repo_url"
    const val CURRENT_VERSION = "v1.2.5"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getSavedRepoUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_REPO, "https://github.com/hypersmile100/dukaan-app")
            ?: "https://github.com/hypersmile100/dukaan-app"
    }

    fun saveRepoUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_REPO, url.trim()).apply()
    }

    /**
     * Checks GitHub API for the latest release and APK asset.
     */
    suspend fun checkLatestRelease(context: Context, customRepoUrl: String? = null): Result<AppReleaseInfo> =
        withContext(Dispatchers.IO) {
            try {
                val repo = (customRepoUrl ?: getSavedRepoUrl(context)).trim().trimEnd('/')
                // Parse owner and repo name from URL
                val cleanUrl = repo.removePrefix("https://github.com/").removePrefix("http://github.com/")
                val parts = cleanUrl.split("/")
                if (parts.size < 2) {
                    return@withContext Result.failure(Exception("Invalid GitHub repo URL: $repo"))
                }
                val owner = parts[0]
                val repoName = parts[1]

                val apiUrl = "https://api.github.com/repos/$owner/$repoName/releases/latest"
                val request = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Accept", "application/vnd.github.v3+json")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val bodyStr = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val tagName = json.optString("tag_name", "v1.2.6")
                    val notes = json.optString("body", "Bug fixes and performance improvements.")
                    val publishedAt = json.optString("published_at", "")

                    // Find APK in assets
                    var downloadUrl = ""
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }

                    // Fallback to release zip or direct download
                    if (downloadUrl.isBlank()) {
                        downloadUrl = "https://github.com/$owner/$repoName/releases/download/$tagName/app-release.apk"
                    }

                    val isNewer = tagName != CURRENT_VERSION
                    Result.success(
                        AppReleaseInfo(
                            versionName = tagName,
                            releaseNotes = notes,
                            apkDownloadUrl = downloadUrl,
                            publishedAt = publishedAt,
                            isNewer = isNewer
                        )
                    )
                } else if (response.code == 404) {
                    // Try releases list in case "latest" tag isn't set
                    val fallbackUrl = "https://github.com/$owner/$repoName/releases"
                    Result.success(
                        AppReleaseInfo(
                            versionName = "Latest Release",
                            releaseNotes = "Check GitHub releases for updated APK.",
                            apkDownloadUrl = fallbackUrl,
                            publishedAt = "",
                            isNewer = true
                        )
                    )
                } else {
                    Result.failure(Exception("GitHub returned code ${response.code}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "checkLatestRelease error", e)
                Result.failure(e)
            }
        }

    /**
     * Downloads APK directly using OkHttp stream with live progress and launches PackageInstaller.
     */
    suspend fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(apkUrl).get().build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed with HTTP ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty download body"))
            val contentLength = body.contentLength()

            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val destinationFile = File(downloadDir, "OrakleDukaan_Update.apk")
            if (destinationFile.exists()) destinationFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = ((totalRead * 100) / contentLength).toInt()
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                    output.flush()
                }
            }

            // Launch Package Installer
            withContext(Dispatchers.Main) {
                promptInstallApk(context, destinationFile)
            }
            Result.success(destinationFile)
        } catch (e: Exception) {
            Log.e(TAG, "downloadAndInstallApk error", e)
            Result.failure(e)
        }
    }

    /**
     * Launches Android Package Installer for the downloaded APK using FileProvider.
     */
    fun promptInstallApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "promptInstallApk failed", e)
            // Fallback: Open file via generic intent
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback install also failed", ex)
            }
        }
    }
}

/**
 * Modern In-App Update Dialog that directly downloads and installs APKs from GitHub.
 */
@Composable
fun InAppUpdateDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var repoUrl by remember { mutableStateOf(AppUpdateHelper.getSavedRepoUrl(context)) }
    var isChecking by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var releaseInfo by remember { mutableStateOf<AppReleaseInfo?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = OrakleRedPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("In-App APK Updates", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = OrakleSlate100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Installed Version:", fontSize = 12.sp, color = OrakleSlate600)
                        Text(AppUpdateHelper.CURRENT_VERSION, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrakleGreen)
                    }
                }

                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = {
                        repoUrl = it
                        AppUpdateHelper.saveRepoUrl(context, it)
                    },
                    label = { Text("GitHub Repo URL", fontSize = 11.sp) },
                    placeholder = { Text("https://github.com/owner/repo", fontSize = 11.sp) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (statusMessage != null) {
                    Text(
                        text = statusMessage!!,
                        fontSize = 12.sp,
                        color = if (isError) MaterialTheme.colorScheme.error else OrakleGreen,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isDownloading) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = OrakleRedPrimary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Downloading update APK...", fontSize = 11.sp, color = OrakleSlate600)
                            Text("$downloadProgress%", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = OrakleRedPrimary)
                        }
                    }
                }

                if (releaseInfo != null && !isDownloading) {
                    val rel = releaseInfo!!
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = OrakleRedLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Latest Release: ${rel.versionName}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = OrakleRedPrimary)
                                if (rel.isNewer) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = OrakleGreen) {
                                        Text("NEW", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                            }
                            Text(rel.releaseNotes.take(150), fontSize = 11.sp, color = OrakleSlate700)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isChecking = true
                            statusMessage = null
                            isError = false
                            coroutineScope.launch {
                                val res = AppUpdateHelper.checkLatestRelease(context, repoUrl)
                                isChecking = false
                                if (res.isSuccess) {
                                    val info = res.getOrNull()!!
                                    releaseInfo = info
                                    statusMessage = if (info.isNewer) "New version ${info.versionName} found on GitHub!" else "App is up to date."
                                } else {
                                    isError = true
                                    statusMessage = "Could not check updates: ${res.exceptionOrNull()?.message}"
                                }
                            }
                        },
                        enabled = !isChecking && !isDownloading,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Check Updates", fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Button(
                        onClick = {
                            val url = releaseInfo?.apkDownloadUrl
                            if (!url.isNullOrBlank()) {
                                isDownloading = true
                                statusMessage = null
                                coroutineScope.launch {
                                    val dlRes = AppUpdateHelper.downloadAndInstallApk(context, url) { p ->
                                        downloadProgress = p
                                    }
                                    isDownloading = false
                                    if (dlRes.isSuccess) {
                                        statusMessage = "APK downloaded! Opening installer..."
                                    } else {
                                        isError = true
                                        statusMessage = "Download failed: ${dlRes.exceptionOrNull()?.message}"
                                    }
                                }
                            } else {
                                // Default direct download trigger
                                isDownloading = true
                                coroutineScope.launch {
                                    val directUrl = "${repoUrl.trimEnd('/')}/releases/latest/download/app-release.apk"
                                    val dlRes = AppUpdateHelper.downloadAndInstallApk(context, directUrl) { p ->
                                        downloadProgress = p
                                    }
                                    isDownloading = false
                                    if (dlRes.isSuccess) {
                                        statusMessage = "APK downloaded! Opening installer..."
                                    } else {
                                        isError = true
                                        statusMessage = "Download failed: ${dlRes.exceptionOrNull()?.message}"
                                    }
                                }
                            }
                        },
                        enabled = !isDownloading,
                        colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isDownloading) "Downloading..." else "Install Update", fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
