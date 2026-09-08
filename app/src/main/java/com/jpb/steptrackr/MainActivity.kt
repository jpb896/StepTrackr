package com.jpb.steptrackr

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.jpb.steptrackr.ui.theme.AppTheme
import com.jpb.steptrackr.utils.SensorMetadata
import com.jpb.steptrackr.utils.StepDatabase
import com.jpb.steptrackr.utils.StepDelta
import com.jpb.steptrackr.utils.StepGoalPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Clock

class MainActivity : ComponentActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var lastSavedSteps = 0L
    private lateinit var database: StepDatabase
    private val activityScope = CoroutineScope(Dispatchers.IO)

    // Observable Compose state
    private var isActivityPermissionGranted = mutableStateOf(false)
    private var isNotificationPermissionGranted = mutableStateOf(false)

    enum class Screen { Dashboard, Settings }

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

                    when (currentScreen) {
                        Screen.Dashboard -> {
                            PermissionAndDashboardScreen(
                                hasActivityPermission = isActivityPermissionGranted.value,
                                hasNotificationPermission = isNotificationPermissionGranted.value,
                                onPermissionsUpdated = { activityGranted, notificationGranted ->
                                    isActivityPermissionGranted.value = activityGranted
                                    isNotificationPermissionGranted.value = notificationGranted
                                    if (activityGranted) registerPedometerAndService()
                                },
                                onSyncTrigger = {
                                    triggerImmediateSync(this@MainActivity)
                                    Toast.makeText(this@MainActivity, "Steps synced to Health Connect!", Toast.LENGTH_SHORT).show()
                                },
                                onOpenSettings = { currentScreen = Screen.Settings }
                            )
                        }
                        Screen.Settings -> {
                            SettingsScreen(
                                onNavigateBack = { currentScreen = Screen.Dashboard }
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

    override fun onPause() {
        super.onPause()
        if (isActivityPermissionGranted.value) {
            sensorManager?.unregisterListener(this)
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
}

@Composable
fun PermissionAndDashboardScreen(
    hasActivityPermission: Boolean,
    hasNotificationPermission: Boolean,
    onPermissionsUpdated: (Boolean, Boolean) -> Unit,
    onSyncTrigger: () -> Unit,
    onOpenSettings: () -> Unit
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

    var hasHealthPermission by remember { mutableStateOf(false) }
    val requiredHealthPermissions = remember {
        setOf(HealthPermission.getWritePermission(StepsRecord::class))
    }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        hasHealthPermission = grantedPermissions.containsAll(requiredHealthPermissions)
    }

    // Handles core hardware system permissions dynamically
    val systemPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val activityGranted = permissions[Manifest.permission.ACTIVITY_RECOGNITION] == true
        val notificationGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] == true

        // Triggers instant UI recomposition and starts service when granted
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

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
    ) {
        // Settings Icon Row
        Row(
            modifier = Modifier.fillMaxWidth().safeDrawingPadding(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = "Settings"
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
        ) {
            Material3ExpressiveStepGauge(currentSteps = todaySteps, stepGoal = stepGoal)

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
                        text = "Health Connect integration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!hasHealthPermission) {
                        Text(
                            text = "Connect this app with Health Connect to share your daily progress safely and securely.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { healthPermissionLauncher.launch(requiredHealthPermissions) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Link Health Connect")
                        }
                    } else {
                        Text(
                            text = "Your step data is securely syncing automatically with Android's system health registry.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                onSyncTrigger()
                                Toast.makeText(
                                    context,
                                    "Steps synced to Health Connect!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sync data now")
                        }
                    }
                }
            }

            // Dynamically hides button as soon as both permissions are granted
            if (!hasActivityPermission || !hasNotificationPermission) {
                Button(
                    onClick = {
                        val channel = NotificationChannel(
                            "non_gms_activity_tracking_channel",
                            "Activity tracking",
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
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Grant required permissions")
                }
            } else {
                Text(
                    text = "Pedometer monitoring active",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun Material3ExpressiveStepGauge(
    currentSteps: Long,
    stepGoal: Long,
    modifier: Modifier = Modifier
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
            .size(280.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val progressColor = MaterialTheme.colorScheme.primary
        val trackColor = MaterialTheme.colorScheme.surfaceVariant

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 28.dp.toPx()
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
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(
                text = String.format("%,d", currentSteps),
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "of ${String.format("%,d", stepGoal)} steps",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}