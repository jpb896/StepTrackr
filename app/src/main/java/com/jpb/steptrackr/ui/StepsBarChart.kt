package com.jpb.steptrackr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpb.steptrackr.utils.StepDataPoint

@Composable
fun StepsBarChart(
    stepData: List<StepDataPoint>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    dailyGoal: Int = 10000,
    height: Dp = 220.dp
) {
    val textMeasurer = rememberTextMeasurer()
    val maxSteps = (stepData.maxOfOrNull { it.steps } ?: dailyGoal).coerceAtLeast(dailyGoal)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "Steps over time",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val labelPadding = 40f // Bottom space reserved for labels
            val graphHeight = canvasHeight - labelPadding

            val itemWidth = canvasWidth / stepData.size
            val barWidth = itemWidth * 0.45f

            // Draw goal reference line
            val goalY = graphHeight - (dailyGoal.toFloat() / maxSteps * graphHeight)
            drawLine(
                color = Color.Gray.copy(alpha = 0.4f),
                start = Offset(0f, goalY),
                end = Offset(canvasWidth, goalY),
                strokeWidth = 2f
            )

            // Draw Bars and Labels
            stepData.forEachIndexed { index, dataPoint ->
                val xCenter = (index * itemWidth) + (itemWidth / 2)
                val barHeight = (dataPoint.steps.toFloat() / maxSteps) * graphHeight
                val barTop = graphHeight - barHeight

                // Draw Bar
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(xCenter - (barWidth / 2), barTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(8f, 8f)
                )

                // Draw X-Axis Time Label
                val textLayoutResult = textMeasurer.measure(
                    text = dataPoint.label,
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                )

                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        xCenter - (textLayoutResult.size.width / 2),
                        graphHeight + 8f
                    )
                )
            }
        }
    }
}