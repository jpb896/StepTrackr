package com.jpb.steptrackr.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.app.NotificationCompat
import com.jpb.steptrackr.R
import com.jpb.steptrackr.utils.SensorMetadata
import com.jpb.steptrackr.utils.StepDatabase
import com.jpb.steptrackr.utils.StepDelta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

class NonGmsStepService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var lastSavedSteps = 0L

    // Core structural properties added for database lifecycle operations
    private lateinit var database: StepDatabase
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Initialize your database singleton context instance
        database = StepDatabase.getDatabase(this)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        createNotificationChannel()
        // Android requires a persistent notification for foreground services
        val notification = NotificationCompat.Builder(this, "step_channel")
            .setContentTitle("Tracking Steps")
            .setSmallIcon(R.drawable.ic_walk)
            .build()

        startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)

        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // Prime the initial baseline value out of the DB when the service wakes up
        serviceScope.launch {
            val historicalBaseline = database.stepDao().getLastSensorValue()
            if (historicalBaseline != null) {
                lastSavedSteps = historicalBaseline
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            // Extract index 0 safely or use .first() and cast that Float primitive to a Long
            val totalStepsSinceBoot = event.values[0].toLong()

            if (lastSavedSteps in 1..<totalStepsSinceBoot) {
                val delta = totalStepsSinceBoot - lastSavedSteps
                saveStepsToLocalDatabase(delta, Clock.System.now())

                // Keep memory baseline perfectly synced with processed records
                lastSavedSteps = totalStepsSinceBoot
            } else if (lastSavedSteps == 0L || totalStepsSinceBoot < lastSavedSteps) {
                // Initial setup catch, or a device reboot condition where hardware resets to 0
                lastSavedSteps = totalStepsSinceBoot
                serviceScope.launch {
                    database.stepDao().updateSensorValue(SensorMetadata(lastSensorValue = totalStepsSinceBoot))
                }
            }
        }
    }

    private fun saveStepsToLocalDatabase(delta: Long, timestamp: Instant) {
        serviceScope.launch {
            try {
                // Convert kotlinx.datetime.Instant directly to raw epoch milliseconds for Room storage
                val epochMillis = timestamp.toEpochMilliseconds()

                // Inserts the newly generated step increment fragment line into the local Room table
                val stepDeltaEntity = StepDelta(
                    delta = delta,
                    timestamp = epochMillis,
                    isSynced = false
                )
                database.stepDao().insertDelta(stepDeltaEntity)

                // Persist the updated sensor baseline tracking checkpoint to preserve continuity across restarts
                val metadataUpdate = SensorMetadata(
                    lastSensorValue = lastSavedSteps
                )
                database.stepDao().updateSensorValue(metadataUpdate)

            } catch (e: Exception) {
                // Handle unexpected storage failures or write exceptions smoothly
                e.printStackTrace()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?) = null

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(CHANNEL_ID, "Activity Tracking", NotificationManager.IMPORTANCE_MIN)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "non_gms_activity_tracking_channel"
    }
}
