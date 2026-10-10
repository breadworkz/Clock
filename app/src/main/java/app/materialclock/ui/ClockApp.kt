package app.materialclock.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.materialclock.alarm.Notifications
import app.materialclock.core.Alarm
import app.materialclock.core.WorldCity
import app.materialclock.data.ClockSettings
import app.materialclock.ui.screens.AlarmsScreen
import app.materialclock.ui.screens.StopwatchScreen
import app.materialclock.ui.screens.TimersScreen
import app.materialclock.ui.screens.WorldClockScreen
import app.materialclock.ui.sheets.AddCitySheet
import app.materialclock.ui.sheets.AlarmEditSheet
import app.materialclock.ui.sheets.AlarmSettingsSheet
import app.materialclock.ui.sheets.AppSettingsSheet
import app.materialclock.ui.sheets.StopwatchSettingsSheet
import app.materialclock.ui.sheets.TimerSettingsSheet
import app.materialclock.ui.sheets.WorldSettingsSheet
import app.materialclock.ui.theme.ClockTheme

private val DOCK_CLEARANCE = 20.dp
private val DOCK_RESERVE = DOCK_HEIGHT + DOCK_CLEARANCE + 12.dp

enum class Tab(val label: String, val icon: ImageVector, val key: String) {
    ALARMS("Alarms", Icons.Outlined.Alarm, Notifications.TAB_ALARMS),
    WORLD("World Clock", Icons.Outlined.Public, "world"),
    TIMERS("Timers", Icons.Outlined.HourglassEmpty, Notifications.TAB_TIMERS),
    STOPWATCH("Stopwatch", Icons.Outlined.Timer, Notifications.TAB_STOPWATCH),
}

@Composable
fun ClockApp(startTab: String? = null, vm: ClockViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    ClockTheme(settings.theme) {
        var tab by rememberSaveable { mutableStateOf(startTab?.let { k -> Tab.entries.firstOrNull { it.key == k } } ?: Tab.ALARMS) }
        var editing by remember { mutableStateOf<Alarm?>(null) }
        var showSettings by remember { mutableStateOf(false) }
        var showMainSettings by remember { mutableStateOf(false) }
        var addingCity by remember { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            LaunchedEffect(Unit) { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        val interaction = remember { MutableInteractionSource() }
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClickLabel = "Open ${tab.label.lowercase()} settings",
                            ) { showSettings = true },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
        ) { padding ->
            val ld = LocalLayoutDirection.current

            if (isTablet) {
                // TABLET / LARGE DISPLAY: Vertical Dock on the Left
                val body = PaddingValues(
                    start = padding.calculateStartPadding(ld) + 16.dp,
                    end = padding.calculateEndPadding(ld) + 16.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                )
                val edgeToEdge = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClockDock(
                        destinations = Tab.entries,
                        selected = tab,
                        onSelect = { tab = it },
                        showAdd = tab == Tab.ALARMS || tab == Tab.WORLD,
                        addLabel = if (tab == Tab.WORLD) "Add city" else "Add alarm",
                        onAdd = { if (tab == Tab.WORLD) addingCity = true else editing = vm.blankAlarm() },
                        onOpenSettings = { showMainSettings = true },
                        isVertical = true,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        TabContent(tab, vm, settings, body, edgeToEdge, onEdit = { editing = it }, onRemoveCity = { city ->
                            vm.removeCity(city.zone)
                            scope.launch {
                                val r = snackbar.showSnackbar(
                                    message = "Removed ${city.city}",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short,
                                )
                                if (r == SnackbarResult.ActionPerformed) vm.addCity(city)
                            }
                        })
                    }
                }
            } else {
                // MOBILE / PHONE DISPLAY: Horizontal Dock at the Bottom
                val underDock = padding.calculateBottomPadding() + DOCK_RESERVE
                val body = PaddingValues(
                    start = padding.calculateStartPadding(ld) + 16.dp,
                    end = padding.calculateEndPadding(ld) + 16.dp,
                    top = padding.calculateTopPadding(),
                    bottom = underDock,
                )
                val edgeToEdge = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = underDock,
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    TabContent(tab, vm, settings, body, edgeToEdge, onEdit = { editing = it }, onRemoveCity = { city ->
                        vm.removeCity(city.zone)
                        scope.launch {
                            val r = snackbar.showSnackbar(
                                message = "Removed ${city.city}",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short,
                            )
                            if (r == SnackbarResult.ActionPerformed) vm.addCity(city)
                        }
                    })

                    ClockDock(
                        destinations = Tab.entries,
                        selected = tab,
                        onSelect = { tab = it },
                        showAdd = tab == Tab.ALARMS || tab == Tab.WORLD,
                        addLabel = if (tab == Tab.WORLD) "Add city" else "Add alarm",
                        onAdd = { if (tab == Tab.WORLD) addingCity = true else editing = vm.blankAlarm() },
                        onOpenSettings = { showMainSettings = true },
                        isVertical = false,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = DOCK_CLEARANCE),
                    )
                }
            }
        }

        editing?.let { draft ->
            AlarmEditSheet(
                initial = draft,
                weekStart = settings.alarms.weekStart,
                onDismiss = { editing = null },
                onSave = { vm.saveAlarm(it) },
                onDelete = if (draft.id == 0L) null else ({ id -> vm.deleteAlarm(id) }),
            )
        }

        if (addingCity) {
            val cities by vm.cities.collectAsStateWithLifecycle()
            val now by rememberWallTicker(60_000L)
            AddCitySheet(
                existing = cities,
                nowUtcMillis = now,
                onAdd = vm::addCity,
                onDismiss = { addingCity = false },
            )
        }

        if (showMainSettings) {
            AppSettingsSheet(settings, vm::updateSettings) { showMainSettings = false }
        }

        if (showSettings) {
            when (tab) {
                Tab.ALARMS -> AlarmSettingsSheet(settings, vm::updateSettings) { showSettings = false }
                Tab.WORLD -> WorldSettingsSheet(settings, vm::updateSettings) { showSettings = false }
                Tab.TIMERS -> TimerSettingsSheet(settings, vm::updateSettings) { showSettings = false }
                Tab.STOPWATCH -> StopwatchSettingsSheet(settings, vm::updateSettings) { showSettings = false }
            }
        }
    }
}

