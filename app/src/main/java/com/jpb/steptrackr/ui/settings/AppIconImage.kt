package com.jpb.steptrackr.ui.settings

import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.jpb.steptrackr.R

@Composable
fun AppIconImage(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val bitmap = remember(context) {
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        drawable?.let {
            val bmp = createBitmap(
                it.intrinsicWidth.coerceAtLeast(1),
                it.intrinsicHeight.coerceAtLeast(1)
            )
            val canvas = Canvas(bmp)
            it.setBounds(0, 0, canvas.width, canvas.height)
            it.draw(canvas)
            bmp.asImageBitmap()
        }
    }

    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "App Icon",
            modifier = modifier
        )
    }
}