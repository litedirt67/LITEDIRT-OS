package com.example.installer

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
import android.provider.OpenableColumns
import com.example.data.model.HostInstalledApp
import com.example.data.model.ImportedApp
import com.example.data.model.ParsedApkMetadata
import com.example.data.repository.VirtualOsRepository
import com.example.engine.VirtualRuntimeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class ApkXapkInstaller(
    private val context: Context,
    private val repository: VirtualOsRepository,
    private val runtimeEngine: VirtualRuntimeEngine
) {
    /**
     * Memuat daftar aplikasi terpasang di perangkat host (ala VMOS) menggunakan Intent LAUNCHER
     * tanpa memerlukan izin sensitif QUERY_ALL_PACKAGES.
     */
    suspend fun loadHostLaunchableApps(): List<HostInstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }

        val iconCacheDir = File(context.cacheDir, "host_app_icons").apply { mkdirs() }
        val seenPackages = HashSet<String>()
        val results = mutableListOf<HostInstalledApp>()

        resolved.forEach { info ->
            val actInfo = info.activityInfo ?: return@forEach
            val pkgName = actInfo.packageName ?: return@forEach
            if (pkgName == context.packageName || !seenPackages.add(pkgName)) return@forEach

            val appInfo = actInfo.applicationInfo
            val sourcePath = appInfo.publicSourceDir ?: appInfo.sourceDir ?: return@forEach
            val splitPaths = appInfo.splitPublicSourceDirs?.toList() ?: emptyList()

            val baseSize = runCatching { File(sourcePath).length() }.getOrDefault(0L)
            val splitSize = splitPaths.sumOf { runCatching { File(it).length() }.getOrDefault(0L) }
            val totalSize = (baseSize + splitSize).coerceAtLeast(1024L)

            val pkgInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkgName, 0)
                }
            }.getOrNull()

            val versionName = pkgInfo?.versionName ?: "1.0"
            val versionCode = if (pkgInfo != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode
                else @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
            } else 1L

            val label = runCatching {
                info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
            }.getOrNull() ?: pkgName
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val cachedIcon = File(iconCacheDir, "$pkgName.png")
            if (!cachedIcon.exists()) {
                runCatching {
                    val drawable = info.loadIcon(pm)
                    saveDrawableToCache(drawable, cachedIcon)
                }
            }

            results += HostInstalledApp(
                packageName = pkgName,
                appName = label,
                versionName = versionName,
                versionCode = versionCode,
                apkPath = sourcePath,
                splitApkPaths = splitPaths,
                totalSizeBytes = totalSize,
                isSystemApp = isSystem,
                iconCachePath = cachedIcon.takeIf { it.exists() }?.absolutePath
            )
        }

        // Pastikan Roblox tetap masuk daftar jika terpasang meski intent launcher tidak dilaporkan OEM
        if (!seenPackages.contains(RobloxAutoUpdateManager.ROBLOX_PACKAGE)) {
            runCatching {
                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(RobloxAutoUpdateManager.ROBLOX_PACKAGE, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(RobloxAutoUpdateManager.ROBLOX_PACKAGE, 0)
                }
                val appInfo = pkgInfo.applicationInfo
                val sourcePath = appInfo?.publicSourceDir ?: appInfo?.sourceDir
                if (appInfo != null && sourcePath != null) {
                    val splitPaths = appInfo.splitPublicSourceDirs?.toList() ?: emptyList()
                    val baseSize = runCatching { File(sourcePath).length() }.getOrDefault(0L)
                    val splitSize = splitPaths.sumOf { runCatching { File(it).length() }.getOrDefault(0L) }
                    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pkgInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pkgInfo.versionCode.toLong()
                    }
                    val cachedIcon = File(iconCacheDir, "${RobloxAutoUpdateManager.ROBLOX_PACKAGE}.png")
                    if (!cachedIcon.exists()) {
                        runCatching { saveDrawableToCache(appInfo.loadIcon(pm), cachedIcon) }
                    }
                    results += HostInstalledApp(
                        packageName = RobloxAutoUpdateManager.ROBLOX_PACKAGE,
                        appName = runCatching { appInfo.loadLabel(pm)?.toString() }.getOrNull() ?: "Roblox",
                        versionName = pkgInfo.versionName ?: "1.0",
                        versionCode = versionCode,
                        apkPath = sourcePath,
                        splitApkPaths = splitPaths,
                        totalSizeBytes = (baseSize + splitSize).coerceAtLeast(1024L),
                        isSystemApp = false,
                        iconCachePath = cachedIcon.takeIf { it.exists() }?.absolutePath
                    )
                }
            }
        }

        results.sortedWith(compareBy<HostInstalledApp> { it.isSystemApp }.thenBy { it.appName.lowercase() })
    }

    /**
     * Mengimpor aplikasi langsung dari daftar aplikasi terpasang di host ke instance Android 10 virtual.
     */
    suspend fun importInstalledHostApp(
        hostApp: HostInstalledApp,
        instanceId: String
    ): Result<ImportedApp> = withContext(Dispatchers.IO) {
        runCatching {
            val rootfs = runtimeEngine.getInstanceRootfsDir(instanceId)
            val appInstallDir = File(rootfs, "data/app/${hostApp.packageName}").apply { mkdirs() }
            val targetBase = File(appInstallDir, "base.apk")

            val srcBase = File(hostApp.apkPath)
            if (!srcBase.exists()) {
                throw IllegalStateException("APK sumber (${hostApp.appName}) tidak ditemukan di host.")
            }
            copyFileSafely(srcBase, targetBase)

            hostApp.splitApkPaths.forEachIndexed { idx, splitPath ->
                val splitFile = File(splitPath)
                if (splitFile.exists()) {
                    runCatching {
                        copyFileSafely(splitFile, File(appInstallDir, "split_$idx.apk"))
                    }
                }
            }

            File(rootfs, "data/data/${hostApp.packageName}").mkdirs()

            var finalIconPath: String? = null
            hostApp.iconCachePath?.let { iconPath ->
                val srcIcon = File(iconPath)
                if (srcIcon.exists()) {
                    val dstIcon = File(appInstallDir, "icon.png")
                    srcIcon.copyTo(dstIcon, overwrite = true)
                    finalIconPath = dstIcon.absolutePath
                }
            }

            val existing = repository.getAppsForInstanceOnce(instanceId)
                .firstOrNull { it.packageName == hostApp.packageName }

            val format = if (hostApp.splitApkPaths.isNotEmpty()) "SPLIT-APK" else "APK"
            val app = ImportedApp(
                id = existing?.id ?: UUID.randomUUID().toString(),
                instanceId = instanceId,
                packageName = hostApp.packageName,
                appName = hostApp.appName,
                versionName = hostApp.versionName,
                versionCode = hostApp.versionCode,
                sourceFormat = format,
                apkFilePath = targetBase.absolutePath,
                iconFilePath = finalIconPath,
                sizeBytes = hostApp.totalSizeBytes,
                permissionsSummary = "Diimpor langsung dari aplikasi host",
                isSystemApp = hostApp.isSystemApp,
                installedAt = System.currentTimeMillis()
            )
            repository.saveImportedApp(app)
            runtimeEngine.appendLog(
                instanceId,
                "INSTALLER",
                "Impor langsung aplikasi host: ${hostApp.appName} (${hostApp.packageName}) -> Berhasil"
            )
            app
        }
    }

    suspend fun inspectPackageFromUri(uri: Uri): Result<ParsedApkMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val rawName = resolveDisplayName(uri) ?: "package_${System.currentTimeMillis()}.apk"
            val sanitizedName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val stagingDir = File(context.cacheDir, "apk_staging").apply { mkdirs() }
            val stagedFile = File(stagingDir, "${System.currentTimeMillis()}_$sanitizedName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(stagedFile).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Tidak dapat membuka berkas APK/XAPK dari penyimpanan.")

            if (stagedFile.length() == 0L) {
                throw IllegalStateException("Berkas APK/XAPK kosong.")
            }

            val isBundleArchive = sanitizedName.endsWith(".xapk", ignoreCase = true) ||
                sanitizedName.endsWith(".apks", ignoreCase = true) ||
                sanitizedName.endsWith(".apkm", ignoreCase = true) ||
                isZipApkBundle(stagedFile)

            if (isBundleArchive) {
                inspectXapkBundle(stagedFile, stagingDir)
            } else {
                inspectSingleApk(stagedFile, stagingDir, "APK")
            }
        }
    }

    suspend fun installToInstance(
        metadata: ParsedApkMetadata,
        instanceId: String
    ): Result<ImportedApp> = withContext(Dispatchers.IO) {
        runCatching {
            val rootfs = runtimeEngine.getInstanceRootfsDir(instanceId)
            val appInstallDir = File(rootfs, "data/app/${metadata.packageName}").apply { mkdirs() }
            val targetName = if (metadata.sourceFormat == "XAPK") "bundle.xapk" else "base.apk"
            val installedFile = File(appInstallDir, targetName)
            val staged = File(metadata.stagedFilePath)
            if (!staged.exists()) throw IllegalStateException("Berkas staging APK tidak ditemukan.")

            copyFileSafely(staged, installedFile)
            if (metadata.sourceFormat == "XAPK") {
                extractApksFromBundle(staged, appInstallDir)
            }
            File(rootfs, "data/data/${metadata.packageName}").mkdirs()

            var finalIconPath: String? = null
            metadata.iconCachePath?.let { cacheIcon ->
                val srcIcon = File(cacheIcon)
                if (srcIcon.exists()) {
                    val dstIcon = File(appInstallDir, "icon.png")
                    copyFileSafely(srcIcon, dstIcon)
                    finalIconPath = dstIcon.absolutePath
                }
            }

            val existing = repository.getAppsForInstanceOnce(instanceId)
                .firstOrNull { it.packageName == metadata.packageName }

            val app = ImportedApp(
                id = existing?.id ?: UUID.randomUUID().toString(),
                instanceId = instanceId,
                packageName = metadata.packageName,
                appName = metadata.appName,
                versionName = metadata.versionName,
                versionCode = metadata.versionCode,
                sourceFormat = metadata.sourceFormat,
                apkFilePath = installedFile.absolutePath,
                iconFilePath = finalIconPath,
                sizeBytes = installedFile.length(),
                permissionsSummary = metadata.permissions.take(6).joinToString(", "),
                isSystemApp = false,
                installedAt = System.currentTimeMillis()
            )
            repository.saveImportedApp(app)
            runtimeEngine.appendLog(
                instanceId,
                "INSTALLER",
                "pm install -r ${metadata.packageName} (${metadata.sourceFormat} v${metadata.versionName}) -> Berhasil"
            )
            app
        }
    }

    suspend fun uninstallFromInstance(app: ImportedApp) = withContext(Dispatchers.IO) {
        File(app.apkFilePath).parentFile?.deleteRecursively()
        repository.deleteImportedApp(app.id)
        runtimeEngine.appendLog(app.instanceId, "INSTALLER", "pm uninstall ${app.packageName} -> Dihapus")
    }

    private fun inspectSingleApk(apkFile: File, stagingDir: File, format: String): ParsedApkMetadata {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } ?: throw IllegalStateException("Berkas bukan arsip APK Android yang valid.")

        val appInfo = pkgInfo.applicationInfo?.apply {
            sourceDir = apkFile.absolutePath
            publicSourceDir = apkFile.absolutePath
        }

        val label = runCatching {
            appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: pkgInfo.packageName
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }

        val iconFile = runCatching {
            appInfo?.loadIcon(pm)?.let { drawable ->
                saveDrawableToCache(drawable, File(stagingDir, "${pkgInfo.packageName}_icon.png"))
            }
        }.getOrNull()

        val permissions = pkgInfo.requestedPermissions?.toList() ?: emptyList()

        return ParsedApkMetadata(
            packageName = pkgInfo.packageName,
            appName = label,
            versionName = pkgInfo.versionName ?: "1.0",
            versionCode = versionCode,
            minSdk = appInfo?.minSdkVersion ?: 21,
            targetSdk = appInfo?.targetSdkVersion ?: 29,
            sourceFormat = format,
            sizeBytes = apkFile.length(),
            permissions = permissions,
            stagedFilePath = apkFile.absolutePath,
            iconCachePath = iconFile?.absolutePath
        )
    }

    private fun copyFileSafely(src: File, dst: File) {
        if (dst.exists()) dst.delete()
        try {
            FileInputStream(src).use { input ->
                FileOutputStream(dst).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
        } catch (e: SecurityException) {
            throw IllegalStateException("Akses berkas ${src.name} ditolak sistem host: ${e.message}", e)
        }
    }

    private fun isZipApkBundle(file: File): Boolean {
        return runCatching {
            var hasNestedApk = false
            var hasManifestJson = false
            var hasRootAndroidManifest = false
            ZipInputStream(FileInputStream(file)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == "AndroidManifest.xml") hasRootAndroidManifest = true
                    if (name == "manifest.json" || name == "info.json") hasManifestJson = true
                    if (!entry.isDirectory && name.endsWith(".apk", ignoreCase = true)) hasNestedApk = true
                    entry = zis.nextEntry
                }
            }
            !hasRootAndroidManifest && (hasManifestJson || hasNestedApk)
        }.getOrDefault(false)
    }

    private fun extractApksFromBundle(bundleFile: File, targetDir: File) {
        runCatching {
            var splitIndex = 0
            ZipInputStream(FileInputStream(bundleFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                        val outName = if (entry.name.endsWith("base.apk", ignoreCase = true) || splitIndex == 0) {
                            "base.apk"
                        } else {
                            "split_${splitIndex}.apk"
                        }
                        splitIndex++
                        val outFile = File(targetDir, outName)
                        if (outFile.exists()) outFile.delete()
                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                    }
                    entry = zis.nextEntry
                }
            }
        }
    }

    private fun inspectXapkBundle(xapkFile: File, stagingDir: File): ParsedApkMetadata {
        var manifestObj: JSONObject? = null
        var extractedBaseApk: File? = null
        var extractedIconFile: File? = null

        ZipInputStream(xapkFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    when {
                        entry.name == "manifest.json" -> {
                            manifestObj = JSONObject(zis.readBytes().decodeToString())
                        }
                        entry.name.endsWith(".apk", ignoreCase = true) && extractedBaseApk == null -> {
                            val tempApk = File(stagingDir, "xapk_base_${System.currentTimeMillis()}.apk")
                            FileOutputStream(tempApk).use { zis.copyTo(it) }
                            extractedBaseApk = tempApk
                        }
                        entry.name.equals("icon.png", ignoreCase = true) -> {
                            val iconOut = File(stagingDir, "xapk_icon_${System.currentTimeMillis()}.png")
                            FileOutputStream(iconOut).use { zis.copyTo(it) }
                            extractedIconFile = iconOut
                        }
                    }
                }
                entry = zis.nextEntry
            }
        }

        val apk = extractedBaseApk
        if (apk != null) {
            val parsed = inspectSingleApk(apk, stagingDir, "XAPK")
            val m = manifestObj
            return parsed.copy(
                packageName = m?.optString("package_name")?.takeIf { it.isNotBlank() } ?: parsed.packageName,
                appName = m?.optString("name")?.takeIf { it.isNotBlank() } ?: parsed.appName,
                versionName = m?.optString("version_name")?.takeIf { it.isNotBlank() } ?: parsed.versionName,
                sizeBytes = xapkFile.length(),
                stagedFilePath = xapkFile.absolutePath,
                iconCachePath = parsed.iconCachePath ?: extractedIconFile?.absolutePath
            )
        }

        val m = manifestObj ?: throw IllegalStateException("Arsip XAPK tidak memiliki manifest.json atau APK di dalamnya.")
        return ParsedApkMetadata(
            packageName = m.optString("package_name", "com.virtualos.imported"),
            appName = m.optString("name", xapkFile.nameWithoutExtension),
            versionName = m.optString("version_name", "1.0"),
            versionCode = m.optLong("version_code", 1L),
            minSdk = m.optInt("min_sdk_version", 21),
            targetSdk = m.optInt("target_sdk_version", 29),
            sourceFormat = "XAPK",
            sizeBytes = xapkFile.length(),
            permissions = emptyList(),
            stagedFilePath = xapkFile.absolutePath,
            iconCachePath = extractedIconFile?.absolutePath
        )
    }

    private fun saveDrawableToCache(drawable: Drawable, outFile: File): File? {
        return runCatching {
            val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
            }
            outFile
        }.getOrNull()
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }
}
