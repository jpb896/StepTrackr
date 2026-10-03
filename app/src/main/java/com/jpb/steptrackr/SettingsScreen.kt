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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jpb.steptrackr.ui.ExpressiveButton
import com.jpb.steptrackr.utils.AppLocaleManager
import com.jpb.steptrackr.utils.DashboardLayout
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
    var selectedLayout by remember { mutableStateOf(goalPrefs.getDashboardLayout()) }

    val presetGoals = remember { listOf(5000L, 8000L, 10000L, 12000L, 15000L) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step target configuration section
            Text(
                text = stringResource(R.string.daily_target_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            // Quick Select Chips - for selecting a preset step target
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.preset_targets),
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
                            label = { Text(stringResource(R.string.goal_thousand, goal/1000)) }
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
                label = { Text(stringResource(R.string.custom_target)) },
                isError = isError,
                supportingText = {
                    if (isError) {
                        Text(stringResource(R.string.custom_target_validation))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Homepage Layout Selector Section
            Text(
                text = stringResource(R.string.homepage_layout),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.homepage_layout_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedLayout == DashboardLayout.DEFAULT,
                            onClick = {
                                selectedLayout = DashboardLayout.DEFAULT
                                goalPrefs.setDashboardLayout(DashboardLayout.DEFAULT)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text(stringResource(R.string.default_homepage))
                        }
                        SegmentedButton(
                            selected = selectedLayout == DashboardLayout.ALTERNATIVE,
                            onClick = {
                                selectedLayout = DashboardLayout.ALTERNATIVE
                                goalPrefs.setDashboardLayout(DashboardLayout.ALTERNATIVE)
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text(stringResource(R.string.card_homepage))
                        }
                    }
                }
            }

            var currentLanguage by remember { mutableStateOf(AppLocaleManager.getCurrentLanguage(context)) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.language_selection),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentLanguage.startsWith("en"),
                            onClick = {
                                AppLocaleManager.setLanguage(context, "en")
                                currentLanguage = "en"
                            },
                            label = { Text("English") }
                        )
                        FilterChip(
                            selected = currentLanguage.startsWith("pl"),
                            onClick = {
                                AppLocaleManager.setLanguage(context, "pl")
                                currentLanguage = "pl"
                            },
                            label = { Text("Polski") }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Application info/about section
            Text(
                text = stringResource(R.string.appinfo),
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
                            text = stringResource(R.string.aboutapp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.version),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}