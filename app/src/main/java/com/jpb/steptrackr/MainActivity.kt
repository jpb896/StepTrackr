package com.jpb.steptrackr

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpb.steptrackr.utils.StepDatabase
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var todaySteps by remember { mutableLongStateOf(4230L) }
                    val stepGoal = 10000L

                    // Read local cache to populate current steps on launch
                    val db = StepDatabase.getDatabase(this)
                    LaunchedEffect(Unit) {
                        launch {
                            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            todaySteps = db.stepDao().getTodayLocalSteps(startOfDay) ?: 0L
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Material3ExpressiveStepGauge(currentSteps = todaySteps, stepGoal = stepGoal)
                    }
                }
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