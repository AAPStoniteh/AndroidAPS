package app.aaps.ui.compose.graphs.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.CalculatedGraphDataCache
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for Overview graphs (Compose/Vico version).
 *
 * Architecture: Independent Series Updates
 * - Each series (BG readings, bucketed, IOB, COB, etc.) has its own StateFlow
 * - UI collects each flow separately
 * - Only the changed series triggers recomposition
 * - Time range is derived from all series (recalculates as data arrives)
 *
 * Workers emit to cache flows → ViewModel exposes flows → UI collects independently
 */

/**
 * Static chart configuration (doesn't change during graph lifetime)
 */
data class ChartConfig(
    val highMark: Double,
    val lowMark: Double
)

/**
 * UI state for BG info section display
 */
data class BgInfoUiState(
    val bgInfo: BgInfoData?,
    val timeAgoText: String
)

class GraphViewModel @Inject constructor(
    cache: CalculatedGraphDataCache,
    private val aapsLogger: AAPSLogger,
    preferences: Preferences,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper
) : ViewModel() {

    // Static chart config - read once at initialization
    val chartConfig = ChartConfig(
        highMark = preferences.get(UnitDoubleKey.OverviewHighMark),
        lowMark = preferences.get(UnitDoubleKey.OverviewLowMark)
    )

    // Individual series flows - each can trigger independent recomposition
    val bgReadingsFlow: StateFlow<List<BgDataPoint>> = cache.bgReadingsFlow
    val bucketedDataFlow: StateFlow<List<BgDataPoint>> = cache.bucketedDataFlow

    // Secondary graph flows
    val iobGraphFlow = cache.iobGraphFlow
    val cobGraphFlow = cache.cobGraphFlow

    // =========================================================================
    // BG Info Section (Overview info display)
    // =========================================================================

    // Ticker flow for periodic timeAgo updates (every 30 seconds)
    private val timeAgoTicker = flow {
        while (true) {
            emit(Unit)
            delay(30_000L)
        }
    }

    // BG info UI state - combines bgInfo with periodic timeAgo updates
    val bgInfoState: StateFlow<BgInfoUiState> = combine(
        cache.bgInfoFlow,
        timeAgoTicker
    ) { bgInfo, _ ->
        BgInfoUiState(
            bgInfo = bgInfo,
            timeAgoText = dateUtil.minOrSecAgo(rh, bgInfo?.timestamp)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BgInfoUiState(bgInfo = null, timeAgoText = "")
    )

    // Derived time range from actual data (recalculates as series arrive)
    val derivedTimeRange: StateFlow<Pair<Long, Long>?> = combine(
        cache.bgReadingsFlow,
        cache.bucketedDataFlow,
        cache.timeRangeFlow
    ) { bgReadings, bucketedData, cacheTimeRange ->
        // Combine all timestamps from all series
        val allTimestamps = (bgReadings + bucketedData).map { it.timestamp }

        if (allTimestamps.isEmpty()) {
            // Fall back to cache time range if no data yet
            cacheTimeRange?.let { Pair(it.fromTime, it.toTime) }
        } else {
            val minTime = allTimestamps.minOrNull() ?: return@combine null
            val maxTime = allTimestamps.maxOrNull() ?: return@combine null
            Pair(minTime, maxTime)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    init {
        aapsLogger.debug(LTag.UI, "GraphViewModel initialized - exposing independent series flows")
    }

    override fun onCleared() {
        super.onCleared()
        aapsLogger.debug(LTag.UI, "GraphViewModel cleared")
    }
}
