package com.jpb.steptrackr.utils

import android.content.Context
import androidx.room.*

@Entity(tableName = "step_deltas")
data class StepDelta(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val delta: Long,
    val timestamp: Long, // Epoch Milliseconds
    val isSynced: Boolean = false
)

@Entity(tableName = "sensor_metadata")
data class SensorMetadata(
    @PrimaryKey val id: Int = 1,
    val lastSensorValue: Long
)

@Dao
interface StepDao {
    @Insert
    suspend fun insertDelta(stepDelta: StepDelta)

    @Query("SELECT * FROM step_deltas WHERE isSynced = 0")
    suspend fun getUnsyncedDeltas(): List<StepDelta>

    @Query("UPDATE step_deltas SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("SELECT lastSensorValue FROM sensor_metadata WHERE id = 1")
    suspend fun getLastSensorValue(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSensorValue(metadata: SensorMetadata)

    @Query("SELECT SUM(delta) FROM step_deltas WHERE timestamp >= :startOfDay")
    suspend fun getTodayLocalSteps(startOfDay: Long): Long?
}

@Database(entities = [StepDelta::class, SensorMetadata::class], version = 1, exportSchema = false)
abstract class StepDatabase : RoomDatabase() {
    abstract fun stepDao(): StepDao

    companion object {
        @Volatile private var INSTANCE: StepDatabase? = null
        fun getDatabase(context: Context): StepDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StepDatabase::class.java,
                    "step_tracker_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}