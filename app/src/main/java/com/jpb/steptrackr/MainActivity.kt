package com.jpb.steptrackr

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jpb.steptrackr.utils.StepDatabase
import java.time.LocalDate
import java.time.ZoneId
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PermissionAndDashboardScreen()
                }
            }
        }
    }
}

@Composable
fun PermissionAndDashboardScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val db = remember { StepDatabase.getDatabase(appContext) }
    val healthConnectClient = remember { HealthConnectClient.getOrCreate(appContext) }

    val startOfDay = remember {
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    val todayStepsState by db.stepDao().getTodayLocalStepsFlow(startOfDay).collectAsState(initial = 0L)
    val todaySteps = todayStepsState ?: 0L
    val stepGoal = 10000L

    // Standard OS System Permissions States
    var hasActivityPermission by remember { mutableStateOf(checkPermission(appContext, Manifest.permission.ACTIVITY_RECOGNITION)) }
    var hasNotificationPermission by remember { mutableStateOf(checkPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)) }

    // NEW: Health Connect Specific Permission State
    var hasHealthPermission by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    // Define the structural Health permission required for the sync worker
    val requiredHealthPermissions = remember {
        setOf(HealthPermission.getWritePermission(StepsRecord::class))
    }

    // 1. Health Connect Custom Permission Request Contract Launcher
    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        hasHealthPermission = grantedPermissions.containsAll(requiredHealthPermissions)
    }

    // 2. Standard System Permissions Launcher
    val systemPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasActivityPermission = permissions[Manifest.permission.ACTIVITY_RECOGNITION] ?: hasActivityPermission
        hasNotificationPermission = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPermission
    }

    fun startTrackingService() {
        if (hasActivityPermission) {
            try {
                val serviceIntent = Intent(appContext, com.jpb.steptrackr.services.NonGmsStepService::class.java)
                ContextCompat.startForegroundService(appContext, serviceIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun triggerImmediateSync() {
        isSyncing = true
        val syncWorkRequest = androidx.work.OneTimeWorkRequestBuilder<com.jpb.steptrackr.services.HealthSyncWorker>().build()
        val workManager = androidx.work.WorkManager.getInstance(appContext)
        workManager.enqueue(syncWorkRequest)

        workManager.getWorkInfoByIdLiveData(syncWorkRequest.id).observeForever { workInfo ->
            if (workInfo != null && workInfo.state.isFinished) {
                isSyncing = false
            }
        }
    }

    // Check existing Health Connect permissions dynamically on view load
    LaunchedEffect(Unit) {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        hasHealthPermission = granted.containsAll(requiredHealthPermissions)
        if (hasActivityPermission) {
            startTrackingService()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Material3ExpressiveStepGauge(currentSteps = todaySteps, stepGoal = stepGoal)

        Spacer(modifier = Modifier.height(32.dp))

        // Check Phase A: Standard Hardware Pedometer & Notification Access
        if (!hasActivityPermission || !hasNotificationPermission) {
            Text("Tracking requires step and notification access.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val channel = android.app.NotificationChannel("non_gms_activity_tracking_channel", "Activity Tracking", android.app.NotificationManager.IMPORTANCE_MIN)
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    manager.createNotificationChannel(channel)

                    systemPermissionLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS))
                }
            ) {
                Text("Grant System Permissions")
            }
        }
        // Check Phase B: Health Connect Storage Writing Integration Access
        else if (!hasHealthPermission) {
            Text("App needs permission to store data into Health Connect.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    // Fires the dedicated full-screen Health Connect system authorization sheet
                    healthPermissionLauncher.launch(requiredHealthPermissions)
                }
            ) {
                Text("Grant Health Connect Access")
            }
        }
        // All Permissions are Cleared
        else {
            Text("✓ Step tracking & syncing fully active", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { triggerImmediateSync() },
                enabled = !isSyncing
            ) {
                Text(if (isSyncing) "Syncing..." else "Sync Data to Health Connect")
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

    // Smooth responsive animation matching Google Health's accent acceleration
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1400, easing = { it * it * (3f - 2f * it) }),
        label = "GaugeProgress"
    )

    // Material 3 Expressive Muted color schemes
    val progressColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .size(280.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 28.dp.toPx()
            val canvasSize = size
            val radius = (canvasSize.minDimension - strokeWidth) / 2
            val center = Offset(canvasSize.width / 2, canvasSize.height / 2)

            // 1. Draw Background Track (Expressive 300-degree arc open at bottom)
            drawArc(
                color = trackColor,
                startAngle = 120f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Draw Active Progress Arc
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

        // 3. Focal off-center typography layout inside the widget
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(
                text = String.format("%,d", currentSteps),
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
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

private fun checkPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
