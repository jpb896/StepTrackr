package com.jpb.steptrackr.utils

import java.time.LocalDate
import java.time.LocalTime

enum class HistoryTimeFrame {
    HOURLY,
    DAILY,
    HEALTH_CONNECT_HOURLY
}

data class StepDataPoint(
    val label: String, // e.g., "10 AM" for Hourly, "Mon" / "Sep 10" for Daily, "14:10" for Health Connect
    val steps: Int
)

data class StepHistoryState(
    val selectedTimeFrame: HistoryTimeFrame = HistoryTimeFrame.HOURLY,
    val hourlyData: List<StepDataPoint> = emptyList(),
    val dailyData: List<StepDataPoint> = emptyList(),
    val healthConnectData: List<StepDataPoint> = emptyList(),
    val selectedHealthConnectDate: LocalDate = LocalDate.now(),
    val selectedHealthConnectHour: Int = LocalTime.now().hour
)