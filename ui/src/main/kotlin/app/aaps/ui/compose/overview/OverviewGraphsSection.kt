package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aaps.ui.compose.graphs.BgGraphCompose
import app.aaps.ui.compose.graphs.CobGraphCompose
import app.aaps.ui.compose.graphs.DEFAULT_GRAPH_ZOOM_MINUTES
import app.aaps.ui.compose.graphs.IobGraphCompose
import app.aaps.ui.compose.graphs.viewmodels.GraphViewModel
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce

/**
 * Overview graphs section using Vico charts.
 *
 * Pattern: Observe Primary + Sync to Secondary
 * - Each graph has its OWN VicoScrollState and VicoZoomState
 * - BG graph: Interactive - user can scroll/zoom
 * - IOB/COB graphs: Non-interactive - scroll/zoom disabled
 * - LaunchedEffect observes BG graph's state changes and syncs to IOB/COB
 *
 * Synchronization Implementation:
 * - snapshotFlow observes scroll/zoom values from BG graph
 * - debounce(50) waits for gesture to settle
 * - zoomState.zoom() and scrollState.scroll() copy state to secondary graphs
 *
 * Graphs (top to bottom):
 * - BG Graph: Blood glucose readings (200dp height) - Primary interactive
 * - IOB Graph: Insulin on board (75dp height) - Display only, follows BG graph
 * - COB Graph: Carbs on board (75dp height) - Display only, follows BG graph
 */
@OptIn(FlowPreview::class)
@Composable
fun OverviewGraphsSection(
    graphViewModel: GraphViewModel,
    modifier: Modifier = Modifier
) {
    // BG graph - primary interactive
    val bgScrollState = rememberVicoScrollState(
        scrollEnabled = true,
        initialScroll = Scroll.Absolute.End
    )
    val bgZoomState = rememberVicoZoomState(
        zoomEnabled = true,
        initialZoom = Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES)
    )

    // IOB graph - non-interactive, synced from BG
    val iobScrollState = rememberVicoScrollState(
        scrollEnabled = false,
        initialScroll = Scroll.Absolute.End
    )
    val iobZoomState = rememberVicoZoomState(
        zoomEnabled = false,
        initialZoom = Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES)
    )

    // COB graph - non-interactive, synced from BG
    val cobScrollState = rememberVicoScrollState(
        scrollEnabled = false,
        initialScroll = Scroll.Absolute.End
    )
    val cobZoomState = rememberVicoZoomState(
        zoomEnabled = false,
        initialZoom = Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES)
    )

    // Observe BG graph scroll/zoom and sync to IOB/COB graphs
    LaunchedEffect(bgScrollState, bgZoomState, iobScrollState, iobZoomState, cobScrollState, cobZoomState) {
        snapshotFlow { bgScrollState.value to bgZoomState.value }
            .debounce(50) // Wait for gesture to settle
            .collect { (scroll, zoom) ->
                // Sync zoom first, then scroll (order matters for proper positioning)
                iobZoomState.zoom(Zoom.fixed(zoom))
                cobZoomState.zoom(Zoom.fixed(zoom))
                delay(10)
                iobScrollState.scroll(Scroll.Absolute.pixels(scroll))
                cobScrollState.scroll(Scroll.Absolute.pixels(scroll))
            }
    }

    // Auto-scroll to end when new BG value arrives
    val bgInfoState by graphViewModel.bgInfoState.collectAsState()
    var lastBgTimestamp by remember { mutableLongStateOf(0L) }

    LaunchedEffect(bgInfoState.bgInfo?.timestamp) {
        val newTimestamp = bgInfoState.bgInfo?.timestamp ?: return@LaunchedEffect
        if (lastBgTimestamp != 0L && newTimestamp > lastBgTimestamp) {
            // New BG arrived - scroll all graphs to show latest data
            bgScrollState.scroll(Scroll.Absolute.End)
        }
        lastBgTimestamp = newTimestamp
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // BG Graph - primary interactive graph
        BgGraphCompose(
            viewModel = graphViewModel,
            scrollState = bgScrollState,
            zoomState = bgZoomState,
            modifier = Modifier.fillMaxWidth()
        )

        // IOB Graph - non-interactive, synced from BG graph
        IobGraphCompose(
            viewModel = graphViewModel,
            scrollState = iobScrollState,
            zoomState = iobZoomState,
            modifier = Modifier.fillMaxWidth()
        )

        // COB Graph - non-interactive, synced from BG graph
        CobGraphCompose(
            viewModel = graphViewModel,
            scrollState = cobScrollState,
            zoomState = cobZoomState,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
