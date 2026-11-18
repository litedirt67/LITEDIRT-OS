package com.example.instance

import android.content.Context
import android.net.Uri
import com.example.data.model.ImportedApp
import com.example.data.model.InstanceState
import com.example.data.model.NetworkMode
import com.example.data.model.VirtualInstance
import com.example.data.model.VirtualOsArchitectureMode
import com.example.data.repository.VirtualOsRepository
import com.example.engine.VirtualRuntimeEngine
import com.example.rom.RomImageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class InstanceManager(
    private val context: Context,
    private val repository: VirtualOsRepository,
    private val romImageManager: RomImageManager,
    private val runtimeEngine: VirtualRuntimeEngine
) {
    suspend fun ensureInitialInstance(): VirtualInstance = withContext(Dispatchers.IO) {
        val defaultRom = romImageManager.ensureDefaultAndroid10Rom()
        val existing = repository.getInstancesOnce()
        if (existing.isNotEmpty()) {
            // Pastikan tidak ada state RUNNING nyangkut setelah proses mati paksa
            existing.filter { it.state == InstanceState.RUNNING || it.state == InstanceState.BOOTING }
                .forEach { repository.updateInstanceState(it.id, InstanceState.STOPPED) }
            return@withContext repository.getInstancesOnce().first()
        }

        createInstance(
            name = "Android 10 Utama",
            romId = defaultRom.id,
            engineMode = VirtualOsArchitectureMode.PROOT_CONTAINER,
            widthPx = 1080,
            heightPx = 1920,
            dpi = 420,
            ramLimitMb = 2048,
            cpuCores = 4,
            rootEnabled = true
        )
    }

    suspend fun createInstance(
        name: String,
        romId: String = RomImageManager.DEFAULT_ROM_ID,
        engineMode: VirtualOsArchitectureMode = VirtualOsArchitectureMode.PROOT_CONTAINER,
        widthPx: Int = 1080,
        heightPx: Int = 1920,
        dpi: Int = 420,
        ramLimitMb: Int = 2048,
        cpuCores: Int = 4,
        rootEnabled: Boolean = true,
        spoofBrand: String = "Google",
        spoofModel: String = "Pixel 4"
    ): VirtualInstance = withContext(Dispatchers.IO) {
        val existing = repository.getInstancesOnce()
        val id = "vm_${UUID.randomUUID().toString().take(6)}"
        val vncPort = 5901 + existing.size
        val adbPort = 5555 + existing.size * 2

        val instance = VirtualInstance(
            id = id,
            name = name.ifBlank { "Android 10 #${existing.size + 1}" },
            romId = romId,
            engineMode = engineMode,
            state = InstanceState.STOPPED,
            widthPx = widthPx,
            heightPx = heightPx,
            dpi = dpi,
            ramLimitMb = ramLimitMb,
            cpuCores = cpuCores,
            rootEnabled = rootEnabled,
            spoofBrand = spoofBrand,
            spoofManufacturer = spoofBrand,
            spoofModel = spoofModel,
            spoofImei = generateLuhnImei(),
            spoofAndroidId = UUID.randomUUID().toString().replace("-", "").take(16),
            vncPort = vncPort,
            adbPort = adbPort
        )

        val rom = repository.getRomById(romId) ?: romImageManager.ensureDefaultAndroid10Rom()
        val sourceRootfs = File(rom.localPath, "rootfs")
        val targetRootfs = runtimeEngine.getInstanceRootfsDir(instance.id)
        if (sourceRootfs.exists()) {
            sourceRootfs.copyRecursively(targetRootfs, overwrite = true)
        } else {
            romImageManager.provisionAndroid10Skeleton(targetRootfs)
        }
        runtimeEngine.syncBuildProp(instance, targetRootfs)
        repository.saveInstance(instance)
        repository.setActiveInstanceId(instance.id)
        runtimeEngine.appendLog(instance.id, "INSTANCE", "Instance '${instance.name}' dibuat (Android 10 API 29).")
        instance
    }

    suspend fun cloneInstance(source: VirtualInstance): VirtualInstance = withContext(Dispatchers.IO) {
        val existing = repository.getInstancesOnce()
        val newId = "vm_${UUID.randomUUID().toString().take(6)}"
        val cloned = source.copy(
            id = newId,
            name = "${source.name} (Klon)",
            state = InstanceState.STOPPED,
            spoofImei = generateLuhnImei(),
            spoofAndroidId = UUID.randomUUID().toString().replace("-", "").take(16),
            vncPort = 5901 + existing.size,
            adbPort = 5555 + existing.size * 2,
            createdAt = System.currentTimeMillis(),
            lastBootedAt = 0L,
            bootCount = 0
        )

        val srcDir = File(runtimeEngine.instancesDir, source.id)
        val dstDir = File(runtimeEngine.instancesDir, cloned.id)
        if (srcDir.exists()) {
            srcDir.copyRecursively(dstDir, overwrite = true)
        }
        val targetRootfs = runtimeEngine.getInstanceRootfsDir(cloned.id)
        runtimeEngine.syncBuildProp(cloned, targetRootfs)

        repository.saveInstance(cloned)
        val sourceApps = repository.getAppsForInstanceOnce(source.id)
        sourceApps.forEach { app ->
            val updatedPath = app.apkFilePath.replace("/${source.id}/", "/${cloned.id}/")
            val updatedIcon = app.iconFilePath?.replace("/${source.id}/", "/${cloned.id}/")
            repository.saveImportedApp(
                app.copy(
                    id = UUID.randomUUID().toString(),
                    instanceId = cloned.id,
                    apkFilePath = updatedPath,
                    iconFilePath = updatedIcon
                )
            )
        }

        runtimeEngine.appendLog(cloned.id, "INSTANCE", "Berhasil mengkloning '${source.name}' -> '${cloned.name}'.")
        cloned
    }

    suspend fun updateInstanceSettings(updated: VirtualInstance) = withContext(Dispatchers.IO) {
        repository.updateInstance(updated)
        val rootfs = runtimeEngine.getInstanceRootfsDir(updated.id)
        runtimeEngine.syncBuildProp(updated, rootfs)
        runtimeEngine.appendLog(updated.id, "CONFIG", "Pengaturan instance diperbarui (${updated.widthPx}x${updated.heightPx}, root=${updated.rootEnabled}).")
    }

    suspend fun deleteInstance(instance: VirtualInstance) = withContext(Dispatchers.IO) {
        runtimeEngine.stopInstance(instance.id)
        File(runtimeEngine.instancesDir, instance.id).deleteRecursively()
        repository.deleteInstanceRecord(instance.id)
        val remaining = repository.getInstancesOnce()
        repository.setActiveInstanceId(remaining.firstOrNull()?.id)
    }

    suspend fun exportInstanceBundle(instance: VirtualInstance, destinationUri: Uri): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val manifest = JSONObject().apply {
                    put("format", "LitedirtVM-Bundle-v1")
                    put("androidVersion", "10")
                    put("apiLevel", 29)
                    put("name", instance.name)
                    put("engineMode", instance.engineMode.name)
                    put("widthPx", instance.widthPx)
                    put("heightPx", instance.heightPx)
                    put("dpi", instance.dpi)
                    put("ramLimitMb", instance.ramLimitMb)
                    put("cpuCores", instance.cpuCores)
                    put("rootEnabled", instance.rootEnabled)
                    put("spoofBrand", instance.spoofBrand)
                    put("spoofModel", instance.spoofModel)
                    put("networkMode", instance.networkMode.name)
                }

                val instanceFolder = File(runtimeEngine.instancesDir, instance.id)
                var writtenBytes = 0L

                context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    ZipOutputStream(out).use { zos ->
                        zos.putNextEntry(ZipEntry("virtualos_manifest.json"))
                        val manifestBytes = manifest.toString(2).toByteArray()
                        zos.write(manifestBytes)
                        zos.closeEntry()
                        writtenBytes += manifestBytes.size

                        if (instanceFolder.exists()) {
                            instanceFolder.walkTopDown().filter { it.isFile }.forEach { file ->
                                val relPath = "payload/" + file.relativeTo(instanceFolder).path.replace("\\", "/")
                                zos.putNextEntry(ZipEntry(relPath))
                                FileInputStream(file).use { fis ->
                                    writtenBytes += fis.copyTo(zos)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                } ?: throw IllegalStateException("Gagal membuka tujuan ekspor.")

                runtimeEngine.appendLog(instance.id, "EXPORT", "Ekspor bundle .vos selesai ($writtenBytes byte).")
                writtenBytes
            }
        }

    suspend fun importInstanceBundle(sourceUri: Uri): Result<VirtualInstance> = withContext(Dispatchers.IO) {
        runCatching {
            val defaultRom = romImageManager.ensureDefaultAndroid10Rom()
            val existing = repository.getInstancesOnce()
            val newId = "vm_${UUID.randomUUID().toString().take(6)}"
            val targetDir = File(runtimeEngine.instancesDir, newId).apply { mkdirs() }
            var manifestJson: JSONObject? = null

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            if (entry.name == "virtualos_manifest.json") {
                                manifestJson = JSONObject(zis.readBytes().decodeToString())
                            } else if (entry.name.startsWith("payload/")) {
                                val rel = entry.name.removePrefix("payload/")
                                val outFile = File(targetDir, rel).canonicalFile
                                if (outFile.path.startsWith(targetDir.canonicalPath)) {
                                    outFile.parentFile?.mkdirs()
                                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            } ?: throw IllegalStateException("Tidak dapat membaca berkas .vos.")

            val m = manifestJson ?: throw IllegalStateException("Berkas bukan arsip LitedirtVM (.vos) yang valid.")
            val apiLevel = m.optInt("apiLevel", 29)
            if (apiLevel != 29) {
                targetDir.deleteRecursively()
                throw IllegalStateException("LitedirtVM hanya mendukung bundle Android 10 (API 29).")
            }

            val imported = VirtualInstance(
                id = newId,
                name = m.optString("name", "Impor Android 10") + " (Impor)",
                romId = defaultRom.id,
                engineMode = runCatching { VirtualOsArchitectureMode.valueOf(m.optString("engineMode")) }
                    .getOrDefault(VirtualOsArchitectureMode.PROOT_CONTAINER),
                state = InstanceState.STOPPED,
                widthPx = m.optInt("widthPx", 1080),
                heightPx = m.optInt("heightPx", 1920),
                dpi = m.optInt("dpi", 420),
                ramLimitMb = m.optInt("ramLimitMb", 2048),
                cpuCores = m.optInt("cpuCores", 4),
                rootEnabled = m.optBoolean("rootEnabled", true),
                spoofBrand = m.optString("spoofBrand", "Google"),
                spoofModel = m.optString("spoofModel", "Pixel 4"),
                spoofManufacturer = m.optString("spoofBrand", "Google"),
                spoofImei = generateLuhnImei(),
                spoofAndroidId = UUID.randomUUID().toString().replace("-", "").take(16),
                networkMode = runCatching { NetworkMode.valueOf(m.optString("networkMode")) }
                    .getOrDefault(NetworkMode.NAT),
                vncPort = 5901 + existing.size,
                adbPort = 5555 + existing.size * 2
            )

            val rootfs = runtimeEngine.getInstanceRootfsDir(imported.id)
            if (!File(rootfs, "system/build.prop").exists()) {
                romImageManager.provisionAndroid10Skeleton(rootfs)
            }
            runtimeEngine.syncBuildProp(imported, rootfs)
            repository.saveInstance(imported)
            repository.setActiveInstanceId(imported.id)
            imported
        }
    }

    fun generateLuhnImei(): String {
        val prefix = listOf("35391210", "35824005", "86753009", "35910211").random()
        val body = prefix + (1..6).map { Random.nextInt(0, 10) }.joinToString("")
        var sum = 0
        for (i in body.indices) {
            var digit = body[i].digitToInt()
            if (i % 2 == 1) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        val checkDigit = (10 - (sum % 10)) % 10
        return body + checkDigit
    }
}
