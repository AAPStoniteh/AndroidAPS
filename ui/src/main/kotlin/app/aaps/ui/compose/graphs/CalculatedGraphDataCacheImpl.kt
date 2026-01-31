package app.aaps.ui.compose.graphs

import app.aaps.core.interfaces.overview.graph.AbsIobGraphData
import app.aaps.core.interfaces.overview.graph.ActivityGraphData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgiGraphData
import app.aaps.core.interfaces.overview.graph.CalculatedGraphDataCache
import app.aaps.core.interfaces.overview.graph.CobGraphData
import app.aaps.core.interfaces.overview.graph.DeviationsGraphData
import app.aaps.core.interfaces.overview.graph.DevSlopeGraphData
import app.aaps.core.interfaces.overview.graph.IobGraphData
import app.aaps.core.interfaces.overview.graph.RatioGraphData
import app.aaps.core.interfaces.overview.graph.TimeRange
import app.aaps.core.interfaces.overview.graph.VarSensGraphData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of CalculatedGraphDataCache using MutableStateFlow.
 * Singleton cache populated by workers, observed by GraphViewModel.
 *
 * Architecture: Independent Series Updates
 * - Each series has its own MutableStateFlow
 * - Workers update their series independently
 * - Each update triggers only that series' flow
 * - ViewModel collects each flow separately for granular recomposition
 *
 * MIGRATION NOTE: This coexists with OverviewDataImpl during migration.
 * Workers populate both. After migration complete, OverviewDataImpl will be deleted.
 */
@Singleton
class CalculatedGraphDataCacheImpl @Inject constructor() : CalculatedGraphDataCache {

    override var calcProgressPct: Int = 100

    // Time range - observable for UI to react to range changes
    private val _timeRangeFlow = MutableStateFlow<TimeRange?>(null)
    override val timeRangeFlow: StateFlow<TimeRange?> = _timeRangeFlow.asStateFlow()

    // Independent series flows - each can update without affecting others
    private val _bgReadingsFlow = MutableStateFlow<List<BgDataPoint>>(emptyList())
    private val _bucketedDataFlow = MutableStateFlow<List<BgDataPoint>>(emptyList())
    private val _bgInfoFlow = MutableStateFlow<BgInfoData?>(null)

    override val bgReadingsFlow: StateFlow<List<BgDataPoint>> = _bgReadingsFlow.asStateFlow()
    override val bucketedDataFlow: StateFlow<List<BgDataPoint>> = _bucketedDataFlow.asStateFlow()
    override val bgInfoFlow: StateFlow<BgInfoData?> = _bgInfoFlow.asStateFlow()

    private val _iobGraphFlow = MutableStateFlow(IobGraphData(emptyList(), emptyList()))
    private val _absIobGraphFlow = MutableStateFlow(AbsIobGraphData(emptyList()))
    private val _cobGraphFlow = MutableStateFlow(CobGraphData(emptyList(), emptyList()))
    private val _activityGraphFlow = MutableStateFlow(ActivityGraphData(emptyList(), emptyList()))
    private val _bgiGraphFlow = MutableStateFlow(BgiGraphData(emptyList(), emptyList()))
    private val _deviationsGraphFlow = MutableStateFlow(DeviationsGraphData(emptyList()))
    private val _ratioGraphFlow = MutableStateFlow(RatioGraphData(emptyList()))
    private val _devSlopeGraphFlow = MutableStateFlow(DevSlopeGraphData(emptyList(), emptyList()))
    private val _varSensGraphFlow = MutableStateFlow(VarSensGraphData(emptyList()))

    override val iobGraphFlow: StateFlow<IobGraphData> = _iobGraphFlow.asStateFlow()
    override val absIobGraphFlow: StateFlow<AbsIobGraphData> = _absIobGraphFlow.asStateFlow()
    override val cobGraphFlow: StateFlow<CobGraphData> = _cobGraphFlow.asStateFlow()
    override val activityGraphFlow: StateFlow<ActivityGraphData> = _activityGraphFlow.asStateFlow()
    override val bgiGraphFlow: StateFlow<BgiGraphData> = _bgiGraphFlow.asStateFlow()
    override val deviationsGraphFlow: StateFlow<DeviationsGraphData> = _deviationsGraphFlow.asStateFlow()
    override val ratioGraphFlow: StateFlow<RatioGraphData> = _ratioGraphFlow.asStateFlow()
    override val devSlopeGraphFlow: StateFlow<DevSlopeGraphData> = _devSlopeGraphFlow.asStateFlow()
    override val varSensGraphFlow: StateFlow<VarSensGraphData> = _varSensGraphFlow.asStateFlow()

    // Update methods - each triggers only its own flow
    override fun updateTimeRange(range: TimeRange?) {
        _timeRangeFlow.value = range
    }

    override fun updateBgReadings(data: List<BgDataPoint>) {
        _bgReadingsFlow.value = data
    }

    override fun updateBucketedData(data: List<BgDataPoint>) {
        _bucketedDataFlow.value = data
    }

    override fun updateBgInfo(data: BgInfoData?) {
        _bgInfoFlow.value = data
    }

    override fun updateIobGraph(data: IobGraphData) {
        _iobGraphFlow.value = data
    }

    override fun updateAbsIobGraph(data: AbsIobGraphData) {
        _absIobGraphFlow.value = data
    }

    override fun updateCobGraph(data: CobGraphData) {
        _cobGraphFlow.value = data
    }

    override fun updateActivityGraph(data: ActivityGraphData) {
        _activityGraphFlow.value = data
    }

    override fun updateBgiGraph(data: BgiGraphData) {
        _bgiGraphFlow.value = data
    }

    override fun updateDeviationsGraph(data: DeviationsGraphData) {
        _deviationsGraphFlow.value = data
    }

    override fun updateRatioGraph(data: RatioGraphData) {
        _ratioGraphFlow.value = data
    }

    override fun updateDevSlopeGraph(data: DevSlopeGraphData) {
        _devSlopeGraphFlow.value = data
    }

    override fun updateVarSensGraph(data: VarSensGraphData) {
        _varSensGraphFlow.value = data
    }

    override fun reset() {
        _timeRangeFlow.value = null
        _bgReadingsFlow.value = emptyList()
        _bucketedDataFlow.value = emptyList()
        _bgInfoFlow.value = null
        // Secondary graph flows - reset to empty data
        _iobGraphFlow.value = IobGraphData(emptyList(), emptyList())
        _absIobGraphFlow.value = AbsIobGraphData(emptyList())
        _cobGraphFlow.value = CobGraphData(emptyList(), emptyList())
        _activityGraphFlow.value = ActivityGraphData(emptyList(), emptyList())
        _bgiGraphFlow.value = BgiGraphData(emptyList(), emptyList())
        _deviationsGraphFlow.value = DeviationsGraphData(emptyList())
        _ratioGraphFlow.value = RatioGraphData(emptyList())
        _devSlopeGraphFlow.value = DevSlopeGraphData(emptyList(), emptyList())
        _varSensGraphFlow.value = VarSensGraphData(emptyList())
        calcProgressPct = 100
    }
}
