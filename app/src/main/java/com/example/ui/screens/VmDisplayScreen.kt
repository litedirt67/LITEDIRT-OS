package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.InstanceState
import com.example.data.model.VirtualInstance
import com.example.engine.GuestAppDestination
import com.example.renderer.VirtualScreenRenderer
import com.example.ui.VirtualOsUiState
import com.example.ui.theme.RootBadgeColor
import com.example.ui.theme.StatusBooting
import com.example.ui.theme.StatusPaused
import com.example.ui.theme.StatusRunning
import com.example.ui.theme.StatusStopped

@Composable
fun VmDisplayScreen(
    state: VirtualOsUiState,
    onSelectInstance: (String) -> Unit,
    onStartInstance: (VirtualInstance) -> Unit,
    onPauseInstance: (String) -> Unit,
    onStopInstance: (String) -> Unit,
    onRotateDisplay: (String) -> Unit,
    onToggleFullscreen: (Boolean) -> Unit,
    onOpenFloatingWindow: () -> Unit,
    onEnterSystemPip: () -> Unit,
    onOpenInstanceSettings: (String) -> Unit,
    onOpenGuestApp: (String, GuestAppDestination) -> Unit,
    onBackClick: (String) -> Unit,
    onHomeClick: (String) -> Unit,
    onRecentsClick: (String) -> Unit,
    onRemoveRecent: (String, GuestAppDestination) -> Unit,
    onRunShellCommand: (VirtualInstance, String) -> Unit,
    onResolveSuperuserPrompt: (String, Boolean) -> Unit,
    onUpdateBrowserUrl: (String, String) -> Unit,
    onCopyToHostClipboard: (VirtualInstance, String) -> Unit,
    onReadHostClipboard: (VirtualInstance) -> String?,
    onRefreshHostApps: () -> Unit,
    onImportHostApp: (com.example.data.model.HostInstalledApp, String) -> Unit,
    onPickApkUri: (android.net.Uri) -> Unit,
    onInstallInspectedApk: (String) -> Unit,
    onClearInspectedApk: () -> Unit,
    onImportSharedFile: (String, android.net.Uri) -> Unit,
    onExportSharedFile: (com.example.data.model.SharedFileItem, android.net.Uri) -> Unit
) {
    val active = state.activeInstance
    var showKeyboard by remember { mutableStateOf(false) }

    if (state.isFullscreenDisplay) {
        BackHandler { onToggleFullscreen(false) }
    }

    if (active == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Belum ada instance Android 10. Buat dari tab Instance.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    val activeSession = state.sessions[active.id]
    val ramUsedMb = state.telemetry.perInstanceRamMb[active.id] ?: 0
    val cpuLoad = state.telemetry.perInstanceCpuPercent[active.id] ?: 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (state.isFullscreenDisplay) 4.dp else 12.dp, vertical = 6.dp)
    ) {
        if (!state.isFullscreenDisplay) {
            // Multi-instance active switcher strip
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(state.instances, key = { it.id }) { inst ->
                    val selected = inst.id == active.id
                    val dotColor = when (inst.state) {
                        InstanceState.RUNNING -> StatusRunning
                        InstanceState.BOOTING -> StatusBooting
                        InstanceState.PAUSED -> StatusPaused
                        else -> StatusStopped
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectInstance(inst.id) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = inst.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        modifier = Modifier.testTag("switcher_chip_${inst.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Clean VM Control Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${active.name} • Android 10",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (active.state == InstanceState.RUNNING) {
                                "RAM $ramUsedMb/${active.ramLimitMb} MB • CPU ${"%.1f".format(cpuLoad)}%"
                            } else {
                                "${active.state.label} • ${active.widthPx}×${active.heightPx}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (active.state == InstanceState.RUNNING) {
                            IconButton(
                                onClick = { onPauseInstance(active.id) },
                                modifier = Modifier.testTag("pause_vm_button")
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = "Jeda VM")
                            }
                            IconButton(
                                onClick = { onStopInstance(active.id) },
                                modifier = Modifier.testTag("stop_vm_button")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Hentikan VM", tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            IconButton(
                                onClick = { onStartInstance(active) },
                                modifier = Modifier.testTag("start_vm_toolbar_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Jalankan VM", tint = StatusRunning)
                            }
                        }

                        IconButton(onClick = { onOpenInstanceSettings(active.id) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Pengaturan Instance")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        // Virtual Screen Viewport
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            VirtualScreenRenderer(
                instance = active,
                session = activeSession,
                installedApps = state.activeInstanceApps,
                sharedFiles = state.sharedFiles,
                showKeyboardInput = showKeyboard,
                modifier = Modifier.fillMaxSize(),
                hostApps = state.hostApps,
                inspectedApk = state.inspectedApk,
                importingPackageName = state.importingPackageName,
                isLoadingHostApps = state.isLoadingHostApps,
                onStartClick = { onStartInstance(active) },
                onOpenGuestApp = { dest -> onOpenGuestApp(active.id, dest) },
                onBackClick = { onBackClick(active.id) },
                onHomeClick = { onHomeClick(active.id) },
                onRecentsClick = { onRecentsClick(active.id) },
                onRemoveRecent = { dest -> onRemoveRecent(active.id, dest) },
                onRunShellCommand = { cmd -> onRunShellCommand(active, cmd) },
                onResolveSuperuserPrompt = { granted -> onResolveSuperuserPrompt(active.id, granted) },
                onUpdateBrowserUrl = { url -> onUpdateBrowserUrl(active.id, url) },
                onCopyToHostClipboard = { txt -> onCopyToHostClipboard(active, txt) },
                onReadHostClipboard = { onReadHostClipboard(active) },
                onRefreshHostApps = onRefreshHostApps,
                onImportHostApp = { hostApp -> onImportHostApp(hostApp, active.id) },
                onPickApkUri = onPickApkUri,
                onInstallInspectedApk = { onInstallInspectedApk(active.id) },
                onClearInspectedApk = onClearInspectedApk,
                onImportSharedFile = { uri -> onImportSharedFile(active.id, uri) },
                onExportSharedFile = onExportSharedFile
            )

            if (state.isFullscreenDisplay) {
                Surface(
                    color = Color(0xCC0D1520),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                ) {
                    IconButton(onClick = { onToggleFullscreen(false) }) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "Keluar layar penuh", tint = Color.White)
                    }
                }
            }
        }

        if (!state.isFullscreenDisplay) {
            Spacer(modifier = Modifier.height(6.dp))
            // Display Action Deck: Rotate, Keyboard, Floating Window, System PiP, Fullscreen
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DisplayDockAction(
                        icon = Icons.Default.RotateRight,
                        label = "Putar",
                        testTag = "rotate_vm_button",
                        onClick = { onRotateDisplay(active.id) }
                    )
                    DisplayDockAction(
                        icon = Icons.Default.Keyboard,
                        label = "Papan Ketik",
                        testTag = "keyboard_vm_button",
                        active = showKeyboard,
                        onClick = { showKeyboard = !showKeyboard }
                    )
                    DisplayDockAction(
                        icon = Icons.Default.VerticalSplit,
                        label = "Mengambang",
                        testTag = "floating_vm_button",
                        onClick = onOpenFloatingWindow
                    )
                    DisplayDockAction(
                        icon = Icons.Default.PictureInPictureAlt,
                        label = "PiP Sistem",
                        testTag = "pip_vm_button",
                        onClick = onEnterSystemPip
                    )
                    DisplayDockAction(
                        icon = Icons.Default.Fullscreen,
                        label = "Layar Penuh",
                        testTag = "fullscreen_vm_button",
                        onClick = { onToggleFullscreen(true) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DisplayDockAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    testTag: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
