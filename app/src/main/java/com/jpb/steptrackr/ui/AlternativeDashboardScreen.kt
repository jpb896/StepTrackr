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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpb.steptrackr.R
import com.jpb.steptrackr.utils.AppLocaleManager
import com.jpb.steptrackr.utils.GoalNotificationHelper
import com.jpb.steptrackr.utils.RouteDistanceCalculator
import com.jpb.steptrackr.utils.StepDatabase
import com.jpb.steptrackr.utils.StepGoalPreferences
import com.jpb.steptrackr.utils.icuStringResource
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlternativeDashboardScreen(
    currentSteps: Long,
    dailyGoal: Int,
    onNavigateToMap: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit,
    goalPrefs: StepGoalPreferences,
    modifier: Modifier = Modifier
) {
    val greeting = when (LocalTime.now().hour) {
        in 0..11 -> stringResource(R.string.morning_greeting)
        in 12..16 -> stringResource(R.string.afternoon_greeting)
        else -> stringResource(R.string.evening_greeting)
    }

    val context = LocalContext.current
    var currentLanguage by remember {
        mutableStateOf(AppLocaleManager.getCurrentLanguage(context))
    }

    val database = remember { StepDatabase.getDatabase(context) }
    val startOfDay = remember {
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    val routePoints by database.stepDao()
        .getRoutePointsForRangeFlow(startOfDay, System.currentTimeMillis())
        .collectAsState(initial = emptyList())

    val distanceKm = remember(routePoints) {
        RouteDistanceCalculator.calculateTotalDistanceKm(routePoints.map { RouteLocation(it.latitude, it.longitude) })
    }

    var hasTriggeredGoal by remember { mutableStateOf(goalPrefs.hasCelebratedToday()) }

    LaunchedEffect(currentSteps) {
        if (currentSteps >= dailyGoal && dailyGoal > 0 && !hasTriggeredGoal) {
            hasTriggeredGoal = true
            goalPrefs.setCelebratedToday()
            GoalNotificationHelper.showGoalReachedNotification(context, dailyGoal)
        }
    }

    val containerBg = MaterialTheme.colorScheme.surface
    val mainCardBg = MaterialTheme.colorScheme.primaryContainer
    val mainCardOnColor = MaterialTheme.colorScheme.onPrimaryContainer

    val arcProgressColor = MaterialTheme.colorScheme.primary
    val arcTrackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)

    val actionCardBg = MaterialTheme.colorScheme.secondaryContainer
    val actionCardOnColor = MaterialTheme.colorScheme.onSecondaryContainer

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onMenuClick) {
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
                Text(
                    text = greeting,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

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
                                text = stringResource(R.string.todays_progress),
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
                                text = stringResource(R.string.step_goal_main_display_variant, dailyGoal),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = mainCardOnColor.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Distance: %.2f km".format(distanceKm),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = mainCardOnColor.copy(alpha = 0.9f)
                            )
                        }

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

                                drawArc(
                                    color = arcTrackColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
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

                Text(
                    text = stringResource(R.string.your_movement),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

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
                                painter = painterResource(R.drawable.map_24px),
                                contentDescription = null,
                                tint = actionCardOnColor,
                                modifier = Modifier.size(24.dp)
                            )
                            if (currentLanguage == "en") {
                                Text(
                                    text = stringResource(R.string.where_youve_been),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = actionCardOnColor
                                )
                            } else {
                                val currentGender = remember { goalPrefs.getUserGender().value }

                                Text(
                                    text = icuStringResource(
                                        id = R.string.where_youve_been,
                                        args = mapOf("gender" to currentGender)
                                    ),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = actionCardOnColor
                                )
                            }
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_forward),
                            contentDescription = "Open map",
                            tint = actionCardOnColor
                        )
                    }
                }

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
                                text = stringResource(R.string.your_walking_statistics),
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

        if (hasTriggeredGoal && !goalPrefs.hasCelebratedToday()) {
            KonfettiView(
                parties = listOf(
                    Party(
                        speed = 0f,
                        maxSpeed = 30f,
                        damping = 0.9f,
                        spread = 360,
                        colors = listOf(0xfab1a0, 0x00b894, 0x0984e3, 0xfdcb6e),
                        emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
                        position = Position.Relative(0.5, 0.3)
                    )
                ),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}