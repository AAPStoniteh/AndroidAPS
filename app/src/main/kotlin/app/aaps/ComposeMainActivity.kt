package app.aaps

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.aaps.activities.HistoryBrowseActivity
import app.aaps.compose.navigation.AppRoute
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalRxBus
import app.aaps.core.ui.compose.ProtectionHost
import app.aaps.core.ui.compose.preference.PluginPreferencesScreen
import app.aaps.implementation.protection.BiometricCheck
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import app.aaps.plugins.configuration.activities.SingleFragmentActivity
import app.aaps.plugins.configuration.setupwizard.SetupWizardActivity
import app.aaps.plugins.main.skins.SkinProvider
import app.aaps.ui.compose.actions.viewmodels.ActionsViewModel
import app.aaps.ui.compose.graphs.viewmodels.GraphViewModel
import app.aaps.ui.compose.main.MainMenuItem
import app.aaps.ui.compose.main.MainNavDestination
import app.aaps.ui.compose.main.MainScreen
import app.aaps.ui.compose.main.MainViewModel
import app.aaps.ui.compose.preferences.AllPreferencesScreen
import app.aaps.ui.compose.profileHelper.ProfileHelperScreen
import app.aaps.ui.compose.profileManagement.ProfileActivationScreen
import app.aaps.ui.compose.profileManagement.ProfileEditorScreen
import app.aaps.ui.compose.profileManagement.ProfileManagementScreen
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileEditorViewModel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileHelperViewModel
import app.aaps.ui.compose.profileManagement.viewmodels.ProfileManagementViewModel
import app.aaps.ui.compose.stats.StatsScreen
import app.aaps.ui.compose.stats.viewmodels.StatsViewModel
import app.aaps.ui.compose.tempTarget.TempTargetManagementScreen
import app.aaps.ui.compose.tempTarget.viewmodels.TempTargetManagementViewModel
import app.aaps.ui.compose.treatments.TreatmentsScreen
import app.aaps.ui.compose.treatments.viewmodels.TreatmentsViewModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.launch
import javax.inject.Inject

