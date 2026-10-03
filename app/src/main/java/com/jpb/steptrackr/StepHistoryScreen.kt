package com.jpb.steptrackr

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jpb.steptrackr.ui.StepsBarChart
import com.jpb.steptrackr.utils.HistoryTimeFrame
import com.jpb.steptrackr.utils.StepHistoryState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepHistoryScreen(
    historyState: StepHistoryState,
    onBackClick: () -> Unit,
    onTimeFrameSelected: (HistoryTimeFrame) -> Unit,
    onHourlyDateSelected: (LocalDate) -> Unit,
    onDailyRangeSelected: (LocalDate, LocalDate) -> Unit,
    onHealthConnectDateAndHourSelected: (LocalDate, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    var selectedHourlyDate by remember { mutableStateOf(today) }
    var customStartDate by remember { mutableStateOf(today.minusDays(6)) }
    var customEndDate by remember { mutableStateOf(today) }

    val currentData = when (historyState.selectedTimeFrame) {
        HistoryTimeFrame.HOURLY -> historyState.hourlyData
        HistoryTimeFrame.DAILY -> historyState.dailyData
        HistoryTimeFrame.HEALTH_CONNECT_HOURLY -> historyState.healthConnectData
    }

    val totalSteps: Long = currentData.sumOf { it.steps.toLong() }
    val averageSteps: Long = if (currentData.isNotEmpty()) totalSteps / currentData.size else 0L

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stephistory)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val contentModifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)

        if (isLandscape) {
            Row(
                modifier = contentModifier,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                ) {
                    TimeFrameSelector(
                        selectedTimeFrame = historyState.selectedTimeFrame,
                        onTimeFrameSelected = onTimeFrameSelected
                    )

                    GraphConfigControls(
                        selectedTimeFrame = historyState.selectedTimeFrame,
                        selectedDate = selectedHourlyDate,
                        onDateSelected = { newDate ->
                            selectedHourlyDate = newDate
                            onHourlyDateSelected(newDate)
                        },
                        startDate = customStartDate,
                        endDate = customEndDate,
                        onRangeSelected = { start, end ->
                            customStartDate = start
                            customEndDate = end
                            onDailyRangeSelected(start, end)
                        },
                        hcDate = historyState.selectedHealthConnectDate,
                        hcHour = historyState.selectedHealthConnectHour,
                        onHcSelected = onHealthConnectDateAndHourSelected
                    )

                    SummaryCard(
                        totalSteps = totalSteps,
                        averageSteps = averageSteps,
                        timeFrame = historyState.selectedTimeFrame
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    StepsBarChart(
                        stepData = currentData,
                        dailyGoal = when (historyState.selectedTimeFrame) {
                            HistoryTimeFrame.HOURLY -> 2000
                            HistoryTimeFrame.HEALTH_CONNECT_HOURLY -> 500
                            HistoryTimeFrame.DAILY -> 10000
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            Column(
                modifier = contentModifier,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TimeFrameSelector(
                    selectedTimeFrame = historyState.selectedTimeFrame,
                    onTimeFrameSelected = onTimeFrameSelected
                )

                GraphConfigControls(
                    selectedTimeFrame = historyState.selectedTimeFrame,
                    selectedDate = selectedHourlyDate,
                    onDateSelected = { newDate ->
                        selectedHourlyDate = newDate
                        onHourlyDateSelected(newDate)
                    },
                    startDate = customStartDate,
                    endDate = customEndDate,
                    onRangeSelected = { start, end ->
                        customStartDate = start
                        customEndDate = end
                        onDailyRangeSelected(start, end)
                    },
                    hcDate = historyState.selectedHealthConnectDate,
                    hcHour = historyState.selectedHealthConnectHour,
                    onHcSelected = onHealthConnectDateAndHourSelected
                )

                SummaryCard(
                    totalSteps = totalSteps,
                    averageSteps = averageSteps,
                    timeFrame = historyState.selectedTimeFrame
                )

                StepsBarChart(
                    stepData = currentData,
                    dailyGoal = when (historyState.selectedTimeFrame) {
                        HistoryTimeFrame.HOURLY -> 2000
                        HistoryTimeFrame.HEALTH_CONNECT_HOURLY -> 500
                        HistoryTimeFrame.DAILY -> 10000
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TimeFrameSelector(
    selectedTimeFrame: HistoryTimeFrame,
    onTimeFrameSelected: (HistoryTimeFrame) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth()
    ) {
        SegmentedButton(
            selected = selectedTimeFrame == HistoryTimeFrame.HOURLY,
            onClick = { onTimeFrameSelected(HistoryTimeFrame.HOURLY) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
        ) {
            Text("Hourly")
        }
        SegmentedButton(
            selected = selectedTimeFrame == HistoryTimeFrame.DAILY,
            onClick = { onTimeFrameSelected(HistoryTimeFrame.DAILY) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
        ) {
            Text("Daily")
        }
        SegmentedButton(
            selected = selectedTimeFrame == HistoryTimeFrame.HEALTH_CONNECT_HOURLY,
            onClick = { onTimeFrameSelected(HistoryTimeFrame.HEALTH_CONNECT_HOURLY) },
            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
        ) {
            Text("HC 10m")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraphConfigControls(
    selectedTimeFrame: HistoryTimeFrame,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    startDate: LocalDate,
    endDate: LocalDate,
    onRangeSelected: (LocalDate, LocalDate) -> Unit,
    hcDate: LocalDate,
    hcHour: Int,
    onHcSelected: (LocalDate, Int) -> Unit
) {
    var showSingleDatePicker by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showHcDatePicker by remember { mutableStateOf(false) }
    var hcHourMenuExpanded by remember { mutableStateOf(false) }

    val formatter = remember { DateTimeFormatter.ofPattern("MMM d") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (selectedTimeFrame) {
                HistoryTimeFrame.HOURLY -> {
                    Text(
                        text = selectedDate.format(formatter),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedDate == LocalDate.now(),
                            onClick = { onDateSelected(LocalDate.now()) },
                            label = { Text(stringResource(R.string.today)) }
                        )
                        OutlinedButton(onClick = { showSingleDatePicker = true }) {
                            Text(stringResource(R.string.custom_date))
                        }
                    }
                }
                HistoryTimeFrame.DAILY -> {
                    Text(
                        text = "${startDate.format(formatter)} - ${endDate.format(formatter)}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = startDate == LocalDate.now().minusDays(6) && endDate == LocalDate.now(),
                            onClick = { onRangeSelected(LocalDate.now().minusDays(6), LocalDate.now()) },
                            label = { Text(stringResource(R.string.pastweek)) }
                        )
                        OutlinedButton(onClick = { showDateRangePicker = true }) {
                            Text(stringResource(R.string.custom_range))
                        }
                    }
                }
                HistoryTimeFrame.HEALTH_CONNECT_HOURLY -> {
                    Text(
                        text = "${hcDate.format(formatter)} @ ${String.format("%02d:00", hcHour)}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { showHcDatePicker = true }) {
                            Text(stringResource(R.string.date))
                        }
                        Box {
                            OutlinedButton(onClick = { hcHourMenuExpanded = true }) {
                                Text("${String.format("%02d:00", hcHour)}")
                            }
                            DropdownMenu(
                                expanded = hcHourMenuExpanded,
                                onDismissRequest = { hcHourMenuExpanded = false }
                            ) {
                                (0..23).forEach { hour ->
                                    DropdownMenuItem(
                                        text = { Text(String.format("%02d:00", hour)) },
                                        onClick = {
                                            hcHourMenuExpanded = false
                                            onHcSelected(hcDate, hour)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSingleDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showSingleDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val pickedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        onDateSelected(pickedDate)
                    }
                    showSingleDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showSingleDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (showHcDatePicker) {
        val hcDatePickerState = rememberDatePickerState(
            initialSelectedDateMillis = hcDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showHcDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hcDatePickerState.selectedDateMillis?.let { millis ->
                        val pickedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        onHcSelected(pickedDate, hcHour)
                    }
                    showHcDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showHcDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DatePicker(state = hcDatePickerState) }
    }

    if (showDateRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val startMillis = dateRangePickerState.selectedStartDateMillis
                    val endMillis = dateRangePickerState.selectedEndDateMillis
                    if (startMillis != null && endMillis != null) {
                        val pickedStart = Instant.ofEpochMilli(startMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                        val pickedEnd = Instant.ofEpochMilli(endMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                        onRangeSelected(pickedStart, pickedEnd)
                    }
                    showDateRangePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) { DateRangePicker(state = dateRangePickerState) }
    }
}

@Composable
private fun SummaryCard(
    totalSteps: Long,
    averageSteps: Long,
    timeFrame: HistoryTimeFrame
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.total_steps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "%,d".format(totalSteps),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when (timeFrame) {
                        HistoryTimeFrame.HOURLY -> stringResource(R.string.hourly_average)
                        HistoryTimeFrame.DAILY -> stringResource(R.string.daily_average)
                        HistoryTimeFrame.HEALTH_CONNECT_HOURLY -> stringResource(R.string.ten_min_average)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "%,d".format(averageSteps),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}