package com.jpb.steptrackr

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.fitness.FitnessLocal
import com.google.android.gms.fitness.data.LocalDataType
import com.jpb.steptrackr.services.HealthConnectRepository
import com.jpb.steptrackr.ui.AlternativeDashboardScreen
import com.jpb.steptrackr.ui.ExpressiveButton
import com.jpb.steptrackr.ui.theme.AppTheme
import com.jpb.steptrackr.utils.AppLocaleManager
import com.jpb.steptrackr.utils.DashboardLayout
import com.jpb.steptrackr.utils.GoalNotificationHelper
import com.jpb.steptrackr.utils.HistoryTimeFrame
import com.jpb.steptrackr.utils.SensorMetadata
import com.jpb.steptrackr.utils.StepDatabase
import com.jpb.steptrackr.utils.StepDelta
import com.jpb.steptrackr.utils.StepGoalPreferences
import com.jpb.steptrackr.utils.StepRepository
import com.jpb.steptrackr.viewmodels.StepHistoryViewModel
import com.jpb.steptrackr.viewmodels.StepHistoryViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.getValue
import kotlin.time.Clock

class MainActivity : ComponentActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var lastSavedSteps = 0L
    private lateinit var database: StepDatabase
    private val activityScope = CoroutineScope(Dispatchers.IO)
    private var isActivityPermissionGranted = mutableStateOf(false)
    private var isNotificationPermissionGranted = mutableStateOf(false)

    private val stepHistoryViewModel: StepHistoryViewModel by viewModels {
        val hcClient = HealthConnectClient.getOrCreate(applicationContext)
        StepHistoryViewModelFactory(
            repository = StepRepository(database.stepDao()),
            healthConnectRepository = HealthConnectRepository(hcClient, database.stepDao())
        )
    }

    enum class Screen { Dashboard, Settings, About, StepHistory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        database = StepDatabase.getDatabase(applicationContext)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        activityScope.launch {
            val historicalBaseline = database.stepDao().getLastSensorValue()
            if (historicalBaseline != null) {
                lastSavedSteps = historicalBaseline
            }
        }

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
                    val goalPrefs = remember { StepGoalPreferences(applicationContext) }
                    val currentLayout = remember(currentScreen) { goalPrefs.getDashboardLayout() }

                    when (currentScreen) {
                        Screen.Dashboard -> {
                            if (currentLayout == DashboardLayout.ALTERNATIVE) {
                                val startOfDay = remember {
                                    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                }
                                val todayStepsState by database.stepDao().getTodayLocalStepsFlow(startOfDay)
                                    .collectAsState(initial = 0L)

                                AlternativeDashboardScreen(
                                    currentSteps = todayStepsState ?: 0L,
                                    dailyGoal = goalPrefs.getStepGoal().toInt(),
                                    onNavigateToMap = { /* Optional map navigation action */ },
                                    onNavigateToHistory = { currentScreen = Screen.StepHistory },
                                    onMenuClick = { currentScreen = Screen.Settings },
                                    onSettingsClick = { currentScreen = Screen.Settings },
                                    goalPrefs = goalPrefs
                                )
                            } else {
                                PermissionAndDashboardScreen(
                                    hasActivityPermission = isActivityPermissionGranted.value,
                                    hasNotificationPermission = isNotificationPermissionGranted.value,
                                    onPermissionsUpdated = { activityGranted, notificationGranted ->
                                        isActivityPermissionGranted.value = activityGranted
                                        isNotificationPermissionGranted.value = notificationGranted
                                        if (activityGranted) {
                                            registerPedometerAndService()
                                            setupGmsStepRecording(this@MainActivity)
                                        }
                                    },
                                    onSyncTrigger = {
                                        triggerImmediateSync(this@MainActivity)
                                        Toast.makeText(this@MainActivity, getString(R.string.manualsync_text), Toast.LENGTH_SHORT).show()                                    },
                                    onOpenSettings = { currentScreen = Screen.Settings },
                                    onOpenStepHistory = { currentScreen = Screen.StepHistory }
                                )
                            }
                        }
                        Screen.Settings -> {
                            SettingsScreen(
                                onNavigateBack = { currentScreen = Screen.Dashboard },
                                onNavigateToAbout = { currentScreen = Screen.About }
                            )
                        }
                        Screen.About -> {
                            AboutScreen(
                                onNavigateBack = { currentScreen = Screen.Settings }
                            )
                        }
                        Screen.StepHistory -> {
                            val historyState by stepHistoryViewModel.uiState.collectAsState()
                            StepHistoryScreen(
                                historyState = historyState,
                                onBackClick = { currentScreen = Screen.Dashboard },
                                onTimeFrameSelected = { timeFrame ->
                                    stepHistoryViewModel.onTimeFrameSelected(timeFrame)
                                    if (timeFrame == HistoryTimeFrame.HEALTH_CONNECT_HOURLY) {
                                        lifecycleScope.launch {
                                            triggerImmediateSync(this@MainActivity)
                                            stepHistoryViewModel.refreshHealthConnectData()
                                        }
                                    }
                                },
                                onHourlyDateSelected = { date -> stepHistoryViewModel.onHourlyDateSelected(date) },
                                onDailyRangeSelected = { start, end -> stepHistoryViewModel.onDailyRangeSelected(start, end) },
                                onHealthConnectDateAndHourSelected = { date, hour ->
                                    stepHistoryViewModel.onHealthConnectDateAndHourSelected(date, hour)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()

        if (isActivityPermissionGranted.value) {
            registerPedometerAndService()
            setupGmsStepRecording(this)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isActivityPermissionGranted.value) {
            sensorManager?.unregisterListener(this)
            triggerImmediateSync(this)
        }
    }

    private fun updatePermissionStates() {
        isActivityPermissionGranted.value = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        isNotificationPermissionGranted.value = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun registerPedometerAndService() {
        stepSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        try {
            val serviceIntent = Intent(
                applicationContext,
                com.jpb.steptrackr.services.NonGmsStepService::class.java
            )
            ContextCompat.startForegroundService(applicationContext, serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupGmsStepRecording(context: Context) {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
            FitnessLocal.getLocalRecordingClient(context)
                .subscribe(LocalDataType.TYPE_STEP_COUNT_DELTA)
                .addOnSuccessListener {
                    Log.d("GMS", "Successfully subscribed to GMS local step recording.")
                }
                .addOnFailureListener { e ->
                    Log.e("GMS", "Failed to subscribe to GMS step recording", e)
                }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceBoot = event.values[0].toLong()

            if (lastSavedSteps == 0L || totalStepsSinceBoot < lastSavedSteps) {
                lastSavedSteps = totalStepsSinceBoot
                activityScope.launch {
                    database.stepDao().updateSensorValue(
                        SensorMetadata(lastSensorValue = totalStepsSinceBoot)
                    )
                }
                return
            }

            if (totalStepsSinceBoot > lastSavedSteps) {
                val delta = totalStepsSinceBoot - lastSavedSteps
                lastSavedSteps = totalStepsSinceBoot
                activityScope.launch {
                    try {
                        database.stepDao().insertDelta(
                            StepDelta(
                                delta = delta,
                                timestamp = Clock.System.now().toEpochMilliseconds(),
                                isSynced = false
                            )
                        )
                        database.stepDao().updateSensorValue(
                            SensorMetadata(lastSensorValue = totalStepsSinceBoot)
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerImmediateSync(context: Context) {
        val syncWorkRequest = OneTimeWorkRequestBuilder<com.jpb.steptrackr.services.HealthSyncWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueue(syncWorkRequest)
    }

    override fun attachBaseContext(newBase: Context) {
        val currentLang = AppLocaleManager.getCurrentLanguage(newBase)
        val locale = java.util.Locale.forLanguageTag(currentLang)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val localizedContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(localizedContext)
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun PermissionAndDashboardScreen(
    hasActivityPermission: Boolean,
    hasNotificationPermission: Boolean,
    onPermissionsUpdated: (Boolean, Boolean) -> Unit,
    onSyncTrigger: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStepHistory: () -> Unit
) {
    val context = LocalContext.current
    val goalPrefs = remember { StepGoalPreferences(context) }
    val activityContext = remember(context) { context as Activity }
    val appContext = remember(context) { context.applicationContext }
    val db = remember { StepDatabase.getDatabase(appContext) }
    val healthConnectClient = remember { HealthConnectClient.getOrCreate(activityContext) }

    val startOfDay = remember {
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val todayStepsState by db.stepDao().getTodayLocalStepsFlow(startOfDay)
        .collectAsState(initial = 0L)
    val todaySteps = todayStepsState ?: 0L
    val stepGoal = remember(context) { goalPrefs.getStepGoal() }

    var hasTriggeredGoal by remember { mutableStateOf(false) }

    LaunchedEffect(todaySteps) {
        if (todaySteps >= stepGoal && stepGoal > 0 && !hasTriggeredGoal) {
            hasTriggeredGoal = true
            GoalNotificationHelper.showGoalReachedNotification(context, todaySteps)
        }
    }

    var hasHealthPermission by remember { mutableStateOf(false) }
    val requiredHealthPermissions = remember {
        setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class)
        )
    }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        hasHealthPermission = grantedPermissions.containsAll(requiredHealthPermissions)
    }

    val systemPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val activityGranted = permissions[Manifest.permission.ACTIVITY_RECOGNITION] == true
        val notificationGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] == true
        onPermissionsUpdated(activityGranted, notificationGranted)
    }

    LaunchedEffect(Unit) {
        try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            hasHealthPermission = granted.containsAll(requiredHealthPermissions)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Step Gauge
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Material3ExpressiveStepGauge(
                        currentSteps = todaySteps,
                        stepGoal = stepGoal,
                        gaugeSize = 220.dp
                    )
                }

                // Right Column: Controls & Information
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onOpenStepHistory) {
                            Icon(
                                painter = painterResource(R.drawable.ic_history),
                                contentDescription = "Step History"
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings),
                                contentDescription = "Settings"
                            )
                        }
                    }

                    HealthConnectCard(
                        hasHealthPermission = hasHealthPermission,
                        onRequestPermission = { healthPermissionLauncher.launch(requiredHealthPermissions) },
                        onSyncTrigger = onSyncTrigger
                    )

                    PermissionStatusSection(
                        hasActivityPermission = hasActivityPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        onRequestPermissions = {
                            val channel = NotificationChannel(
                                "non_gms_activity_tracking_channel",
                                context.getString(R.string.steptracking_notification_channel),
                                NotificationManager.IMPORTANCE_MIN
                            )
                            val manager =
                                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            manager.createNotificationChannel(channel)
                            systemPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACTIVITY_RECOGNITION,
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            )
                        }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Top Action Bar pinned to the top-right corner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onOpenStepHistory) {
                        Icon(
                            painter = painterResource(R.drawable.ic_history),
                            contentDescription = "Step History"
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "Settings"
                        )
                    }
                }

                // Vertically centered scrollable container for dashboard items
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
                ) {
                    Material3ExpressiveStepGauge(
                        currentSteps = todaySteps,
                        stepGoal = stepGoal,
                        gaugeSize = 260.dp
                    )

                    HealthConnectCard(
                        hasHealthPermission = hasHealthPermission,
                        onRequestPermission = { healthPermissionLauncher.launch(requiredHealthPermissions) },
                        onSyncTrigger = onSyncTrigger
                    )

                    PermissionStatusSection(
                        hasActivityPermission = hasActivityPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        onRequestPermissions = {
                            val channel = NotificationChannel(
                                "non_gms_activity_tracking_channel",
                                context.getString(R.string.steptracking_notification_channel),
                                NotificationManager.IMPORTANCE_MIN
                            )
                            val manager =
                                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            manager.createNotificationChannel(channel)
                            systemPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACTIVITY_RECOGNITION,
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            )
                        }
                    )
                }
            }
        }

        if (hasTriggeredGoal) {
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

@Composable
private fun HealthConnectCard(
    hasHealthPermission: Boolean,
    onRequestPermission: () -> Unit,
    onSyncTrigger: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.hc_card_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!hasHealthPermission) {
                Text(
                    text = stringResource(R.string.hc_connection_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                ExpressiveButton(
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.hc_link_button))
                }
            } else {
                Text(
                    text = stringResource(R.string.hc_sync_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                ExpressiveButton(
                    onClick = onSyncTrigger,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.hc_synchronize_now))
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusSection(
    hasActivityPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestPermissions: () -> Unit
) {
    if (!hasActivityPermission || !hasNotificationPermission) {
        ExpressiveButton(
            onClick = onRequestPermissions,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.grant_perms_button))
        }
    } else {
        Text(
            text = stringResource(R.string.pedometer_monitoring_active),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun Material3ExpressiveStepGauge(
    currentSteps: Long,
    stepGoal: Long,
    modifier: Modifier = Modifier,
    gaugeSize: androidx.compose.ui.unit.Dp = 260.dp
) {
    val progress = if (stepGoal > 0) (currentSteps.toFloat() / stepGoal.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(
            durationMillis = 1400,
            easing = { it * it * (3f - 2f * it) }
        ),
        label = "GaugeProgress"
    )

    Box(
        modifier = modifier
            .size(gaugeSize)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        val progressColor = MaterialTheme.colorScheme.primary
        val trackColor = MaterialTheme.colorScheme.surfaceVariant

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = (gaugeSize.toPx() * 0.1f)
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)

            drawArc(
                color = trackColor,
                startAngle = 120f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                color = progressColor,
                startAngle = 120f,
                sweepAngle = 300f * animatedProgress,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = String.format("%,d", currentSteps),
                fontSize = if (gaugeSize < 250.dp) 32.sp else 40.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.step_goal_main_display, stepGoal),
                fontSize = if (gaugeSize < 250.dp) 13.sp else 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}