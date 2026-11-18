package com.example.privilege

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.system.Os
import com.example.data.model.HostPrivilegeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.TimeUnit

class HostPrivilegeManager(private val context: Context) {

    private val _status = MutableStateFlow(HostPrivilegeStatus())
    val status: StateFlow<HostPrivilegeStatus> = _status.asStateFlow()

    private val requestCode = 2901

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshSync()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        refreshSync()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { code, grantResult ->
            if (code == requestCode) {
                refreshSync()
            }
        }

    init {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        }
        refreshSync()
    }

    fun cleanup() {
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        }
    }

    suspend fun refreshStatus(): HostPrivilegeStatus = withContext(Dispatchers.IO) {
        refreshSync()
    }

    fun requestShizukuPermission(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refreshSync()
                true
            } else {
                Shizuku.requestPermission(requestCode)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun refreshSync(): HostPrivilegeStatus {
        val shizukuInstalled = isPackageInstalled(SHIZUKU_PACKAGE)
        val shizukuBinderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val shizukuPermission = if (shizukuBinderAlive) {
            runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
        } else {
            false
        }
        val shizukuVersion = if (shizukuBinderAlive) {
            runCatching { Shizuku.getVersion() }.getOrDefault(-1)
        } else {
            -1
        }

        val suPath = findHostSuBinary()
        val hostRootFound = suPath != null
        val kvmAccessible = File("/dev/kvm").let { it.exists() && it.canRead() && it.canWrite() }
        val kernelVersion = runCatching {
            val uname = Os.uname()
            "${uname.release} (${uname.machine})"
        }.getOrDefault("Linux ${System.getProperty("os.version") ?: ""}")

        val selinuxStatus = probeSelinuxStatus()

        val activeLabel = when {
            hostRootFound -> "Host Root Terdeteksi ($suPath) + Isolasi Container"
            shizukuBinderAlive && shizukuPermission -> "Shizuku Aktif (ADB/Shell Privilege v$shizukuVersion)"
            shizukuBinderAlive -> "Shizuku Terdeteksi (Menunggu Izin Pengguna)"
            else -> "Mode Sandbox Standar (Fallback Non-Root)"
        }

        val updated = HostPrivilegeStatus(
            shizukuInstalled = shizukuInstalled,
            shizukuBinderAlive = shizukuBinderAlive,
            shizukuPermissionGranted = shizukuPermission,
            shizukuVersion = shizukuVersion,
            hostRootBinaryFound = hostRootFound,
            hostSuPath = suPath,
            hostKernelVersion = kernelVersion,
            hostSelinuxStatus = selinuxStatus,
            kvmDeviceAccessible = kvmAccessible,
            activeCapabilityLabel = activeLabel
        )
        _status.value = updated
        return updated
    }

    /**
     * Menguji eksekusi perintah shell di host via Shizuku atau fallback lokal.
     * Catatan: Shizuku.newProcess tersedia bila Shizuku aktif dan diizinkan.
     */
    suspend fun runHostDiagnosticCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        val current = _status.value
        if (current.shizukuBinderAlive && current.shizukuPermissionGranted) {
            return@withContext runCatching {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val proc = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
                proc.waitFor(4, TimeUnit.SECONDS)
                val out = proc.inputStream.bufferedReader().readText()
                val err = proc.errorStream.bufferedReader().readText()
                (out + if (err.isNotBlank()) "\n$err" else "").trim().ifBlank { "(selesai tanpa keluaran)" }
            }
        }

        // Fallback unprivileged shell
        return@withContext runCatching {
            val proc = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            proc.waitFor(3, TimeUnit.SECONDS)
            proc.inputStream.bufferedReader().readText().trim().ifBlank { "(mode sandbox terbatas)" }
        }
    }

    private fun findHostSuBinary(): String? {
        val paths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su",
            "/su/bin/su",
            "/data/adb/magisk/magisk"
        )
        return paths.firstOrNull { File(it).exists() }
    }

    private fun probeSelinuxStatus(): String {
        return runCatching {
            val enforceFile = File("/sys/fs/selinux/enforce")
            if (enforceFile.canRead()) {
                if (enforceFile.readText().trim() == "1") "Enforcing" else "Permissive"
            } else {
                "Enforcing (akses sysfs dibatasi SELinux App Sandbox)"
            }
        }.getOrDefault("Enforcing (App Sandbox)")
    }

    private fun isPackageInstalled(pkg: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(pkg, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
