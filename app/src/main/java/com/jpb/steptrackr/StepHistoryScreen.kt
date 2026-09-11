package com.jpb.steptrackr

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.jpb.steptrackr.utils.HistoryTimeFrame
import com.jpb.steptrackr.utils.StepHistoryState
import com.jpb.steptrackr.ui.StepsBarChart

@Composable
fun StepHistoryScreen(
    historyState: StepHistoryState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTimeFrame by remember { mutableStateOf(historyState.selectedTimeFrame) }

    val currentData = when (selectedTimeFrame) {
        HistoryTimeFrame.HOURLY -> historyState.hourlyData
        HistoryTimeFrame.DAILY -> historyState.dailyData
    }

    val totalSteps = currentData.sumOf { it.steps }
    val averageSteps = if (currentData.isNotEmpty()) totalSteps / currentData.size else 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Step history") },
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Time Frame Selector (Hourly vs Daily)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = selectedTimeFrame == HistoryTimeFrame.HOURLY,
                    onClick = { selectedTimeFrame = HistoryTimeFrame.HOURLY },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Hourly")
                }
                SegmentedButton(
                    selected = selectedTimeFrame == HistoryTimeFrame.DAILY,
                    onClick = { selectedTimeFrame = HistoryTimeFrame.DAILY },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Daily")
                }
            }

            // Summary Stats Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Total Steps",
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
                            text = if (selectedTimeFrame == HistoryTimeFrame.HOURLY) "Avg/hr" else "Avg/day",
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

            // Render the Bar Chart
            StepsBarChart(
                stepData = currentData,
                dailyGoal = if (selectedTimeFrame == HistoryTimeFrame.HOURLY) 2000 else 10000,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}