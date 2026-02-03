package app.aaps.ui.compose.graphs

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.graph.AbsIobGraphData
import app.aaps.core.interfaces.overview.graph.ActivityGraphData
import app.aaps.core.interfaces.overview.graph.BgDataPoint
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.interfaces.overview.graph.BgiGraphData
import app.aaps.core.interfaces.overview.graph.CobGraphData
import app.aaps.core.interfaces.overview.graph.DevSlopeGraphData
import app.aaps.core.interfaces.overview.graph.DeviationsGraphData
import app.aaps.core.interfaces.overview.graph.IobGraphData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.overview.graph.ProfileDisplayData
import app.aaps.core.interfaces.overview.graph.RatioGraphData
import app.aaps.core.interfaces.overview.graph.TempTargetDisplayData
import app.aaps.core.interfaces.overview.graph.TempTargetState
import app.aaps.core.interfaces.overview.graph.TimeRange
import app.aaps.core.interfaces.overview.graph.VarSensGraphData
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.profile.ProfileSealed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Implementation of OverviewDataCache using MutableStateFlow.
 * Singleton cache that observes database changes and updates UI state reactively.
 *
 * Architecture: Reactive Data Observation
 * - Observes GlucoseValue, TempTarget, EffectiveProfileSwitch changes via Flow
 * - Updates state flows immediately when data changes
 * - No dependency on calculation workflow for basic display data
 * - Each data type has its own StateFlow for granular recomposition
 *
 * MIGRATION NOTE: This coexists with OverviewDataImpl during migration.
 * Workers populate graph data. After migration complete, OverviewDataImpl will be deleted.
 */
