package com.example.installer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.SharedFileItem
import com.example.engine.VirtualRuntimeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class SharedStorageBridge(
    private val context: Context,
    private val runtimeEngine: VirtualRuntimeEngine
) {
    fun getSharedFolder(instanceId: String): File {
        val rootfs = runtimeEngine.getInstanceRootfsDir(instanceId)
        return File(rootfs, "sdcard/VirtualShared").apply { mkdirs() }
    }

    suspend fun listSharedFiles(instanceId: String): List<SharedFileItem> = withContext(Dispatchers.IO) {
        val dir = getSharedFolder(instanceId)
        dir.listFiles()?.map { file ->
            SharedFileItem(
                name = file.name,
                absolutePath = file.absolutePath,
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isDirectory) 0L else file.length(),
                lastModified = file.lastModified()
            )
        }?.sortedWith(compareByDescending<SharedFileItem> { it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()
    }

    suspend fun importFileFromHost(instanceId: String, uri: Uri): Result<SharedFileItem> = withContext(Dispatchers.IO) {
        runCatching {
            val name = resolveDisplayName(uri) ?: "berkas_${System.currentTimeMillis()}"
            val dest = File(getSharedFolder(instanceId), name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Gagal membaca berkas dari host.")

            runtimeEngine.appendLog(instanceId, "SHARED_FS", "Host -> Virtual: /sdcard/VirtualShared/$name")
            SharedFileItem(
                name = dest.name,
                absolutePath = dest.absolutePath,
                isDirectory = false,
                sizeBytes = dest.length(),
                lastModified = dest.lastModified()
            )
        }
    }

    suspend fun exportFileToHost(item: SharedFileItem, destinationUri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val src = File(item.absolutePath)
            if (!src.exists() || src.isDirectory) {
                throw IllegalStateException("Berkas tidak dapat diekspor.")
            }
            var copied = 0L
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                FileInputStream(src).use { input ->
                    copied = input.copyTo(output)
                }
            } ?: throw IllegalStateException("Gagal menulis berkas ke penyimpanan host.")
            copied
        }
    }

    suspend fun createNoteFileInVirtual(instanceId: String, fileName: String, content: String): Result<SharedFileItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "catatan.txt" }
                val file = File(getSharedFolder(instanceId), safeName)
                file.writeText(content)
                runtimeEngine.appendLog(instanceId, "SHARED_FS", "Membuat berkas virtual: $safeName")
                SharedFileItem(
                    name = file.name,
                    absolutePath = file.absolutePath,
                    isDirectory = false,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified()
                )
            }
        }

    suspend fun deleteSharedFile(instanceId: String, item: SharedFileItem) = withContext(Dispatchers.IO) {
        File(item.absolutePath).deleteRecursively()
        runtimeEngine.appendLog(instanceId, "SHARED_FS", "Menghapus berkas bersama: ${item.name}")
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }
}
