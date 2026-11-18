package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.InstanceState
import com.example.data.model.VirtualInstance
import com.example.data.model.VirtualOsArchitectureMode
import com.example.ui.VirtualOsUiState
import com.example.ui.theme.RootBadgeColor
import com.example.ui.theme.StatusBooting
import com.example.ui.theme.StatusPaused
import com.example.ui.theme.StatusRunning
import com.example.ui.theme.StatusStopped

@Composable
fun InstancesScreen(
    state: VirtualOsUiState,
    onStartInstance: (VirtualInstance) -> Unit,
    onPauseInstance: (String) -> Unit,
    onStopInstance: (String) -> Unit,
    onStopAll: () -> Unit,
    onOpenDisplay: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
    onCloneInstance: (VirtualInstance) -> Unit,
    onDeleteInstance: (VirtualInstance) -> Unit,
    onExportInstance: (VirtualInstance, android.net.Uri) -> Unit,
    onImportInstance: (android.net.Uri) -> Unit,
    onCreateInstance: (
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
    ) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingExportInstance by remember { mutableStateOf<VirtualInstance?>(null) }
    var pendingDeleteInstance by remember { mutableStateOf<VirtualInstance?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val target = pendingExportInstance
        pendingExportInstance = null
        if (uri != null && target != null) {
            onExportInstance(target, uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportInstance(uri)
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Instance Baru") },
                modifier = Modifier.testTag("create_instance_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manajer Multi-Instance",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "${state.runningInstances.size} aktif dari ${state.instances.size} instance Android 10",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.testTag("import_vos_button")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Impor")
                        }
                        if (state.runningInstances.isNotEmpty()) {
                            TextButton(onClick = onStopAll) {
                                Text("Matikan Semua", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            items(state.instances, key = { it.id }) { inst ->
                val storageMb = state.telemetry.perInstanceStorageMb[inst.id] ?: 1L
                val ramUsedMb = state.telemetry.perInstanceRamMb[inst.id] ?: 0
                val cpuLoad = state.telemetry.perInstanceCpuPercent[inst.id] ?: 0f

                InstanceCard(
                    instance = inst,
                    isActiveSelected = inst.id == state.activeInstance?.id,
                    storageMb = storageMb,
                    ramUsedMb = ramUsedMb,
                    cpuPercent = cpuLoad,
                    onStart = { onStartInstance(inst) },
                    onPause = { onPauseInstance(inst.id) },
                    onStop = { onStopInstance(inst.id) },
                    onOpenDisplay = { onOpenDisplay(inst.id) },
                    onOpenSettings = { onOpenSettings(inst.id) },
                    onClone = { onCloneInstance(inst) },
                    onExport = {
                        pendingExportInstance = inst
                        exportLauncher.launch("${inst.name.replace(" ", "_")}.vos")
                    },
                    onDelete = { pendingDeleteInstance = inst }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateInstanceDialog(
            state = state,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, romId, mode, w, h, dpi, ram, cpu, root, brand, model ->
                onCreateInstance(name, romId, mode, w, h, dpi, ram, cpu, root, brand, model)
                showCreateDialog = false
            }
        )
    }

    pendingDeleteInstance?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteInstance = null },
            title = { Text("Hapus Instance?") },
            text = {
                Text("Instance '${target.name}' beserta seluruh data Android 10 & APK terpasang di dalamnya akan dihapus permanen.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteInstance(target)
                        pendingDeleteInstance = null
                    }
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteInstance = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun InstanceCard(
    instance: VirtualInstance,
    isActiveSelected: Boolean,
    storageMb: Long,
    ramUsedMb: Int,
    cpuPercent: Float,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenSettings: () -> Unit,
    onClone: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (instance.state) {
        InstanceState.RUNNING -> StatusRunning
        InstanceState.BOOTING -> StatusBooting
        InstanceState.PAUSED -> StatusPaused
        else -> StatusStopped
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActiveSelected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("instance_card_${instance.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = instance.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Android 10 (API 29) • ${instance.engineMode.shortLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = instance.state.label,
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                InfoPill("${instance.widthPx}x${instance.heightPx}")
                InfoPill("${instance.cpuCores} Core / ${instance.ramLimitMb} MB")
                InfoPill(if (instance.rootEnabled) "#SU Root" else "Non-Root", highlight = instance.rootEnabled)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (instance.state == InstanceState.RUNNING) {
                    "RAM aktif: $ramUsedMb MB • CPU: ${"%.1f".format(cpuPercent)}% • Disk: $storageMb MB • VNC:${instance.vncPort}"
                } else {
                    "Model: ${instance.spoofBrand} ${instance.spoofModel} • Disk: $storageMb MB • ${instance.networkMode.name}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (instance.state == InstanceState.RUNNING || instance.state == InstanceState.BOOTING) {
                        Button(onClick = onOpenDisplay, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                            Icon(Icons.Default.Monitor, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Layar")
                        }
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = "Jeda")
                        }
                        IconButton(onClick = onStop) {
                            Icon(Icons.Default.Stop, contentDescription = "Hentikan", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Button(onClick = onStart, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Jalankan")
                        }
                        OutlinedButton(onClick = onOpenDisplay, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("Buka")
                        }
                    }
                }

                Row {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Pengaturan")
                    }
                    IconButton(onClick = onClone) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Kloning")
                    }
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Ekspor .vos")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String, highlight: Boolean = false) {
    Surface(
        color = if (highlight) RootBadgeColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = if (highlight) RootBadgeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CreateInstanceDialog(
    state: VirtualOsUiState,
    onDismiss: () -> Unit,
    onConfirm: (
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
    ) -> Unit
) {
    var name by remember { mutableStateOf("Android 10 #${state.instances.size + 1}") }
    var selectedRomId by remember { mutableStateOf(state.romPackages.firstOrNull()?.id ?: "") }
    var engineMode by remember { mutableStateOf(VirtualOsArchitectureMode.PROOT_CONTAINER) }
    var resPreset by remember { mutableStateOf("1080x1920") }
    var ramMb by remember { mutableFloatStateOf(2048f) }
    var cpuCores by remember { mutableFloatStateOf(4f) }
    var rootEnabled by remember { mutableStateOf(true) }
    var spoofModel by remember { mutableStateOf("Pixel 4") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat Instance Android 10") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama Instance") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("ROM Android 10 (API 29)", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    state.romPackages.forEach { rom ->
                        FilterChip(
                            selected = selectedRomId == rom.id,
                            onClick = { selectedRomId = rom.id },
                            label = { Text(rom.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    Text("Engine Virtualisasi", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VirtualOsArchitectureMode.entries.forEach { mode ->
                            FilterChip(
                                selected = engineMode == mode,
                                onClick = { engineMode = mode },
                                label = { Text(mode.shortLabel) }
                            )
                        }
                    }
                }
                item {
                    Text("Resolusi Layar", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("720x1280", "1080x1920", "1080x2400").forEach { preset ->
                            FilterChip(
                                selected = resPreset == preset,
                                onClick = { resPreset = preset },
                                label = { Text(preset) }
                            )
                        }
                    }
                }
                item {
                    Text("Alokasi RAM: ${ramMb.toInt()} MB", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = ramMb,
                        onValueChange = { ramMb = it },
                        valueRange = 1024f..6144f,
                        steps = 9
                    )
                    Text("CPU Virtual: ${cpuCores.toInt()} Core", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = cpuCores,
                        onValueChange = { cpuCores = it },
                        valueRange = 1f..8f,
                        steps = 6
                    )
                }
                item {
                    OutlinedTextField(
                        value = spoofModel,
                        onValueChange = { spoofModel = it },
                        label = { Text("Model Perangkat Palsu") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Aktifkan Akses Root (su)", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = rootEnabled, onCheckedChange = { rootEnabled = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val (w, h) = when (resPreset) {
                        "720x1280" -> 720 to 1280
                        "1080x2400" -> 1080 to 2400
                        else -> 1080 to 1920
                    }
                    val dpi = if (w == 720) 320 else 420
                    onConfirm(
                        name,
                        selectedRomId,
                        engineMode,
                        w,
                        h,
                        dpi,
                        ramMb.toInt(),
                        cpuCores.toInt(),
                        rootEnabled,
                        "Google",
                        spoofModel
                    )
                }
            ) {
                Text("Buat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
