package com.example.renderer

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.model.DisplayRotation
import com.example.data.model.HostInstalledApp
import com.example.data.model.ImportedApp
import com.example.data.model.InstanceState
import com.example.data.model.ParsedApkMetadata
import com.example.data.model.SharedFileItem
import com.example.data.model.VirtualInstance
import com.example.engine.GuestAppDestination
import com.example.engine.RunningVmSession
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VirtualScreenRenderer(
    instance: VirtualInstance,
    session: RunningVmSession?,
    installedApps: List<ImportedApp>,
    sharedFiles: List<SharedFileItem>,
    showKeyboardInput: Boolean,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false,
    hostApps: List<HostInstalledApp> = emptyList(),
    inspectedApk: ParsedApkMetadata? = null,
    importingPackageName: String? = null,
    isLoadingHostApps: Boolean = false,
    onStartClick: () -> Unit,
    onOpenGuestApp: (GuestAppDestination) -> Unit,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onRecentsClick: () -> Unit,
    onRemoveRecent: (GuestAppDestination) -> Unit,
    onRunShellCommand: (String) -> Unit,
    onResolveSuperuserPrompt: (Boolean) -> Unit,
    onUpdateBrowserUrl: (String) -> Unit,
    onCopyToHostClipboard: (String) -> Unit,
    onReadHostClipboard: () -> String?,
    onRefreshHostApps: () -> Unit = {},
    onImportHostApp: (HostInstalledApp) -> Unit = {},
    onPickApkUri: (Uri) -> Unit = {},
    onInstallInspectedApk: () -> Unit = {},
    onClearInspectedApk: () -> Unit = {},
    onImportSharedFile: (Uri) -> Unit = {},
    onExportSharedFile: (SharedFileItem, Uri) -> Unit = { _, _ -> }
) {
    val rotation = session?.rotation ?: DisplayRotation.PORTRAIT_0
    val aspect = remember(instance.widthPx, instance.heightPx, rotation) {
        val raw = instance.widthPx.toFloat() / instance.heightPx.coerceAtLeast(1).toFloat()
        val adjusted = if (rotation.isLandscape) 1f / raw else raw
        adjusted.coerceIn(0.45f, 2.2f)
    }

    var softInputText by remember { mutableStateOf("") }
    var showQuickSettings by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val boxAspect = maxWidth / maxHeight.coerceAtLeast(1.dp)
        val frameShape = RoundedCornerShape(if (compactMode) 14.dp else 24.dp)
        val bezelColor = Color(0xFF2B2F36)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(aspect, matchHeightConstraintsFirst = boxAspect > aspect)
                    .shadow(if (compactMode) 4.dp else 10.dp, frameShape)
                    .clip(frameShape)
                    .background(Color(0xFF0F1115))
                    .border(width = if (compactMode) 1.5.dp else 2.dp, color = bezelColor, shape = frameShape)
            ) {
                when (instance.state) {
                    InstanceState.STOPPED, InstanceState.PAUSED, InstanceState.ERROR -> {
                        StoppedVirtualScreenOverlay(
                            instance = instance,
                            compactMode = compactMode,
                            onStartClick = onStartClick
                        )
                    }
                    InstanceState.BOOTING -> {
                        BootingVirtualScreenOverlay(
                            instance = instance,
                            progress = session?.bootProgress ?: 0.1f,
                            compactMode = compactMode
                        )
                    }
                    InstanceState.RUNNING -> {
                        val activeSession = session ?: RunningVmSession(instanceId = instance.id, bootProgress = 1f)
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Status Bar AOSP Android 10 (ketuk untuk panel Quick Settings)
                            GuestStatusBar(
                                instance = instance,
                                compactMode = compactMode,
                                onToggleQuickSettings = {
                                    if (!compactMode) showQuickSettings = !showQuickSettings
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                when (val dest = activeSession.currentGuestApp) {
                                    GuestAppDestination.Launcher -> GuestAndroid10Launcher(
                                        instance = instance,
                                        installedApps = installedApps,
                                        compactMode = compactMode,
                                        onOpenGuestApp = onOpenGuestApp
                                    )
                                    GuestAppDestination.Terminal -> GuestTerminalWindow(
                                        instance = instance,
                                        session = activeSession,
                                        compactMode = compactMode,
                                        onRunCommand = onRunShellCommand,
                                        onCopyToHost = onCopyToHostClipboard,
                                        onPasteFromHost = onReadHostClipboard
                                    )
                                    GuestAppDestination.FileManager -> GuestFileManagerWindow(
                                        sharedFiles = sharedFiles,
                                        compactMode = compactMode,
                                        onImportSharedFile = onImportSharedFile,
                                        onExportSharedFile = onExportSharedFile,
                                        onOpenImportCenter = { onOpenGuestApp(GuestAppDestination.FileTransfer) }
                                    )
                                    GuestAppDestination.GuestSettings -> GuestSettingsWindow(
                                        instance = instance,
                                        compactMode = compactMode
                                    )
                                    GuestAppDestination.DeviceInfo -> GuestDeviceInfoWindow(
                                        instance = instance,
                                        compactMode = compactMode
                                    )
                                    GuestAppDestination.Browser -> GuestBrowserWindow(
                                        url = activeSession.browserUrl,
                                        spoofModel = instance.spoofModel,
                                        compactMode = compactMode,
                                        onUrlChange = onUpdateBrowserUrl
                                    )
                                    GuestAppDestination.FileTransfer -> GuestImportTransferWindow(
                                        instanceName = instance.name,
                                        hostApps = hostApps,
                                        installedApps = installedApps,
                                        sharedFiles = sharedFiles,
                                        inspectedApk = inspectedApk,
                                        importingPackageName = importingPackageName,
                                        isLoadingHostApps = isLoadingHostApps,
                                        compactMode = compactMode,
                                        onRefreshHostApps = onRefreshHostApps,
                                        onImportHostApp = onImportHostApp,
                                        onPickApkUri = onPickApkUri,
                                        onInstallInspectedApk = onInstallInspectedApk,
                                        onClearInspectedApk = onClearInspectedApk,
                                        onImportSharedFile = onImportSharedFile,
                                        onExportSharedFile = onExportSharedFile
                                    )
                                    is GuestAppDestination.InstalledPackage -> GuestImportedAppWindow(
                                        app = dest.app,
                                        instance = instance
                                    )
                                }

                                if (showQuickSettings && !compactMode) {
                                    GuestQuickSettingsShade(
                                        instance = instance,
                                        onDismiss = { showQuickSettings = false },
                                        onOpenSettings = {
                                            showQuickSettings = false
                                            onOpenGuestApp(GuestAppDestination.GuestSettings)
                                        }
                                    )
                                }

                                if (activeSession.pendingSuperuserPrompt) {
                                    SuperuserPromptOverlay(
                                        instanceName = instance.name,
                                        onGrant = { onResolveSuperuserPrompt(true) },
                                        onDeny = { onResolveSuperuserPrompt(false) }
                                    )
                                }

                                if (activeSession.showRecentsOverlay) {
                                    GuestRecentsOverlay(
                                        recents = activeSession.recentApps,
                                        onSelect = { onOpenGuestApp(it) },
                                        onRemove = onRemoveRecent,
                                        onClose = onRecentsClick
                                    )
                                }
                            }

                            GuestNavigationBar(
                                compactMode = compactMode,
                                onBackClick = {
                                    if (showQuickSettings) showQuickSettings = false else onBackClick()
                                },
                                onHomeClick = {
                                    showQuickSettings = false
                                    onHomeClick()
                                },
                                onRecentsClick = {
                                    showQuickSettings = false
                                    onRecentsClick()
                                }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showKeyboardInput && instance.state == InstanceState.RUNNING && !compactMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        BasicTextField(
                            value = softInputText,
                            onValueChange = { softInputText = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (softInputText.isNotBlank()) {
                                        onRunShellCommand(softInputText)
                                        softInputText = ""
                                    }
                                }
                            ),
                            decorationBox = { inner ->
                                Box {
                                    if (softInputText.isEmpty()) {
                                        Text(
                                            text = "Ketik input / perintah ke Android 10 virtual...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    inner()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                if (softInputText.isNotBlank()) {
                                    onRunShellCommand(softInputText)
                                    softInputText = ""
                                }
                            }
                        ) {
                            Text("Kirim")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoppedVirtualScreenOverlay(
    instance: VirtualInstance,
    compactMode: Boolean,
    onStartClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF15171C)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Surface(
                color = Color(0xFF232730),
                shape = CircleShape,
                modifier = Modifier.size(if (compactMode) 48.dp else 72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (instance.state == InstanceState.PAUSED) Icons.Default.Pause else Icons.Default.Android,
                        contentDescription = "Status Android 10",
                        tint = Color(0xFF81C995),
                        modifier = Modifier.size(if (compactMode) 26.dp else 40.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = instance.name,
                color = Color.White,
                style = if (compactMode) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            if (!compactMode) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Android 10 (API 29) • ${instance.spoofBrand} ${instance.spoofModel}",
                    color = Color(0xFF9AA0A6),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onStartClick,
                modifier = Modifier.testTag("start_virtual_screen_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (instance.state == InstanceState.PAUSED) "Lanjutkan" else "Nyalakan Android 10")
            }
        }
    }
}

@Composable
private fun BootingVirtualScreenOverlay(
    instance: VirtualInstance,
    progress: Float,
    compactMode: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = "Memulai Android 10",
                tint = Color(0xFF3DDC84),
                modifier = Modifier.size(if (compactMode) 36.dp else 56.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "android",
                color = Color.White,
                style = if (compactMode) MaterialTheme.typography.titleSmall else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = Color(0xFF3DDC84),
                trackColor = Color(0xFF202124),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
            )
            if (!compactMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Memulai ${instance.name} (${(progress * 100).toInt()}%)",
                    color = Color(0xFF9AA0A6),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun GuestStatusBar(
    instance: VirtualInstance,
    compactMode: Boolean,
    onToggleQuickSettings: () -> Unit
) {
    val timeText = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xD9101216))
            .clickable(enabled = !compactMode, onClick = onToggleQuickSettings)
            .padding(horizontal = if (compactMode) 8.dp else 14.dp, vertical = if (compactMode) 3.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = timeText,
                color = Color.White,
                fontSize = if (compactMode) 10.sp else 12.sp,
                fontWeight = FontWeight.Medium
            )
            if (instance.rootEnabled && !compactMode) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Root Virtual Aktif",
                    tint = Color(0xFFFDD663),
                    modifier = Modifier.size(12.dp)
                )
            }
            if (instance.mockLocationEnabled && !compactMode) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Lokasi Palsu Aktif",
                    tint = Color(0xFF8AB4F8),
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "Wi-Fi",
                tint = Color.White,
                modifier = Modifier.size(if (compactMode) 11.dp else 13.dp)
            )
            Icon(
                imageVector = Icons.Default.SignalCellularAlt,
                contentDescription = "Sinyal",
                tint = Color.White,
                modifier = Modifier.size(if (compactMode) 11.dp else 13.dp)
            )
            Icon(
                imageVector = Icons.Default.BatteryFull,
                contentDescription = "Baterai",
                tint = Color.White,
                modifier = Modifier.size(if (compactMode) 11.dp else 13.dp)
            )
            if (!compactMode) {
                Text(
                    text = "98%",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun GuestQuickSettingsShade(
    instance: VirtualInstance,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable { onDismiss() }
    ) {
        Surface(
            color = Color(0xFF202124),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Panel Cepat Android 10",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickTileChip(
                        label = if (instance.networkMode.name == "NAT") "Wi-Fi NAT" else "Bridge LAN",
                        active = true,
                        modifier = Modifier.weight(1f)
                    )
                    QuickTileChip(
                        label = if (instance.rootEnabled) "Root su ON" else "Non-Root",
                        active = instance.rootEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    QuickTileChip(
                        label = if (instance.mockLocationEnabled) "GPS Mock" else "GPS Asli",
                        active = instance.mockLocationEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${instance.spoofBrand} ${instance.spoofModel} • ${instance.networkMode.virtualIp}",
                        color = Color(0xFF9AA0A6),
                        fontSize = 11.sp
                    )
                    TextButton(onClick = onOpenSettings) {
                        Text("Setelan", color = Color(0xFF8AB4F8))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTileChip(label: String, active: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = if (active) Color(0xFF1A73E8) else Color(0xFF303134),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        )
    }
}

@Composable
private fun GuestAndroid10Launcher(
    instance: VirtualInstance,
    installedApps: List<ImportedApp>,
    compactMode: Boolean,
    onOpenGuestApp: (GuestAppDestination) -> Unit
) {
    data class LauncherTile(
        val title: String,
        val bgColor: Color,
        val iconTint: Color,
        val destination: GuestAppDestination,
        val iconFilePath: String? = null,
        val vectorIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Android
    )

    val builtInTiles = remember {
        listOf(
            LauncherTile("Impor", Color(0xFF00897B), Color.White, GuestAppDestination.FileTransfer, vectorIcon = Icons.Default.SwapVert),
            LauncherTile("Berkas", Color(0xFF4285F4), Color.White, GuestAppDestination.FileManager, vectorIcon = Icons.Default.Folder),
            LauncherTile("Browser", Color(0xFFFFFFFF), Color(0xFF1A73E8), GuestAppDestination.Browser, vectorIcon = Icons.Default.Language),
            LauncherTile("Setelan", Color(0xFF5F6368), Color.White, GuestAppDestination.GuestSettings, vectorIcon = Icons.Default.Settings),
            LauncherTile("Info HW", Color(0xFF1E8E3E), Color.White, GuestAppDestination.DeviceInfo, vectorIcon = Icons.Default.PhoneAndroid),
            LauncherTile("Terminal", Color(0xFF202124), Color(0xFF81C995), GuestAppDestination.Terminal, vectorIcon = Icons.Default.Terminal)
        )
    }

    val importedTiles = remember(installedApps) {
        installedApps.map { app ->
            LauncherTile(
                title = app.appName,
                bgColor = Color.White,
                iconTint = Color(0xFF1E8E3E),
                destination = GuestAppDestination.InstalledPackage(app),
                iconFilePath = app.iconFilePath
            )
        }
    }

    val allTiles = builtInTiles + importedTiles
    val dateLabel = remember {
        SimpleDateFormat("EEEE, d MMM", Locale("id", "ID")).format(Date())
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.img_android10_wallpaper),
            contentDescription = "Wallpaper Android 10",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x40000000), Color(0x18000000), Color(0x55000000))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compactMode) 8.dp else 16.dp, vertical = if (compactMode) 8.dp else 14.dp)
        ) {
            // Pixel At a Glance Header
            if (!compactMode) {
                Column(modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)) {
                    Text(
                        text = dateLabel.replaceFirstChar { it.uppercase() },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Android 10 • ${instance.spoofBrand} ${instance.spoofModel}",
                        color = Color(0xFFE8EAED),
                        fontSize = 12.sp
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(if (compactMode) 3 else 4),
                verticalArrangement = Arrangement.spacedBy(if (compactMode) 10.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(allTiles) { tile ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenGuestApp(tile.destination) }
                            .padding(2.dp)
                    ) {
                        val iconBitmap = remember(tile.iconFilePath) {
                            tile.iconFilePath?.let { path ->
                                if (File(path).exists()) BitmapFactory.decodeFile(path)?.asImageBitmap() else null
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(if (compactMode) 32.dp else 46.dp)
                                .shadow(3.dp, CircleShape)
                                .clip(CircleShape)
                                .background(tile.bgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap,
                                    contentDescription = tile.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = tile.vectorIcon,
                                    contentDescription = tile.title,
                                    tint = tile.iconTint,
                                    modifier = Modifier.size(if (compactMode) 17.dp else 23.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = tile.title,
                            color = Color.White,
                            fontSize = if (compactMode) 9.sp else 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Pixel Search Pill Dock
            if (!compactMode) {
                Surface(
                    color = Color(0xEBFFFFFF),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenGuestApp(GuestAppDestination.Browser) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Telusuri",
                            tint = Color(0xFF5F6368),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Telusuri aplikasi & web...",
                            color = Color(0xFF5F6368),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuestTerminalWindow(
    instance: VirtualInstance,
    session: RunningVmSession,
    compactMode: Boolean,
    onRunCommand: (String) -> Unit,
    onCopyToHost: (String) -> Unit,
    onPasteFromHost: () -> String?
) {
    var commandInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(session.terminalHistory.size) {
        if (session.terminalHistory.isNotEmpty()) {
            listState.animateScrollToItem(session.terminalHistory.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141619))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (session.guestRootGrantedInShell && instance.rootEnabled) "root@android10:#" else "shell@android10:$",
                color = if (session.guestRootGrantedInShell) Color(0xFFFDD663) else Color(0xFF8AB4F8),
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = if (compactMode) 10.sp else 12.sp
            )
            if (!compactMode) {
                Row {
                    IconButton(
                        onClick = {
                            val lastOutput = session.terminalHistory.takeLast(5).joinToString("\n") { it.text }
                            onCopyToHost(lastOutput)
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Salin ke Host", tint = Color(0xFF9AA0A6), modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { onPasteFromHost()?.let { commandInput += it } },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Tempel dari Host", tint = Color(0xFF9AA0A6), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (!compactMode) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                val quickCmds = listOf("su", "id", "getprop", "pm list packages", "ls /sdcard/VirtualShared", "free -m")
                items(quickCmds) { cmd ->
                    Surface(
                        color = Color(0xFF23272F),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onRunCommand(cmd) }
                    ) {
                        Text(
                            text = cmd,
                            color = Color(0xFF8AB4F8),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            items(session.terminalHistory) { line ->
                Text(
                    text = line.text,
                    color = when {
                        line.isError -> Color(0xFFF28B82)
                        line.isCommand -> Color(0xFF81C995)
                        else -> Color(0xFFE8EAED)
                    },
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = if (compactMode) 9.sp else 11.sp,
                    lineHeight = if (compactMode) 12.sp else 15.sp
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF20232A), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (session.guestRootGrantedInShell && instance.rootEnabled) "# " else "$ ",
                color = if (session.guestRootGrantedInShell) Color(0xFFFDD663) else Color(0xFF81C995),
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 12.sp
            )
            BasicTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp
                ),
                cursorBrush = SolidColor(Color(0xFF8AB4F8)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (commandInput.isNotBlank()) {
                            onRunCommand(commandInput)
                            commandInput = ""
                        }
                    }
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Kirim",
                color = Color(0xFF8AB4F8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable {
                        if (commandInput.isNotBlank()) {
                            onRunCommand(commandInput)
                            commandInput = ""
                        }
                    }
                    .padding(horizontal = 6.dp)
            )
        }
    }
}

@Composable
private fun GuestFileManagerWindow(
    sharedFiles: List<SharedFileItem>,
    compactMode: Boolean,
    onImportSharedFile: (Uri) -> Unit,
    onExportSharedFile: (SharedFileItem, Uri) -> Unit,
    onOpenImportCenter: () -> Unit
) {
    var pendingExportItem by remember { mutableStateOf<SharedFileItem?>(null) }

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportSharedFile(uri)
    }

    val exportCreator = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val target = pendingExportItem
        pendingExportItem = null
        if (uri != null && target != null) {
            onExportSharedFile(target, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(12.dp)
    ) {
        Text(
            text = "File Saya • /sdcard/VirtualShared",
            color = Color(0xFF202124),
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compactMode) 11.sp else 14.sp
        )
        if (!compactMode) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { importPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vm_files_import_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Impor File", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onOpenImportCenter,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vm_files_import_apk_button")
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Impor APK", fontSize = 11.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (sharedFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Belum ada berkas. Ketuk 'Impor File' untuk menyalin berkas dari perangkat host.",
                    color = Color(0xFF5F6368),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sharedFiles) { item ->
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp),
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
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
                                Text("${(item.sizeBytes / 1024).coerceAtLeast(1)} KB", color = Color(0xFF5F6368), fontSize = 10.sp)
                            }
                            if (!item.isDirectory && !compactMode) {
                                IconButton(
                                    onClick = {
                                        pendingExportItem = item
                                        exportCreator.launch(item.name)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.FileUpload, contentDescription = "Ekspor ke HP", tint = Color(0xFF1A73E8), modifier = Modifier.size(16.dp))
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
private fun GuestSettingsWindow(
    instance: VirtualInstance,
    compactMode: Boolean
) {
    val rows = listOf(
        "Versi Android" to "10 (API 29)",
        "Arsitektur" to instance.engineMode.shortLabel,
        "Layar" to "${instance.widthPx}×${instance.heightPx} (${instance.dpi} dpi)",
        "Memori & CPU" to "${instance.ramLimitMb} MB • ${instance.cpuCores} Core",
        "Akses Root (su)" to if (instance.rootEnabled) "Aktif" else "Nonaktif",
        "Jaringan" to "${instance.networkMode.label} (${instance.networkMode.virtualIp})",
        "Clipboard Bersama" to if (instance.sharedClipboardEnabled) "Aktif" else "Mati"
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            Text(
                text = "Setelan",
                color = Color(0xFF202124),
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compactMode) 13.sp else 17.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(rows) { (k, v) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(k, color = Color(0xFF202124), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(v, color = Color(0xFF5F6368), fontSize = 11.sp)
            }
            HorizontalDivider(color = Color(0xFFE8EAED))
        }
    }
}

@Composable
private fun GuestDeviceInfoWindow(
    instance: VirtualInstance,
    compactMode: Boolean
) {
    val props = listOf(
        "Merek" to instance.spoofBrand,
        "Model" to instance.spoofModel,
        "IMEI Virtual" to instance.spoofImei,
        "Android ID" to instance.spoofAndroidId,
        "Lokasi Mock" to if (instance.mockLocationEnabled) "${instance.mockLocationLabel} (${instance.mockLatitude}, ${instance.mockLongitude})" else "Nonaktif",
        "Port VNC / ADB" to "${instance.vncPort} / ${instance.adbPort}"
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            Text(
                text = "Tentang Ponsel",
                color = Color(0xFF202124),
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compactMode) 13.sp else 17.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(props) { (k, v) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(k, color = Color(0xFF5F6368), fontSize = 10.sp)
                Text(v, color = Color(0xFF202124), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(color = Color(0xFFE8EAED))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GuestBrowserWindow(
    url: String,
    spoofModel: String,
    compactMode: Boolean,
    onUrlChange: (String) -> Unit
) {
    var draftUrl by remember { mutableStateOf(url) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    fun normalizeInput(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.isEmpty() -> "https://www.google.com"
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            !trimmed.contains(" ") && trimmed.contains(".") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${Uri.encode(trimmed)}"
        }
    }

    fun navigateTo(input: String) {
        val target = normalizeInput(input)
        draftUrl = target
        pageError = null
        onUrlChange(target)
        webViewRef?.loadUrl(target)
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                onPause()
                destroy()
            }
            webViewRef = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8F9FA))
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            if (!compactMode) {
                IconButton(
                    onClick = { webViewRef?.goBack() },
                    enabled = canGoBack,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Mundur",
                        tint = if (canGoBack) Color(0xFF202124) else Color(0xFFBDC1C6),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { webViewRef?.goForward() },
                    enabled = canGoForward,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Maju",
                        tint = if (canGoForward) Color(0xFF202124) else Color(0xFFBDC1C6),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFFECEFF1), RoundedCornerShape(18.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = Color(0xFF5F6368),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                BasicTextField(
                    value = draftUrl,
                    onValueChange = { draftUrl = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFF202124), fontSize = if (compactMode) 10.sp else 12.sp),
                    cursorBrush = SolidColor(Color(0xFF1A73E8)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { navigateTo(draftUrl) }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vm_browser_url_input")
                )
            }

            IconButton(
                onClick = {
                    if (isLoading) {
                        webViewRef?.stopLoading()
                    } else {
                        val current = webViewRef?.url
                        if (current.isNullOrBlank()) navigateTo(draftUrl) else webViewRef?.reload()
                    }
                },
                modifier = Modifier
                    .size(30.dp)
                    .testTag("vm_browser_reload_button")
            ) {
                Icon(
                    imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                    contentDescription = if (isLoading) "Hentikan" else "Muat ulang",
                    tint = Color(0xFF202124),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (isLoading && loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                color = Color(0xFF1A73E8),
                trackColor = Color(0xFFE8EAED),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
            )
        }

        if (pageError != null) {
            Surface(
                color = Color(0xFFFCE8E6),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Gagal memuat halaman: $pageError",
                        color = Color(0xFFC5221F),
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { navigateTo(draftUrl) }) {
                        Text("Coba Lagi", fontSize = 10.sp)
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 10; $spoofModel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val targetUri = request?.url ?: return false
                                val scheme = targetUri.scheme?.lowercase()
                                return if (scheme == "http" || scheme == "https") {
                                    false
                                } else {
                                    runCatching {
                                        ctx.startActivity(
                                            Intent(Intent.ACTION_VIEW, targetUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                    true
                                }
                            }

                            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                                isLoading = true
                                pageError = null
                                if (!pageUrl.isNullOrBlank()) {
                                    draftUrl = pageUrl
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                isLoading = false
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                if (!pageUrl.isNullOrBlank()) {
                                    draftUrl = pageUrl
                                    onUrlChange(pageUrl)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    pageError = error?.description?.toString() ?: "Kesalahan jaringan"
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                            }
                        }

                        webViewRef = this
                        loadUrl(normalizeInput(url))
                    }
                },
                update = { _ -> },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun GuestImportedAppWindow(
    app: ImportedApp,
    instance: VirtualInstance
) {
    val context = LocalContext.current
    val launchIntent = remember(app.packageName) {
        context.packageManager.getLaunchIntentForPackage(app.packageName)
    }
    var launchFeedback by remember(app.packageName) { mutableStateOf<String?>(null) }

    val iconBitmap = remember(app.iconFilePath) {
        app.iconFilePath?.let { path ->
            if (File(path).exists()) BitmapFactory.decodeFile(path)?.asImageBitmap() else null
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = app.appName,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        } else {
            Icon(Icons.Default.Android, contentDescription = app.appName, tint = Color(0xFF1E8E3E), modifier = Modifier.size(52.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(app.appName, color = Color(0xFF202124), fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
        Text(app.packageName, color = Color(0xFF5F6368), fontSize = 11.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Surface(color = Color.White, shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Aplikasi Terisolasi (${app.sourceFormat} • v${app.versionName})",
                    color = Color(0xFF202124),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Data: /data/app/${app.packageName}\nProfil: ${instance.spoofBrand} ${instance.spoofModel}",
                    color = Color(0xFF5F6368),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (launchIntent != null) {
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure {
                        launchFeedback = "Gagal membuka aplikasi: ${it.message}"
                    }
                },
                modifier = Modifier.testTag("vm_launch_imported_app_button")
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Jalankan Aplikasi", fontSize = 12.sp)
            }
        } else {
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.onFailure {
                        launchFeedback = "Paket APK tersimpan di sandbox rootfs (/data/app/${app.packageName})."
                    }
                }
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Buka di Play Store", fontSize = 12.sp)
            }
        }
        if (launchFeedback != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = launchFeedback!!,
                color = Color(0xFF5F6368),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SuperuserPromptOverlay(
    instanceName: String,
    onGrant: () -> Unit,
    onDeny: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(0.86f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFE37400))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Permintaan Superuser (su)", color = Color(0xFF202124), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Shell terminal di '$instanceName' meminta akses root (uid=0) di dalam sistem virtual Android 10.",
                    color = Color(0xFF5F6368),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDeny) {
                        Text("Tolak", color = Color(0xFFD93025))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(onClick = onGrant) {
                        Text("Izinkan")
                    }
                }
            }
        }
    }
}

@Composable
private fun GuestRecentsOverlay(
    recents: List<GuestAppDestination>,
    onSelect: (GuestAppDestination) -> Unit,
    onRemove: (GuestAppDestination) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC121316))
            .clickable { onClose() }
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Aplikasi Terakhir",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (recents.isEmpty()) {
                Text("Belum ada aplikasi aktif.", color = Color(0xFF9AA0A6), fontSize = 12.sp)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recents) { item ->
                        val title = when (item) {
                            GuestAppDestination.Launcher -> "Beranda"
                            GuestAppDestination.Terminal -> "Terminal"
                            GuestAppDestination.FileManager -> "Berkas"
                            GuestAppDestination.GuestSettings -> "Setelan"
                            GuestAppDestination.DeviceInfo -> "Info Perangkat"
                            GuestAppDestination.Browser -> "Browser"
                            GuestAppDestination.FileTransfer -> "Impor File & APK"
                            is GuestAppDestination.InstalledPackage -> item.app.appName
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF202124), RoundedCornerShape(12.dp))
                                .clickable { onSelect(item) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(title, color = Color.White, fontSize = 12.sp)
                            IconButton(onClick = { onRemove(item) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color(0xFF9AA0A6), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuestNavigationBar(
    compactMode: Boolean,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onRecentsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF101216))
            .padding(vertical = if (compactMode) 3.dp else 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(if (compactMode) 32.dp else 44.dp)
                .testTag("virtual_nav_back")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali Virtual",
                tint = Color(0xFFE8EAED),
                modifier = Modifier.size(if (compactMode) 16.dp else 20.dp)
            )
        }
        IconButton(
            onClick = onHomeClick,
            modifier = Modifier
                .size(if (compactMode) 32.dp else 44.dp)
                .testTag("virtual_nav_home")
        ) {
            Icon(
                imageVector = Icons.Default.Circle,
                contentDescription = "Beranda Virtual",
                tint = Color(0xFFE8EAED),
                modifier = Modifier.size(if (compactMode) 14.dp else 18.dp)
            )
        }
        IconButton(
            onClick = onRecentsClick,
            modifier = Modifier
                .size(if (compactMode) 32.dp else 44.dp)
                .testTag("virtual_nav_recents")
        ) {
            Icon(
                imageVector = Icons.Default.CheckBoxOutlineBlank,
                contentDescription = "Aplikasi Terakhir Virtual",
                tint = Color(0xFFE8EAED),
                modifier = Modifier.size(if (compactMode) 14.dp else 18.dp)
            )
        }
    }
}
