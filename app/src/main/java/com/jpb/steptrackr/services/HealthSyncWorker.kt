package com.jpb.steptrackr.services

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jpb.steptrackr.utils.StepDatabase
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.fitness.FitnessLocal
import com.google.android.gms.fitness.data.LocalDataType
import com.google.android.gms.fitness.data.LocalField
import com.google.android.gms.fitness.request.LocalDataReadRequest
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.ZoneOffset

class HealthSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val healthConnectClient = HealthConnectClient.getOrCreate(context)

        // 1. Verify runtime permission clearances
        val permissions = setOf(HealthPermission.getWritePermission(StepsRecord::class))
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        if (!granted.containsAll(permissions)) return Result.failure()

        val gmsAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        val db = StepDatabase.getDatabase(context)

        // FIX: Pull this declaration out of the conditional block so it stays in scope globally
        val unsyncedDeltas = if (!gmsAvailable) db.stepDao().getUnsyncedDeltas() else emptyList()

        return try {
            val stepRecordsToInsert = mutableListOf<StepsRecord>()

            if (!gmsAvailable) {
                if (unsyncedDeltas.isNotEmpty()) {
                    // Group raw ticks by a logical 10-minute window interval boundary
                    val tenMinutesInMs = 10 * 60 * 1000
                    val groupedDeltas = unsyncedDeltas.groupBy { it.timestamp / tenMinutesInMs }

                    for ((timeBlock, deltas) in groupedDeltas) {
                        val totalStepsInBlock = deltas.sumOf { it.delta }
                        val blockStartTime = Instant.ofEpochMilli(timeBlock * tenMinutesInMs)
                        val blockEndTime = blockStartTime.plusSeconds(600) // Explicit 10-minute duration

                        val localZoneOffset = ZoneOffset.systemDefault().rules.getOffset(blockStartTime)

                        // Construct a standard compliant StepsRecord
                        val record = StepsRecord(
                            count = totalStepsInBlock,
                            startTime = blockStartTime,
                            endTime = blockEndTime,
                            startZoneOffset = localZoneOffset,
                            endZoneOffset = localZoneOffset,
                            metadata = Metadata.autoRecorded(
                                device = Device(type = Device.TYPE_PHONE)
                            )
                        )
                        stepRecordsToInsert.add(record)
                    }
                }
            } else {
                // --- GMS PATH: Fetch via Recording API ---
                val localRecordingClient = FitnessLocal.getLocalRecordingClient(context)
                val endTime = Instant.now()
                val startTime = endTime.minus(java.time.Duration.ofHours(4))

                val readRequest = LocalDataReadRequest.Builder()
                    .read(LocalDataType.TYPE_STEP_COUNT_DELTA)
                    .setTimeRange(startTime.toEpochMilli(), endTime.toEpochMilli(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build()

                val localDataResponse = localRecordingClient.readData(readRequest).await()
                for (dataSet in localDataResponse.dataSets) {
                    for (point in dataSet.dataPoints) {
                        val steps = point.getValue(LocalField.FIELD_STEPS).asInt().toLong()
                        val pStart = Instant.ofEpochMilli(point.getStartTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                        val pEnd = Instant.ofEpochMilli(point.getEndTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                        val localZoneOffset = ZoneOffset.systemDefault().rules.getOffset(pStart)

                        val record = StepsRecord(
                            count = steps,
                            startTime = pStart,
                            endTime = pEnd,
                            startZoneOffset = localZoneOffset,
                            endZoneOffset = localZoneOffset,
                            metadata = Metadata.autoRecorded(
                                device = Device(type = Device.TYPE_PHONE)
                            )
                        )
                        stepRecordsToInsert.add(record)
                    }
                }
            }

            // Push batched blocks directly into Health Connect
            if (stepRecordsToInsert.isNotEmpty()) {
                healthConnectClient.insertRecords(stepRecordsToInsert)

                // This now compiles successfully because unsyncedDeltas is accessible here!
                if (!gmsAvailable) {
                    val processedIds = unsyncedDeltas.map { it.id }
                    db.stepDao().markAsSynced(processedIds)
                }
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}