package com.example.dukaan.service

import android.content.Context
import android.content.Intent
import android.net.Uri
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
    val assetApiUrl: String = "",
    val assetName: String = "",
    val publishedAt: String,
    val isNewer: Boolean
)

object AppUpdateHelper {
    private const val TAG = "AppUpdateHelper"
    private const val PREFS_NAME = "orakle_dukaan_updater"
    private const val KEY_CUSTOM_REPO = "github_repo_url"
    private const val KEY_GITHUB_TOKEN = "github_token"
    private const val KEY_DIRECT_APK_URL = "direct_apk_url"
    const val CURRENT_VERSION = "v1.2.6"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
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

    fun getSavedGitHubToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GITHUB_TOKEN, "").orEmpty()
    }

    fun saveGitHubToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GITHUB_TOKEN, token.trim()).apply()
    }

    fun getSavedDirectApkUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DIRECT_APK_URL, "").orEmpty()
    }

    fun saveDirectApkUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DIRECT_APK_URL, url.trim()).apply()
    }

    /**
     * Checks GitHub API for the latest release and APK asset.
     * Supports private repositories when a GitHub Personal Access Token (PAT) is supplied.
     */
    suspend fun checkLatestRelease(
        context: Context,
        customRepoUrl: String? = null,
        token: String? = null
    ): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        try {
            val repo = (customRepoUrl ?: getSavedRepoUrl(context)).trim().trimEnd('/')
            val effectiveToken = (token ?: getSavedGitHubToken(context)).trim()
            val cleanUrl = repo.removePrefix("https://github.com/").removePrefix("http://github.com/")
            val parts = cleanUrl.split("/")
            if (parts.size < 2) {
                return@withContext Result.failure(Exception("Invalid GitHub repo URL. Use format: https://github.com/owner/repo"))
            }
            val owner = parts[0]
            val repoName = parts[1]

            val apiUrl = "https://api.github.com/repos/$owner/$repoName/releases/latest"
            val reqBuilder = Request.Builder()
                .url(apiUrl)
                .addHeader("Accept", "application/vnd.github.v3+json")

            if (effectiveToken.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $effectiveToken")
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            val bodyStr = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val json = JSONObject(bodyStr)
                val tagName = json.optString("tag_name", "v1.2.7")
                val notes = json.optString("body", "Bug fixes and single-line UI updates.")
                val publishedAt = json.optString("published_at", "")

                // Find APK in assets
                var downloadUrl = ""
                var assetApiUrl = ""
                var assetName = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            val browserUrl = asset.optString("browser_download_url", "")
                            val apiAssetUrl = asset.optString("url", "")
                            assetName = name
                            assetApiUrl = apiAssetUrl
                            downloadUrl = if (effectiveToken.isNotBlank() && apiAssetUrl.isNotBlank()) {
                                apiAssetUrl
                            } else if (browserUrl.isNotBlank()) {
                                browserUrl
                            } else {
                                apiAssetUrl
                            }
                            break
                        }
                    }
                }

                if (downloadUrl.isBlank()) {
                    downloadUrl = "https://github.com/$owner/$repoName/releases/download/$tagName/app-release.apk"
                }

                val isNewer = tagName != CURRENT_VERSION
                Result.success(
                    AppReleaseInfo(
                        versionName = tagName,
                        releaseNotes = notes,
                        apkDownloadUrl = downloadUrl,
                        assetApiUrl = assetApiUrl,
                        assetName = assetName,
                        publishedAt = publishedAt,
                        isNewer = isNewer
                    )
                )
            } else if (response.code == 404) {
                val msg = if (effectiveToken.isBlank()) {
                    "HTTP 404: Repository '$owner/$repoName' is private or no Releases exist!\n\nGitHub private repositories require a GitHub Personal Access Token (PAT). Please enter your Token below, or use Direct APK URL."
                } else {
                    "HTTP 404: No Releases found in '$owner/$repoName'. Make sure a Release with an attached .apk asset is created on GitHub."
                }
                Result.failure(Exception(msg))
            } else if (response.code == 401 || response.code == 403) {
                Result.failure(Exception("HTTP ${response.code}: Access denied. Please check your GitHub Personal Access Token permissions ('repo' scope needed)."))
            } else {
                Result.failure(Exception("GitHub returned HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkLatestRelease error", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads APK directly using OkHttp stream with live progress and launches PackageInstaller.
     * Supports token authorization header and GitHub Release Assets API for private repositories.
     */
    suspend fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        token: String? = null,
        assetApiUrl: String? = null,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val effectiveToken = (token ?: getSavedGitHubToken(context)).trim()
            var effectiveUrl = if (effectiveToken.isNotBlank() && !assetApiUrl.isNullOrBlank()) {
                assetApiUrl
            } else {
                apkUrl
            }

            var reqBuilder = Request.Builder().url(effectiveUrl)

            if (effectiveToken.isNotBlank() && (effectiveUrl.contains("github.com") || effectiveUrl.contains("api.github.com"))) {
                reqBuilder.addHeader("Authorization", "Bearer $effectiveToken")
            }
            if (effectiveUrl.contains("api.github.com/repos") && effectiveUrl.contains("/assets/")) {
                reqBuilder.addHeader("Accept", "application/octet-stream")
            }

            var response = httpClient.newCall(reqBuilder.build()).execute()

            // If 404 and we have a token, or if browser_download_url returned 404 on a private repo:
            if (response.code == 404 && effectiveToken.isNotBlank() && effectiveUrl.contains("github.com") && !effectiveUrl.contains("api.github.com")) {
                response.close()
                val cleanUrl = effectiveUrl.removePrefix("https://github.com/").removePrefix("http://github.com/")
                val parts = cleanUrl.split("/")
                if (parts.size >= 2) {
                    val owner = parts[0]
                    val repoName = parts[1]
                    val releaseRes = checkLatestRelease(context, "https://github.com/$owner/$repoName", effectiveToken)
                    val info = releaseRes.getOrNull()
                    if (info != null && info.assetApiUrl.isNotBlank()) {
                        effectiveUrl = info.assetApiUrl
                        val retryReq = Request.Builder()
                            .url(effectiveUrl)
                            .addHeader("Authorization", "Bearer $effectiveToken")
                            .addHeader("Accept", "application/octet-stream")
                            .build()
                        response = httpClient.newCall(retryReq).execute()
                    }
                }
            }

            if (!response.isSuccessful) {
                val code = response.code
                val err = if (code == 404) {
                    "Download failed with HTTP 404.\n\n" +
                    "Karan (Reason): Repository PRIVATE hone ke karan GitHub releases direct download nahi karne deta bina GitHub Token ke, ya fir GitHub release me koi .apk asset upload nahi hai.\n\n" +
                    "Solution:\n" +
                    "1. 'GitHub Token' box me apna Token (PAT) dalein ('repo' permission ke sath), YA\n" +
                    "2. GitHub par repository ko 'Public' set karein, YA\n" +
                    "3. 'Direct APK Link' tab me direct link paste karein."
                } else if (code == 401 || code == 403) {
                    "HTTP $code: GitHub access denied. Please check your GitHub Personal Access Token."
                } else {
                    "Download failed with HTTP $code: ${response.message}"
                }
                return@withContext Result.failure(Exception(err))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty download body from server"))
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
 * Modern In-App Update Dialog supporting GitHub (Public & Private) and Direct APK URLs.
 */
@Composable
fun InAppUpdateDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var updateSourceTab by remember { mutableStateOf(0) } // 0: GitHub Repo, 1: Direct APK URL
    var repoUrl by remember { mutableStateOf(AppUpdateHelper.getSavedRepoUrl(context)) }
    var githubToken by remember { mutableStateOf(AppUpdateHelper.getSavedGitHubToken(context)) }
    var directApkUrl by remember { mutableStateOf(AppUpdateHelper.getSavedDirectApkUrl(context)) }

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
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = OrakleRedPrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("In-App APK Updates", fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
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
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current Version:", fontSize = 11.sp, color = OrakleSlate600, maxLines = 1)
                        Text(AppUpdateHelper.CURRENT_VERSION, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = OrakleGreen, maxLines = 1)
                    }
                }

                // Source Tabs: GitHub vs Direct APK URL
                TabRow(
                    selectedTabIndex = updateSourceTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = OrakleRedPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = updateSourceTab == 0,
                        onClick = { updateSourceTab = 0 },
                        text = { Text("GitHub Repo", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                    )
                    Tab(
                        selected = updateSourceTab == 1,
                        onClick = { updateSourceTab = 1 },
                        text = { Text("Direct APK Link", fontSize = 11.sp, maxLines = 1, softWrap = false) }
                    )
                }

                if (updateSourceTab == 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Private Repository / 404 Guide", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0369A1), maxLines = 1)
                            }
                            Text(
                                "Agar repo private hai, toh GitHub bina Token ke 404 deta hai. Niche apna GitHub Personal Access Token (PAT) dalein jisme 'repo' access ho, ya 'Direct APK Link' tab me direct APK URL dalein.",
                                fontSize = 10.sp,
                                color = OrakleSlate600,
                                lineHeight = 13.sp
                            )
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

                    OutlinedTextField(
                        value = githubToken,
                        onValueChange = {
                            githubToken = it
                            AppUpdateHelper.saveGitHubToken(context, it)
                        },
                        label = { Text("GitHub Token (Required for Private Repos)", fontSize = 11.sp) },
                        placeholder = { Text("ghp_xxxx or Personal Access Token", fontSize = 11.sp) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = directApkUrl,
                        onValueChange = {
                            directApkUrl = it
                            AppUpdateHelper.saveDirectApkUrl(context, it)
                        },
                        label = { Text("Direct APK Download URL", fontSize = 11.sp) },
                        placeholder = { Text("https://.../app-release.apk", fontSize = 11.sp) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Paste any direct APK download link (Supabase, Drive direct, server URL).",
                        fontSize = 10.sp,
                        color = OrakleSlate500
                    )
                }

                if (statusMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isError) OrakleRedContainer else OrakleGreenContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage!!,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isError) OrakleOnRedContainer else Color(0xFF166534),
                            modifier = Modifier.padding(8.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                            Text("Downloading update APK...", fontSize = 11.sp, color = OrakleSlate600, maxLines = 1)
                            Text("$downloadProgress%", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = OrakleRedPrimary, maxLines = 1)
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
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Latest: ${rel.versionName}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OrakleRedPrimary, maxLines = 1)
                                if (rel.isNewer) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = OrakleGreen) {
                                        Text("NEW", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                            }
                            Text(rel.releaseNotes.take(120), fontSize = 10.sp, color = OrakleSlate700, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (updateSourceTab == 0) {
                        Button(
                            onClick = {
                                isChecking = true
                                statusMessage = null
                                isError = false
                                coroutineScope.launch {
                                    val res = AppUpdateHelper.checkLatestRelease(context, repoUrl, githubToken)
                                    isChecking = false
                                    if (res.isSuccess) {
                                        val info = res.getOrNull()!!
                                        releaseInfo = info
                                        statusMessage = if (info.isNewer) "New version ${info.versionName} found on GitHub!" else "App is up to date (${info.versionName})."
                                    } else {
                                        isError = true
                                        statusMessage = res.exceptionOrNull()?.message ?: "Check failed"
                                    }
                                }
                            },
                            enabled = !isChecking && !isDownloading,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Check GitHub", fontSize = 11.sp, maxLines = 1, softWrap = false, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val targetUrl = if (updateSourceTab == 1 && directApkUrl.isNotBlank()) {
                                directApkUrl.trim()
                            } else {
                                releaseInfo?.apkDownloadUrl?.ifBlank { null }
                                    ?: "${repoUrl.trimEnd('/')}/releases/latest/download/app-release.apk"
                            }

                            isDownloading = true
                            statusMessage = null
                            isError = false
                            coroutineScope.launch {
                                val dlRes = AppUpdateHelper.downloadAndInstallApk(
                                    context = context,
                                    apkUrl = targetUrl,
                                    token = if (updateSourceTab == 0) githubToken else null,
                                    assetApiUrl = if (updateSourceTab == 0) releaseInfo?.assetApiUrl else null
                                ) { p ->
                                    downloadProgress = p
                                }
                                isDownloading = false
                                if (dlRes.isSuccess) {
                                    statusMessage = "APK downloaded! Launching installer..."
                                } else {
                                    isError = true
                                    statusMessage = dlRes.exceptionOrNull()?.message ?: "Download failed"
                                }
                            }
                        },
                        enabled = !isDownloading && (updateSourceTab == 0 || directApkUrl.isNotBlank()),
                        colors = ButtonDefaults.buttonColors(containerColor = OrakleRedPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isDownloading) "Downloading..." else "Install Update", fontSize = 11.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
