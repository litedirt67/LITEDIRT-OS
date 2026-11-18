package com.example.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ImportedApp
import com.example.data.model.SharedFileItem
import com.example.data.model.VirtualInstance
import com.example.engine.GuestAppDestination
import com.example.engine.RunningVmSession
import kotlin.math.roundToInt

@Composable
fun FloatingVirtualWindow(
    instance: VirtualInstance,
    session: RunningVmSession?,
    installedApps: List<ImportedApp>,
    sharedFiles: List<SharedFileItem>,
    onClose: () -> Unit,
    onExpandToFullScreen: () -> Unit,
    onEnterSystemPip: () -> Unit,
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
    onReadHostClipboard: () -> String?
) {
    var offsetX by remember { mutableFloatStateOf(24f) }
    var offsetY by remember { mutableFloatStateOf(140f) }
    var largeSize by remember { mutableStateOf(false) }

    val windowWidth = if (largeSize) 248.dp else 184.dp
    val windowHeight = if (largeSize) 410.dp else 310.dp

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF191C22),
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .width(windowWidth)
            .height(windowHeight)
            .shadow(14.dp, RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .testTag("floating_virtual_window")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Handle geser jendela mengambang
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF232730))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = "Geser jendela",
                        tint = Color(0xFF9AA0A6),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = instance.name,
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEnterSystemPip, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "PiP Sistem",
                            tint = Color(0xFF39E69B),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(onClick = { largeSize = !largeSize }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.ZoomOutMap,
                            contentDescription = "Ubah ukuran",
                            tint = Color(0xFFD6E2F0),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(onClick = onExpandToFullScreen, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Buka penuh",
                            tint = Color(0xFFD6E2F0),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup jendela mengambang",
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                VirtualScreenRenderer(
                    instance = instance,
                    session = session,
                    installedApps = installedApps,
                    sharedFiles = sharedFiles,
                    showKeyboardInput = false,
                    compactMode = true,
                    modifier = Modifier.fillMaxSize(),
                    onStartClick = onStartClick,
                    onOpenGuestApp = onOpenGuestApp,
                    onBackClick = onBackClick,
                    onHomeClick = onHomeClick,
                    onRecentsClick = onRecentsClick,
                    onRemoveRecent = onRemoveRecent,
                    onRunShellCommand = onRunShellCommand,
                    onResolveSuperuserPrompt = onResolveSuperuserPrompt,
                    onUpdateBrowserUrl = onUpdateBrowserUrl,
                    onCopyToHostClipboard = onCopyToHostClipboard,
                    onReadHostClipboard = onReadHostClipboard
                )
            }
        }
    }
}
