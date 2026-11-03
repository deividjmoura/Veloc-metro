package com.gpsspeedometer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

private val Bg = Color(0xFF080B10)
private val Panel = Color(0xFF111821)
private val Accent = Color(0xFFFF5A36)
private val Muted = Color(0xFF8492A3)
private val White = Color(0xFFF4F7FA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 11, 16)
        window.navigationBarColor = android.graphics.Color.rgb(8, 11, 16)
        setContent { SpeedometerApp() }
    }
}

@Composable
private fun SpeedometerApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("trip_data", Context.MODE_PRIVATE) }
    val tripDatabase = remember { createTripDatabase(context) }
    val tripDao = remember { tripDatabase.tripRecordDao() }
    val tripHistory by tripDao.observeAll().collectAsState(initial = emptyList())
    var showHistory by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(0f) }
    var tripKm by remember { mutableDoubleStateOf(prefs.getFloat("trip_km", 0f).toDouble()) }
    var totalKm by remember { mutableDoubleStateOf(prefs.getFloat("total_km", 0f).toDouble()) }
    var maxSpeed by remember { mutableFloatStateOf(0f) }
    var averageKmh by remember { mutableFloatStateOf(0f) }
    var tripStartedAt by remember { mutableLongStateOf(prefs.getLong("trip_started_at", 0L)) }
    var hasFix by remember { mutableStateOf(false) }\n    var trackingActive by remember { mutableStateOf(false) }
    var mph by remember { mutableStateOf(false) }
    var previousLocation by remember { mutableStateOf<Location?>(null) }
    var currentLatLng by remember { mutableStateOf<LatLng?>(null) }
    var destination by remember { mutableStateOf("") }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeSummary by remember { mutableStateOf<String?>(null) }
    var routeError by remember { mutableStateOf<String?>(null) }
    var routeLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        context.startForegroundService(
            Intent(context, GpsTrackingService::class.java).setAction(GpsTrackingService.ACTION_START)
        )
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            speed = prefs.getFloat("speed_kmh", 0f)
            maxSpeed = prefs.getFloat("max_speed_kmh", 0f)
            tripKm = prefs.getFloat("trip_km", 0f).toDouble()
            totalKm = prefs.getFloat("total_km", 0f).toDouble()
            tripStartedAt = prefs.getLong("trip_started_at", 0L)
            hasFix = prefs.getBoolean("has_fix", false)
            currentLatLng = if (hasFix) {
                val lat = prefs.getFloat("current_lat", Float.NaN)
                val lng = prefs.getFloat("current_lng", Float.NaN)
                if (lat.isFinite() && lng.isFinite()) LatLng(lat.toDouble(), lng.toDouble()) else null
            } else null
            trackingActive = prefs.getBoolean("tracking_active", false)
            delay(500L)
        }
    }

    val startTracking = {
        if (!permissionGranted) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startForegroundService(
                Intent(context, GpsTrackingService::class.java).setAction(GpsTrackingService.ACTION_START)
            )
        }
    }

    LaunchedEffect(tripStartedAt, tripKm) {
        while (true) {
            averageKmh = if (tripStartedAt > 0L) {
                val elapsedHours = ((System.currentTimeMillis() - tripStartedAt).coerceAtLeast(1L)).toDouble() / 3_600_000.0
                (tripKm / elapsedHours).toFloat()
            } else 0f
            delay(1000L)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Panel, primary = Accent, onBackground = White, onSurface = White)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("GPS SPEEDOMETER", color = White, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 17.sp)
                        Text("PERFORMANCE DASHBOARD / v0.4", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
                    }
                    TextButton(onClick = {
                        if (trackingActive) {
                            context.startService(Intent(context, GpsTrackingService::class.java).setAction(GpsTrackingService.ACTION_STOP))
                        } else startTracking()
                    }) {
                        Text(if (trackingActive) "PAUSAR GPS" else "INICIAR GPS", color = Accent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                    Icon(Icons.Default.GpsFixed, null, tint = if (hasFix) Color(0xFF5BE0A0) else Muted)
                    Spacer(Modifier.width(5.dp))
                    Text(if (hasFix) "GPS ATIVO" else "AGUARDANDO GPS", color = if (hasFix) Color(0xFF5BE0A0) else Muted, fontSize = 10.sp)
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = { mph = !mph }) { Text(if (mph) "MPH" else "KM/H", color = Accent, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1.15f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        SpeedDial(if (mph) speed * 0.621371f else speed, if (mph) "MPH" else "KM/H")
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("MÁXIMA", (if (mph) maxSpeed * .621371f else maxSpeed).roundToInt().toString(), if (mph) "mph" else "km/h", Modifier.weight(1f))
                            MetricCard("MÉDIA", (if (mph) averageKmh * .621371f else averageKmh).roundToInt().toString(), if (mph) "mph" else "km/h", Modifier.weight(1f))
                        }
                    }
                    Column(Modifier.weight(.95f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("NAVEGAÇÃO", color = White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Map, null, tint = Accent, modifier = Modifier.size(18.dp))
                        }
                        MapNavigationPane(
                            currentLocation = currentLatLng,
                            destination = destination,
                            onDestinationChange = { destination = it },
                            onCenter = { currentLatLng },
                            routePoints = routePoints,
                            routeSummary = routeSummary,
                            routeError = routeError,
                            routeLoading = routeLoading,
                            onBuildRoute = {
                                val origin = currentLatLng
                                val target = destination.trim()
                                when {
                                    origin == null -> routeError = "Aguardando localização GPS para calcular a rota."
                                    target.isBlank() -> routeError = "Informe um destino."
                                    BuildConfig.DIRECTIONS_API_KEY.isBlank() -> routeError = "Configure DIRECTIONS_API_KEY em local.properties."
                                    else -> scope.launch {
                                        routeLoading = true
                                        routeError = null
                                        routeSummary = null
                                        routePoints = emptyList()
                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                requestDirections(origin, target, BuildConfig.DIRECTIONS_API_KEY)
                                            }
                                            routePoints = result.points
                                            routeSummary = "${result.distance} • ${result.duration}"
                                        } catch (error: Exception) {
                                            routeError = error.message ?: "Não foi possível calcular a rota."
                                        } finally {
                                            routeLoading = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("VIAGEM", String.format(Locale.getDefault(), "%.2f", tripKm), "km", Modifier.weight(1f))
                            MetricCard("TOTAL", String.format(Locale.getDefault(), "%.1f", totalKm), "km", Modifier.weight(1f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("CONTADOR DE VIAGEM", color = Muted, fontSize = 9.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showHistory = true }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("HISTÓRICO", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = {
                                val finishedAt = System.currentTimeMillis()
                                val finishedDistance = tripKm
                                val duration = if (tripStartedAt > 0L) (finishedAt - tripStartedAt).coerceAtLeast(0L) else 0L
                                if (finishedDistance >= 0.01) {
                                    scope.launch {
                                        tripDao.insert(
                                            TripRecord(
                                                finishedAt = finishedAt,
                                                distanceKm = finishedDistance,
                                                durationMillis = duration,
                                                averageKmh = averageKmh.toDouble(),
                                                maxSpeedKmh = maxSpeed.toDouble()
                                            )
                                        )
                                    }
                                }
                                tripKm = 0.0
                                averageKmh = 0f
                                maxSpeed = 0f
                                tripStartedAt = 0L
                                previousLocation = null
                                prefs.edit().putFloat("trip_km", 0f).putLong("trip_started_at", 0L).putFloat("max_speed_kmh", 0f).apply()
                            }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("FINALIZAR", fontSize = 10.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Média da viagem desde o início do contador. Rotas internas exigem chave Directions API.", color = Muted, fontSize = 9.sp)
            }
        }
        if (showHistory) {
            AlertDialog(
                onDismissRequest = { showHistory = false },
                title = { Text("HISTÓRICO DE VIAGENS", fontWeight = FontWeight.Black) },
                text = {
                    if (tripHistory.isEmpty()) {
                        Text("Nenhuma viagem salva ainda. Use FINALIZAR depois de percorrer uma distância para guardar o registro.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(tripHistory, key = { it.id }) { trip ->
                                Column(
                                    Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).padding(10.dp)
                                ) {
                                    Text(
                                        SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()).format(Date(trip.finishedAt)),
                                        color = Accent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        String.format(Locale.getDefault(), "%.2f km  •  média %.1f km/h  •  máxima %.1f km/h", trip.distanceKm, trip.averageKmh, trip.maxSpeedKmh),
                                        color = White,
                                        fontSize = 11.sp
                                    )
                                    Text("Duração: ${trip.durationMillis / 60000} min", color = Muted, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHistory = false }) { Text("FECHAR") }
                },
                dismissButton = {
                    if (tripHistory.isNotEmpty()) {
                        TextButton(onClick = { scope.launch { tripDao.deleteAll() } }) { Text("LIMPAR", color = Color(0xFFFF8A70)) }
                    }
                },
                containerColor = Panel,
                titleContentColor = White,
                textContentColor = White
            )
        }
    }
}

@Composable
private fun MapNavigationPane(
    currentLocation: LatLng?,
    destination: String,
    onDestinationChange: (String) -> Unit,
    onCenter: () -> LatLng?,
    routePoints: List<LatLng>,
    routeSummary: String?,
    routeError: String?,
    routeLoading: Boolean,
    onBuildRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cameraState = rememberCameraPositionState()
    var centeredOnFirstFix by remember { mutableStateOf(false) }

    LaunchedEffect(currentLocation) {
        if (currentLocation != null && !centeredOnFirstFix) {
            cameraState.animate(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f))
            centeredOnFirstFix = true
        }
    }
    LaunchedEffect(routePoints) {
        if (routePoints.isNotEmpty()) {
            val bounds = LatLngBounds.builder().apply { routePoints.forEach { include(it) } }.build()
            cameraState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 56))
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0xFF263442), RoundedCornerShape(14.dp))) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraState,
                properties = MapProperties(isBuildingEnabled = true, isTrafficEnabled = false),
                uiSettings = MapUiSettings(zoomControlsEnabled = false, compassEnabled = true, myLocationButtonEnabled = false)
            ) {
                currentLocation?.let { Marker(state = MarkerState(position = it), title = "Minha localização", snippet = "Posição GPS atual") }
                if (routePoints.isNotEmpty()) {
                    Polyline(points = routePoints, color = Accent, width = 10f)
                    Marker(state = MarkerState(position = routePoints.last()), title = "Destino")
                }
            }
            Surface(Modifier.align(Alignment.TopStart).padding(8.dp), color = Color(0xDD080B10), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(if (currentLocation != null) Color(0xFF5BE0A0) else Accent, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(if (currentLocation != null) "GPS LOCALIZADO" else "AGUARDANDO GPS", color = White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            FilledTonalIconButton(
                onClick = { onCenter()?.let { cameraState.move(CameraUpdateFactory.newLatLngZoom(it, 16f)) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(9.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xEE111821), contentColor = Accent)
            ) { Icon(Icons.Default.MyLocation, contentDescription = "Centralizar localização") }
            if (currentLocation == null) {
                Surface(Modifier.align(Alignment.Center).padding(12.dp), color = Color(0xDD080B10), shape = RoundedCornerShape(10.dp)) {
                    Text("Aguardando sinal GPS…", color = White, fontSize = 11.sp, modifier = Modifier.padding(10.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = destination,
                onValueChange = onDestinationChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Destino ou endereço", fontSize = 11.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = White),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Go),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF263442),
                    focusedTextColor = White, unfocusedTextColor = White,
                    focusedPlaceholderColor = Muted, unfocusedPlaceholderColor = Muted
                )
            )
            IconButton(
                onClick = onBuildRoute,
                enabled = destination.isNotBlank() && !routeLoading,
                modifier = Modifier.size(48.dp).background(Accent, RoundedCornerShape(10.dp))
            ) {
                if (routeLoading) CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Default.Navigation, contentDescription = "Calcular rota no mapa", tint = Color.White)
            }
        }
        routeSummary?.let { Text("ROTA • $it", color = Color(0xFF5BE0A0), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        routeError?.let { Text(it, color = Color(0xFFFFA58F), fontSize = 9.sp, maxLines = 2) }
        TextButton(
            onClick = {
                val query = destination.trim()
                if (query.isNotEmpty()) {
                    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(query)}&travelmode=driving")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            },
            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp)
        ) { Text("ABRIR NAVEGAÇÃO NO GOOGLE MAPS", color = Muted, fontSize = 8.sp) }
    }
}

private data class DirectionsResult(val points: List<LatLng>, val distance: String, val duration: String)

private fun requestDirections(origin: LatLng, destination: String, apiKey: String): DirectionsResult {
    val endpoint = "https://maps.googleapis.com/maps/api/directions/json" +
        "?origin=${origin.latitude},${origin.longitude}" +
        "&destination=${Uri.encode(destination)}&mode=driving&language=pt-BR&key=$apiKey"
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15000
        readTimeout = 15000
    }
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) error("Erro HTTP $code ao consultar rotas.")
        val json = JSONObject(body)
        val status = json.optString("status")
        if (status != "OK") {
            val detail = json.optString("error_message").takeIf { it.isNotBlank() }
            error(detail ?: "Google Directions: $status")
        }
        val route = json.getJSONArray("routes").getJSONObject(0)
        val leg = route.getJSONArray("legs").getJSONObject(0)
        return DirectionsResult(
            decodePolyline(route.getJSONObject("overview_polyline").getString("points")),
            leg.getJSONObject("distance").getString("text"),
            leg.getJSONObject("duration").getString("text")
        )
    } finally {
        connection.disconnect()
    }
}

internal fun decodePolyline(encoded: String): List<LatLng> {
    val points = ArrayList<LatLng>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        var result = 0
        var shift = 0
        var byte: Int
        do {
            require(index < encoded.length) { "Polyline inválida recebida do serviço de rotas." }
            byte = encoded[index++].code - 63
            require(byte in 0..63) { "Polyline contém caractere inválido." }
            require(shift <= 30) { "Polyline excede o limite de coordenadas suportado." }
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
        } while (byte >= 0x20)
        lat += if ((result and 1) != 0) (result shr 1).inv() else result shr 1

        result = 0
        shift = 0
        do {
            require(index < encoded.length) { "Polyline inválida recebida do serviço de rotas." }
            byte = encoded[index++].code - 63
            require(byte in 0..63) { "Polyline contém caractere inválido." }
            require(shift <= 30) { "Polyline excede o limite de coordenadas suportado." }
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
        } while (byte >= 0x20)
        lng += if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        points.add(LatLng(lat / 1E5, lng / 1E5))
    }
    check(points.isNotEmpty()) { "O serviço de rotas não retornou pontos." }
    return points
}

@Composable
private fun SpeedDial(speed: Float, unit: String) {
    Box(Modifier.fillMaxWidth().aspectRatio(1.45f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 13.dp.toPx()
            val diameter = size.minDimension * .82f
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            drawArc(Color(0xFF26313D), 135f, 270f, false, topLeft, androidx.compose.ui.geometry.Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Accent, 135f, (speed.coerceIn(0f, 240f) / 240f) * 270f, false, topLeft, androidx.compose.ui.geometry.Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(speed.roundToInt().toString(), color = White, fontSize = 62.sp, fontWeight = FontWeight.Black, letterSpacing = (-3).sp)
            Text(unit, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text("VELOCIDADE ATUAL", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Panel, RoundedCornerShape(12.dp)).border(1.dp, Color(0xFF202C38), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Text(unit, color = Accent, fontSize = 9.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}
