package com.jpb.steptrackr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jpb.steptrackr.ui.ExpressiveButton
import com.jpb.steptrackr.utils.StepGoalPreferences

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val context = LocalContext.current
    val goalPrefs = remember { StepGoalPreferences(context) }

    var currentGoal by remember { mutableLongStateOf(goalPrefs.getStepGoal()) }
    var customGoalInput by remember { mutableStateOf(currentGoal.toString()) }
    var isError by remember { mutableStateOf(false) }

    val presetGoals = remember { listOf(5000L, 8000L, 10000L, 12000L, 15000L) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()) // Prevents soft-keyboard clipping
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step target configuration section
            Text(
                text = "Daily step target",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            // Quick Select Chips - for selecting a preset step target
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Preset targets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetGoals.forEach { goal ->
                        FilterChip(
                            selected = currentGoal == goal,
                            onClick = {
                                currentGoal = goal
                                customGoalInput = goal.toString()
                                goalPrefs.setStepGoal(goal)
                                isError = false
                            },
                            label = { Text("${goal / 1000}k") }
                        )
                    }
                }
            }

            // Input field for inputting custom goal/target
            OutlinedTextField(
                value = customGoalInput,
                onValueChange = { newValue ->
                    customGoalInput = newValue.filter { it.isDigit() }
                    val parsed = customGoalInput.toLongOrNull()
                    if (parsed != null && parsed > 0) {
                        currentGoal = parsed
                        goalPrefs.setStepGoal(parsed)
                        isError = false
                    } else {
                        isError = true
                    }
                },
                label = { Text("Custom target") },
                isError = isError,
                supportingText = {
                    if (isError) {
                        Text("Please enter a valid step count (e.g., 8000)")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Application info/about section
            Text(
                text = "App info",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Surface(
                onClick = onNavigateToAbout,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "About StepTrackr",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Version 1.2",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right), // Or an ic_chevron_right if available
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Primary Bottom Action: backwards navigation #2
            // TODO: Get rid of this for v1.2
            ExpressiveButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save and close")
            }
        }
    }
}