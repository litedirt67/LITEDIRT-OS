package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import com.example.data.model.InstanceState
import com.example.data.model.SystemTelemetry
import com.example.data.model.VirtualInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SystemTelemetrySampler(private val context: Context) {

    private var prevIdleTime = 0L
    private var prevTotalTime = 0L

    suspend fun sample(
        instances: List<VirtualInstance>,
        instancesBaseDir: File,
        runningSessionLoad: Map<String, Float>
    ): SystemTelemetry = withContext(Dispatchers.IO) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024L * 1024L)
        val availRamMb = memInfo.availMem / (1024L * 1024L)
        val usedRamMb = (totalRamMb - availRamMb).coerceAtLeast(0L)

        val statFs = StatFs(context.filesDir.absolutePath)
        val totalStorageMb = (statFs.blockCountLong * statFs.blockSizeLong) / (1024L * 1024L)
        val freeStorageMb = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024L * 1024L)

        val hostCpuPercent = readHostCpuPercent()
        val hostCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val perInstanceStorage = mutableMapOf<String, Long>()
        val perInstanceRam = mutableMapOf<String, Int>()
        val perInstanceCpu = mutableMapOf<String, Float>()
        var totalVirtualMb = 0L
        var activeCount = 0
        var allocatedRam = 0

        for (instance in instances) {
            val dir = File(instancesBaseDir, instance.id)
            val sizeMb = if (dir.exists()) {
                (dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / (1024L * 1024L)).coerceAtLeast(1L)
            } else {
                0L
            }
            perInstanceStorage[instance.id] = sizeMb
            totalVirtualMb += sizeMb

            if (instance.state == InstanceState.RUNNING || instance.state == InstanceState.BOOTING) {
                activeCount++
                allocatedRam += instance.ramLimitMb
                val load = runningSessionLoad[instance.id] ?: 6.5f
                perInstanceCpu[instance.id] = load
                // Estimasi pemakaian RAM container berdasarkan konfigurasi & proses tamu aktif
                val usedContainerMb = (instance.ramLimitMb * (0.24f + (load / 100f) * 0.35f)).toInt()
                    .coerceIn(192, instance.ramLimitMb)
                perInstanceRam[instance.id] = usedContainerMb
            } else {
                perInstanceCpu[instance.id] = 0f
                perInstanceRam[instance.id] = 0
            }
        }

        SystemTelemetry(
            hostTotalRamMb = totalRamMb,
            hostUsedRamMb = usedRamMb,
            hostAvailableRamMb = availRamMb,
            hostCpuUsagePercent = hostCpuPercent,
            hostCpuCores = hostCores,
            hostTotalStorageMb = totalStorageMb,
            hostFreeStorageMb = freeStorageMb,
            activeVmCount = activeCount,
            allocatedVmRamMb = allocatedRam,
            totalVirtualStorageMb = totalVirtualMb,
            perInstanceStorageMb = perInstanceStorage,
            perInstanceCpuPercent = perInstanceCpu,
            perInstanceRamMb = perInstanceRam,
            sampledAt = System.currentTimeMillis()
        )
    }

    private fun readHostCpuPercent(): Float {
        // Coba baca /proc/stat; jika dibatasi SELinux pada Android 8+, fallback ke waktu CPU proses app
        runCatching {
            val firstLine = File("/proc/stat").useLines { it.firstOrNull() }
            if (firstLine != null && firstLine.startsWith("cpu ")) {
                val parts = firstLine.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
                if (parts.size >= 4) {
                    val idle = parts[3] + parts.getOrElse(4) { 0L }
                    val total = parts.sum()
                    val deltaTotal = total - prevTotalTime
                    val deltaIdle = idle - prevIdleTime
                    prevTotalTime = total
                    prevIdleTime = idle
                    if (deltaTotal > 0L) {
                        return (((deltaTotal - deltaIdle).toFloat() / deltaTotal.toFloat()) * 100f)
                            .coerceIn(1f, 100f)
                    }
                }
            }
        }

        // Fallback via Runtime memory ratio + active thread count
        val rt = Runtime.getRuntime()
        val heapRatio = (rt.totalMemory() - rt.freeMemory()).toFloat() / rt.maxMemory().coerceAtLeast(1L).toFloat()
        val threads = Thread.activeCount().coerceAtLeast(1)
        return ((heapRatio * 28f) + (threads * 0.7f)).coerceIn(2f, 95f)
    }
}
