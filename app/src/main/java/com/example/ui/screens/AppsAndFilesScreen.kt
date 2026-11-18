package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.HostInstalledApp
import com.example.data.model.ImportedApp
import com.example.data.model.SharedFileItem
import com.example.installer.RobloxStatus
import com.example.ui.VirtualOsUiState
import com.example.ui.theme.StatusRunning
import java.io.File

@Composable
fun AppsAndFilesScreen(
    state: VirtualOsUiState,
    onSelectInstance: (String) -> Unit,
    onRefreshHostApps: () -> Unit,
    onImportHostApp: (HostInstalledApp, String) -> Unit,
    onSyncRoblox: (String) -> Unit,
    onCheckRobloxUpdate: () -> Unit,
    onSetAutoUpdateRoblox: (Boolean) -> Unit,
    onOpenRobloxPlayStore: () -> Unit,
    onPickApkUri: (android.net.Uri) -> Unit,
    onInstallInspectedApk: (String) -> Unit,
    onClearInspectedApk: () -> Unit,
    onLaunchImportedApp: (ImportedApp) -> Unit,
    onUninstallApp: (ImportedApp) -> Unit,
    onImportSharedFile: (String, android.net.Uri) -> Unit,
    onExportSharedFile: (SharedFileItem, android.net.Uri) -> Unit,
    onCreateVirtualNote: (String, String, String) -> Unit,
    onDeleteSharedFile: (String, SharedFileItem) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val active = state.activeInstance
    var pendingExportItem by remember { mutableStateOf<SharedFileItem?>(null) }
    var showCreateNoteDialog by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var showSystemHostApps by remember { mutableStateOf(false) }
    var appSubMode by remember { mutableIntStateOf(0) } // 0 = Impor dari Perangkat, 1 = Terpasang di VM

    val apkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onPickApkUri(uri)
    }

    val sharedImportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && active != null) {
            onImportSharedFile(active.id, uri)
        }
    }

    val sharedExportCreator = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val target = pendingExportItem
        pendingExportItem = null
        if (uri != null && target != null) {
            onExportSharedFile(target, uri)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(text = "Impor Aplikasi & Berkas", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.instances, key = { it.id }) { inst ->
                    FilterChip(
                        selected = inst.id == active?.id,
                        onClick = { onSelectInstance(inst.id) },
                        label = { Text(inst.name) }
                    )
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Impor Aplikasi") },
                modifier = Modifier.testTag("tab_import_apk")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Berbagi Berkas") },
                modifier = Modifier.testTag("tab_shared_files")
            )
        }

        if (active == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Pilih atau buat instance terlebih dahulu.")
            }
            return
        }

        if (selectedTab == 0) {
            val installedAppMap = remember(state.activeInstanceApps) {
                state.activeInstanceApps.associateBy { it.packageName }
            }

            val filteredHostApps = remember(state.hostApps, searchQuery, showSystemHostApps) {
                state.hostApps.filter { app ->
                    val matchesType = showSystemHostApps || !app.isSystemApp
                    val matchesQuery = searchQuery.isBlank() ||
                        app.appName.contains(searchQuery, ignoreCase = true) ||
                        app.packageName.contains(searchQuery, ignoreCase = true)
                    matchesType && matchesQuery
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Switcher: Impor dari HP vs Sudah Terpasang di VM + Tombol File APK/XAPK opsional
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = appSubMode == 0,
                                onClick = { appSubMode = 0 },
                                label = { Text("Aplikasi Host (${state.hostApps.size})") }
                            )
                            FilterChip(
                                selected = appSubMode == 1,
                                onClick = { appSubMode = 1 },
                                label = { Text("Di VM (${state.activeInstanceApps.size})") }
                            )
                        }

                        OutlinedButton(
                            onClick = { apkPicker.launch(arrayOf("*/*")) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("pick_apk_xapk_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("File APK")
                        }
                    }
                }

                if (state.importingPackageName == "__inspect__") {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Membaca paket APK/XAPK...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // Pratinjau jika user memilih file .apk/.xapk manual
                state.inspectedApk?.let { meta ->
                    item {
                        val isInstallingFile = state.importingPackageName == "__install__"
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val bmp = remember(meta.iconCachePath) {
                                        meta.iconCachePath?.let {
                                            if (File(it).exists()) BitmapFactory.decodeFile(it)?.asImageBitmap() else null
                                        }
                                    }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = meta.appName,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                    } else {
                                        Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(40.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(meta.appName, style = MaterialTheme.typography.titleSmall)
                                        Text(meta.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${meta.sourceFormat} • v${meta.versionName} • ${formatSize(meta.sizeBytes)}", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onInstallInspectedApk(active.id) },
                                        enabled = !isInstallingFile,
                                        modifier = Modifier.testTag("confirm_install_apk_button")
                                    ) {
                                        if (isInstallingFile) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(if (isInstallingFile) "Memasang..." else "Pasang ke ${active.name}")
                                    }
                                    TextButton(onClick = onClearInspectedApk, enabled = !isInstallingFile) {
                                        Text("Batal")
                                    }
                                }
                            }
                        }
                    }
                }

                if (appSubMode == 0) {
                    item {
                        RobloxAutoUpdateCard(
                            instanceName = active.name,
                            status = state.robloxStatus,
                            autoUpdateEnabled = state.autoUpdateRoblox,
                            onToggleAutoUpdate = onSetAutoUpdateRoblox,
                            onSyncNow = { onSyncRoblox(active.id) },
                            onOpenPlayStore = onOpenRobloxPlayStore,
                            onCheckUpdate = onCheckRobloxUpdate
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Cari aplikasi di perangkat Anda...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Hapus pencarian")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !showSystemHostApps,
                                        onClick = { showSystemHostApps = false },
                                        label = { Text("Aplikasi Pengguna") }
                                    )
                                    FilterChip(
                                        selected = showSystemHostApps,
                                        onClick = { showSystemHostApps = true },
                                        label = { Text("Semua (+Sistem)") }
                                    )
                                }
                                IconButton(onClick = onRefreshHostApps) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Muat ulang daftar aplikasi")
                                }
                            }
                        }
                    }

                    if (state.isLoadingHostApps) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Memindai aplikasi terpasang di perangkat...")
                            }
                        }
                    } else if (filteredHostApps.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Tidak ada aplikasi pengguna yang cocok", style = MaterialTheme.typography.titleSmall)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Coba aktifkan filter 'Semua (+Sistem)' atau gunakan tombol 'File APK' di kanan atas.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredHostApps, key = { it.packageName }) { hostApp ->
                            val installedApp = installedAppMap[hostApp.packageName]
                            HostAppImportRow(
                                hostApp = hostApp,
                                installedApp = installedApp,
                                isImporting = state.importingPackageName == hostApp.packageName,
                                importBusy = state.importingPackageName != null,
                                onImportClick = { onImportHostApp(hostApp, active.id) }
                            )
                        }
                    }
                } else {
                    if (state.activeInstanceApps.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Belum ada aplikasi yang diimpor ke '${active.name}'. Pilih dari daftar Aplikasi Host untuk mengimpor langsung dengan satu ketukan.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(state.activeInstanceApps, key = { it.id }) { app ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    val bmp = remember(app.iconFilePath) {
                                        app.iconFilePath?.let {
                                            if (File(it).exists()) BitmapFactory.decodeFile(it)?.asImageBitmap() else null
                                        }
                                    }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = app.appName,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                    } else {
                                        Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(40.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.appName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${app.sourceFormat} • v${app.versionName} • ${formatSize(app.sizeBytes)}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = { onLaunchImportedApp(app) }) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = "Buka aplikasi", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { onUninstallApp(app) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus aplikasi", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Folder Berbagi Dua Arah", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Host ↔ /sdcard/VirtualShared. Berkas yang dimasukkan ke sini langsung terbaca di Manajer Berkas sistem Android 10 virtual.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { sharedImportPicker.launch(arrayOf("*/*")) },
                                    modifier = Modifier.testTag("import_shared_file_button")
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Host → Virtual")
                                }
                                OutlinedButton(onClick = { showCreateNoteDialog = true }) {
                                    Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Buat Berkas")
                                }
                            }
                        }
                    }
                }

                items(state.sharedFiles, key = { it.absolutePath }) { item ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = if (item.isDirectory) "Direktori" else formatSize(item.sizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!item.isDirectory) {
                                IconButton(
                                    onClick = {
                                        pendingExportItem = item
                                        sharedExportCreator.launch(item.name)
                                    }
                                ) {
                                    Icon(Icons.Default.FileUpload, contentDescription = "Ekspor ke Host")
                                }
                            }
                            IconButton(onClick = { onDeleteSharedFile(active.id, item) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus berkas", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateNoteDialog && active != null) {
        var fileName by remember { mutableStateOf("catatan_virtual.txt") }
        var content by remember { mutableStateOf("Berkas bersama antara host dan Android 10 virtual.") }
        AlertDialog(
            onDismissRequest = { showCreateNoteDialog = false },
            title = { Text("Buat Berkas di VirtualShared") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("Nama Berkas") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Isi Teks") },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCreateVirtualNote(active.id, fileName, content)
                    showCreateNoteDialog = false
                }) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateNoteDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun HostAppImportRow(
    hostApp: HostInstalledApp,
    installedApp: ImportedApp?,
    isImporting: Boolean,
    importBusy: Boolean,
    onImportClick: () -> Unit
) {
    val alreadyInstalled = installedApp != null
    val hasNewerHostVersion = installedApp != null && hostApp.versionCode > installedApp.versionCode

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            val bmp = remember(hostApp.iconCachePath) {
                hostApp.iconCachePath?.let { path ->
                    if (File(path).exists()) BitmapFactory.decodeFile(path)?.asImageBitmap() else null
                }
            }
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = hostApp.appName,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(11.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = hostApp.appName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hostApp.appName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = hostApp.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "v${hostApp.versionName} • ${formatSize(hostApp.totalSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            when {
                isImporting -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(22.dp),
                        strokeWidth = 2.dp
                    )
                }
                hasNewerHostVersion -> {
                    Button(
                        onClick = onImportClick,
                        enabled = !importBusy,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("update_host_app_${hostApp.packageName}")
                    ) {
                        Text("Update")
                    }
                }
                alreadyInstalled -> {
                    AssistChip(
                        onClick = { if (!importBusy) onImportClick() },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusRunning,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Terpasang") }
                    )
                }
                else -> {
                    FilledTonalButton(
                        onClick = onImportClick,
                        enabled = !importBusy,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("import_host_app_${hostApp.packageName}")
                    ) {
                        Text("Impor")
                    }
                }
            }
        }
    }
}

