package com.jpb.steptrackr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val goalPrefs = remember { StepGoalPreferences(context) }

    var currentGoal by remember { mutableLongStateOf(goalPrefs.getStepGoal()) }
    var customGoalInput by remember { mutableStateOf(currentGoal.toString()) }
    var isError by remember { mutableStateOf(false) }

    val presetGoals = listOf(5000L, 8000L, 10000L, 12000L, 15000L)

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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Daily step target",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Preset Goal Chip Group
            Text(
                text = "Preset targets",
                style = MaterialTheme.typography.labelLarge,
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

            HorizontalDivider()

            // Custom Goal Input
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

            Spacer(modifier = Modifier.weight(1f))

            ExpressiveButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}