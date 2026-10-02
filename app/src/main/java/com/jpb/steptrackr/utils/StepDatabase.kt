package com.jpb.steptrackr.utils

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

// Helper data classes to hold aggregated results from SQL
data class HourlyStepTuple(
    val hourOfDay: Int,  // Hour as integer 0 - 23
    val totalSteps: Long
)

data class DailyStepTuple(
    val dateString: String, // e.g., "YYYY-MM-DD" or formatted date
    val totalSteps: Long
)

@Dao
interface StepDao {

    // Helper Flow to observe any changes in the step_deltas table
    @Query("SELECT COUNT(*) FROM step_deltas")
    fun getAllRecentDeltasFlow(): Flow<Int>

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

    @Query("SELECT SUM(delta) FROM step_deltas WHERE timestamp >= :startOfDay")
    fun getTodayLocalStepsFlow(startOfDay: Long): Flow<Long?>

    /**
     * Groups steps by hour for a specific range.
     * Restricted between startOfDayMs and endOfDayMs to prevent current steps from spilling into historical views.
     */
    @Query("""
        SELECT 
            CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hourOfDay,
            SUM(delta) AS totalSteps
        FROM step_deltas
        WHERE timestamp >= :startOfDayMs AND timestamp <= :endOfDayMs
        GROUP BY hourOfDay
        ORDER BY hourOfDay ASC
    """)
    fun getHourlyStepsFlow(startOfDayMs: Long, endOfDayMs: Long): Flow<List<HourlyStepTuple>>

    /**
     * Groups steps by day for a historical date range.
     * SQLite strftime('%Y-%m-%d', ...) groups by date.
     */
    @Query("""
        SELECT 
            strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS dateString,
            SUM(delta) AS totalSteps
        FROM step_deltas
        WHERE timestamp >= :startTimeMs AND timestamp <= :endTimeMs
        GROUP BY dateString
        ORDER BY dateString ASC
    """)
    fun getDailyStepsFlow(startTimeMs: Long, endTimeMs: Long): Flow<List<DailyStepTuple>>

    @Query("""
    SELECT * FROM step_deltas 
    WHERE timestamp >= :startTimeMs AND timestamp <= :endTimeMs
""")
    suspend fun getDeltasForRange(startTimeMs: Long, endTimeMs: Long): List<StepDelta>
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

class StepRepository(private val stepDao: StepDao) {

    /**
     * Fetch hourly data strictly for the selected date (00:00:00.000 to 23:59:59.999).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getHourlyStepsForDate(date: LocalDate): Flow<List<StepDataPoint>> {
        return stepDao.getAllRecentDeltasFlow().map { _ ->
            val startOfDayMs = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDayMs = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            Pair(startOfDayMs, endOfDayMs)
        }.flatMapLatest { (startMs, endMs) ->
            stepDao.getHourlyStepsFlow(startMs, endMs)
        }.map { tuples ->
            tuples.map { tuple ->
                val hour = tuple.hourOfDay
                val label = when {
                    hour == 0 -> "12 AM"
                    hour == 12 -> "12 PM"
                    hour > 12 -> "${hour - 12} PM"
                    else -> "$hour AM"
                }
                StepDataPoint(label = label, steps = tuple.totalSteps.toInt())
            }
        }
    }

    /**
     * Fetch daily data strictly between startDate 00:00:00.000 and endDate 23:59:59.999.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDailyStepsForRange(startDate: LocalDate, endDate: LocalDate): Flow<List<StepDataPoint>> {
        val dateFormatter = DateTimeFormatter.ofPattern("MMM d")

        return stepDao.getAllRecentDeltasFlow().map { _ ->
            val startTimeMs = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endTimeMs = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            Pair(startTimeMs, endTimeMs)
        }.flatMapLatest { (startTime, endTime) ->
            stepDao.getDailyStepsFlow(startTime, endTime)
        }.map { tuples ->
            tuples.map { tuple ->
                val date = LocalDate.parse(tuple.dateString)
                StepDataPoint(
                    label = date.format(dateFormatter),
                    steps = tuple.totalSteps.toInt()
                )
            }
        }
    }
}