@Composable
private fun RobloxAutoUpdateCard(
    instanceName: String,
    status: RobloxStatus,
    autoUpdateEnabled: Boolean,
    onToggleAutoUpdate: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val bmp = remember(status.hostApp?.iconCachePath) {
                    status.hostApp?.iconCachePath?.let { path ->
                        if (File(path).exists()) BitmapFactory.decodeFile(path)?.asImageBitmap() else null
                    }
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Roblox",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(11.dp))
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = "Roblox",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Roblox • Auto-Update",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "com.roblox.client",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onCheckUpdate,
                    enabled = !status.isChecking,
                    modifier = Modifier.testTag("roblox_check_update_button")
                ) {
                    if (status.isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Cek pembaruan Roblox")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Host: ${status.hostVersionName?.let { "v$it" } ?: "Belum terpasang"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "VM ($instanceName): ${status.vmVersionName?.let { "v$it" } ?: "Belum ada"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status.vmInstalled) StatusRunning else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!status.latestRemoteVersion.isNullOrBlank()) {
                Text(
                    text = "Rilis Resmi Terbaru: v${status.latestRemoteVersion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = status.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-update ke versi terbaru", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Otomatis sinkron ke VM saat versi baru tersedia di host",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoUpdateEnabled,
                    onCheckedChange = onToggleAutoUpdate,
                    modifier = Modifier.testTag("auto_update_roblox_switch")
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (status.hostInstalled) {
                    Button(
                        onClick = onSyncNow,
                        enabled = !status.isChecking,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("roblox_sync_button")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            when {
                                !status.vmInstalled -> "Tambah ke VM"
                                status.needsVmSyncFromHost -> "Update VM (v${status.hostVersionName})"
                                else -> "Sinkron Ulang"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenPlayStore,
                        modifier = Modifier.testTag("roblox_playstore_button")
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (status.needsPlayStoreInstallOrUpdate) "Update Play Store" else "Play Store")
                    }
                } else {
                    Button(
                        onClick = onOpenPlayStore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("roblox_playstore_button")
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pasang Roblox via Play Store")
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) String.format(java.util.Locale.US, "%.1f MB", mb)
    else "${kb.toInt().coerceAtLeast(1)} KB"
}
