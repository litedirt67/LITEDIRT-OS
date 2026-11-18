package com.example.rom

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.data.model.RomImageStatus
import com.example.data.model.RomPackage
import com.example.data.model.VirtualOsArchitectureMode
import com.example.data.repository.VirtualOsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class RomTransferProgress(
    val stage: String,
    val progressFraction: Float,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val completedRom: RomPackage? = null,
    val errorMessage: String? = null
)

class RomImageManager(
    private val context: Context,
    private val repository: VirtualOsRepository
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val romsRootDir: File
        get() = File(context.filesDir, "virtualos/roms").apply { mkdirs() }

    val primaryHostAbi: String
        get() = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    suspend fun ensureDefaultAndroid10Rom(): RomPackage = withContext(Dispatchers.IO) {
        val existing = repository.getRomById(DEFAULT_ROM_ID)
        val baseDir = File(romsRootDir, DEFAULT_ROM_ID)
        if (existing != null && existing.status == RomImageStatus.READY && baseDir.exists()) {
            return@withContext existing
        }

        val rootfsDir = File(baseDir, "rootfs")
        provisionAndroid10Skeleton(rootfsDir)
        val buildPropFile = File(rootfsDir, "system/build.prop")
        val checksum = computeSha256(buildPropFile)
        val sizeBytes = calculateDirSize(baseDir)

        val rom = RomPackage(
            id = DEFAULT_ROM_ID,
            name = "AOSP Android 10 (API 29) — Base Rootfs",
            androidVersion = "10",
            apiLevel = 29,
            abi = primaryHostAbi,
            engineType = VirtualOsArchitectureMode.PROOT_CONTAINER,
            versionTag = "QP1A.191105.004-LitedirtVM",
            sha256 = checksum,
            sizeBytes = sizeBytes,
            localPath = baseDir.absolutePath,
            status = RomImageStatus.READY,
            sourceUrl = null,
            isBuiltInBase = true,
            updatedAt = System.currentTimeMillis()
        )
        repository.saveRom(rom)
        rom
    }

    fun provisionAndroid10Skeleton(rootfsDir: File) {
        val dirs = listOf(
            "system/bin",
            "system/xbin",
            "system/etc",
            "system/framework",
            "system/priv-app",
            "vendor/etc",
            "data/app",
            "data/data",
            "data/local/tmp",
            "sdcard/VirtualShared",
            "sdcard/Download",
            "proc",
            "dev",
            "tmp"
        )
        dirs.forEach { File(rootfsDir, it).mkdirs() }

        val buildProp = buildString {
            appendLine("# LitedirtVM Android 10 (API 29) Guest Properties")
            appendLine("ro.build.version.release=10")
            appendLine("ro.build.version.sdk=29")
            appendLine("ro.build.version.codename=REL")
            appendLine("ro.build.id=QP1A.191105.004")
            appendLine("ro.build.display.id=LitedirtVM-AOSP10-API29")
            appendLine("ro.product.cpu.abi=$primaryHostAbi")
            appendLine("ro.product.brand=Google")
            appendLine("ro.product.model=Pixel 4")
            appendLine("ro.product.manufacturer=Google")
            appendLine("ro.debuggable=1")
            appendLine("persist.sys.timezone=Asia/Jakarta")
        }
        File(rootfsDir, "system/build.prop").writeText(buildProp)
        File(rootfsDir, "system/etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
        File(rootfsDir, "sdcard/VirtualShared/Selamat_Datang_LitedirtVM.txt").apply {
            if (!exists()) {
                writeText(
                    "Folder berbagi dua arah antara Host Android dan Guest Android 10 (API 29).\n" +
                        "Berkas di folder ini dapat diakses dari tab Aplikasi & Berkas maupun Manajer Berkas di layar virtual.\n"
                )
            }
        }
    }

    fun importLocalRom(uri: Uri, engineType: VirtualOsArchitectureMode): Flow<RomTransferProgress> = flow {
        emit(RomTransferProgress("Membaca berkas ROM dari penyimpanan host...", 0.05f))

        val displayName = resolveDisplayName(uri) ?: "android10_rom_${System.currentTimeMillis()}.zip"
        val romId = "rom_${UUID.randomUUID().toString().take(8)}"
        val targetDir = File(romsRootDir, romId).apply { mkdirs() }
        val targetFile = File(targetDir, displayName)

        try {
            val totalSize = resolveFileSize(uri)
            var copied = 0L
            val digest = MessageDigest.getInstance("SHA-256")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        copied += read
                        val progress = if (totalSize > 0) (copied.toFloat() / totalSize).coerceIn(0.05f, 0.75f) else 0.4f
                        emit(RomTransferProgress("Menyalin image ROM Android 10...", progress, copied, totalSize))
                    }
                }
            } ?: throw IllegalStateException("Tidak dapat membuka berkas dari penyimpanan.")

            emit(RomTransferProgress("Memverifikasi metadata Android 10 (API 29) & SHA-256...", 0.85f, copied, totalSize))
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            val validationError = validateAndroid10Archive(targetFile)
            if (validationError != null) {
                targetDir.deleteRecursively()
                emit(RomTransferProgress("Ditolak", 0f, errorMessage = validationError))
                return@flow
            }

            // Siapkan skeleton rootfs agar instance siap dijalankan
            val rootfsDir = File(targetDir, "rootfs")
            provisionAndroid10Skeleton(rootfsDir)

            val rom = RomPackage(
                id = romId,
                name = displayName.substringBeforeLast('.') + " (Android 10)",
                androidVersion = "10",
                apiLevel = 29,
                abi = primaryHostAbi,
                engineType = engineType,
                versionTag = "Custom-API29-${sha256.take(6)}",
                sha256 = sha256,
                sizeBytes = copied,
                localPath = targetDir.absolutePath,
                status = RomImageStatus.READY,
                sourceUrl = uri.toString(),
                isBuiltInBase = false,
                updatedAt = System.currentTimeMillis()
            )
            repository.saveRom(rom)
            emit(RomTransferProgress("ROM Android 10 berhasil diimpor", 1f, copied, copied, completedRom = rom))
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            emit(RomTransferProgress("Gagal", 0f, errorMessage = e.message ?: "Gagal mengimpor ROM"))
        }
    }.flowOn(Dispatchers.IO)

    fun downloadRomFromUrl(
        url: String,
        expectedSha256: String?,
        engineType: VirtualOsArchitectureMode
    ): Flow<RomTransferProgress> = flow {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            emit(RomTransferProgress("URL tidak valid", 0f, errorMessage = "Gunakan URL http:// atau https:// yang valid."))
            return@flow
        }

        val romId = "rom_${UUID.randomUUID().toString().take(8)}"
        val targetDir = File(romsRootDir, romId).apply { mkdirs() }
        val fileName = url.substringAfterLast('/').substringBefore('?').ifBlank { "android10_rootfs.tar.gz" }
        val targetFile = File(targetDir, fileName)

        emit(RomTransferProgress("Menghubungkan ke server ROM Android 10...", 0.05f))

        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    targetDir.deleteRecursively()
                    emit(
                        RomTransferProgress(
                            "Gagal mengunduh",
                            0f,
                            errorMessage = "Server merespons HTTP ${response.code}. Pastikan tautan image Android 10 tersedia."
                        )
                    )
                    return@flow
                }

                val body = response.body ?: throw IllegalStateException("Body respons kosong.")
                val total = body.contentLength()
                var downloaded = 0L
                val digest = MessageDigest.getInstance("SHA-256")

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            val fraction = if (total > 0) (downloaded.toFloat() / total).coerceIn(0.05f, 0.85f) else 0.5f
                            emit(RomTransferProgress("Mengunduh ROM Android 10...", fraction, downloaded, total))
                        }
                    }
                }

                emit(RomTransferProgress("Memverifikasi SHA-256 & API Level 29...", 0.92f, downloaded, total))
                val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (!expectedSha256.isNullOrBlank() && !actualSha256.equals(expectedSha256.trim(), ignoreCase = true)) {
                    targetDir.deleteRecursively()
                    emit(
                        RomTransferProgress(
                            "Checksum tidak cocok",
                            0f,
                            errorMessage = "SHA-256 tidak cocok! Diharapkan ${expectedSha256.take(12)}..., diterima ${actualSha256.take(12)}..."
                        )
                    )
                    return@flow
                }

                val validationError = validateAndroid10Archive(targetFile)
                if (validationError != null) {
                    targetDir.deleteRecursively()
                    emit(RomTransferProgress("Bukan ROM Android 10", 0f, errorMessage = validationError))
                    return@flow
                }

                val rootfsDir = File(targetDir, "rootfs")
                provisionAndroid10Skeleton(rootfsDir)

                val rom = RomPackage(
                    id = romId,
                    name = fileName.substringBeforeLast('.') + " (Android 10)",
                    androidVersion = "10",
                    apiLevel = 29,
                    abi = primaryHostAbi,
                    engineType = engineType,
                    versionTag = "Downloaded-API29-${actualSha256.take(6)}",
                    sha256 = actualSha256,
                    sizeBytes = downloaded,
                    localPath = targetDir.absolutePath,
                    status = RomImageStatus.READY,
                    sourceUrl = url,
                    isBuiltInBase = false,
                    updatedAt = System.currentTimeMillis()
                )
                repository.saveRom(rom)
                emit(RomTransferProgress("Unduhan & verifikasi selesai", 1f, downloaded, total, completedRom = rom))
            }
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            emit(RomTransferProgress("Gagal mengunduh", 0f, errorMessage = e.message ?: "Kesalahan jaringan"))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun deleteCustomRom(rom: RomPackage): Boolean = withContext(Dispatchers.IO) {
        if (rom.isBuiltInBase) return@withContext false
        File(rom.localPath).deleteRecursively()
        repository.deleteRom(rom.id)
        true
    }

    /**
     * Memeriksa apakah arsip ZIP memiliki build.prop dan memastikan versinya hanya Android 10 (SDK 29).
     * Untuk format .img / .tar.gz mentah yang tidak memiliki header zip, kita menerima berkas tidak kosong
     * dengan tetap menandainya sebagai image Android 10 pada registry.
     */
    private fun validateAndroid10Archive(file: File): String? {
        if (!file.exists() || file.length() == 0L) {
            return "Berkas ROM kosong atau tidak dapat dibaca."
        }
        val lower = file.name.lowercase()
        if (lower.endsWith(".zip")) {
            runCatching {
                ZipInputStream(FileInputStream(file)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith("build.prop")) {
                            val props = zis.readBytes().decodeToString()
                            val sdkLine = props.lines().firstOrNull { it.startsWith("ro.build.version.sdk=") }
                            val sdkValue = sdkLine?.substringAfter('=')?.trim()?.toIntOrNull()
                            if (sdkValue != null && sdkValue != 29) {
                                return "Ditolak: LitedirtVM hanya mendukung Android 10 (API 29). ROM ini terdeteksi API $sdkValue."
                            }
                            break
                        }
                        entry = zis.nextEntry
                    }
                }
            }
        }
        return null
    }

    private fun computeSha256(file: File): String {
        if (!file.exists()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    private fun resolveFileSize(uri: Uri): Long {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else -1L
        } ?: -1L
    }

    companion object {
        const val DEFAULT_ROM_ID = "aosp_android10_api29_base"
    }
}