@Singleton
class OverviewDataCacheImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val persistenceLayer: PersistenceLayer,
    private val profileUtil: ProfileUtil,
    private val profileFunction: ProfileFunction,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper,
    private val trendCalculator: TrendCalculator,
    private val iobCobCalculator: IobCobCalculator,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val loop: Loop,
    private val config: Config,
    private val processedDeviceStatusData: ProcessedDeviceStatusData
) : OverviewDataCache {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Load initial data from database
        scope.launch {
            aapsLogger.debug(LTag.UI, "OverviewDataCache: Loading initial data")
            updateBgInfoFromDatabase()
            updateProfileFromDatabase()
            updateTempTargetFromDatabase()
        }

        // Observe GlucoseValue changes
        scope.launch {
            persistenceLayer.observeChanges(GV::class.java).collect { glucoseValues ->
                aapsLogger.debug(LTag.UI, "GV change detected, updating BgInfo (${glucoseValues.size} values)")
                updateBgInfoFromDatabase()
            }
        }

        // Observe TempTarget changes
        scope.launch {
            persistenceLayer.observeChanges(TT::class.java).collect {
                aapsLogger.debug(LTag.UI, "TT change detected, updating TempTarget state")
                updateTempTargetFromDatabase()
            }
        }

        // Observe EffectiveProfileSwitch changes
        scope.launch {
            persistenceLayer.observeChanges(EPS::class.java).collect {
                aapsLogger.debug(LTag.UI, "EPS change detected, updating Profile state")
                updateProfileFromDatabase()
                // TT display depends on profile being loaded (for default target)
                updateTempTargetFromDatabase()
            }
        }
    }

    // =========================================================================
    // BgInfo computation
    // =========================================================================

    private suspend fun updateBgInfoFromDatabase() {
        val lastGv = persistenceLayer.getLastGlucoseValue()
        if (lastGv == null) {
            _bgInfoFlow.value = null
            return
        }

        val highMark = preferences.get(UnitDoubleKey.OverviewHighMark)
        val lowMark = preferences.get(UnitDoubleKey.OverviewLowMark)
        val valueInUnits = profileUtil.fromMgdlToUnits(lastGv.value)

        val bgRange = when {
            valueInUnits > highMark -> BgRange.HIGH
            valueInUnits < lowMark  -> BgRange.LOW
            else                    -> BgRange.IN_RANGE
        }

        val isOutdated = lastGv.timestamp < dateUtil.now() - 9 * 60 * 1000L
        val trendArrow = trendCalculator.getTrendArrow(iobCobCalculator.ads)
        val trendDescription = trendCalculator.getTrendDescription(iobCobCalculator.ads)
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData

        _bgInfoFlow.value = BgInfoData(
            bgValue = valueInUnits,
            bgText = profileUtil.fromMgdlToStringInUnits(lastGv.value),
            bgRange = bgRange,
            isOutdated = isOutdated,
            timestamp = lastGv.timestamp,
            trendArrow = trendArrow,
            trendDescription = trendDescription,
            delta = glucoseStatus?.let { profileUtil.fromMgdlToUnits(it.delta) },
            deltaText = glucoseStatus?.let { profileUtil.fromMgdlToSignedStringInUnits(it.delta) },
            shortAvgDelta = glucoseStatus?.let { profileUtil.fromMgdlToUnits(it.shortAvgDelta) },
            shortAvgDeltaText = glucoseStatus?.let { profileUtil.fromMgdlToSignedStringInUnits(it.shortAvgDelta) },
            longAvgDelta = glucoseStatus?.let { profileUtil.fromMgdlToUnits(it.longAvgDelta) },
            longAvgDeltaText = glucoseStatus?.let { profileUtil.fromMgdlToSignedStringInUnits(it.longAvgDelta) }
        )
    }

    // =========================================================================
    // TempTarget computation
    // =========================================================================

    private suspend fun updateTempTargetFromDatabase() {
        val units = profileFunction.getUnits()
        val now = dateUtil.now()
        val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(now)

        val displayData = if (tempTarget != null) {
            // Active TT - show target range + "until HH:MM"
            val targetText = profileUtil.toTargetRangeString(tempTarget.lowTarget, tempTarget.highTarget, GlucoseUnit.MGDL, units) +
                " " + dateUtil.untilString(tempTarget.end, rh)
            TempTargetDisplayData(
                targetText = targetText,
                state = TempTargetState.ACTIVE,
                timestamp = tempTarget.timestamp,
                duration = tempTarget.duration
            )
        } else {
            // No active TT - check profile
            val profile = profileFunction.getProfile()
            if (profile != null) {
                // Check if APS/AAPSCLIENT has adjusted target
                val targetUsed = when {
                    config.APS -> loop.lastRun?.constraintsProcessed?.targetBG ?: 0.0
                    config.AAPSCLIENT -> processedDeviceStatusData.getAPSResult()?.targetBG ?: 0.0
                    else -> 0.0
                }

                if (targetUsed != 0.0 && abs(profile.getTargetMgdl() - targetUsed) > 0.01) {
                    // APS adjusted target
                    val apsTarget = profileUtil.toTargetRangeString(targetUsed, targetUsed, GlucoseUnit.MGDL, units)
                    TempTargetDisplayData(apsTarget, TempTargetState.ADJUSTED, 0L, 0L)
                } else {
                    // Default profile target
                    val profileTarget = profileUtil.toTargetRangeString(profile.getTargetLowMgdl(), profile.getTargetHighMgdl(), GlucoseUnit.MGDL, units)
                    TempTargetDisplayData(profileTarget, TempTargetState.NONE, 0L, 0L)
                }
            } else {
                // No profile loaded yet
                TempTargetDisplayData("", TempTargetState.NONE, 0L, 0L)
            }
        }

        _tempTargetFlow.value = displayData
    }

    // =========================================================================
    // Profile computation
    // =========================================================================

    private fun updateProfileFromDatabase() {
        val profile = profileFunction.getProfile()
        var isModified = false
        var timestamp = 0L
        var duration = 0L

        if (profile is ProfileSealed.EPS) {
            val eps = profile.value
            isModified = eps.originalPercentage != 100 || eps.originalTimeshift != 0L || eps.originalDuration != 0L
            timestamp = eps.timestamp
            duration = eps.originalDuration
        }

        _profileFlow.value = ProfileDisplayData(
            profileName = profileFunction.getProfileNameWithRemainingTime(),
            isLoaded = profile != null,
            isModified = isModified,
            timestamp = timestamp,
            duration = duration
        )
    }

    // =========================================================================
    // State flows
    // =========================================================================

    override var calcProgressPct: Int = 100

    // Time range
    private val _timeRangeFlow = MutableStateFlow<TimeRange?>(null)
    override val timeRangeFlow: StateFlow<TimeRange?> = _timeRangeFlow.asStateFlow()

    // BG data flows
    private val _bgReadingsFlow = MutableStateFlow<List<BgDataPoint>>(emptyList())
    private val _bucketedDataFlow = MutableStateFlow<List<BgDataPoint>>(emptyList())
    private val _bgInfoFlow = MutableStateFlow<BgInfoData?>(null)

    override val bgReadingsFlow: StateFlow<List<BgDataPoint>> = _bgReadingsFlow.asStateFlow()
    override val bucketedDataFlow: StateFlow<List<BgDataPoint>> = _bucketedDataFlow.asStateFlow()
    override val bgInfoFlow: StateFlow<BgInfoData?> = _bgInfoFlow.asStateFlow()

    // Overview chip flows
    private val _tempTargetFlow = MutableStateFlow<TempTargetDisplayData?>(null)
    private val _profileFlow = MutableStateFlow<ProfileDisplayData?>(null)

    override val tempTargetFlow: StateFlow<TempTargetDisplayData?> = _tempTargetFlow.asStateFlow()
    override val profileFlow: StateFlow<ProfileDisplayData?> = _profileFlow.asStateFlow()

    // Secondary graph flows
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

    // =========================================================================
    // Update methods
    // =========================================================================

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
        _tempTargetFlow.value = null
        _profileFlow.value = null
        // Secondary graph flows
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
