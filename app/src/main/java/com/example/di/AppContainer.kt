package com.example.di

import android.content.Context
import com.example.data.local.AppPreferences
import com.example.data.local.VirtualOsDatabase
import com.example.data.repository.VirtualOsRepository
import com.example.engine.SystemTelemetrySampler
import com.example.engine.VirtualRuntimeEngine
import com.example.installer.ApkXapkInstaller
import com.example.installer.RobloxAutoUpdateManager
import com.example.installer.SharedStorageBridge
import com.example.instance.InstanceManager
import com.example.privilege.HostPrivilegeManager
import com.example.rom.RomImageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer private constructor(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database = VirtualOsDatabase.getInstance(appContext)
    val preferences = AppPreferences(appContext)
    val repository = VirtualOsRepository(
        instanceDao = database.instanceDao(),
        importedAppDao = database.importedAppDao(),
        romPackageDao = database.romPackageDao(),
        appPreferences = preferences
    )

    val romImageManager = RomImageManager(appContext, repository)
    val runtimeEngine = VirtualRuntimeEngine(appContext, repository, appScope)
    val instanceManager = InstanceManager(appContext, repository, romImageManager, runtimeEngine)
    val apkXapkInstaller = ApkXapkInstaller(appContext, repository, runtimeEngine)
    val robloxAutoUpdateManager = RobloxAutoUpdateManager(appContext, repository, apkXapkInstaller)
    val sharedStorageBridge = SharedStorageBridge(appContext, runtimeEngine)
    val hostPrivilegeManager = HostPrivilegeManager(appContext)
    val telemetrySampler = SystemTelemetrySampler(appContext)

    companion object {
        @Volatile
        private var INSTANCE: AppContainer? = null

        fun get(context: Context): AppContainer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppContainer(context).also { INSTANCE = it }
            }
        }
    }
}
