package com.jpb.steptrackr.services

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.jpb.steptrackr.utils.StepDao
import com.jpb.steptrackr.utils.StepDataPoint
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HealthConnectRepository(
    private val healthConnectClient: HealthConnectClient,
    private val stepDao: StepDao? = null
) {

    suspend fun getHealthConnectStepsForHour(date: LocalDate, hour: Int): List<StepDataPoint> {
        val zoneId = ZoneId.systemDefault()

        val startZdt = date.atTime(hour, 0, 0).atZone(zoneId)
        val endZdt = date.atTime(hour, 59, 59, 999_999_999).atZone(zoneId)

        val startMs = startZdt.toInstant().toEpochMilli()
        val endMs = endZdt.toInstant().toEpochMilli()

        val buckets = LongArray(6) { 0L }
        var hasHealthConnectData = false

        try {
            // 1. Query Health Connect for step records in or overlapping the hour window
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        startZdt.toInstant().minus(Duration.ofMinutes(59)),
                        endZdt.toInstant()
                    )
                )
            )

            response.records.forEach { record ->
                val recordStart = ZonedDateTime.ofInstant(record.startTime, zoneId)
                val recordEnd = ZonedDateTime.ofInstant(record.endTime, zoneId)

                if (recordEnd.isBefore(startZdt)) return@forEach

                val effectiveTime = if (!recordEnd.isBefore(startZdt) && !recordEnd.isAfter(endZdt)) {
                    recordEnd
                } else {
                    recordStart
                }

                if (effectiveTime.toLocalDate() == date && effectiveTime.hour == hour) {
                    val bucketIndex = (effectiveTime.minute / 10).coerceIn(0, 5)
                    buckets[bucketIndex] += record.count
                    hasHealthConnectData = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Local Fallback: If Health Connect returns 0 steps, load directly from Room deltas
        if (!hasHealthConnectData && stepDao != null) {
            val localDeltas = stepDao.getDeltasForRange(startMs, endMs)
            localDeltas.forEach { delta ->
                val deltaLocalTime = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(delta.timestamp),
                    zoneId
                )
                val bucketIndex = (deltaLocalTime.minute / 10).coerceIn(0, 5)
                buckets[bucketIndex] += delta.delta
            }
        }

        // Map into 6 interval points ("00", "10", "20", "30", "40", "50")
        return (0..5).map { index ->
            val minuteStart = index * 10
            val label = String.format("%02d:%02d", hour, minuteStart)
            StepDataPoint(label = label, steps = buckets[index].toInt())
        }
    }
}