package xyz.intiluha.thecrag.ui.map

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import xyz.intiluha.thecrag.R
import xyz.intiluha.thecrag.TheCragApp
import xyz.intiluha.thecrag.data.repo.MapRepository
import xyz.intiluha.thecrag.domain.MapMarker
import xyz.intiluha.thecrag.ui.common.Attribution
import xyz.intiluha.thecrag.ui.common.describe

/** OpenFreeMap: OSM-based vector tiles, no key or registration. */
const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

class MapViewModel(private val repo: MapRepository) : ViewModel() {
    val markers = MutableStateFlow<List<MapMarker>>(emptyList())
    val error = MutableStateFlow<String?>(null)
    private var job: Job? = null

    fun viewportChanged(vp: MapRepository.Viewport) {
        job?.cancel()
        job = viewModelScope.launch {
            delay(350)
            try {
                markers.value = repo.markers(vp)
                error.value = null
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) error.value = e.describe()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(lat: Double, lon: Double, zoom: Double, onOpenArea: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: MapViewModel = viewModel { MapViewModel(TheCragApp.graph(context).map) }
    val markers by vm.markers.collectAsState()
    val error by vm.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            MapLibreView(lat, lon, zoom, markers, onViewport = vm::viewportChanged, onOpenArea = onOpenArea)
            Box(
                Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), MaterialTheme.shapes.small),
            ) { Attribution(extra = stringResource(R.string.map_attribution)) }
            error?.let { Text(it, Modifier.align(Alignment.TopCenter).padding(8.dp)) }
        }
    }
}

@Composable
private fun MapLibreView(
    lat: Double,
    lon: Double,
    zoom: Double,
    markers: List<MapMarker>,
    onViewport: (MapRepository.Viewport) -> Unit,
    onOpenArea: (Long) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { MapController(context, lat, lon, zoom, onViewport, onOpenArea) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.mapView.onStart()
                Lifecycle.Event.ON_RESUME -> controller.mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> controller.mapView.onPause()
                Lifecycle.Event.ON_STOP -> controller.mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> controller.mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { controller.mapView },
        modifier = Modifier.fillMaxSize(),
        update = { controller.setMarkers(markers) },
    )
}

/** Owns the MapView, its style layers and the callbacks; plain class so Compose stays thin. */
private class MapController(
    context: Context,
    lat: Double,
    lon: Double,
    zoom: Double,
    private val onViewport: (MapRepository.Viewport) -> Unit,
    private val onOpenArea: (Long) -> Unit,
) {
    val mapView: MapView
    private var map: MapLibreMap? = null
    private var pointSource: GeoJsonSource? = null
    private var polygonSource: GeoJsonSource? = null
    private var pending: List<MapMarker> = emptyList()

    init {
        MapLibre.getInstance(context)
        mapView = MapView(context)
        mapView.onCreate(null)
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isLogoEnabled = false
            m.cameraPosition = CameraPosition.Builder().target(LatLng(lat, lon)).zoom(zoom).build()
            m.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { style -> onStyleReady(m, style) }
            m.addOnCameraIdleListener { reportViewport(m) }
            m.addOnMapClickListener { latLng ->
                val screen = m.projection.toScreenLocation(latLng)
                val hit = m.queryRenderedFeatures(screen, LAYER_CIRCLES, LAYER_LABELS).firstOrNull()
                    ?: m.queryRenderedFeatures(screen, LAYER_FILL).firstOrNull()
                val id = hit?.getNumberProperty("id")?.toLong()
                if (id != null) { onOpenArea(id); true } else false
            }
        }
    }

    private fun onStyleReady(m: MapLibreMap, style: Style) {
        pointSource = GeoJsonSource(SRC_POINTS).also { style.addSource(it) }
        polygonSource = GeoJsonSource(SRC_POLYGONS).also { style.addSource(it) }
        style.addLayer(
            FillLayer(LAYER_FILL, SRC_POLYGONS).withProperties(
                PropertyFactory.fillColor("#2E5E3A"),
                PropertyFactory.fillOpacity(0.08f),
            ),
        )
        style.addLayer(
            LineLayer(LAYER_OUTLINE, SRC_POLYGONS).withProperties(
                PropertyFactory.lineColor("#2E5E3A"),
                PropertyFactory.lineWidth(1.5f),
            ),
        )
        style.addLayer(
            CircleLayer(LAYER_CIRCLES, SRC_POINTS).withProperties(
                PropertyFactory.circleColor("#2E5E3A"),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(1.5f),
                PropertyFactory.circleRadius(
                    Expression.interpolate(
                        Expression.linear(), Expression.get("routes"),
                        Expression.stop(0, 5f), Expression.stop(100, 8f), Expression.stop(1000, 12f), Expression.stop(20000, 18f),
                    ),
                ),
            ),
        )
        style.addLayer(
            SymbolLayer(LAYER_LABELS, SRC_POINTS).withProperties(
                PropertyFactory.textField(Expression.get("label")),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textSize(12f),
                PropertyFactory.textOffset(arrayOf(0f, 1.2f)),
                PropertyFactory.textAnchor("top"),
                PropertyFactory.textColor("#1B1B1B"),
                PropertyFactory.textHaloColor("#FFFFFF"),
                PropertyFactory.textHaloWidth(1.5f),
                PropertyFactory.textOptional(true),
            ),
        )
        setMarkers(pending)
        reportViewport(m)
    }

    private fun reportViewport(m: MapLibreMap) {
        val b = m.projection.visibleRegion.latLngBounds
        onViewport(MapRepository.Viewport(b.longitudeWest, b.latitudeSouth, b.longitudeEast, b.latitudeNorth, m.cameraPosition.zoom))
    }

    fun setMarkers(markers: List<MapMarker>) {
        pending = markers
        val points = pointSource ?: return
        val polygons = polygonSource ?: return
        points.setGeoJson(
            FeatureCollection.fromFeatures(
                markers.map { mk ->
                    Feature.fromGeometry(Point.fromLngLat(mk.center.lon, mk.center.lat)).apply {
                        addNumberProperty("id", mk.id)
                        addNumberProperty("routes", mk.numberRoutes)
                        addStringProperty("label", if (mk.numberRoutes > 0) "${mk.name} (${mk.numberRoutes})" else mk.name)
                    }
                },
            ),
        )
        polygons.setGeoJson(
            FeatureCollection.fromFeatures(
                markers.filter { it.polygon.size >= 3 }.map { mk ->
                    val ring = mk.polygon.map { Point.fromLngLat(it.lon, it.lat) }.let { it + it.first() }
                    Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply { addNumberProperty("id", mk.id) }
                },
            ),
        )
    }

    fun moveTo(lat: Double, lon: Double, zoom: Double) {
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom))
    }

    companion object {
        const val SRC_POINTS = "crag-points"
        const val SRC_POLYGONS = "crag-polygons"
        const val LAYER_CIRCLES = "crag-circles"
        const val LAYER_LABELS = "crag-labels"
        const val LAYER_FILL = "crag-fill"
        const val LAYER_OUTLINE = "crag-outline"
    }
}
