package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "virtualos_prefs")

class AppPreferences(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEEP_BACKGROUND_SERVICE = booleanPreferencesKey("keep_background_service")
        val ACTIVE_INSTANCE_ID = stringPreferencesKey("active_instance_id")
        val AUTO_UPDATE_ROBLOX = booleanPreferencesKey("auto_update_roblox")
    }

    val themePreference: Flow<ThemePreference> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: ThemePreference.SYSTEM.name
        runCatching { ThemePreference.valueOf(raw) }.getOrDefault(ThemePreference.SYSTEM)
    }

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DYNAMIC_COLOR] ?: false
    }

    val keepBackgroundService: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.KEEP_BACKGROUND_SERVICE] ?: true
    }

    val activeInstanceId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_INSTANCE_ID]
    }

    val autoUpdateRoblox: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_UPDATE_ROBLOX] ?: true
    }

    suspend fun setThemePreference(mode: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setKeepBackgroundService(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_BACKGROUND_SERVICE] = enabled }
    }

    suspend fun setActiveInstanceId(instanceId: String?) {
        context.dataStore.edit {
            if (instanceId == null) it.remove(Keys.ACTIVE_INSTANCE_ID)
            else it[Keys.ACTIVE_INSTANCE_ID] = instanceId
        }
    }

    suspend fun setAutoUpdateRoblox(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_UPDATE_ROBLOX] = enabled }
    }
}
