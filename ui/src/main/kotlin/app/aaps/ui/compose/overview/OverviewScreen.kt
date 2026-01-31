package app.aaps.ui.compose.overview

import androidx.collection.arrayMapOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aaps.ui.compose.graphs.viewmodels.GraphViewModel
import app.aaps.ui.compose.main.TempTargetChipState

@Composable
fun OverviewScreen(
    profileName: String,
    isProfileModified: Boolean,
    profileProgress: Float,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    graphViewModel: GraphViewModel,
    onProfileManagementClick: () -> Unit,
    onTempTargetClick: () -> Unit,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier
) {
    // Collect BG info state from ViewModel
    val bgInfoState by graphViewModel.bgInfoState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
    ) {
        // BG Info section at the top
        BgInfoSection(
            bgInfo = bgInfoState.bgInfo,
            timeAgoText = bgInfoState.timeAgoText
        )

        // Chips column
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Profile chip
            if (profileName.isNotEmpty()) {
                ProfileChip(
                    profileName = profileName,
                    isModified = isProfileModified,
                    progress = profileProgress,
                    onClick = onProfileManagementClick
                )
            }
            // TempTarget chip (show when text is available)
            if (tempTargetText.isNotEmpty()) {
                TempTargetChip(
                    targetText = tempTargetText,
                    state = tempTargetState,
                    progress = tempTargetProgress,
                    onClick = onTempTargetClick
                )
            }
        }

        // Graph content - New Compose/Vico graphs
        OverviewGraphsSection(graphViewModel = graphViewModel)
    }
}
