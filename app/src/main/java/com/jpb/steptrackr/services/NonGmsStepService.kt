package com.jpb.steptrackr.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jpb.steptrackr.R
import com.jpb.steptrackr.utils.SensorMetadata
import com.jpb.steptrackr.utils.StepDelta
import com.jpb.steptrackr.utils.StepDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

class NonGmsStepService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var lastSavedSteps = 0L
    private lateinit var database: StepDatabase
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        database = StepDatabase.getDatabase(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // 1. CRITICAL: Register the channel with the OS BEFORE trying to post the notification object
        createNotificationChannel()

        // 2. CRITICAL: Use the EXACT same companion object string key here (CHANNEL_ID)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracking Steps")
            .setContentText("Pedometer background service is active.")
            .setSmallIcon(R.drawable.ic_walk)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        // Match your local notification reference identifier (1001) safely
        startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)

        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        serviceScope.launch {
            val historicalBaseline = database.stepDao().getLastSensorValue()
            if (historicalBaseline != null) {
                lastSavedSteps = historicalBaseline
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceBoot = event.values[0].toLong()

            if (lastSavedSteps in 1..<totalStepsSinceBoot) {
                val delta = totalStepsSinceBoot - lastSavedSteps
                saveStepsToLocalDatabase(delta, Clock.System.now())
                lastSavedSteps = totalStepsSinceBoot
            } else if (lastSavedSteps == 0L || totalStepsSinceBoot < lastSavedSteps) {
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
                database.stepDao().insertDelta(
                    StepDelta(
                        delta = delta,
                        timestamp = timestamp.toEpochMilliseconds(),
                        isSynced = false
                    )
                )
                database.stepDao().updateSensorValue(
                    SensorMetadata(lastSensorValue = lastSavedSteps)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Activity Tracking",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Maintains connection with system step counters."
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        // Shared single immutable key to guarantee validation matches
        const val CHANNEL_ID = "non_gms_activity_tracking_channel"
    }
}