class ComposeMainActivity : DaggerAppCompatActivityWithResult() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var passwordCheck: PasswordCheck
    @Inject lateinit var cryptoUtil: CryptoUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var config: Config
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var skinProvider: SkinProvider
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var visibilityContext: PreferenceVisibilityContext

    // ViewModels
    @Inject lateinit var mainViewModel: MainViewModel
    @Inject lateinit var actionsViewModel: ActionsViewModel
    @Inject lateinit var graphViewModel: GraphViewModel
    @Inject lateinit var treatmentsViewModel: TreatmentsViewModel
    @Inject lateinit var tempTargetManagementViewModel: TempTargetManagementViewModel
    @Inject lateinit var statsViewModel: StatsViewModel
    @Inject lateinit var profileHelperViewModel: ProfileHelperViewModel
    @Inject lateinit var profileEditorViewModel: ProfileEditorViewModel
    @Inject lateinit var profileManagementViewModel: ProfileManagementViewModel

    private val disposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupEventListeners()
        setupWakeLock()

        setContent {
            MainContent()
        }
    }

    @Composable
    private fun MainContent() {
        val navController = rememberNavController()

        CompositionLocalProvider(
            LocalPreferences provides preferences,
            LocalRxBus provides rxBus
        ) {
            AapsTheme {
                // Protection dialog host - handles all protection requests
                ProtectionHost(
                    protectionCheck = protectionCheck,
                    preferences = preferences,
                    checkPassword = cryptoUtil::checkPassword,
                    showBiometric = { activity, titleRes, onGranted, onCancelled, onDenied ->
                        BiometricCheck.biometricPrompt(activity, titleRes, onGranted, onCancelled, onDenied, passwordCheck)
                    }
                )

                val state by mainViewModel.uiState.collectAsState()

                NavHost(
                    navController = navController,
                    startDestination = AppRoute.Main.route
                ) {
                    composable(AppRoute.Main.route) {
                        MainScreen(
                            uiState = state,
                            versionName = mainViewModel.versionName,
                            appIcon = mainViewModel.appIcon,
                            aboutDialogData = if (state.showAboutDialog) {
                                mainViewModel.buildAboutDialogData(getString(R.string.app_name))
                            } else null,
                            actionsViewModel = actionsViewModel,
                            onMenuClick = { mainViewModel.openDrawer() },
                            onProfileManagementClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        navController.navigate(AppRoute.Profile.route)
                                    }
                                }
                            },
                            onPreferencesClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        navController.navigate(AppRoute.Preferences.route)
                                    }
                                }
                            },
                            onMenuItemClick = { menuItem ->
                                handleMenuItemClick(menuItem, navController)
                            },
                            onCategoryClick = { category ->
                                mainViewModel.handleCategoryClick(category) { plugin ->
                                    handlePluginClick(plugin)
                                }
                            },
                            onCategoryExpand = { category -> mainViewModel.showCategorySheet(category) },
                            onCategorySheetDismiss = { mainViewModel.dismissCategorySheet() },
                            onPluginClick = { plugin -> handlePluginClick(plugin) },
                            onPluginEnableToggle = { plugin, type, enabled ->
                                mainViewModel.togglePluginEnabled(plugin, type, enabled)
                            },
                            onPluginPreferencesClick = { plugin ->
                                protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        navController.navigate(AppRoute.PluginPreferences.createRoute(plugin.javaClass.simpleName))
                                    }
                                }
                            },
                            onDrawerClosed = { mainViewModel.closeDrawer() },
                            onNavDestinationSelected = { destination ->
                                mainViewModel.setNavDestination(destination)
                                if (destination == MainNavDestination.Manage) {
                                    actionsViewModel.refreshState()
                                }
                            },
                            onSwitchToClassicUi = { switchToClassicUi() },
                            onAboutDialogDismiss = { mainViewModel.setShowAboutDialog(false) },
                            // Actions callbacks
                            onTempTargetClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        navController.navigate(AppRoute.TempTargetManagement.route)
                                    }
                                }
                            },
                            onTempBasalClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        uiInteraction.runTempBasalDialog(supportFragmentManager)
                                    }
                                }
                            },
                            onExtendedBolusClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        uiInteraction.showOkCancelDialog(
                                            context = this@ComposeMainActivity,
                                            title = app.aaps.core.ui.R.string.extended_bolus,
                                            message = app.aaps.plugins.main.R.string.ebstopsloop,
                                            ok = { uiInteraction.runExtendedBolusDialog(supportFragmentManager) }
                                        )
                                    }
                                }
                            },
                            onFillClick = {
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        uiInteraction.runFillDialog(supportFragmentManager)
                                    }
                                }
                            },
                            onHistoryBrowserClick = {
                                startActivity(Intent(this@ComposeMainActivity, uiInteraction.historyBrowseActivity))
                            },
                            onTddStatsClick = {
                                startActivity(Intent(this@ComposeMainActivity, uiInteraction.tddStatsActivity))
                            },
                            onBgCheckClick = {
                                uiInteraction.runCareDialog(supportFragmentManager, UiInteraction.EventType.BGCHECK, app.aaps.core.ui.R.string.careportal_bgcheck)
                            },
                            onSensorInsertClick = {
                                uiInteraction.runCareDialog(supportFragmentManager, UiInteraction.EventType.SENSOR_INSERT, app.aaps.core.ui.R.string.cgm_sensor_insert)
                            },
                            onBatteryChangeClick = {
                                uiInteraction.runCareDialog(supportFragmentManager, UiInteraction.EventType.BATTERY_CHANGE, app.aaps.core.ui.R.string.pump_battery_change)
                            },
                            onNoteClick = {
                                uiInteraction.runCareDialog(supportFragmentManager, UiInteraction.EventType.NOTE, app.aaps.core.ui.R.string.careportal_note)
                            },
                            onExerciseClick = {
                                uiInteraction.runCareDialog(supportFragmentManager, UiInteraction.EventType.EXERCISE, app.aaps.core.ui.R.string.careportal_exercise)
                            },
                            onQuestionClick = {
                                uiInteraction.runCareDialog(supportFragmentManager, UiInteraction.EventType.QUESTION, app.aaps.core.ui.R.string.careportal_question)
                            },
                            onAnnouncementClick = {
                                uiInteraction.runCareDialog(supportFragmentManager, UiInteraction.EventType.ANNOUNCEMENT, app.aaps.core.ui.R.string.careportal_announcement)
                            },
                            onSiteRotationClick = {
                                uiInteraction.runSiteRotationDialog(supportFragmentManager)
                            },
                            onActionsError = { comment, title ->
                                uiInteraction.runAlarm(comment, title, app.aaps.core.ui.R.raw.boluserror)
                            },
                            graphViewModel = graphViewModel
                        )
                    }

                    composable(AppRoute.Profile.route) {
                        ProfileManagementScreen(
                            viewModel = profileManagementViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onEditProfile = { index ->
                                profileEditorViewModel.selectProfile(index)
                                navController.navigate(AppRoute.ProfileEditor.createRoute(index))
                            },
                            onActivateProfile = { index ->
                                protectionCheck.requestProtection(ProtectionCheck.Protection.BOLUS) { result ->
                                    if (result == ProtectionResult.GRANTED) {
                                        navController.navigate(AppRoute.ProfileActivation.createRoute(index))
                                    }
                                }
                            }
                        )
                    }

                    composable(AppRoute.TempTargetManagement.route) {
                        TempTargetManagementScreen(
                            viewModel = tempTargetManagementViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = AppRoute.ProfileActivation.route,
                        arguments = listOf(
                            androidx.navigation.navArgument("profileIndex") {
                                type = androidx.navigation.NavType.IntType
                            }
                        )
                    ) { backStackEntry ->
                        val profileIndex = backStackEntry.arguments?.getInt("profileIndex") ?: 0
                        val profileName = profileManagementViewModel.uiState.value.profileNames.getOrNull(profileIndex) ?: ""
                        val reuseValues = profileManagementViewModel.getReuseValues()

                        ProfileActivationScreen(
                            profileName = profileName,
                            currentPercentage = reuseValues?.first ?: 100,
                            currentTimeshiftHours = reuseValues?.second ?: 0,
                            hasReuseValues = reuseValues != null,
                            showNotesField = preferences.get(BooleanKey.OverviewShowNotesInDialogs),
                            initialTimestamp = profileManagementViewModel.dateUtil.nowWithoutMilliseconds(),
                            dateUtil = profileManagementViewModel.dateUtil,
                            rh = rh,
                            onNavigateBack = { navController.popBackStack() },
                            onActivate = { duration, percentage, timeshift, withTT, notes, timestamp, timeChanged ->
                                val success = profileManagementViewModel.activateProfile(
                                    profileIndex = profileIndex,
                                    durationMinutes = duration,
                                    percentage = percentage,
                                    timeshiftHours = timeshift,
                                    withTT = withTT,
                                    notes = notes,
                                    timestamp = timestamp,
                                    timeChanged = timeChanged
                                )
                                if (success) {
                                    navController.popBackStack(AppRoute.Profile.route, inclusive = false)
                                }
                            }
                        )
                    }

                    composable(
                        route = AppRoute.ProfileEditor.route,
                        arguments = listOf(
                            androidx.navigation.navArgument("profileIndex") {
                                type = androidx.navigation.NavType.IntType
                            }
                        )
                    ) { backStackEntry ->
                        val profileIndex = backStackEntry.arguments?.getInt("profileIndex") ?: 0
                        profileEditorViewModel.selectProfile(profileIndex)
                        ProfileEditorScreen(
                            viewModel = profileEditorViewModel,
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoute.Treatments.route) {
                        TreatmentsScreen(
                            viewModel = treatmentsViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoute.Stats.route) {
                        StatsScreen(
                            viewModel = statsViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoute.ProfileHelper.route) {
                        ProfileHelperScreen(
                            viewModel = profileHelperViewModel,
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoute.Preferences.route) {
                        AllPreferencesScreen(
                            activePlugin = activePlugin,
                            preferences = preferences,
                            config = config,
                            rh = rh,
                            checkPassword = cryptoUtil::checkPassword,
                            hashPassword = cryptoUtil::hashPassword,
                            visibilityContext = visibilityContext,
                            profileUtil = profileUtil,
                            skinEntries = skinProvider.list.associate { skin -> skin.javaClass.name to rh.gs(skin.description) },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoute.PluginPreferences.route) { backStackEntry ->
                        val pluginKey = backStackEntry.arguments?.getString("pluginKey")
                        val plugin = activePlugin.getPluginsList().find {
                            it.javaClass.simpleName == pluginKey
                        }
                        if (plugin != null) {
                            PluginPreferencesScreen(
                                plugin = plugin,
                                config = config,
                                profileUtil = profileUtil,
                                visibilityContext = visibilityContext,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Profile and TempTarget state are now updated reactively via OverviewDataCache flows
        actionsViewModel.refreshState()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    private fun setupEventListeners() {
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ event ->
                           // Handle screen wake lock
                           if (event.isChanged(BooleanKey.OverviewKeepScreenOn.key)) {
                               setupWakeLock()
                           }
                           // Language change requires full restart to reload resources
                           if (event.isChanged(StringKey.GeneralLanguage.key)) {
                               finish()
                           }
                       }, fabricPrivacy::logException)
    }

    private fun setupWakeLock() {
        val keepScreenOn = preferences.get(BooleanKey.OverviewKeepScreenOn)
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun switchToClassicUi() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun handleMenuItemClick(menuItem: MainMenuItem, navController: NavController) {
        when (menuItem) {
            is MainMenuItem.Preferences,
            is MainMenuItem.PluginPreferences -> {
                protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                    if (result == ProtectionResult.GRANTED) {
                        navController.navigate(AppRoute.Preferences.route)
                    }
                }
            }

            is MainMenuItem.Treatments        -> navController.navigate(AppRoute.Treatments.route)

            is MainMenuItem.HistoryBrowser    -> {
                startActivity(Intent(this, HistoryBrowseActivity::class.java).setAction("app.aaps.ComposeMainActivity"))
            }

            is MainMenuItem.SetupWizard       -> {
                protectionCheck.requestProtection(ProtectionCheck.Protection.PREFERENCES) { result ->
                    if (result == ProtectionResult.GRANTED) {
                        startActivity(Intent(this, SetupWizardActivity::class.java).setAction("app.aaps.ComposeMainActivity"))
                    }
                }
            }

            is MainMenuItem.Stats             -> navController.navigate(AppRoute.Stats.route)
            is MainMenuItem.ProfileHelper     -> navController.navigate(AppRoute.ProfileHelper.route)
            is MainMenuItem.About             -> mainViewModel.setShowAboutDialog(true)

            is MainMenuItem.Exit              -> {
                finish()
                configBuilder.exitApp("Menu", Sources.Aaps, false)
            }
        }
    }

    private fun handlePluginClick(plugin: PluginBase) {
        if (!plugin.hasFragment() && !plugin.hasComposeContent()) {
            return
        }
        lifecycleScope.launch {
            val pluginIndex = activePlugin.getPluginsList().indexOf(plugin)
            startActivity(
                Intent(this@ComposeMainActivity, SingleFragmentActivity::class.java)
                    .setAction(this@ComposeMainActivity::class.simpleName)
                    .putExtra("plugin", pluginIndex)
            )
        }
    }

}
