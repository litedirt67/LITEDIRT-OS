package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.ThemePreference
import com.example.di.AppContainer
import com.example.renderer.FloatingVirtualWindow
import com.example.renderer.VirtualScreenRenderer
import com.example.ui.screens.AppsAndFilesScreen
import com.example.ui.screens.InstanceSettingsScreen
import com.example.ui.screens.InstancesScreen
import com.example.ui.screens.SystemAndRomScreen
import com.example.ui.screens.VmDisplayScreen
import com.example.ui.theme.VirtualOsTheme

object VirtualOsRoutes {
    const val ROUTE_VM_DISPLAY = "vm_display"
    const val ROUTE_INSTANCES = "instances"
    const val ROUTE_APPS_FILES = "apps_files"
    const val ROUTE_SYSTEM_ROM = "system_rom"
    const val ROUTE_INSTANCE_SETTINGS = "instance_settings/{instanceId}"

    fun instanceSettings(instanceId: String) = "instance_settings/$instanceId"
}

private data class NavTabItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun VirtualOsApp(
    isInPictureInPictureMode: Boolean,
    onEnterPictureInPicture: () -> Unit,
    viewModel: VirtualOsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(state.statusBannerMessage) {
        val msg = state.statusBannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }

    val darkTheme = when (state.themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
    }

    VirtualOsTheme(
        darkTheme = darkTheme,
        dynamicColor = state.dynamicColor
    ) {
        // Jika sistem Android berada dalam mode Picture-in-Picture, tampilkan layar virtual penuh tanpa chrome UI
        if (isInPictureInPictureMode && state.activeInstance != null) {
            val active = state.activeInstance!!
            VirtualScreenRenderer(
                instance = active,
                session = state.activeSession,
                installedApps = state.activeInstanceApps,
                sharedFiles = state.sharedFiles,
                showKeyboardInput = false,
                compactMode = true,
                modifier = Modifier.fillMaxSize(),
                onStartClick = { viewModel.startInstance(active) },
                onOpenGuestApp = { dest -> viewModel.openGuestApp(active.id, dest) },
                onBackClick = { viewModel.pressGuestBack(active.id) },
                onHomeClick = { viewModel.pressGuestHome(active.id) },
                onRecentsClick = { viewModel.toggleGuestRecents(active.id) },
                onRemoveRecent = { dest -> viewModel.removeFromRecents(active.id, dest) },
                onRunShellCommand = { cmd -> viewModel.executeGuestCommand(active, cmd) },
                onResolveSuperuserPrompt = { granted -> viewModel.resolveSuperuserPrompt(active.id, granted) },
                onUpdateBrowserUrl = { url -> viewModel.updateBrowserUrl(active.id, url) },
                onCopyToHostClipboard = { txt -> viewModel.syncClipboardToHost(active, txt) },
                onReadHostClipboard = { viewModel.readHostClipboard(active) }
            )
            return@VirtualOsTheme
        }

        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        val tabs = remember {
            listOf(
                NavTabItem(
                    route = VirtualOsRoutes.ROUTE_VM_DISPLAY,
                    label = "Layar VM",
                    selectedIcon = Icons.Filled.PhoneAndroid,
                    unselectedIcon = Icons.Outlined.PhoneAndroid
                ),
                NavTabItem(
                    route = VirtualOsRoutes.ROUTE_INSTANCES,
                    label = "Instance",
                    selectedIcon = Icons.Filled.Layers,
                    unselectedIcon = Icons.Outlined.Layers
                ),
                NavTabItem(
                    route = VirtualOsRoutes.ROUTE_APPS_FILES,
                    label = "APK & Berkas",
                    selectedIcon = Icons.Filled.Apps,
                    unselectedIcon = Icons.Outlined.Apps
                ),
                NavTabItem(
                    route = VirtualOsRoutes.ROUTE_SYSTEM_ROM,
                    label = "Sistem",
                    selectedIcon = Icons.Filled.DeveloperBoard,
                    unselectedIcon = Icons.Outlined.DeveloperBoard
                )
            )
        }

        val showChromeNav = !state.isFullscreenDisplay &&
            currentRoute != VirtualOsRoutes.ROUTE_INSTANCE_SETTINGS

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showChromeNav && !useNavigationRail) {
                        NavigationBar {
                            tabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("nav_tab_${tab.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (showChromeNav && useNavigationRail) {
                        NavigationRail(modifier = Modifier.fillMaxHeight()) {
                            tabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        NavHost(
                            navController = navController,
                            startDestination = VirtualOsRoutes.ROUTE_VM_DISPLAY,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(VirtualOsRoutes.ROUTE_VM_DISPLAY) {
                                VmDisplayScreen(
                                    state = state,
                                    onSelectInstance = viewModel::selectActiveInstance,
                                    onStartInstance = viewModel::startInstance,
                                    onPauseInstance = viewModel::pauseInstance,
                                    onStopInstance = viewModel::stopInstance,
                                    onRotateDisplay = viewModel::rotateDisplay,
                                    onToggleFullscreen = viewModel::setFullscreenDisplay,
                                    onOpenFloatingWindow = {
                                        viewModel.setFloatingWindowOpen(true)
                                        navController.navigate(VirtualOsRoutes.ROUTE_INSTANCES) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onEnterSystemPip = onEnterPictureInPicture,
                                    onOpenInstanceSettings = { id ->
                                        navController.navigate(VirtualOsRoutes.instanceSettings(id))
                                    },
                                    onOpenGuestApp = viewModel::openGuestApp,
                                    onBackClick = viewModel::pressGuestBack,
                                    onHomeClick = viewModel::pressGuestHome,
                                    onRecentsClick = viewModel::toggleGuestRecents,
                                    onRemoveRecent = viewModel::removeFromRecents,
                                    onRunShellCommand = viewModel::executeGuestCommand,
                                    onResolveSuperuserPrompt = viewModel::resolveSuperuserPrompt,
                                    onUpdateBrowserUrl = viewModel::updateBrowserUrl,
                                    onCopyToHostClipboard = viewModel::syncClipboardToHost,
                                    onReadHostClipboard = viewModel::readHostClipboard,
                                    onRefreshHostApps = viewModel::refreshHostApps,
                                    onImportHostApp = viewModel::importHostApp,
                                    onPickApkUri = viewModel::inspectApkUri,
                                    onInstallInspectedApk = viewModel::installInspectedApk,
                                    onClearInspectedApk = viewModel::clearInspectedApk,
                                    onImportSharedFile = viewModel::importFileToShared,
                                    onExportSharedFile = viewModel::exportSharedFileToHost
                                )
                            }

                            composable(VirtualOsRoutes.ROUTE_INSTANCES) {
                                InstancesScreen(
                                    state = state,
                                    onStartInstance = viewModel::startInstance,
                                    onPauseInstance = viewModel::pauseInstance,
                                    onStopInstance = viewModel::stopInstance,
                                    onStopAll = viewModel::stopAllInstances,
                                    onOpenDisplay = { id ->
                                        viewModel.selectActiveInstance(id)
                                        navController.navigate(VirtualOsRoutes.ROUTE_VM_DISPLAY) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenSettings = { id ->
                                        navController.navigate(VirtualOsRoutes.instanceSettings(id))
                                    },
                                    onCloneInstance = viewModel::cloneInstance,
                                    onDeleteInstance = viewModel::deleteInstance,
                                    onExportInstance = viewModel::exportInstance,
                                    onImportInstance = viewModel::importInstance,
                                    onCreateInstance = viewModel::createInstance
                                )
                            }

                            composable(VirtualOsRoutes.ROUTE_APPS_FILES) {
                                AppsAndFilesScreen(
                                    state = state,
                                    onSelectInstance = viewModel::selectActiveInstance,
                                    onRefreshHostApps = viewModel::refreshHostApps,
                                    onImportHostApp = viewModel::importHostApp,
                                    onSyncRoblox = viewModel::syncRobloxToInstance,
                                    onCheckRobloxUpdate = { viewModel.checkRobloxStatus(notifyIfUpdated = true) },
                                    onSetAutoUpdateRoblox = viewModel::setAutoUpdateRoblox,
                                    onOpenRobloxPlayStore = { viewModel.openRobloxPlayStore(context) },
                                    onPickApkUri = viewModel::inspectApkUri,
                                    onInstallInspectedApk = viewModel::installInspectedApk,
                                    onClearInspectedApk = viewModel::clearInspectedApk,
                                    onLaunchImportedApp = { app -> viewModel.launchAppFromVm(context, app) },
                                    onUninstallApp = viewModel::uninstallImportedApp,
                                    onImportSharedFile = viewModel::importFileToShared,
                                    onExportSharedFile = viewModel::exportSharedFileToHost,
                                    onCreateVirtualNote = viewModel::createVirtualSharedNote,
                                    onDeleteSharedFile = viewModel::deleteSharedFile
                                )
                            }

                            composable(VirtualOsRoutes.ROUTE_SYSTEM_ROM) {
                                SystemAndRomScreen(
                                    state = state,
                                    onImportLocalRom = viewModel::importLocalRom,
                                    onDownloadRomUrl = viewModel::downloadRomFromUrl,
                                    onDismissRomProgress = viewModel::dismissRomProgress,
                                    onDeleteRom = viewModel::deleteCustomRom,
                                    onRefreshPrivilege = viewModel::refreshPrivilegeStatus,
                                    onRequestShizukuPermission = viewModel::requestShizukuPermission,
                                    onRunHostDiagnostic = viewModel::runHostDiagnostic,
                                    onSetThemePreference = viewModel::setThemePreference,
                                    onSetDynamicColor = viewModel::setDynamicColor,
                                    onSetKeepBackgroundService = viewModel::setKeepBackgroundService
                                )
                            }

                            composable(
                                route = VirtualOsRoutes.ROUTE_INSTANCE_SETTINGS,
                                arguments = listOf(navArgument("instanceId") { type = NavType.StringType })
                            ) { entry ->
                                val instanceId = entry.arguments?.getString("instanceId")
                                val inst = state.instances.firstOrNull { it.id == instanceId }
                                if (inst != null) {
                                    InstanceSettingsScreen(
                                        instance = inst,
                                        rootfsDir = AppContainer.get(context).runtimeEngine.getInstanceRootfsDir(inst.id),
                                        onGenerateImei = viewModel::generateRandomImei,
                                        onSave = viewModel::updateInstanceSettings,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        }

                        // Jendela Mengambang In-App (Floating Window) di atas semua tab
                        val active = state.activeInstance
                        if (state.isFloatingWindowOpen && active != null && currentRoute != VirtualOsRoutes.ROUTE_VM_DISPLAY) {
                            FloatingVirtualWindow(
                                instance = active,
                                session = state.activeSession,
                                installedApps = state.activeInstanceApps,
                                sharedFiles = state.sharedFiles,
                                onClose = { viewModel.setFloatingWindowOpen(false) },
                                onExpandToFullScreen = {
                                    viewModel.setFloatingWindowOpen(false)
                                    navController.navigate(VirtualOsRoutes.ROUTE_VM_DISPLAY) {
                                        launchSingleTop = true
                                    }
                                },
                                onEnterSystemPip = onEnterPictureInPicture,
                                onStartClick = { viewModel.startInstance(active) },
                                onOpenGuestApp = { dest -> viewModel.openGuestApp(active.id, dest) },
                                onBackClick = { viewModel.pressGuestBack(active.id) },
                                onHomeClick = { viewModel.pressGuestHome(active.id) },
                                onRecentsClick = { viewModel.toggleGuestRecents(active.id) },
                                onRemoveRecent = { dest -> viewModel.removeFromRecents(active.id, dest) },
                                onRunShellCommand = { cmd -> viewModel.executeGuestCommand(active, cmd) },
                                onResolveSuperuserPrompt = { granted -> viewModel.resolveSuperuserPrompt(active.id, granted) },
                                onUpdateBrowserUrl = { url -> viewModel.updateBrowserUrl(active.id, url) },
                                onCopyToHostClipboard = { txt -> viewModel.syncClipboardToHost(active, txt) },
                                onReadHostClipboard = { viewModel.readHostClipboard(active) }
                            )
                        }
                    }
                }
            }
        }
    }
}
