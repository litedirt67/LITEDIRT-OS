package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.RomPackage
import com.example.data.model.ThemePreference
import com.example.data.model.VirtualOsArchitectureMode
import com.example.engine.ContainerEngineConfig
import com.example.ui.VirtualOsUiState
import com.example.ui.theme.RootBadgeColor
import com.example.ui.theme.StatusRunning

@Composable
fun SystemAndRomScreen(
    state: VirtualOsUiState,
    onImportLocalRom: (android.net.Uri, VirtualOsArchitectureMode) -> Unit,
    onDownloadRomUrl: (String, String?, VirtualOsArchitectureMode) -> Unit,
    onDismissRomProgress: () -> Unit,
    onDeleteRom: (RomPackage) -> Unit,
    onRefreshPrivilege: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onRunHostDiagnostic: (String) -> Unit,
    onSetThemePreference: (ThemePreference) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetKeepBackgroundService: (Boolean) -> Unit
) {
    var showUrlDialog by remember { mutableStateOf(false) }
    var importEngineMode by remember { mutableStateOf(VirtualOsArchitectureMode.PROOT_CONTAINER) }

    val romPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportLocalRom(uri, importEngineMode)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(text = "Sistem, ROM & Hak Akses", style = MaterialTheme.typography.headlineMedium)
        }

        // Banner Manajer Image ROM Android 10 (API 29)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_rom_banner),
                            contentDescription = "Banner ROM Android 10",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = Color(0xAA0A0F17),
                            modifier = Modifier.fillMaxSize()
                        ) {}
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp)
                        ) {
                            Surface(
                                color = StatusRunning.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "HANYA ANDROID 10 • API 29",
                                    color = StatusRunning,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Manajer Image ROM Android 10",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { romPicker.launch(arrayOf("*/*")) },
                                modifier = Modifier.testTag("import_rom_local_button")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Impor ROM Lokal")
                            }
                            OutlinedButton(
                                onClick = { showUrlDialog = true },
                                modifier = Modifier.testTag("download_rom_url_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Unduh URL")
                            }
                        }

                        state.romTransferProgress?.let { prog ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(prog.stage, style = MaterialTheme.typography.labelLarge)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { prog.progressFraction.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (prog.completedRom != null || prog.errorMessage != null) {
                                        TextButton(onClick = onDismissRomProgress) {
                                            Text("Tutup")
                                        }
                                    }
                                }
                            }
                        }

                        state.romPackages.forEach { rom ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rom.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "API ${rom.apiLevel} • ${rom.abi} • ${rom.engineType.shortLabel}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "SHA-256: ${rom.sha256.take(18)}...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (!rom.isBuiltInBase) {
                                        IconButton(onClick = { onDeleteRom(rom) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus ROM", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Telemetri Host & VM (RAM / CPU / Storage)
        item {
            val t = state.telemetry
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pemantauan RAM, CPU & Penyimpanan", style = MaterialTheme.typography.titleMedium)
                    }

                    val ramRatio = if (t.hostTotalRamMb > 0) (t.hostUsedRamMb.toFloat() / t.hostTotalRamMb).coerceIn(0f, 1f) else 0f
                    TelemetryGauge(
                        label = "RAM Host (${t.hostUsedRamMb} / ${t.hostTotalRamMb} MB) • Alokasi VM ${t.allocatedVmRamMb} MB",
                        progress = ramRatio
                    )

                    TelemetryGauge(
                        label = "CPU Host (${"%.1f".format(t.hostCpuUsagePercent)}% • ${t.hostCpuCores} Core)",
                        progress = (t.hostCpuUsagePercent / 100f).coerceIn(0f, 1f)
                    )

                    val storageUsed = (t.hostTotalStorageMb - t.hostFreeStorageMb).coerceAtLeast(0L)
                    val storageRatio = if (t.hostTotalStorageMb > 0) (storageUsed.toFloat() / t.hostTotalStorageMb).coerceIn(0f, 1f) else 0f
                    TelemetryGauge(
                        label = "Penyimpanan Host (Sisa ${t.hostFreeStorageMb / 1024} GB) • LitedirtVM ${t.totalVirtualStorageMb} MB",
                        progress = storageRatio
                    )
                }
            }
        }

        // Integrasi Shizuku & Root Host
        item {
            val p = state.privilegeStatus
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = RootBadgeColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Root Host & Shizuku", style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = onRefreshPrivilege) {
                            Icon(Icons.Default.Refresh, contentDescription = "Segarkan status")
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Mode Aktif: ${p.activeCapabilityLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    StatusLine("Aplikasi Shizuku", if (p.shizukuInstalled) "Terpasang" else "Tidak Terpasang")
                    StatusLine("Shizuku Binder", if (p.shizukuBinderAlive) "Aktif (v${p.shizukuVersion})" else "Mati")
                    StatusLine("Izin Shizuku", if (p.shizukuPermissionGranted) "Diizinkan" else "Belum Diizinkan")
                    StatusLine("Binary su Host", p.hostSuPath ?: "Tidak ditemukan (Non-Root)")
                    StatusLine("/dev/kvm Host", if (p.kvmDeviceAccessible) "Dapat Diakses" else "Terkunci / Tidak Tersedia")
                    StatusLine("Kernel Host", p.hostKernelVersion)
                    StatusLine("SELinux", p.hostSelinuxStatus)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onRequestShizukuPermission,
                            enabled = p.shizukuBinderAlive && !p.shizukuPermissionGranted,
                            modifier = Modifier.testTag("request_shizuku_button")
                        ) {
                            Text(if (p.shizukuPermissionGranted) "Shizuku Diizinkan" else "Minta Izin Shizuku")
                        }
                        OutlinedButton(onClick = { onRunHostDiagnostic("id && uname -r") }) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Uji Shell Host")
                        }
                    }

                    state.hostDiagnosticOutput?.let { out ->
                        Surface(
                            color = Color(0xFF0A0F17),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = out,
                                color = Color(0xFF39E69B),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Preferensi Tema & Layanan Latar Depan
        item {
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tampilan & Layanan Latar Belakang", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { mode ->
                            FilterChip(
                                selected = state.themePreference == mode,
                                onClick = { onSetThemePreference(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Warna Dinamis Material You (Android 12+)", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = state.dynamicColor, onCheckedChange = onSetDynamicColor)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Foreground Service & Notifikasi Status", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Jaga instance Android 10 tetap berjalan di latar belakang",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = state.keepBackgroundService, onCheckedChange = onSetKeepBackgroundService)
                    }
                }
            }
        }

        // Penjelasan Jujur Arsitektur & Batasan Host/Kernel
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = RootBadgeColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Arsitektur & Batasan Teknis Jujur", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = ContainerEngineConfig.getArchitectureRationale(),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Ringkasan yang tidak dapat dicapai aplikasi non-root di perangkat host:\n" +
                            "• Panggilan kernel chroot(), mount(), dan manipulasi namespace jaringan Bridge/TAP asli memerlukan CAP_SYS_ADMIN (root host).\n" +
                            "• Akselerasi hardware VM (/dev/kvm) umumnya dibatasi oleh SELinux & vendor kernel pada Android komersial.\n" +
                            "• Virtualisasi SurfaceFlinger, Binder IPC penuh, dan menjalankan biner eksternal yang diunduh dari app_data diblokir kebijakan W^X Android 10+ (targetSdk >= 29).\n" +
                            "• Jendela mengambang lintas-aplikasi penuh tanpa batas memakai Picture-in-Picture (PiP) resmi & jendela mengambang in-app agar tetap patuh kebijakan izin Google Play.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showUrlDialog) {
        var url by remember { mutableStateOf("") }
        var sha by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Unduh Image ROM Android 10 (API 29)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Masukkan tautan langsung arsip rootfs/system image Android 10 (.zip / .tar.gz / .img) yang Anda hosting.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL ROM Android 10") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = sha,
                        onValueChange = { sha = it },
                        label = { Text("SHA-256 Opsional") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onDownloadRomUrl(url.trim(), sha.ifBlank { null }, importEngineMode)
                    showUrlDialog = false
                }) {
                    Text("Unduh & Verifikasi")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun TelemetryGauge(label: String, progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}
