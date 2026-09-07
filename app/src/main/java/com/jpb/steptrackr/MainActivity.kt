package com.jpb.steptrackr

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
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
import com.jpb.steptrackr.utils.StepDatabase
import java.time.LocalDate
import java.time.ZoneId

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

    // CRITICAL FIX: Pass the base Activity instance context for handling the Health Connect UI window
    val activityContext = remember(context) { context as Activity }
    val db = remember { StepDatabase.getDatabase(context.applicationContext) }
    val healthConnectClient = remember { HealthConnectClient.getOrCreate(activityContext) }

    val startOfDay = remember {
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    val todayStepsState by db.stepDao().getTodayLocalStepsFlow(startOfDay).collectAsState(initial = 0L)
    val todaySteps = todayStepsState ?: 0L
    val stepGoal = 10000L

    // Unified UI permission states
    var hasActivityPermission by remember { mutableStateOf(checkPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)) }
    var hasNotificationPermission by remember { mutableStateOf(checkPermission(context, Manifest.permission.POST_NOTIFICATIONS)) }
    var hasHealthPermission by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    val requiredHealthPermissions = remember { setOf(HealthPermission.getWritePermission(StepsRecord::class)) }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        hasHealthPermission = grantedPermissions.containsAll(requiredHealthPermissions)
    }

    val systemPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasActivityPermission = permissions[Manifest.permission.ACTIVITY_RECOGNITION] ?: hasActivityPermission
        hasNotificationPermission = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPermission
    }

    fun startTrackingService() {
        if (hasActivityPermission) {
            try {
                val serviceIntent = Intent(context.applicationContext, com.jpb.steptrackr.services.NonGmsStepService::class.java)
                ContextCompat.startForegroundService(context.applicationContext, serviceIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun triggerImmediateSync() {
        isSyncing = true
        val syncWorkRequest = OneTimeWorkRequestBuilder<com.jpb.steptrackr.services.HealthSyncWorker>().build()
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.enqueue(syncWorkRequest)

        workManager.getWorkInfoByIdLiveData(syncWorkRequest.id).observeForever { workInfo ->
            if (workInfo != null && workInfo.state.isFinished) {
                isSyncing = false
            }
        }
    }

    // Refresh configurations natively on initial screen layout load
    LaunchedEffect(hasActivityPermission) {
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

        // FIX 3: Conditional Visibility - Button layouts disappear completely upon verification clearance
        if (!hasActivityPermission || !hasNotificationPermission) {
            Text("Tracking requires step and notification access.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val channel = NotificationChannel("non_gms_activity_tracking_channel", "Activity Tracking", NotificationManager.IMPORTANCE_MIN)
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.createNotificationChannel(channel)

                    systemPermissionLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.POST_NOTIFICATIONS))
                }
            ) {
                Text("Grant System Permissions")
            }
        } else if (!hasHealthPermission) {
            Text("App needs permission to store data into Health Connect.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { healthPermissionLauncher.launch(requiredHealthPermissions) }) {
                Text("Grant Health Connect Access")
            }
        } else {
            // All cleared state: Show only status information and the immediate sync trigger
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
fun Material3ExpressiveStepGauge(currentSteps: Long, stepGoal: Long, modifier: Modifier = Modifier) {
    val progress = if (stepGoal > 0) (currentSteps.toFloat() / stepGoal.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1400, easing = { it * it * (3f - 2f * it) }),
        label = "GaugeProgress"
    )

    Box(modifier = modifier.size(280.dp).padding(16.dp), contentAlignment = Alignment.Center) {
        val progressColor = MaterialTheme.colorScheme.primary
        val trackColor = MaterialTheme.colorScheme.surfaceVariant

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 28.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)

            drawArc(
                color = trackColor, startAngle = 120f, sweepAngle = 300f, useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor, startAngle = 120f, sweepAngle = 300f * animatedProgress, useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 12.dp)) {
            Text(text = String.format("%,d", currentSteps), fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = "of ${String.format("%,d", stepGoal)} steps", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun checkPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}