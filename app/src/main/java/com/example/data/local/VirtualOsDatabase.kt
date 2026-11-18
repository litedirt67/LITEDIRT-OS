package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import com.example.data.model.ImportedApp
import com.example.data.model.InstanceState
import com.example.data.model.NetworkMode
import com.example.data.model.RomImageStatus
import com.example.data.model.RomPackage
import com.example.data.model.VirtualInstance
import com.example.data.model.VirtualOsArchitectureMode
import kotlinx.coroutines.flow.Flow

class VirtualOsConverters {
    @TypeConverter
    fun fromArchMode(value: VirtualOsArchitectureMode): String = value.name

    @TypeConverter
    fun toArchMode(value: String): VirtualOsArchitectureMode =
        runCatching { VirtualOsArchitectureMode.valueOf(value) }
            .getOrDefault(VirtualOsArchitectureMode.PROOT_CONTAINER)

    @TypeConverter
    fun fromInstanceState(value: InstanceState): String = value.name

    @TypeConverter
    fun toInstanceState(value: String): InstanceState =
        runCatching { InstanceState.valueOf(value) }.getOrDefault(InstanceState.STOPPED)

    @TypeConverter
    fun fromNetworkMode(value: NetworkMode): String = value.name

    @TypeConverter
    fun toNetworkMode(value: String): NetworkMode =
        runCatching { NetworkMode.valueOf(value) }.getOrDefault(NetworkMode.NAT)

    @TypeConverter
    fun fromRomStatus(value: RomImageStatus): String = value.name

    @TypeConverter
    fun toRomStatus(value: String): RomImageStatus =
        runCatching { RomImageStatus.valueOf(value) }.getOrDefault(RomImageStatus.READY)
}

@Dao
interface InstanceDao {
    @Query("SELECT * FROM virtual_instances ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<VirtualInstance>>

    @Query("SELECT * FROM virtual_instances ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<VirtualInstance>

    @Query("SELECT * FROM virtual_instances WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VirtualInstance?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(instance: VirtualInstance)

    @Update
    suspend fun update(instance: VirtualInstance)

    @Query("UPDATE virtual_instances SET state = :state WHERE id = :id")
    suspend fun updateState(id: String, state: InstanceState)

    @Query("UPDATE virtual_instances SET state = :stoppedState")
    suspend fun resetAllStates(stoppedState: InstanceState = InstanceState.STOPPED)

    @Query("DELETE FROM virtual_instances WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ImportedAppDao {
    @Query("SELECT * FROM imported_apps WHERE instanceId = :instanceId ORDER BY installedAt DESC")
    fun observeByInstance(instanceId: String): Flow<List<ImportedApp>>

    @Query("SELECT * FROM imported_apps ORDER BY installedAt DESC")
    fun observeAll(): Flow<List<ImportedApp>>

    @Query("SELECT * FROM imported_apps WHERE instanceId = :instanceId")
    suspend fun getByInstanceOnce(instanceId: String): List<ImportedApp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: ImportedApp)

    @Query("DELETE FROM imported_apps WHERE id = :appId")
    suspend fun deleteById(appId: String)

    @Query("DELETE FROM imported_apps WHERE instanceId = :instanceId")
    suspend fun deleteByInstance(instanceId: String)
}

@Dao
interface RomPackageDao {
    @Query("SELECT * FROM rom_packages ORDER BY isBuiltInBase DESC, updatedAt DESC")
    fun observeAll(): Flow<List<RomPackage>>

    @Query("SELECT * FROM rom_packages ORDER BY isBuiltInBase DESC, updatedAt DESC")
    suspend fun getAllOnce(): List<RomPackage>

    @Query("SELECT * FROM rom_packages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RomPackage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rom: RomPackage)

    @Query("DELETE FROM rom_packages WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(
    entities = [VirtualInstance::class, ImportedApp::class, RomPackage::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(VirtualOsConverters::class)
abstract class VirtualOsDatabase : RoomDatabase() {
    abstract fun instanceDao(): InstanceDao
    abstract fun importedAppDao(): ImportedAppDao
    abstract fun romPackageDao(): RomPackageDao

    companion object {
        @Volatile
        private var INSTANCE: VirtualOsDatabase? = null

        fun getInstance(context: Context): VirtualOsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VirtualOsDatabase::class.java,
                    "virtualos_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
