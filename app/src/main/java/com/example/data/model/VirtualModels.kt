package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class VirtualOsArchitectureMode(
    val displayName: String,
    val shortLabel: String,
    val description: String
) {
    PROOT_CONTAINER(
        displayName = "PRoot / Chroot Container (Direkomendasikan)",
        shortLabel = "PRoot Container",
        description = "Menjalankan rootfs Android 10 menggunakan namespace & syscall translation di kernel host tanpa emulasi CPU penuh. Cepat dan hemat baterai, namun bergantung pada ABI host dan binder/SELinux."
    ),
    QEMU_SYSTEM(
        displayName = "QEMU Emulasi Sistem",
        shortLabel = "QEMU Emulasi",
        description = "Emulasi penuh perangkat keras ARM64/x86_64 via QEMU. Mendukung isolasi kernel penuh, namun lambat tanpa akselerasi /dev/kvm di perangkat non-root."
    )
}

enum class NetworkMode(val label: String, val virtualIp: String) {
    NAT("NAT (Terisolasi via Host)", "10.0.2.15"),
    BRIDGE("Bridge (Virtual TAP/LAN)", "192.168.100.29")
}

enum class DisplayRotation(val degrees: Int, val label: String) {
    PORTRAIT_0(0, "Potret 0°"),
    LANDSCAPE_90(90, "Lanskap 90°"),
    PORTRAIT_180(180, "Terbalik 180°"),
    LANDSCAPE_270(270, "Lanskap 270°");

    fun next(): DisplayRotation = when (this) {
        PORTRAIT_0 -> LANDSCAPE_90
        LANDSCAPE_90 -> PORTRAIT_180
        PORTRAIT_180 -> LANDSCAPE_270
        LANDSCAPE_270 -> PORTRAIT_0
    }

    val isLandscape: Boolean
        get() = this == LANDSCAPE_90 || this == LANDSCAPE_270
}

enum class ThemePreference(val label: String) {
    SYSTEM("Ikuti Sistem"),
    DARK("Tema Gelap"),
    LIGHT("Tema Terang")
}

enum class InstanceState(val label: String) {
    STOPPED("Berhenti"),
    BOOTING("Memulai..."),
    RUNNING("Berjalan"),
    PAUSED("Dijeda"),
    ERROR("Galat")
}

enum class RomImageStatus(val label: String) {
    NOT_INSTALLED("Belum Tersedia"),
    DOWNLOADING("Mengunduh..."),
    VERIFYING("Memverifikasi SHA-256..."),
    READY("Siap Digunakan"),
    CORRUPTED("Checksum Tidak Valid")
}

@Entity(tableName = "virtual_instances")
data class VirtualInstance(
    @PrimaryKey val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val romId: String,
    val engineMode: VirtualOsArchitectureMode = VirtualOsArchitectureMode.PROOT_CONTAINER,
    val state: InstanceState = InstanceState.STOPPED,
    val widthPx: Int = 1080,
    val heightPx: Int = 1920,
    val dpi: Int = 420,
    val ramLimitMb: Int = 2048,
    val cpuCores: Int = 4,
    val rootEnabled: Boolean = true,
    val spoofBrand: String = "Google",
    val spoofModel: String = "Pixel 4",
    val spoofManufacturer: String = "Google",
    val spoofImei: String = "353912104829104",
    val spoofAndroidId: String = "a10f9d29c84b7e12",
    val mockLocationEnabled: Boolean = false,
    val mockLatitude: Double = -6.2088,
    val mockLongitude: Double = 106.8456,
    val mockLocationLabel: String = "Jakarta, Indonesia",
    val networkMode: NetworkMode = NetworkMode.NAT,
    val sharedClipboardEnabled: Boolean = true,
    val vncPort: Int = 5901,
    val adbPort: Int = 5555,
    val createdAt: Long = System.currentTimeMillis(),
    val lastBootedAt: Long = 0L,
    val bootCount: Int = 0
)

@Entity(tableName = "imported_apps")
data class ImportedApp(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val instanceId: String,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val sourceFormat: String,
    val apkFilePath: String,
    val iconFilePath: String?,
    val sizeBytes: Long,
    val permissionsSummary: String = "",
    val isSystemApp: Boolean = false,
    val installedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "rom_packages")
data class RomPackage(
    @PrimaryKey val id: String,
    val name: String,
    val androidVersion: String = "10",
    val apiLevel: Int = 29,
    val abi: String = "arm64-v8a",
    val engineType: VirtualOsArchitectureMode = VirtualOsArchitectureMode.PROOT_CONTAINER,
    val versionTag: String = "QP1A.191105.004",
    val sha256: String = "",
    val sizeBytes: Long = 0L,
    val localPath: String = "",
    val status: RomImageStatus = RomImageStatus.READY,
    val sourceUrl: String? = null,
    val isBuiltInBase: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

data class VirtualLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val instanceId: String,
    val tag: String,
    val level: String = "INFO",
    val message: String
)

data class SystemTelemetry(
    val hostTotalRamMb: Long = 0L,
    val hostUsedRamMb: Long = 0L,
    val hostAvailableRamMb: Long = 0L,
    val hostCpuUsagePercent: Float = 0f,
    val hostCpuCores: Int = 1,
    val hostTotalStorageMb: Long = 0L,
    val hostFreeStorageMb: Long = 0L,
    val activeVmCount: Int = 0,
    val allocatedVmRamMb: Int = 0,
    val totalVirtualStorageMb: Long = 0L,
    val perInstanceStorageMb: Map<String, Long> = emptyMap(),
    val perInstanceCpuPercent: Map<String, Float> = emptyMap(),
    val perInstanceRamMb: Map<String, Int> = emptyMap(),
    val sampledAt: Long = System.currentTimeMillis()
)

data class HostPrivilegeStatus(
    val shizukuInstalled: Boolean = false,
    val shizukuBinderAlive: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    val shizukuVersion: Int = -1,
    val hostRootBinaryFound: Boolean = false,
    val hostSuPath: String? = null,
    val hostKernelVersion: String = "",
    val hostSelinuxStatus: String = "Enforcing / Terbatas oleh Sandbox",
    val kvmDeviceAccessible: Boolean = false,
    val activeCapabilityLabel: String = "Mode Sandbox Standar (Fallback)"
)

data class SharedFileItem(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long
)

data class ParsedApkMetadata(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val sourceFormat: String,
    val sizeBytes: Long,
    val permissions: List<String>,
    val stagedFilePath: String,
    val iconCachePath: String?
)

data class HostInstalledApp(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val apkPath: String,
    val splitApkPaths: List<String>,
    val totalSizeBytes: Long,
    val isSystemApp: Boolean,
    val iconCachePath: String?
)
