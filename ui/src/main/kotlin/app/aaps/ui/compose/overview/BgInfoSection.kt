package app.aaps.ui.compose.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.overview.graph.BgInfoData
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.ui.compose.AapsTheme

/**
 * Displays current BG information in a circular design.
 * Shows BG value centered in a ring, with trend indicated by an arc position.
 *
 * @param bgInfo Current BG info data, or null if no data available
 * @param timeAgoText Formatted "time ago" string (e.g., "2 min")
 * @param modifier Optional modifier for the composable
 * @param size Size of the circular BG display
 */
@Composable
fun BgInfoSection(
    bgInfo: BgInfoData?,
    timeAgoText: String,
    modifier: Modifier = Modifier,
    size: Dp = 110.dp
) {
    if (bgInfo == null) {
        // Show placeholder when no data
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.size(size)
        ) {
            Text(
                text = "---",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val bgColor = bgInfo.bgRange.toColor()
    val ringColor = bgColor.copy(alpha = 0.3f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(8.dp)
    ) {
        // Background ring + trend arc indicator
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 8.dp.toPx()
            val arcSize = Size(size.toPx() - strokeWidth, size.toPx() - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Background ring (full circle, semi-transparent)
            drawArc(
                color = ringColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Trend arc indicator (bright segment showing trend direction)
            bgInfo.trendArrow?.let { trend ->
                trend.toArcIndicator()?.let { indicator ->
                    val segments = if (indicator.markCount > 0) indicator.markCount else 1
                    val totalSweep = indicator.sweepAngle
                    val gapAngle = if (segments > 1) 10f else 0f
                    val segmentSweep = (totalSweep - (segments - 1) * gapAngle) / segments
                    val arcStart = indicator.centerAngle - totalSweep / 2

                    for (i in 0 until segments) {
                        val segStart = arcStart + i * (segmentSweep + gapAngle)
                        drawArc(
                            color = bgColor,
                            startAngle = segStart,
                            sweepAngle = segmentSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }

        // Center content: delta on top, BG value, time ago below
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Delta on top
            bgInfo.deltaText?.let { delta ->
                Text(
                    text = delta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // BG value - large bold text with strikethrough if outdated
            Text(
                text = bgInfo.bgText,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = bgColor,
                textDecoration = if (bgInfo.isOutdated) TextDecoration.LineThrough else TextDecoration.None
            )

            // Time ago below
            Text(
                text = timeAgoText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Maps BgRange to the appropriate theme color.
 */
@Composable
private fun BgRange.toColor(): Color = when (this) {
    BgRange.HIGH     -> AapsTheme.generalColors.bgHigh
    BgRange.IN_RANGE -> AapsTheme.generalColors.bgInRange
    BgRange.LOW      -> AapsTheme.generalColors.bgLow
}

/**
 * Arc indicator describing position and intensity marks.
 * @param centerAngle center of the arc in degrees (0° = right/3 o'clock, -90° = top, 90° = bottom)
 * @param sweepAngle arc length in degrees
 * @param markCount number of perpendicular tick marks (0 = none, 2 = double, 3 = triple)
 */
private data class ArcIndicator(
    val centerAngle: Float,
    val sweepAngle: Float,
    val markCount: Int
)

/**
 * Maps TrendArrow to arc indicator with 5 fixed positions and intensity marks.
 * Returns null for NONE (no arc drawn).
 *
 * Positions: Up (-90°), 45°-up (-45°), Flat (0°), 45°-down (45°), Down (90°)
 * Double/Triple use same position as single but add 2 or 3 tick marks.
 */
private fun TrendArrow.toArcIndicator(): ArcIndicator? {
    val sweepAngle = 40f
    return when (this) {
        TrendArrow.NONE            -> null
        TrendArrow.FLAT            -> ArcIndicator(centerAngle = 0f, sweepAngle = sweepAngle, markCount = 0)
        TrendArrow.FORTY_FIVE_UP   -> ArcIndicator(centerAngle = -45f, sweepAngle = sweepAngle, markCount = 0)
        TrendArrow.FORTY_FIVE_DOWN -> ArcIndicator(centerAngle = 45f, sweepAngle = sweepAngle, markCount = 0)
        TrendArrow.SINGLE_UP       -> ArcIndicator(centerAngle = -90f, sweepAngle = sweepAngle, markCount = 0)
        TrendArrow.SINGLE_DOWN     -> ArcIndicator(centerAngle = 90f, sweepAngle = sweepAngle, markCount = 0)
        TrendArrow.DOUBLE_UP       -> ArcIndicator(centerAngle = -90f, sweepAngle = sweepAngle, markCount = 2)
        TrendArrow.DOUBLE_DOWN     -> ArcIndicator(centerAngle = 90f, sweepAngle = sweepAngle, markCount = 2)
        TrendArrow.TRIPLE_UP       -> ArcIndicator(centerAngle = -90f, sweepAngle = sweepAngle, markCount = 3)
        TrendArrow.TRIPLE_DOWN     -> ArcIndicator(centerAngle = 90f, sweepAngle = sweepAngle, markCount = 3)
    }
}
