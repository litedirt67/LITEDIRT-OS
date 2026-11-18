package com.example.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.example.data.model.DisplayRotation
import com.example.data.model.ImportedApp
import com.example.data.model.InstanceState
import com.example.data.model.VirtualInstance
import com.example.data.model.VirtualLogEntry
import com.example.data.repository.VirtualOsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

sealed interface GuestAppDestination {
    data object Launcher : GuestAppDestination
    data object Terminal : GuestAppDestination
    data object FileManager : GuestAppDestination
    data object GuestSettings : GuestAppDestination
    data object DeviceInfo : GuestAppDestination
    data object Browser : GuestAppDestination
    data object FileTransfer : GuestAppDestination
    data class InstalledPackage(val app: ImportedApp) : GuestAppDestination
}

data class GuestTerminalLine(
    val text: String,
    val isCommand: Boolean = false,
    val isError: Boolean = false
)

data class RunningVmSession(
    val instanceId: String,
    val bootProgress: Float = 0f,
    val startedAtMillis: Long = System.currentTimeMillis(),
    val currentGuestApp: GuestAppDestination = GuestAppDestination.Launcher,
    val recentApps: List<GuestAppDestination> = emptyList(),
    val showRecentsOverlay: Boolean = false,
    val rotation: DisplayRotation = DisplayRotation.PORTRAIT_0,
    val guestRootGrantedInShell: Boolean = false,
    val pendingSuperuserPrompt: Boolean = false,
    val terminalHistory: List<GuestTerminalLine> = emptyList(),
    val currentGuestCwd: String = "/sdcard/VirtualShared",
    val vncTcpReachable: Boolean = false,
    val guestCpuLoadPercent: Float = 5.2f,
    val browserUrl: String = "https://www.google.com"
)

