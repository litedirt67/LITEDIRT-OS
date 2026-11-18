package com.example.installer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import com.example.data.model.HostInstalledApp
import com.example.data.repository.VirtualOsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class RobloxStatus(
    val hostInstalled: Boolean = false,
    val hostVersionName: String? = null,
    val hostVersionCode: Long? = null,
    val hostApp: HostInstalledApp? = null,
    val vmInstalled: Boolean = false,
    val vmVersionName: String? = null,
    val vmVersionCode: Long? = null,
    val latestRemoteVersion: String? = null,
    val latestRemoteBuildGuid: String? = null,
    val isChecking: Boolean = false,
    val autoSyncedThisCheck: Boolean = false,
    val lastCheckedAt: Long = 0L,
    val statusMessage: String = "Memeriksa status Roblox..."
) {
    val needsVmSyncFromHost: Boolean
        get() = hostInstalled && (!vmInstalled || (hostVersionCode ?: 0L) > (vmVersionCode ?: 0L))

    val needsPlayStoreInstallOrUpdate: Boolean
        get() = !hostInstalled || (
            !latestRemoteVersion.isNullOrBlank() &&
                !hostVersionName.isNullOrBlank() &&
                compareSemverPrefix(latestRemoteVersion, hostVersionName) > 0
            )

    private fun compareSemverPrefix(remote: String, local: String): Int {
        val rParts = remote.split(".").mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        val lParts = local.split(".").mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val rv = rParts.getOrElse(i) { 0 }
            val lv = lParts.getOrElse(i) { 0 }
            if (rv != lv) return rv.compareTo(lv)
        }
        return 0
    }
}

class RobloxAutoUpdateManager(
    private val context: Context,
    private val repository: VirtualOsRepository,
    private val apkInstaller: ApkXapkInstaller
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun checkAndAutoSync(
        instanceId: String?,
        autoSyncEnabled: Boolean
    ): RobloxStatus = withContext(Dispatchers.IO) {
        val hostApp = inspectHostRoblox()
        val remoteMeta = fetchLatestOfficialRobloxVersion()

        var vmApp = if (instanceId != null) {
            repository.getAppsForInstanceOnce(instanceId)
                .firstOrNull { it.packageName == ROBLOX_PACKAGE }
        } else null

        var autoSynced = false
        if (autoSyncEnabled && instanceId != null && hostApp != null) {
            val vmCode = vmApp?.versionCode ?: 0L
            if (vmApp == null || hostApp.versionCode > vmCode) {
                val res = apkInstaller.importInstalledHostApp(hostApp, instanceId)
                if (res.isSuccess) {
                    vmApp = res.getOrNull()
                    autoSynced = true
                }
            }
        }

        val message = when {
            autoSynced && vmApp != null ->
                "Roblox otomatis diperbarui di VM ke v${vmApp.versionName}."
            hostApp == null ->
                "Roblox belum terpasang di HP host. Pasang dari Google Play Store lalu otomatis disinkronkan ke VM."
            vmApp == null ->
                "Roblox v${hostApp.versionName} terdeteksi di host dan siap ditambahkan ke VM."
            hostApp.versionCode > vmApp.versionCode ->
                "Versi baru di host (v${hostApp.versionName}) siap menyinkronkan VM (v${vmApp.versionName})."
            remoteMeta != null && !remoteMeta.first.isNullOrBlank() ->
                "Roblox di VM aktif (v${vmApp.versionName}) • Rilis resmi terbaru: v${remoteMeta.first}"
            else ->
                "Roblox di VM aktif (v${vmApp.versionName})."
        }

        RobloxStatus(
            hostInstalled = hostApp != null,
            hostVersionName = hostApp?.versionName,
            hostVersionCode = hostApp?.versionCode,
            hostApp = hostApp,
            vmInstalled = vmApp != null,
            vmVersionName = vmApp?.versionName,
            vmVersionCode = vmApp?.versionCode,
            latestRemoteVersion = remoteMeta?.first,
            latestRemoteBuildGuid = remoteMeta?.second,
            isChecking = false,
            autoSyncedThisCheck = autoSynced,
            lastCheckedAt = System.currentTimeMillis(),
            statusMessage = message
        )
    }

    fun openRobloxInPlayStore(activityContext: Context) {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$ROBLOX_PACKAGE")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activityContext.startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$ROBLOX_PACKAGE")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activityContext.startActivity(webIntent)
        }
    }

    private fun inspectHostRoblox(): HostInstalledApp? {
        val pm = context.packageManager
        val pkgInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(ROBLOX_PACKAGE, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ROBLOX_PACKAGE, 0)
            }
        }.getOrNull() ?: return null

        val appInfo = pkgInfo.applicationInfo ?: return null
        val sourcePath = appInfo.publicSourceDir ?: appInfo.sourceDir ?: return null
        val splitPaths = appInfo.splitPublicSourceDirs?.toList() ?: emptyList()

        val baseSize = runCatching { File(sourcePath).length() }.getOrDefault(0L)
        val splitSize = splitPaths.sumOf { runCatching { File(it).length() }.getOrDefault(0L) }
        val totalSize = (baseSize + splitSize).coerceAtLeast(1024L)

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }

        val iconDir = File(context.cacheDir, "host_app_icons").apply { mkdirs() }
        val iconFile = File(iconDir, "$ROBLOX_PACKAGE.png")
        if (!iconFile.exists()) {
            runCatching {
                saveDrawableToCache(appInfo.loadIcon(pm), iconFile)
            }
        }

        return HostInstalledApp(
            packageName = ROBLOX_PACKAGE,
            appName = appInfo.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: "Roblox",
            versionName = pkgInfo.versionName ?: "1.0",
            versionCode = versionCode,
            apkPath = sourcePath,
            splitApkPaths = splitPaths,
            totalSizeBytes = totalSize,
            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            iconCachePath = iconFile.takeIf { it.exists() }?.absolutePath
        )
    }

    /**
     * Memeriksa metadata rilis resmi Roblox Android dari endpoint ClientSettings API.
     * Hanya membaca informasi versi (read-only JSON), tidak mengunduh APK tidak resmi.
     */
    private fun fetchLatestOfficialRobloxVersion(): Pair<String?, String?>? {
        return runCatching {
            val request = Request.Builder()
                .url(ROBLOX_VERSION_ENDPOINT)
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val raw = response.body?.string() ?: return@use null
                val json = JSONObject(raw)
                val version = json.optString("version").takeIf { it.isNotBlank() }
                val guid = json.optString("clientVersionUpload").takeIf { it.isNotBlank() }
                version to guid
            }
        }.getOrNull()
    }

    private fun saveDrawableToCache(drawable: Drawable, outFile: File) {
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
            }
        }
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
        }
    }

    companion object {
        const val ROBLOX_PACKAGE = "com.roblox.client"
        const val ROBLOX_VERSION_ENDPOINT =
            "https://clientsettings.roblox.com/v2/client-version/AndroidApp"
    }
}
