package com.jpb.steptrackr

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.fitness.FitnessLocal
import com.google.android.gms.fitness.data.LocalDataType
import com.jpb.steptrackr.services.HealthSyncWorker
import java.util.concurrent.TimeUnit

class StepApplication : Application() {

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    override fun onCreate() {
        super.onCreate()
        setupAutomatedHealthSync(this)
    }

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    private fun setupAutomatedHealthSync(context: Context) {
        val gmsAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

        if (gmsAvailable) {
            // Subscribe the device background process to the low power recording engine
            FitnessLocal.getLocalRecordingClient(context).subscribe(LocalDataType.TYPE_STEP_COUNT_DELTA)
        }

        // Constraints to prevent battery drain
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        // WorkManager minimum interval for periodic work is 15 minutes
        val syncWorkRequest = PeriodicWorkRequestBuilder<HealthSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_NAME_HEALTH_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            syncWorkRequest
        )
    }

    companion object {
        const val WORK_NAME_HEALTH_SYNC = "periodic_health_connect_sync"
    }
}