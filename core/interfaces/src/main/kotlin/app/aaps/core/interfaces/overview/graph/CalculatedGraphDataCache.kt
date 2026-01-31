package app.aaps.core.interfaces.overview.graph

import kotlinx.coroutines.flow.StateFlow

/**
 * Cache for calculated graph data. Populated by workers, observed by ViewModel via StateFlow.
 * This replaces OverviewDataImpl for the new Compose graph system.
 *
 * Architecture: Independent Series Updates
 * - Each series has its own StateFlow
 * - Workers update their series independently
 * - ViewModel/UI collects each flow separately
 * - Only the changed series triggers recomposition
 *
 * MIGRATION NOTE: During migration, workers populate BOTH this cache and OverviewDataImpl.
 * After migration complete, OverviewDataImpl will be deleted.
 */
interface CalculatedGraphDataCache {

    // Time range - observable, recalculated as series arrive
    val timeRangeFlow: StateFlow<TimeRange?>
    fun updateTimeRange(range: TimeRange?)

    // Calculation progress (0-100)
    var calcProgressPct: Int

    // BG data - each series independent
    val bgReadingsFlow: StateFlow<List<BgDataPoint>>
    val bucketedDataFlow: StateFlow<List<BgDataPoint>>

    // BG info for overview info section display
    val bgInfoFlow: StateFlow<BgInfoData?>

    // Update methods for workers - each triggers only its flow
    fun updateBgReadings(data: List<BgDataPoint>)
    fun updateBucketedData(data: List<BgDataPoint>)
    fun updateBgInfo(data: BgInfoData?)

    // =========================================================================
    // Secondary graph flows (Phase 5) - one per graph
    // Non-nullable with empty data default (consistent with BG flows)
    // =========================================================================

    // IOB graph: regular IOB line + prediction points
    val iobGraphFlow: StateFlow<IobGraphData>
    fun updateIobGraph(data: IobGraphData)

    // Absolute IOB graph: absolute IOB line only
    val absIobGraphFlow: StateFlow<AbsIobGraphData>
    fun updateAbsIobGraph(data: AbsIobGraphData)

    // COB graph: COB line + failover marker points
    val cobGraphFlow: StateFlow<CobGraphData>
    fun updateCobGraph(data: CobGraphData)

    // Activity graph: historical activity + prediction line
    val activityGraphFlow: StateFlow<ActivityGraphData>
    fun updateActivityGraph(data: ActivityGraphData)

    // BGI graph: historical + prediction line
    val bgiGraphFlow: StateFlow<BgiGraphData>
    fun updateBgiGraph(data: BgiGraphData)

    // Deviations graph: deviation bars with color types
    val deviationsGraphFlow: StateFlow<DeviationsGraphData>
    fun updateDeviationsGraph(data: DeviationsGraphData)

    // Ratio graph: autosens ratio percentage line
    val ratioGraphFlow: StateFlow<RatioGraphData>
    fun updateRatioGraph(data: RatioGraphData)

    // Dev slope graph: max and min slope lines
    val devSlopeGraphFlow: StateFlow<DevSlopeGraphData>
    fun updateDevSlopeGraph(data: DevSlopeGraphData)

    // Variable sensitivity graph: sensitivity line from APS results
    val varSensGraphFlow: StateFlow<VarSensGraphData>
    fun updateVarSensGraph(data: VarSensGraphData)

    fun reset()
}
