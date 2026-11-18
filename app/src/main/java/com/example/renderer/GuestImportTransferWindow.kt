package com.example.renderer

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HostInstalledApp
import com.example.data.model.ImportedApp
import com.example.data.model.ParsedApkMetadata
import com.example.data.model.SharedFileItem
import com.example.ui.theme.StatusRunning
import java.io.File
import java.util.Locale

@Composable
fun GuestImportTransferWindow(
    instanceName: String,
    hostApps: List<HostInstalledApp>,
    installedApps: List<ImportedApp>,
    sharedFiles: List<SharedFileItem>,
    inspectedApk: ParsedApkMetadata?,
    importingPackageName: String?,
    isLoadingHostApps: Boolean,
    compactMode: Boolean,
    onRefreshHostApps: () -> Unit,
    onImportHostApp: (HostInstalledApp) -> Unit,
    onPickApkUri: (Uri) -> Unit,
    onInstallInspectedApk: () -> Unit,
    onClearInspectedApk: () -> Unit,
    onImportSharedFile: (Uri) -> Unit,
    onExportSharedFile: (SharedFileItem, Uri) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Aplikasi Host, 1 = File APK/XAPK, 2 = File Bersama
    var query by remember { mutableStateOf("") }
    var pendingExportItem by remember { mutableStateOf<SharedFileItem?>(null) }

    val apkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedTab = 1
            onPickApkUri(uri)
        }
    }

    val fileImportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportSharedFile(uri)
    }

    val fileExportCreator = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val target = pendingExportItem
        pendingExportItem = null
        if (uri != null && target != null) {
            onExportSharedFile(target, uri)
        }
    }

    val installedByPkg = remember(installedApps) {
        installedApps.associateBy { it.packageName }
    }

    val filteredHostApps = remember(hostApps, query) {
        hostApps.filter { app ->
            !app.isSystemApp && (
                query.isBlank() ||
                    app.appName.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
                )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(if (compactMode) 6.dp else 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Impor File & APK",
                    color = Color(0xFF202124),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (compactMode) 12.sp else 15.sp
                )
                if (!compactMode) {
                    Text(
                        text = "Target: $instanceName",
                        color = Color(0xFF5F6368),
                        fontSize = 11.sp
                    )
                }
            }
            if (selectedTab == 0) {
                IconButton(onClick = onRefreshHostApps, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Segarkan aplikasi host", tint = Color(0xFF1A73E8), modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TransferSegmentChip(
                label = "Aplikasi",
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier
                    .weight(1f)
                    .testTag("vm_import_tab_apps")
            )
            TransferSegmentChip(
                label = "File APK",
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                modifier = Modifier
                    .weight(1f)
                    .testTag("vm_import_tab_apk")
            )
            TransferSegmentChip(
                label = "File",
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                modifier = Modifier
                    .weight(1f)
                    .testTag("vm_import_tab_files")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTab) {
            0 -> {
                if (!compactMode) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Cari aplikasi di HP...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Hapus", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (isLoadingHostApps) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else if (filteredHostApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Tidak ada aplikasi pengguna yang ditemukan. Gunakan tab 'File APK' untuk memasang berkas .apk/.xapk.",
                            color = Color(0xFF5F6368),
                            fontSize = 11.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredHostApps, key = { it.packageName }) { hostApp ->
                            val installed = installedByPkg[hostApp.packageName]
                            val isImportingThis = importingPackageName == hostApp.packageName
                            val hasUpdate = installed != null && hostApp.versionCode > installed.versionCode
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(12.dp),
                                shadowElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
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
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Icon(Icons.Default.Android, contentDescription = null, tint = Color(0xFF1E8E3E), modifier = Modifier.size(32.dp))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = hostApp.appName,
                                            color = Color(0xFF202124),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "v${hostApp.versionName} • ${formatGuestBytes(hostApp.totalSizeBytes)}",
                                            color = Color(0xFF5F6368),
                                            fontSize = 10.sp
                                        )
                                    }
                                    when {
                                        isImportingThis -> {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        }
                                        hasUpdate -> {
                                            Button(
                                                onClick = { onImportHostApp(hostApp) },
                                                enabled = importingPackageName == null,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text("Update", fontSize = 11.sp)
                                            }
                                        }
                                        installed != null -> {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Terpasang",
                                                tint = StatusRunning,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        else -> {
                                            FilledTonalButton(
                                                onClick = { onImportHostApp(hostApp) },
                                                enabled = importingPackageName == null,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier
                                                    .height(30.dp)
                                                    .testTag("vm_import_host_btn_${hostApp.packageName}")
                                            ) {
                                                Text("Impor", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Pasang File APK / XAPK / APKS",
                                    color = Color(0xFF202124),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Pilih berkas paket Android dari penyimpanan perangkat untuk dipasang ke $instanceName.",
                                    color = Color(0xFF5F6368),
                                    fontSize = 11.sp
                                )
                                Button(
                                    onClick = { apkPicker.launch(arrayOf("*/*")) },
                                    enabled = importingPackageName == null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("vm_pick_apk_file_button")
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pilih File .APK / .XAPK", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (importingPackageName == "__inspect__") {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Membaca paket APK/XAPK...", color = Color(0xFF202124), fontSize = 12.sp)
                            }
                        }
                    }

                    inspectedApk?.let { meta ->
                        item {
                            val installing = importingPackageName == "__install__"
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0FE)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(meta.appName, color = Color(0xFF202124), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        text = "${meta.packageName}\n${meta.sourceFormat} • v${meta.versionName} • ${formatGuestBytes(meta.sizeBytes)}",
                                        color = Color(0xFF5F6368),
                                        fontSize = 11.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = onInstallInspectedApk,
                                            enabled = !installing,
                                            modifier = Modifier.testTag("vm_confirm_install_apk_button")
                                        ) {
                                            if (installing) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text(if (installing) "Memasang..." else "Pasang", fontSize = 12.sp)
                                        }
                                        TextButton(onClick = onClearInspectedApk, enabled = !installing) {
                                            Text("Batal", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { fileImportPicker.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vm_import_shared_file_button")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Impor File dari HP ke VM", fontSize = 12.sp)
                        }
                    }

                    if (sharedFiles.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Folder /sdcard/VirtualShared masih kosong.",
                                color = Color(0xFF5F6368),
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(sharedFiles, key = { it.absolutePath }) { item ->
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(10.dp),
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            tint = if (item.isDirectory) Color(0xFF1A73E8) else Color(0xFF5F6368),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, color = Color(0xFF202124), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(formatGuestBytes(item.sizeBytes), color = Color(0xFF5F6368), fontSize = 10.sp)
                                        }
                                        if (!item.isDirectory) {
                                            OutlinedButton(
                                                onClick = {
                                                    pendingExportItem = item
                                                    fileExportCreator.launch(item.name)
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Default.FileUpload, contentDescription = "Ekspor ke HP", modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Ekspor", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferSegmentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) Color(0xFF1A73E8) else Color(0xFFE8EAED),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
        ) {
            Text(
                text = label,
                color = if (selected) Color.White else Color(0xFF3C4043),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatGuestBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb)
    else "${kb.toInt().coerceAtLeast(1)} KB"
}