@Composable
private fun TabContent(
    tab: Tab,
    vm: ClockViewModel,
    settings: ClockSettings,
    body: PaddingValues,
    edgeToEdge: PaddingValues,
    onEdit: (Alarm) -> Unit,
    onRemoveCity: (WorldCity) -> Unit,
) {
    AnimatedContent(
        targetState = tab,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "tab",
    ) { current ->
        when (current) {
            Tab.ALARMS -> {
                val alarms by vm.alarms.collectAsStateWithLifecycle()
                AlarmsScreen(
                    alarms = alarms,
                    weekStart = settings.alarms.weekStart,
                    onToggle = vm::toggleAlarm,
                    onEdit = onEdit,
                    contentPadding = body,
                )
            }

            Tab.WORLD -> {
                val cities by vm.cities.collectAsStateWithLifecycle()
                val now by rememberWallTicker()
                WorldClockScreen(
                    cities = cities,
                    home = vm.homeZone,
                    nowUtcMillis = now,
                    settings = settings.world,
                    onRemove = onRemoveCity,
                    contentPadding = edgeToEdge,
                )
            }

            Tab.TIMERS -> {
                val timer by vm.timer.collectAsStateWithLifecycle()
                val now by rememberElapsedTicker(active = timer != null)
                TimersScreen(
                    timer = timer,
                    draft = vm.draftDuration,
                    nowElapsedMillis = now,
                    onDigit = vm::pressDigit,
                    onBackspace = vm::backspace,
                    onWind = vm::windToMinutes,
                    onStart = { vm.startTimer() },
                    onPauseResume = { vm.pauseOrResumeTimer() },
                    onAddTen = { vm.addTenSeconds() },
                    onCancel = { vm.cancelTimer() },
                    contentPadding = edgeToEdge,
                )
            }

            Tab.STOPWATCH -> {
                val sw by vm.stopwatch.collectAsStateWithLifecycle()
                val now by rememberElapsedTicker(active = sw.running)
                StopwatchScreen(
                    stopwatch = sw,
                    nowElapsedMillis = now,
                    onToggle = { vm.toggleStopwatch() },
                    onLap = { vm.lap() },
                    onReset = { vm.resetStopwatch() },
                    contentPadding = edgeToEdge,
                )
            }
        }
    }
}
