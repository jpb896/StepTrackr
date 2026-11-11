package com.jpb.steptrackr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jpb.steptrackr.R
import com.jpb.steptrackr.utils.RouteDistanceCalculator
import com.jpb.steptrackr.utils.RoutePointEntity
import com.jpb.steptrackr.utils.StepDatabase
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.time.LocalDate
import java.time.ZoneId

data class RouteLocation(val latitude: Double, val longitude: Double)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteMapScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { StepDatabase.getDatabase(context) }

    val startOfDay = remember {
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val endOfDay = remember {
        LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
    }

    val routeEntitiesState by database.stepDao()
        .getRoutePointsForRangeFlow(startOfDay, endOfDay)
        .collectAsState(initial = emptyList())

    val routeLocations = routeEntitiesState.map { RouteLocation(it.latitude, it.longitude) }

    // Calculate total distance traveled
    val totalDistanceKm = remember(routeLocations) {
        RouteDistanceCalculator.calculateTotalDistanceKm(routeLocations)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.where_youve_been)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            RouteMapView(routePoints = routeLocations)

            // Floating overlay card showing distance traveled
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopEnd),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Distance Traveled",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%.2f km", totalDistanceKm),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun RouteMapView(
    routePoints: List<RouteLocation>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    remember {
        Configuration.getInstance().userAgentValue = context.packageName
        true
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(16.0)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()

            if (routePoints.isNotEmpty()) {
                val geoPoints = routePoints.map { GeoPoint(it.latitude, it.longitude) }
                val polyline = Polyline().apply {
                    setPoints(geoPoints)
                    outlinePaint.color = android.graphics.Color.BLUE
                    outlinePaint.strokeWidth = 12f
                }

                mapView.overlays.add(polyline)
                mapView.controller.setCenter(geoPoints.last())
                mapView.invalidate()
            }
        }
    )
}