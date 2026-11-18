package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.HostInstalledApp
import com.example.data.model.HostPrivilegeStatus
import com.example.data.model.ImportedApp
import com.example.data.model.InstanceState
import com.example.data.model.ParsedApkMetadata
import com.example.data.model.RomPackage
import com.example.data.model.SharedFileItem
import com.example.data.model.SystemTelemetry
import com.example.data.model.ThemePreference
import com.example.data.model.VirtualInstance
import com.example.data.model.VirtualLogEntry
import com.example.data.model.VirtualOsArchitectureMode
import com.example.di.AppContainer
import com.example.engine.GuestAppDestination
import com.example.engine.RunningVmSession
import com.example.installer.RobloxStatus
import com.example.rom.RomTransferProgress
import com.example.service.VirtualOsForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VirtualOsUiState(
    val instances: List<VirtualInstance> = emptyList(),
    val activeInstanceId: String? = null,
    val sessions: Map<String, RunningVmSession> = emptyMap(),
    val romPackages: List<RomPackage> = emptyList(),
    val allImportedApps: List<ImportedApp> = emptyList(),
    val sharedFiles: List<SharedFileItem> = emptyList(),
    val telemetry: SystemTelemetry = SystemTelemetry(),
    val privilegeStatus: HostPrivilegeStatus = HostPrivilegeStatus(),
    val logs: List<VirtualLogEntry> = emptyList(),
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val dynamicColor: Boolean = false,
    val keepBackgroundService: Boolean = true,
    val isFloatingWindowOpen: Boolean = false,
    val isFullscreenDisplay: Boolean = false,
    val romTransferProgress: RomTransferProgress? = null,
    val inspectedApk: ParsedApkMetadata? = null,
    val hostApps: List<HostInstalledApp> = emptyList(),
    val isLoadingHostApps: Boolean = false,
    val robloxStatus: RobloxStatus = RobloxStatus(),
    val autoUpdateRoblox: Boolean = true,
    val importingPackageName: String? = null,
    val hostDiagnosticOutput: String? = null,
    val statusBannerMessage: String? = null
) {
    val activeInstance: VirtualInstance?
        get() = instances.firstOrNull { it.id == activeInstanceId } ?: instances.firstOrNull()

    val activeSession: RunningVmSession?
        get() = activeInstance?.let { sessions[it.id] }

    val runningInstances: List<VirtualInstance>
        get() = instances.filter { it.state == InstanceState.RUNNING || it.state == InstanceState.BOOTING }

    val activeInstanceApps: List<ImportedApp>
        get() = activeInstance?.let { inst -> allImportedApps.filter { it.instanceId == inst.id } } ?: emptyList()
}

class VirtualOsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = AppContainer.get(application)
    private val repo = container.repository
    private val romManager = container.romImageManager
    private val engine = container.runtimeEngine
    private val instanceManager = container.instanceManager
    private val apkInstaller = container.apkXapkInstaller
    private val robloxManager = container.robloxAutoUpdateManager
    private val sharedBridge = container.sharedStorageBridge
    private val privilegeManager = container.hostPrivilegeManager
    private val telemetrySampler = container.telemetrySampler

    private val _sharedFiles = MutableStateFlow<List<SharedFileItem>>(emptyList())
    private val _telemetry = MutableStateFlow(SystemTelemetry())
    private val _isFloatingWindowOpen = MutableStateFlow(false)
    private val _isFullscreenDisplay = MutableStateFlow(false)
    private val _romTransferProgress = MutableStateFlow<RomTransferProgress?>(null)
    private val _inspectedApk = MutableStateFlow<ParsedApkMetadata?>(null)
    private val _hostApps = MutableStateFlow<List<HostInstalledApp>>(emptyList())
    private val _isLoadingHostApps = MutableStateFlow(false)
    private val _robloxStatus = MutableStateFlow(RobloxStatus())
    private val _importingPackageName = MutableStateFlow<String?>(null)
    private val _hostDiagnosticOutput = MutableStateFlow<String?>(null)
    private val _statusBannerMessage = MutableStateFlow<String?>(null)

    private val coreDataFlow = combine(
        repo.instances,
        repo.activeInstanceId,
        engine.sessions,
        repo.romPackages,
        repo.allImportedApps
    ) { instances, activeId, sessions, roms, apps ->
        CoreBundle(instances, activeId, sessions, roms, apps)
    }

    private val settingsFlow = combine(
        repo.themePreference,
        repo.dynamicColor,
        repo.keepBackgroundService,
        privilegeManager.status,
        engine.logs
    ) { theme, dynamicColor, keepBg, privilege, logs ->
        SettingsBundle(theme, dynamicColor, keepBg, privilege, logs)
    }

    private val transientFlow = combine(
        _sharedFiles,
        _telemetry,
        _isFloatingWindowOpen,
        _isFullscreenDisplay,
        _romTransferProgress
    ) { shared, telemetry, floating, fullscreen, romProgress ->
        TransientBundle(shared, telemetry, floating, fullscreen, romProgress)
    }

    private val extraFlow = combine(
        _inspectedApk,
        _hostApps,
        _isLoadingHostApps,
        _hostDiagnosticOutput,
        _statusBannerMessage
    ) { apk, hostApps, loadingHost, diag, banner ->
        ExtraBundle(apk, hostApps, loadingHost, diag, banner)
    }

    private val robloxFlow = combine(
        _robloxStatus,
        repo.autoUpdateRoblox,
        _importingPackageName
    ) { status, autoUpdate, importingPkg ->
        Triple(status, autoUpdate, importingPkg)
    }

    val uiState: StateFlow<VirtualOsUiState> = combine(
        coreDataFlow,
        settingsFlow,
        transientFlow,
        extraFlow,
        robloxFlow
    ) { core, settings, transient, extra, roblox ->
        VirtualOsUiState(
            instances = core.instances,
            activeInstanceId = core.activeId,
            sessions = core.sessions,
            romPackages = core.roms,
            allImportedApps = core.apps,
            sharedFiles = transient.sharedFiles,
            telemetry = transient.telemetry,
            privilegeStatus = settings.privilege,
            logs = settings.logs,
            themePreference = settings.theme,
            dynamicColor = settings.dynamicColor,
            keepBackgroundService = settings.keepBg,
            isFloatingWindowOpen = transient.floating,
            isFullscreenDisplay = transient.fullscreen,
            romTransferProgress = transient.romProgress,
            inspectedApk = extra.inspectedApk,
            hostApps = extra.hostApps,
            isLoadingHostApps = extra.loadingHostApps,
            robloxStatus = roblox.first,
            autoUpdateRoblox = roblox.second,
            importingPackageName = roblox.third,
            hostDiagnosticOutput = extra.diagnosticOutput,
            statusBannerMessage = extra.bannerMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VirtualOsUiState()
    )

    init {
        viewModelScope.launch {
            val initial = instanceManager.ensureInitialInstance()
            refreshSharedFiles(initial.id)
            checkRobloxStatus(instanceId = initial.id, notifyIfUpdated = false)
        }
        refreshHostApps()

        // Pantau instance aktif untuk menyegarkan berkas bersama & menjaga Foreground Service
        viewModelScope.launch {
            combine(repo.instances, repo.keepBackgroundService) { instances, keepBg ->
                instances to keepBg
            }.collectLatest { (instances, keepBg) ->
                val activeCount = instances.count {
                    it.state == InstanceState.RUNNING || it.state == InstanceState.BOOTING
                }
                if (activeCount > 0 && keepBg) {
                    VirtualOsForegroundService.start(getApplication())
                } else if (activeCount == 0) {
                    VirtualOsForegroundService.stop(getApplication())
                }
            }
        }

        // Loop telemetri RAM/CPU/Penyimpanan setiap 2.5 detik
        viewModelScope.launch {
            while (isActive) {
                val instances = repo.getInstancesOnce()
                val loadMap = engine.sessions.value.mapValues { it.value.guestCpuLoadPercent }
                _telemetry.value = telemetrySampler.sample(instances, engine.instancesDir, loadMap)
                delay(2500)
            }
        }
    }

    fun selectActiveInstance(instanceId: String) {
        viewModelScope.launch {
            repo.setActiveInstanceId(instanceId)
            refreshSharedFiles(instanceId)
            checkRobloxStatus(instanceId = instanceId, notifyIfUpdated = false)
        }
    }

    fun startInstance(instance: VirtualInstance) {
        viewModelScope.launch {
            engine.startInstance(instance)
            refreshSharedFiles(instance.id)
        }
    }

    fun pauseInstance(instanceId: String) {
        viewModelScope.launch { engine.pauseInstance(instanceId) }
    }

    fun stopInstance(instanceId: String) {
        viewModelScope.launch { engine.stopInstance(instanceId) }
    }

    fun stopAllInstances() {
        viewModelScope.launch { engine.stopAllInstances() }
    }

    fun createInstance(
        name: String,
        romId: String,
        engineMode: VirtualOsArchitectureMode,
        widthPx: Int,
        heightPx: Int,
        dpi: Int,
        ramLimitMb: Int,
        cpuCores: Int,
        rootEnabled: Boolean,
        spoofBrand: String,
        spoofModel: String
    ) {
        viewModelScope.launch {
            val created = instanceManager.createInstance(
                name = name,
                romId = romId,
                engineMode = engineMode,
                widthPx = widthPx,
                heightPx = heightPx,
                dpi = dpi,
                ramLimitMb = ramLimitMb,
                cpuCores = cpuCores,
                rootEnabled = rootEnabled,
                spoofBrand = spoofBrand,
                spoofModel = spoofModel
            )
            refreshSharedFiles(created.id)
            showMessage("Instance '${created.name}' dibuat.")
        }
    }

    fun cloneInstance(source: VirtualInstance) {
        viewModelScope.launch {
            val cloned = instanceManager.cloneInstance(source)
            showMessage("Berhasil mengkloning ke '${cloned.name}'.")
        }
    }

    fun updateInstanceSettings(updated: VirtualInstance) {
        viewModelScope.launch {
            instanceManager.updateInstanceSettings(updated)
            showMessage("Pengaturan '${updated.name}' disimpan.")
        }
    }

    fun generateRandomImei(): String = instanceManager.generateLuhnImei()

    fun deleteInstance(instance: VirtualInstance) {
        viewModelScope.launch {
            instanceManager.deleteInstance(instance)
            showMessage("Instance '${instance.name}' dihapus.")
        }
    }

    fun exportInstance(instance: VirtualInstance, uri: Uri) {
        viewModelScope.launch {
            val result = instanceManager.exportInstanceBundle(instance, uri)
            result.onSuccess { bytes ->
                showMessage("Ekspor '${instance.name}' berhasil (${bytes / 1024} KB).")
            }.onFailure {
                showMessage("Gagal ekspor: ${it.message}")
            }
        }
    }

    fun importInstance(uri: Uri) {
        viewModelScope.launch {
            val result = instanceManager.importInstanceBundle(uri)
            result.onSuccess { inst ->
                refreshSharedFiles(inst.id)
                showMessage("Instance '${inst.name}' berhasil diimpor.")
            }.onFailure {
                showMessage("Gagal impor bundle: ${it.message}")
            }
        }
    }

    // --- Layar Virtual & Guest OS ---
    fun openGuestApp(instanceId: String, destination: GuestAppDestination) {
        engine.openGuestApp(instanceId, destination)
    }

    fun pressGuestBack(instanceId: String) = engine.pressGuestBack(instanceId)
    fun pressGuestHome(instanceId: String) = engine.pressGuestHome(instanceId)
    fun toggleGuestRecents(instanceId: String) = engine.toggleGuestRecents(instanceId)
    fun removeFromRecents(instanceId: String, destination: GuestAppDestination) =
        engine.removeFromRecents(instanceId, destination)

    fun rotateDisplay(instanceId: String) = engine.rotateDisplay(instanceId)
    fun updateBrowserUrl(instanceId: String, url: String) = engine.updateBrowserUrl(instanceId, url)

    fun executeGuestCommand(instance: VirtualInstance, command: String) {
        viewModelScope.launch {
            engine.executeGuestCommand(instance, command)
        }
    }

    fun resolveSuperuserPrompt(instanceId: String, granted: Boolean) {
        engine.resolveSuperuserPrompt(instanceId, granted)
    }

    fun refreshVncProbe(instance: VirtualInstance) {
        viewModelScope.launch {
            engine.refreshVncProbe(instance)
        }
    }

    fun syncClipboardToHost(instance: VirtualInstance, text: String) {
        engine.syncClipboardToHost(instance, text)
        showMessage("Teks disalin ke clipboard host.")
    }

    fun readHostClipboard(instance: VirtualInstance): String? = engine.readHostClipboard(instance)

    fun setFloatingWindowOpen(open: Boolean) {
        _isFloatingWindowOpen.value = open
    }

    fun setFullscreenDisplay(fullscreen: Boolean) {
        _isFullscreenDisplay.value = fullscreen
    }

    // --- Impor Aplikasi Host & Berkas APK / XAPK ---
    fun refreshHostApps() {
        viewModelScope.launch {
            _isLoadingHostApps.value = true
            _hostApps.value = apkInstaller.loadHostLaunchableApps()
            _isLoadingHostApps.value = false
        }
    }

    fun importHostApp(hostApp: HostInstalledApp, instanceId: String) {
        if (_importingPackageName.value != null) return
        viewModelScope.launch {
            _importingPackageName.value = hostApp.packageName
            try {
                val res = apkInstaller.importInstalledHostApp(hostApp, instanceId)
                res.onSuccess { app ->
                    showMessage("${app.appName} berhasil diimpor ke Android 10 virtual.")
                    if (app.packageName == com.example.installer.RobloxAutoUpdateManager.ROBLOX_PACKAGE) {
                        checkRobloxStatus(instanceId = instanceId, notifyIfUpdated = false)
                    }
                }.onFailure {
                    showMessage("Gagal mengimpor ${hostApp.appName}: ${it.message}")
                }
            } finally {
                _importingPackageName.value = null
            }
        }
    }

    fun checkRobloxStatus(
        instanceId: String? = uiState.value.activeInstance?.id,
        notifyIfUpdated: Boolean = true
    ) {
        viewModelScope.launch {
            _robloxStatus.update { it.copy(isChecking = true) }
            val autoSync = uiState.value.autoUpdateRoblox
            val status = robloxManager.checkAndAutoSync(instanceId, autoSync)
            _robloxStatus.value = status
            if (notifyIfUpdated && status.autoSyncedThisCheck) {
                showMessage(status.statusMessage)
            }
        }
    }

    fun syncRobloxToInstance(instanceId: String) {
        viewModelScope.launch {
            _robloxStatus.update { it.copy(isChecking = true) }
            val status = robloxManager.checkAndAutoSync(instanceId, autoSyncEnabled = true)
            _robloxStatus.value = status
            showMessage(status.statusMessage)
        }
    }

    fun setAutoUpdateRoblox(enabled: Boolean) {
        viewModelScope.launch {
            repo.setAutoUpdateRoblox(enabled)
            if (enabled) {
                checkRobloxStatus(notifyIfUpdated = true)
            }
        }
    }

    fun openRobloxPlayStore(context: android.content.Context) {
        robloxManager.openRobloxInPlayStore(context)
    }

    fun onAppResumed() {
        refreshHostApps()
        checkRobloxStatus(notifyIfUpdated = true)
    }

    fun inspectApkUri(uri: Uri) {
        viewModelScope.launch {
            _importingPackageName.value = "__inspect__"
            try {
                val result = apkInstaller.inspectPackageFromUri(uri)
                result.onSuccess { meta ->
                    _inspectedApk.value = meta
                }.onFailure {
                    showMessage("Gagal membaca APK/XAPK: ${it.message}")
                }
            } finally {
                _importingPackageName.value = null
            }
        }
    }

    fun clearInspectedApk() {
        _inspectedApk.value = null
    }

    fun installInspectedApk(instanceId: String) {
        val meta = _inspectedApk.value ?: return
        viewModelScope.launch {
            _importingPackageName.value = "__install__"
            try {
                val res = apkInstaller.installToInstance(meta, instanceId)
                res.onSuccess { app ->
                    _inspectedApk.value = null
                    showMessage("${app.appName} terpasang ke instance virtual.")
                    if (app.packageName == com.example.installer.RobloxAutoUpdateManager.ROBLOX_PACKAGE) {
                        checkRobloxStatus(instanceId = instanceId, notifyIfUpdated = false)
                    }
                }.onFailure {
                    showMessage("Gagal memasang: ${it.message}")
                }
            } finally {
                _importingPackageName.value = null
            }
        }
    }

    fun launchAppFromVm(context: android.content.Context, app: ImportedApp) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(launchIntent) }
                .onFailure { showMessage("Gagal membuka ${app.appName}: ${it.message}") }
        } else {
            showMessage("${app.appName} tersimpan di rootfs VM (/data/app/${app.packageName}). Pasang di host agar bisa dijalankan langsung dari Android host.")
        }
    }

    fun uninstallImportedApp(app: ImportedApp) {
        viewModelScope.launch {
            apkInstaller.uninstallFromInstance(app)
            showMessage("${app.appName} dihapus dari instance.")
            if (app.packageName == com.example.installer.RobloxAutoUpdateManager.ROBLOX_PACKAGE) {
                checkRobloxStatus(instanceId = app.instanceId, notifyIfUpdated = false)
            }
        }
    }

    fun refreshSharedFiles(instanceId: String? = uiState.value.activeInstance?.id) {
        if (instanceId == null) return
        viewModelScope.launch {
            _sharedFiles.value = sharedBridge.listSharedFiles(instanceId)
        }
    }

    fun importFileToShared(instanceId: String, uri: Uri) {
        viewModelScope.launch {
            val res = sharedBridge.importFileFromHost(instanceId, uri)
            res.onSuccess { item ->
                refreshSharedFiles(instanceId)
                showMessage("Disalin ke VirtualShared: ${item.name}")
            }.onFailure {
                showMessage("Gagal menyalin berkas: ${it.message}")
            }
        }
    }

    fun exportSharedFileToHost(item: SharedFileItem, destinationUri: Uri) {
        viewModelScope.launch {
            val res = sharedBridge.exportFileToHost(item, destinationUri)
            res.onSuccess { bytes ->
                showMessage("Diekspor ke host: ${item.name} (${bytes / 1024} KB)")
            }.onFailure {
                showMessage("Gagal mengekspor: ${it.message}")
            }
        }
    }

    fun createVirtualSharedNote(instanceId: String, fileName: String, content: String) {
        viewModelScope.launch {
            val res = sharedBridge.createNoteFileInVirtual(instanceId, fileName, content)
            res.onSuccess {
                refreshSharedFiles(instanceId)
                showMessage("Berkas '${it.name}' dibuat di /sdcard/VirtualShared.")
            }.onFailure {
                showMessage("Gagal membuat berkas: ${it.message}")
            }
        }
    }

    fun deleteSharedFile(instanceId: String, item: SharedFileItem) {
        viewModelScope.launch {
            sharedBridge.deleteSharedFile(instanceId, item)
            refreshSharedFiles(instanceId)
        }
    }

    // --- Manajer ROM Android 10 ---
    fun importLocalRom(uri: Uri, engineMode: VirtualOsArchitectureMode) {
        viewModelScope.launch {
            romManager.importLocalRom(uri, engineMode).collect { progress ->
                _romTransferProgress.value = progress
                if (progress.completedRom != null) {
                    showMessage("ROM '${progress.completedRom.name}' siap.")
                } else if (progress.errorMessage != null) {
                    showMessage(progress.errorMessage)
                }
            }
        }
    }

    fun downloadRomFromUrl(url: String, expectedSha256: String?, engineMode: VirtualOsArchitectureMode) {
        viewModelScope.launch {
            romManager.downloadRomFromUrl(url, expectedSha256, engineMode).collect { progress ->
                _romTransferProgress.value = progress
                if (progress.completedRom != null) {
                    showMessage("ROM Android 10 berhasil diunduh & diverifikasi.")
                } else if (progress.errorMessage != null) {
                    showMessage(progress.errorMessage)
                }
            }
        }
    }

    fun dismissRomProgress() {
        _romTransferProgress.value = null
    }

    fun deleteCustomRom(rom: RomPackage) {
        viewModelScope.launch {
            if (romManager.deleteCustomRom(rom)) {
                showMessage("ROM '${rom.name}' dihapus.")
            }
        }
    }

    // --- Shizuku, Root & Preferensi Host ---
    fun refreshPrivilegeStatus() {
        viewModelScope.launch {
            privilegeManager.refreshStatus()
        }
    }

    fun requestShizukuPermission() {
        val triggered = privilegeManager.requestShizukuPermission()
        if (!triggered) {
            showMessage("Layanan Shizuku tidak aktif. Buka aplikasi Shizuku dan mulai layanannya terlebih dahulu.")
        }
    }

    fun runHostDiagnostic(command: String) {
        viewModelScope.launch {
            val result = privilegeManager.runHostDiagnosticCommand(command)
            _hostDiagnosticOutput.value = result.getOrElse { "Galat: ${it.message}" }
        }
    }

    fun setThemePreference(mode: ThemePreference) {
        viewModelScope.launch { repo.setThemePreference(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repo.setDynamicColor(enabled) }
    }

    fun setKeepBackgroundService(enabled: Boolean) {
        viewModelScope.launch { repo.setKeepBackgroundService(enabled) }
    }

    fun clearMessage() {
        _statusBannerMessage.value = null
    }

    private fun showMessage(msg: String) {
        _statusBannerMessage.value = msg
    }

    override fun onCleared() {
        privilegeManager.cleanup()
        super.onCleared()
    }

    private data class CoreBundle(
        val instances: List<VirtualInstance>,
        val activeId: String?,
        val sessions: Map<String, RunningVmSession>,
        val roms: List<RomPackage>,
        val apps: List<ImportedApp>
    )

    private data class SettingsBundle(
        val theme: ThemePreference,
        val dynamicColor: Boolean,
        val keepBg: Boolean,
        val privilege: HostPrivilegeStatus,
        val logs: List<VirtualLogEntry>
    )

    private data class TransientBundle(
        val sharedFiles: List<SharedFileItem>,
        val telemetry: SystemTelemetry,
        val floating: Boolean,
        val fullscreen: Boolean,
        val romProgress: RomTransferProgress?
    )

    private data class ExtraBundle(
        val inspectedApk: ParsedApkMetadata?,
        val hostApps: List<HostInstalledApp>,
        val loadingHostApps: Boolean,
        val diagnosticOutput: String?,
        val bannerMessage: String?
    )
}
