package com.example.data.repository

import com.example.data.local.AppPreferences
import com.example.data.local.ImportedAppDao
import com.example.data.local.InstanceDao
import com.example.data.local.RomPackageDao
import com.example.data.model.ImportedApp
import com.example.data.model.InstanceState
import com.example.data.model.RomPackage
import com.example.data.model.ThemePreference
import com.example.data.model.VirtualInstance
import kotlinx.coroutines.flow.Flow

class VirtualOsRepository(
    private val instanceDao: InstanceDao,
    private val importedAppDao: ImportedAppDao,
    private val romPackageDao: RomPackageDao,
    private val appPreferences: AppPreferences
) {
    val instances: Flow<List<VirtualInstance>> = instanceDao.observeAll()
    val allImportedApps: Flow<List<ImportedApp>> = importedAppDao.observeAll()
    val romPackages: Flow<List<RomPackage>> = romPackageDao.observeAll()

    val themePreference: Flow<ThemePreference> = appPreferences.themePreference
    val dynamicColor: Flow<Boolean> = appPreferences.dynamicColor
    val keepBackgroundService: Flow<Boolean> = appPreferences.keepBackgroundService
    val activeInstanceId: Flow<String?> = appPreferences.activeInstanceId
    val autoUpdateRoblox: Flow<Boolean> = appPreferences.autoUpdateRoblox

    fun observeAppsForInstance(instanceId: String): Flow<List<ImportedApp>> =
        importedAppDao.observeByInstance(instanceId)

    suspend fun getInstancesOnce(): List<VirtualInstance> = instanceDao.getAllOnce()
    suspend fun getInstanceById(id: String): VirtualInstance? = instanceDao.getById(id)
    suspend fun saveInstance(instance: VirtualInstance) = instanceDao.upsert(instance)
    suspend fun updateInstance(instance: VirtualInstance) = instanceDao.update(instance)
    suspend fun updateInstanceState(id: String, state: InstanceState) = instanceDao.updateState(id, state)
    suspend fun resetAllInstanceStates() = instanceDao.resetAllStates(InstanceState.STOPPED)
    suspend fun deleteInstanceRecord(id: String) {
        importedAppDao.deleteByInstance(id)
        instanceDao.deleteById(id)
    }

    suspend fun getAppsForInstanceOnce(instanceId: String): List<ImportedApp> =
        importedAppDao.getByInstanceOnce(instanceId)

    suspend fun saveImportedApp(app: ImportedApp) = importedAppDao.upsert(app)
    suspend fun deleteImportedApp(appId: String) = importedAppDao.deleteById(appId)

    suspend fun getRomsOnce(): List<RomPackage> = romPackageDao.getAllOnce()
    suspend fun getRomById(id: String): RomPackage? = romPackageDao.getById(id)
    suspend fun saveRom(rom: RomPackage) = romPackageDao.upsert(rom)
    suspend fun deleteRom(id: String) = romPackageDao.deleteById(id)

    suspend fun setThemePreference(mode: ThemePreference) = appPreferences.setThemePreference(mode)
    suspend fun setDynamicColor(enabled: Boolean) = appPreferences.setDynamicColor(enabled)
    suspend fun setKeepBackgroundService(enabled: Boolean) = appPreferences.setKeepBackgroundService(enabled)
    suspend fun setActiveInstanceId(instanceId: String?) = appPreferences.setActiveInstanceId(instanceId)
    suspend fun setAutoUpdateRoblox(enabled: Boolean) = appPreferences.setAutoUpdateRoblox(enabled)
}
