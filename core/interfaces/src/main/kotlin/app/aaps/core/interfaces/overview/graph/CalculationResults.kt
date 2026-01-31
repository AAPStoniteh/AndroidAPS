package app.aaps.core.interfaces.overview.graph

import app.aaps.core.data.model.TrendArrow

/**
 * Domain models for calculated graph data.
 * These are view-agnostic - no GraphView or Vico dependencies.
 * Populated by workers, consumed by ViewModels/UI.
 */

/**
 * BG range classification for coloring
 */
enum class BgRange {

    HIGH,      // Above high mark
    IN_RANGE,  // Within target range
    LOW        // Below low mark
}

/**
 * BG data point type for different rendering styles and colors
 */
enum class BgType {

    REGULAR,         // Regular CGM reading (outlined circle, white)
    BUCKETED,        // 5-min bucketed average (filled circle, colored by range)
    IOB_PREDICTION,  // IOB-based prediction (filled concentric circles, blue)
    COB_PREDICTION,  // COB-based prediction (filled concentric circles, orange)
    A_COB_PREDICTION,// Absorbed COB prediction (filled concentric circles, lighter orange)
    UAM_PREDICTION,  // Unannounced meals prediction (filled concentric circles, yellow)
    ZT_PREDICTION    // Zero-temp prediction (filled concentric circles, cyan)
}

/**
 * Time range for graph display
 */
data class TimeRange(
    val fromTime: Long,
    val toTime: Long,
    val endTime: Long // includes predictions
)

/**
 * Individual BG data point
 */
data class BgDataPoint(
    val timestamp: Long,
    val value: Double,             // Already converted to user's units (mg/dL or mmol/L)
    val range: BgRange,            // Range classification (high/in-range/low)
    val type: BgType,              // Type determines rendering style and color
    val filledGap: Boolean = false, // For bucketed data - if true, render semi-transparent
)

// ============================================================================
// BG Info Display Data (Overview info section)
// ============================================================================

/**
 * Current BG info for the overview info section display.
 * Contains the latest BG value and related status for UI display.
 * Color mapping (BgRange -> theme color) happens in UI layer.
 */
data class BgInfoData(
    val bgValue: Double,           // Value in user's units (mg/dL or mmol/L)
    val bgText: String,            // Formatted BG string (e.g., "120" or "6.7")
    val bgRange: BgRange,          // HIGH/IN_RANGE/LOW - for color mapping in UI
    val isOutdated: Boolean,       // True if timestamp > 9 min ago (for strikethrough)
    val timestamp: Long,           // BG timestamp (for timeAgo calculation)
    val trendArrow: TrendArrow?,   // Trend direction arrow
    val trendDescription: String,  // Accessibility description of trend
    val delta: Double?,            // Delta in user units (signed)
    val deltaText: String?,        // Formatted delta string (e.g., "+5" or "-0.3")
    val shortAvgDelta: Double?,    // Short average delta in user units
    val shortAvgDeltaText: String?,// Formatted short avg delta
    val longAvgDelta: Double?,     // Long average delta in user units
    val longAvgDeltaText: String?, // Formatted long avg delta
)

/**
 * Generic data point for line graphs (IOB, COB, Activity, BGI, Ratio, etc.)
 */
data class GraphDataPoint(
    val timestamp: Long,
    val value: Double
)

/**
 * Deviation type for color classification in deviation bars
 */
enum class DeviationType {
    POSITIVE,    // Green - above expected (pastSensitivity = "+")
    NEGATIVE,    // Red - below expected (pastSensitivity = "-")
    EQUAL,       // Black/gray - as expected (pastSensitivity = "=")
    UAM,         // UAM color (type = "uam")
    CSF          // Gray - carb absorption (type = "csf" or pastSensitivity = "C")
}

/**
 * Deviation bar data point with color classification
 */
data class DeviationDataPoint(
    val timestamp: Long,
    val value: Double,
    val deviationType: DeviationType
)

/**
 * COB failover marker point (when min absorption rate is used)
 */
data class CobFailOverPoint(
    val timestamp: Long,
    val cobValue: Double
)

// ============================================================================
// Graph-level data classes (one per secondary graph)
// ============================================================================

/**
 * IOB graph data: regular IOB line + prediction points
 */
data class IobGraphData(
    val iob: List<GraphDataPoint>,
    val predictions: List<GraphDataPoint>
)

/**
 * Absolute IOB graph data: absolute IOB line only
 */
data class AbsIobGraphData(
    val absIob: List<GraphDataPoint>
)

/**
 * COB graph data: COB line + failover marker points
 */
data class CobGraphData(
    val cob: List<GraphDataPoint>,
    val failOverPoints: List<CobFailOverPoint>
)

/**
 * Activity graph data: historical activity + prediction line
 */
data class ActivityGraphData(
    val activity: List<GraphDataPoint>,
    val activityPrediction: List<GraphDataPoint>
)

/**
 * BGI (Blood Glucose Impact) graph data: historical + prediction line
 */
data class BgiGraphData(
    val bgi: List<GraphDataPoint>,
    val bgiPrediction: List<GraphDataPoint>
)

/**
 * Deviations graph data: deviation bars with color types
 */
data class DeviationsGraphData(
    val deviations: List<DeviationDataPoint>
)

/**
 * Autosens ratio graph data: ratio percentage line
 */
data class RatioGraphData(
    val ratio: List<GraphDataPoint>
)

/**
 * Deviation slope graph data: max and min slope lines
 */
data class DevSlopeGraphData(
    val dsMax: List<GraphDataPoint>,
    val dsMin: List<GraphDataPoint>
)

/**
 * Variable sensitivity graph data: sensitivity line from APS results
 */
data class VarSensGraphData(
    val varSens: List<GraphDataPoint>
)