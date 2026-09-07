package com.jpb.steptrackr.services

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.fitness.FitnessLocal
import com.google.android.gms.fitness.data.LocalDataType
import com.google.android.gms.fitness.data.LocalField
import com.google.android.gms.fitness.request.LocalDataReadRequest
import kotlinx.coroutines.tasks.await
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class HealthSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val healthConnectClient = HealthConnectClient.getOrCreate(context)

        // Ensure Health Connect permissions are granted before proceeding
        val permissions = setOf(HealthPermission.getWritePermission(StepsRecord::class))
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        if (!granted.containsAll(permissions)) return Result.failure()

        val stepRecordsToInsert = mutableListOf<StepsRecord>()
        val gmsAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        val db = StepDatabase.getDatabase(context)

        return try {
            if (gmsAvailable) {
                // GMS PATH: Fetch via Recording API
                val localRecordingClient = FitnessLocal.getLocalRecordingClient(context)
                val endTime = Instant.now()
                val startTime = endTime.minus(Duration.ofHours(4)) // Pull the latest interval

                val readRequest = LocalDataReadRequest.Builder()
                    .read(LocalDataType.TYPE_STEP_COUNT_DELTA)
                    .setTimeRange(startTime.toEpochMilli(), endTime.toEpochMilli(), TimeUnit.MILLISECONDS)
                    .build()

                val localDataResponse = localRecordingClient.readData(readRequest).await()
                for (dataSet in localDataResponse.dataSets) {
                    for (point in dataSet.dataPoints) {
                        val steps = point.getValue(LocalField.FIELD_STEPS).asInt().toLong()
                        val pStart = Instant.ofEpochMilli(point.getStartTime(TimeUnit.MILLISECONDS))
                        val pEnd = Instant.ofEpochMilli(point.getEndTime(TimeUnit.MILLISECONDS))

                        stepRecordsToInsert.add(createStepsRecord(steps, pStart, pEnd))
                    }
                }
            } else {
                // NON-GMS PATH: Extract from Room Local Cache
                val unsynced = db.stepDao().getUnsyncedDeltas()
                if (unsynced.isNotEmpty()) {
                    for (delta in unsynced) {
                        val pointTime = Instant.ofEpochMilli(delta.timestamp)
                        // Room tracks instantaneous points; create a minimal 1-second record block for validation
                        stepRecordsToInsert.add(createStepsRecord(delta.delta, pointTime.minusSeconds(1), pointTime))
                    }
                }
            }

            if (stepRecordsToInsert.isNotEmpty()) {
                healthConnectClient.insertRecords(stepRecordsToInsert)
                if (!gmsAvailable) {
                    val ids = db.stepDao().getUnsyncedDeltas().map { it.id }
                    db.stepDao().markAsSynced(ids)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun createStepsRecord(count: Long, start: Instant, end: Instant): StepsRecord {
        return StepsRecord(
            count = count,
            startTime = start,
            endTime = end,
            startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(start),
            endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(end),
            metadata = Metadata.autoRecorded(device = Device(Device.TYPE_PHONE))
        )
    }
}
