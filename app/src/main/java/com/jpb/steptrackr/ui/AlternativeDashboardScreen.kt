package com.jpb.steptrackr.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpb.steptrackr.R
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlternativeDashboardScreen(
    currentSteps: Long,
    dailyGoal: Int,
    onNavigateToMap: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val greeting = when (LocalTime.now().hour) {
        in 0..11 -> "Good morning! 👋"
        in 12..16 -> "Good afternoon! 👋"
        else -> "Good evening! 👋"
    }

    // Dynamic Material You Theme Color Mapping
    val containerBg = MaterialTheme.colorScheme.surface
    val mainCardBg = MaterialTheme.colorScheme.primaryContainer
    val mainCardOnColor = MaterialTheme.colorScheme.onPrimaryContainer

    val arcProgressColor = MaterialTheme.colorScheme.primary
    val arcTrackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)

    val actionCardBg = MaterialTheme.colorScheme.secondaryContainer
    val actionCardOnColor = MaterialTheme.colorScheme.onSecondaryContainer

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "StepTrackr",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu),
                            contentDescription = "Menu"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = containerBg)
            )
        },
        containerColor = containerBg
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Greeting Banner
            Text(
                text = greeting,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Progress Card with Dynamically Themed Circular Indicator
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = mainCardBg)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "TODAY'S PROGRESS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = mainCardOnColor.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "%,d".format(currentSteps),
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = mainCardOnColor
                        )
                        Text(
                            text = "out of %,d steps".format(dailyGoal),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = mainCardOnColor.copy(alpha = 0.8f)
                        )
                    }

                    // Dynamically Themed Donut Arc Chart
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(90.dp)
                    ) {
                        val progress = if (dailyGoal > 0) (currentSteps.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f) else 0f
                        val animatedProgress by animateFloatAsState(
                            targetValue = progress,
                            animationSpec = tween(durationMillis = 1000),
                            label = "DynamicArcProgress"
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 12.dp.toPx()

                            // Background Arc Track using dynamic surface/container tint
                            drawArc(
                                color = arcTrackColor,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            // Progress Arc using primary dynamic accent
                            drawArc(
                                color = arcProgressColor,
                                startAngle = -90f,
                                sweepAngle = 360f * animatedProgress,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )

            // Section Label
            Text(
                text = "Your movement",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Action Button: Where you've been
            Card(
                onClick = onNavigateToMap,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = actionCardBg)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_location),
                            contentDescription = null,
                            tint = actionCardOnColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Where you’ve been",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = actionCardOnColor
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_forward),
                        contentDescription = "Open map",
                        tint = actionCardOnColor
                    )
                }
            }

            // Action Button: Walking Statistics
            Card(
                onClick = onNavigateToHistory,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = actionCardBg)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_directions_walk),
                            contentDescription = null,
                            tint = actionCardOnColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Your walking statistics",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = actionCardOnColor
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_forward),
                        contentDescription = "Open statistics",
                        tint = actionCardOnColor
                    )
                }
            }
        }
    }
}