class VirtualRuntimeEngine(
    private val context: Context,
    private val repository: VirtualOsRepository,
    private val scope: CoroutineScope
) {
    private val _sessions = MutableStateFlow<Map<String, RunningVmSession>>(emptyMap())
    val sessions: StateFlow<Map<String, RunningVmSession>> = _sessions.asStateFlow()

    private val _logs = MutableStateFlow<List<VirtualLogEntry>>(emptyList())
    val logs: StateFlow<List<VirtualLogEntry>> = _logs.asStateFlow()

    private val bootJobs = ConcurrentHashMap<String, Job>()

    val instancesDir: File
        get() = File(context.filesDir, "virtualos/instances").apply { mkdirs() }

    fun getInstanceRootfsDir(instanceId: String): File =
        File(instancesDir, "$instanceId/rootfs").apply { mkdirs() }

    suspend fun startInstance(instance: VirtualInstance) {
        if (_sessions.value.containsKey(instance.id) && instance.state == InstanceState.RUNNING) return

        bootJobs[instance.id]?.cancel()
        val rootfs = getInstanceRootfsDir(instance.id)
        syncBuildProp(instance, rootfs)

        repository.updateInstance(
            instance.copy(
                state = InstanceState.BOOTING,
                lastBootedAt = System.currentTimeMillis(),
                bootCount = instance.bootCount + 1
            )
        )
        repository.setActiveInstanceId(instance.id)

        val initSession = RunningVmSession(
            instanceId = instance.id,
            bootProgress = 0.08f,
            terminalHistory = listOf(
                GuestTerminalLine("LitedirtVM Android 10 (API 29) Guest Shell"),
                GuestTerminalLine("Engine: ${instance.engineMode.shortLabel} | Root Virtual: ${if (instance.rootEnabled) "Aktif" else "Nonaktif"}"),
                GuestTerminalLine("Ketik 'help' untuk daftar perintah, atau 'su' untuk menguji akses root.")
            )
        )
        _sessions.update { it + (instance.id to initSession) }
        appendLog(instance.id, "ENGINE", "Memulai ${instance.name} via ${instance.engineMode.shortLabel} (${instance.widthPx}x${instance.heightPx}, ${instance.dpi}dpi)")

        val job = scope.launch {
            val bootSteps = listOf(
                0.22f to "Menyiapkan rootfs Android 10 & mount virtual /system, /data, /sdcard/VirtualShared",
                0.45f to "Menjalankan namespace container (${instance.cpuCores} core, limit ${instance.ramLimitMb} MB RAM)",
                0.68f to "Memuat SurfaceFlinger virtual bridge & port VNC 127.0.0.1:${instance.vncPort}",
                0.88f to "Menginisialisasi Zygote64 & PackageManager tamu (API 29)",
                1.0f to "Sistem Android 10 siap (${instance.spoofBrand} ${instance.spoofModel})"
            )

            for ((progress, msg) in bootSteps) {
                delay(280)
                appendLog(instance.id, "BOOT", msg)
                _sessions.update { map ->
                    val cur = map[instance.id] ?: return@update map
                    map + (instance.id to cur.copy(bootProgress = progress))
                }
            }

            val vncUp = probeLocalPort(instance.vncPort)
            repository.updateInstanceState(instance.id, InstanceState.RUNNING)
            _sessions.update { map ->
                val cur = map[instance.id] ?: return@update map
                map + (instance.id to cur.copy(
                    bootProgress = 1f,
                    vncTcpReachable = vncUp,
                    guestCpuLoadPercent = (4.5f + instance.cpuCores * 1.1f).coerceAtMost(24f)
                ))
            }
            appendLog(
                instance.id,
                "DISPLAY",
                if (vncUp) "Terhubung ke stream VNC lokal di 127.0.0.1:${instance.vncPort}"
                else "Menggunakan LitedirtVM Guest Framebuffer Surface lokal (port VNC ${instance.vncPort} siap)"
            )
        }
        bootJobs[instance.id] = job
    }

    suspend fun pauseInstance(instanceId: String) {
        bootJobs[instanceId]?.cancel()
        repository.updateInstanceState(instanceId, InstanceState.PAUSED)
        appendLog(instanceId, "ENGINE", "Instance dijeda (state disimpan).")
    }

    suspend fun stopInstance(instanceId: String) {
        bootJobs[instanceId]?.cancel()
        bootJobs.remove(instanceId)
        _sessions.update { it - instanceId }
        repository.updateInstanceState(instanceId, InstanceState.STOPPED)
        appendLog(instanceId, "ENGINE", "Instance dihentikan.")
    }

    suspend fun stopAllInstances() {
        bootJobs.values.forEach { it.cancel() }
        bootJobs.clear()
        _sessions.value = emptyMap()
        repository.resetAllInstanceStates()
        appendLog("GLOBAL", "SERVICE", "Semua instance LitedirtVM dihentikan.")
    }

    fun openGuestApp(instanceId: String, destination: GuestAppDestination) {
        _sessions.update { map ->
            val session = map[instanceId] ?: return@update map
            val updatedRecents = if (destination != GuestAppDestination.Launcher) {
                (listOf(destination) + session.recentApps.filterNot { it == destination }).take(6)
            } else {
                session.recentApps
            }
            map + (instanceId to session.copy(
                currentGuestApp = destination,
                recentApps = updatedRecents,
                showRecentsOverlay = false
            ))
        }
    }

    fun pressGuestBack(instanceId: String) {
        _sessions.update { map ->
            val session = map[instanceId] ?: return@update map
            when {
                session.showRecentsOverlay -> map + (instanceId to session.copy(showRecentsOverlay = false))
                session.currentGuestApp != GuestAppDestination.Launcher ->
                    map + (instanceId to session.copy(currentGuestApp = GuestAppDestination.Launcher))
                else -> map
            }
        }
    }

    fun pressGuestHome(instanceId: String) {
        _sessions.update { map ->
            val session = map[instanceId] ?: return@update map
            map + (instanceId to session.copy(
                currentGuestApp = GuestAppDestination.Launcher,
                showRecentsOverlay = false
            ))
        }
    }

    fun toggleGuestRecents(instanceId: String) {
        _sessions.update { map ->
            val session = map[instanceId] ?: return@update map
            map + (instanceId to session.copy(showRecentsOverlay = !session.showRecentsOverlay))
        }
    }

    fun removeFromRecents(instanceId: String, destination: GuestAppDestination) {
        _sessions.update { map ->
            val session = map[instanceId] ?: return@update map
            val nextApp = if (session.currentGuestApp == destination) GuestAppDestination.Launcher else session.currentGuestApp
            map + (instanceId to session.copy(
                recentApps = session.recentApps.filterNot { it == destination },
                currentGuestApp = nextApp
            ))
        }
    }

    fun rotateDisplay(instanceId: String) {
        _sessions.update { map ->
            val session = map[instanceId] ?: return@update map
            map + (instanceId to session.copy(rotation = session.rotation.next()))
        }
    }

    fun updateBrowserUrl(instanceId: String, url: String) {
        _sessions.update { map ->
            val session = map[instanceId] ?: return@update map
            map + (instanceId to session.copy(browserUrl = url))
        }
    }

    fun resolveSuperuserPrompt(instanceId: String, granted: Boolean) {
        _sessions.update { map ->
            val session = map[instanceId] ?: return@update map
            val line = if (granted) {
                GuestTerminalLine("# Akses Superuser (uid=0) diberikan untuk shell virtual.")
            } else {
                GuestTerminalLine("su: Permintaan superuser ditolak oleh pengguna.", isError = true)
            }
            map + (instanceId to session.copy(
                pendingSuperuserPrompt = false,
                guestRootGrantedInShell = granted,
                terminalHistory = (session.terminalHistory + line).takeLast(120)
            ))
        }
    }

    suspend fun refreshVncProbe(instance: VirtualInstance) {
        val reachable = probeLocalPort(instance.vncPort)
        _sessions.update { map ->
            val session = map[instance.id] ?: return@update map
            map + (instance.id to session.copy(vncTcpReachable = reachable))
        }
    }

    fun syncClipboardToHost(instance: VirtualInstance, text: String) {
        if (!instance.sharedClipboardEnabled || text.isBlank()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("LitedirtVM Clipboard", text))
        appendLog(instance.id, "CLIPBOARD", "Menyalin teks ke clipboard host (${text.take(24)}...)")
    }

    fun readHostClipboard(instance: VirtualInstance): String? {
        if (!instance.sharedClipboardEnabled) return null
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    }

    suspend fun executeGuestCommand(instance: VirtualInstance, rawCommand: String) = withContext(Dispatchers.IO) {
        val cmd = rawCommand.trim()
        if (cmd.isEmpty()) return@withContext

        val session = _sessions.value[instance.id] ?: return@withContext
        val rootfs = getInstanceRootfsDir(instance.id)
        val promptPrefix = if (session.guestRootGrantedInShell && instance.rootEnabled) "root@litedirtvm:/ # " else "u0_a10@litedirtvm:/ $ "

        val newLines = mutableListOf(GuestTerminalLine(promptPrefix + cmd, isCommand = true))
        var nextRootState = session.guestRootGrantedInShell
        var openPrompt = false
        var nextCwd = session.currentGuestCwd

        val parts = cmd.split(Regex("\\s+"))
        when (parts[0]) {
            "clear" -> {
                _sessions.update { map ->
                    val cur = map[instance.id] ?: return@update map
                    map + (instance.id to cur.copy(terminalHistory = emptyList()))
                }
                return@withContext
            }
            "help" -> {
                newLines += GuestTerminalLine("Perintah tersedia di Guest Shell Android 10:")
                newLines += GuestTerminalLine("  su | exit       - Masuk/keluar sesi root virtual")
                newLines += GuestTerminalLine("  id | whoami     - Lihat UID/GID saat ini")
                newLines += GuestTerminalLine("  getprop         - Tampilkan properti sistem Android 10")
                newLines += GuestTerminalLine("  uname -a        - Info kernel & arsitektur")
                newLines += GuestTerminalLine("  pm list packages- Daftar APK terpasang di instance")
                newLines += GuestTerminalLine("  ls [path] | pwd - Jelajahi rootfs virtual")
                newLines += GuestTerminalLine("  cat <file>      - Baca berkas dalam rootfs (mis. /system/build.prop)")
                newLines += GuestTerminalLine("  ifconfig        - Info antarmuka jaringan (${instance.networkMode.shortName()})")
                newLines += GuestTerminalLine("  free -m | df -h - Pemakaian memori & penyimpanan")
                newLines += GuestTerminalLine("  clear           - Bersihkan layar terminal")
            }
            "su" -> {
                if (!instance.rootEnabled) {
                    newLines += GuestTerminalLine("su: Akses root dinonaktifkan pada pengaturan instance '${instance.name}'.", isError = true)
                    nextRootState = false
                } else if (session.guestRootGrantedInShell) {
                    newLines += GuestTerminalLine("Sudah berada di sesi root (uid=0).")
                } else {
                    newLines += GuestTerminalLine("Meminta otorisasi Superuser LitedirtVM...")
                    openPrompt = true
                }
            }
            "exit" -> {
                if (session.guestRootGrantedInShell) {
                    nextRootState = false
                    newLines += GuestTerminalLine("Keluar dari shell root -> u0_a10.")
                } else {
                    newLines += GuestTerminalLine("Gunakan tombol navigasi Back/Home untuk menutup Terminal.")
                }
            }
            "id" -> {
                if (session.guestRootGrantedInShell && instance.rootEnabled) {
                    newLines += GuestTerminalLine("uid=0(root) gid=0(root) groups=0(root),1004(input),1007(log),3003(inet) context=u:r:magisk:s0")
                } else {
                    newLines += GuestTerminalLine("uid=10129(u0_a129) gid=10129(u0_a129) groups=3003(inet),9997(everybody) context=u:r:untrusted_app:s0")
                }
            }
            "whoami" -> {
                newLines += GuestTerminalLine(if (session.guestRootGrantedInShell && instance.rootEnabled) "root" else "u0_a129")
            }
            "pwd" -> {
                newLines += GuestTerminalLine(nextCwd)
            }
            "uname" -> {
                val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "aarch64"
                newLines += GuestTerminalLine("Linux localhost 4.14.180-LitedirtVM-Q #1 SMP PREEMPT $abi Android/10")
            }
            "getprop" -> {
                val buildProp = File(rootfs, "system/build.prop")
                val props = if (buildProp.exists()) buildProp.readLines().filter { it.isNotBlank() && !it.startsWith("#") } else emptyList()
                val filterKey = parts.getOrNull(1)
                val filtered = if (filterKey != null) props.filter { it.contains(filterKey, ignoreCase = true) } else props
                if (filtered.isEmpty()) {
                    newLines += GuestTerminalLine("(tidak ada properti yang cocok)")
                } else {
                    filtered.forEach { line ->
                        val k = line.substringBefore('=')
                        val v = line.substringAfter('=', "")
                        newLines += GuestTerminalLine("[$k]: [$v]")
                    }
                }
            }
            "ls" -> {
                val targetArg = parts.getOrNull(1) ?: nextCwd
                val resolved = resolveVirtualPath(rootfs, nextCwd, targetArg)
                if (resolved != null && resolved.exists() && resolved.isDirectory) {
                    val entries = resolved.listFiles()?.sortedBy { it.name } ?: emptyList()
                    if (entries.isEmpty()) {
                        newLines += GuestTerminalLine("(direktori kosong)")
                    } else {
                        entries.forEach { f ->
                            val marker = if (f.isDirectory) "/" else " (${f.length()} B)"
                            newLines += GuestTerminalLine("${f.name}$marker")
                        }
                    }
                } else {
                    newLines += GuestTerminalLine("ls: '$targetArg': Tidak ditemukan atau bukan direktori.", isError = true)
                }
            }
            "cd" -> {
                val targetArg = parts.getOrNull(1) ?: "/sdcard/VirtualShared"
                val resolved = resolveVirtualPath(rootfs, nextCwd, targetArg)
                if (resolved != null && resolved.exists() && resolved.isDirectory) {
                    val rel = resolved.relativeTo(rootfs).path
                    nextCwd = "/" + rel.trimStart('/')
                } else {
                    newLines += GuestTerminalLine("cd: '$targetArg': Direktori tidak ditemukan.", isError = true)
                }
            }
            "cat" -> {
                val targetArg = parts.getOrNull(1)
                if (targetArg == null) {
                    newLines += GuestTerminalLine("Penggunaan: cat <path>", isError = true)
                } else {
                    val resolved = resolveVirtualPath(rootfs, nextCwd, targetArg)
                    if (resolved != null && resolved.exists() && resolved.isFile) {
                        val content = resolved.readLines().take(40)
                        content.forEach { newLines += GuestTerminalLine(it) }
                    } else {
                        newLines += GuestTerminalLine("cat: '$targetArg': Berkas tidak ditemukan.", isError = true)
                    }
                }
            }
            "pm" -> {
                if (parts.getOrNull(1) == "list" && parts.getOrNull(2) == "packages") {
                    val systemPkgs = listOf(
                        "package:android",
                        "package:com.android.systemui",
                        "package:com.android.settings",
                        "package:com.virtualos.terminal",
                        "package:com.virtualos.files"
                    )
                    systemPkgs.forEach { newLines += GuestTerminalLine(it) }
                    val installed = repository.getAppsForInstanceOnce(instance.id)
                    installed.forEach { newLines += GuestTerminalLine("package:${it.packageName}") }
                } else {
                    newLines += GuestTerminalLine("Gunakan: pm list packages")
                }
            }
            "ifconfig" -> {
                newLines += GuestTerminalLine("eth0: flags=4163<UP,BROADCAST,RUNNING,MULTICAST> mtu 1500")
                newLines += GuestTerminalLine("      inet ${instance.networkMode.virtualIp} netmask 255.255.255.0")
                newLines += GuestTerminalLine("      mode: ${instance.networkMode.label}")
            }
            "free" -> {
                val usedMb = (instance.ramLimitMb * 0.34f).toInt()
                val freeMb = (instance.ramLimitMb - usedMb).coerceAtLeast(64)
                newLines += GuestTerminalLine("              total        used        free")
                newLines += GuestTerminalLine("Mem:         ${instance.ramLimitMb.toString().padStart(5)}       ${usedMb.toString().padStart(5)}       ${freeMb.toString().padStart(5)}")
            }
            "df" -> {
                val usedKb = (rootfs.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024L).coerceAtLeast(128L)
                newLines += GuestTerminalLine("Filesystem          Size    Used    Mounted on")
                newLines += GuestTerminalLine("/dev/virtual_data   32G     ${usedKb}K    /data")
                newLines += GuestTerminalLine("host_shared         Host    ${usedKb}K    /sdcard/VirtualShared")
            }
            else -> {
                newLines += GuestTerminalLine("sh: ${parts[0]}: perintah tidak dikenali. Ketik 'help'.", isError = true)
            }
        }

        _sessions.update { map ->
            val cur = map[instance.id] ?: return@update map
            map + (instance.id to cur.copy(
                guestRootGrantedInShell = nextRootState,
                pendingSuperuserPrompt = openPrompt,
                currentGuestCwd = nextCwd,
                terminalHistory = (cur.terminalHistory + newLines).takeLast(140)
            ))
        }
    }

    fun syncBuildProp(instance: VirtualInstance, rootfsDir: File) {
        File(rootfsDir, "system").mkdirs()
        File(rootfsDir, "sdcard/VirtualShared").mkdirs()
        val propText = buildString {
            appendLine("# LitedirtVM Android 10 (API 29) — Sinkronisasi Instance ${instance.id}")
            appendLine("ro.build.version.release=10")
            appendLine("ro.build.version.sdk=29")
            appendLine("ro.build.version.codename=REL")
            appendLine("ro.build.id=QP1A.191105.004")
            appendLine("ro.build.display.id=LitedirtVM-Q-${instance.id}")
            appendLine("ro.product.brand=${instance.spoofBrand}")
            appendLine("ro.product.model=${instance.spoofModel}")
            appendLine("ro.product.manufacturer=${instance.spoofManufacturer}")
            appendLine("persist.radio.imei=${instance.spoofImei}")
            appendLine("ro.virtualos.android_id=${instance.spoofAndroidId}")
            appendLine("ro.sf.lcd_density=${instance.dpi}")
            appendLine("ro.virtualos.resolution=${instance.widthPx}x${instance.heightPx}")
            appendLine("ro.virtualos.root_enabled=${if (instance.rootEnabled) "1" else "0"}")
            appendLine("ro.debuggable=${if (instance.rootEnabled) "1" else "0"}")
            appendLine("persist.virtualos.net_mode=${instance.networkMode.name}")
            appendLine("persist.virtualos.mock_gps=${if (instance.mockLocationEnabled) "${instance.mockLatitude},${instance.mockLongitude}" else "disabled"}")
        }
        File(rootfsDir, "system/build.prop").writeText(propText)
    }

    private fun resolveVirtualPath(rootfs: File, cwd: String, input: String): File? {
        val normalized = if (input.startsWith("/")) input else "$cwd/$input"
        val canonicalRoot = rootfs.canonicalFile
        val target = File(canonicalRoot, normalized.trimStart('/')).canonicalFile
        return if (target.path.startsWith(canonicalRoot.path)) target else null
    }

    private fun probeLocalPort(port: Int): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 180)
                true
            }
        }.getOrDefault(false)
    }

    fun appendLog(instanceId: String, tag: String, message: String, level: String = "INFO") {
        val entry = VirtualLogEntry(
            timestamp = System.currentTimeMillis(),
            instanceId = instanceId,
            tag = tag,
            level = level,
            message = message
        )
        _logs.update { (listOf(entry) + it).take(150) }
    }

    fun formatUptime(startedAt: Long): String {
        val sec = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun com.example.data.model.NetworkMode.shortName(): String =
        if (this == com.example.data.model.NetworkMode.NAT) "NAT" else "Bridge"
}
