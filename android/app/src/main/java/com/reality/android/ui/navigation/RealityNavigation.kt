package com.reality.android.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import com.reality.android.R
import com.reality.android.ui.activities.*
import com.reality.android.ui.dashboard.DashboardScreen
import com.reality.android.ui.progress.ProgressScreen
import com.reality.android.ui.reports.ReportsScreen
import com.reality.android.ui.schedule.ScheduleScreen
import com.reality.android.ui.settings.SettingsScreen
import com.reality.android.ui.tracking.*

private data class MainDestination(val route: Any, val label: Int, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealityNavigation() {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val destination = current?.destination
    val destinations = remember {
        listOf(
            MainDestination(Home, R.string.nav_home, Icons.Default.Home),
            MainDestination(Activities, R.string.nav_activities, Icons.Default.List),
            MainDestination(Tracking(), R.string.nav_track, Icons.Default.PlayArrow),
            MainDestination(Reports(), R.string.nav_reports, Icons.Default.DateRange),
            MainDestination(More, R.string.nav_more, Icons.Default.MoreVert)
        )
    }
    fun go(route: Any) {
        if (nav.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
            nav.navigate(route) { launchSingleTop = true }
        }
    }
    fun top(route: Any) {
        if (nav.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
            nav.navigate(route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val isMain = destination == null || destination.hasRoute<Home>() || destination.hasRoute<Activities>() ||
        destination.hasRoute<Tracking>() || destination.hasRoute<Reports>() || destination.hasRoute<More>()
    val title = when {
        destination?.hasRoute<Activities>() == true -> R.string.nav_activities
        destination?.hasRoute<Tracking>() == true -> R.string.nav_track
        destination?.hasRoute<Reports>() == true -> R.string.nav_reports
        destination?.hasRoute<More>() == true -> R.string.nav_more
        destination?.hasRoute<Schedule>() == true -> R.string.nav_schedule
        destination?.hasRoute<Progress>() == true -> R.string.nav_progress
        destination?.hasRoute<Settings>() == true -> R.string.nav_settings
        destination?.hasRoute<CreateActivity>() == true -> R.string.nav_create
        destination?.hasRoute<EditActivity>() == true -> R.string.nav_edit
        destination?.hasRoute<ActivityDetail>() == true -> R.string.nav_activity
        destination?.hasRoute<SessionDetail>() == true -> R.string.nav_session
        else -> R.string.app_name
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= 840.dp
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(title)) },
                    navigationIcon = {
                        if (!isMain) IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back))
                        }
                    },
                    actions = {
                        if (destination?.hasRoute<Settings>() != true) IconButton(onClick = { go(Settings) }) {
                            Icon(Icons.Default.Settings, stringResource(R.string.nav_settings))
                        }
                    }
                )
            },
            bottomBar = {
                if (!rail) NavigationBar {
                    destinations.forEach { item ->
                        NavigationBarItem(
                            selected = selected(destination, item.route),
                            onClick = { top(item.route) },
                            icon = { Icon(item.icon, null) },
                            label = { Text(stringResource(item.label)) }
                        )
                    }
                }
            }
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (rail) NavigationRail {
                    destinations.forEach { item ->
                        NavigationRailItem(
                            selected = selected(destination, item.route),
                            onClick = { top(item.route) },
                            icon = { Icon(item.icon, null) },
                            label = { Text(stringResource(item.label)) }
                        )
                    }
                }
                NavHost(nav, startDestination = Home, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    composable<Home> {
                        DashboardScreen(
                            onCreate = { go(CreateActivity) }, onActivity = { go(ActivityDetail(it)) },
                            onTrack = { go(Tracking(it)) }, onEdit = { go(EditActivity(it)) },
                            onSchedule = { go(Schedule) }
                        )
                    }
                    composable<Activities> {
                        ActivitiesScreen(onCreate = { go(CreateActivity) }, onDetail = { go(ActivityDetail(it)) }, onEdit = { go(EditActivity(it)) })
                    }
                    composable<CreateActivity> {
                        ActivityFormScreen(activityId = null, onSaved = { nav.popBackStack() })
                    }
                    composable<EditActivity> { entry ->
                        ActivityFormScreen(activityId = entry.toRoute<EditActivity>().activityId, onSaved = { nav.popBackStack() })
                    }
                    composable<ActivityDetail> { entry ->
                        ActivityDetailScreen(
                            activityId = entry.toRoute<ActivityDetail>().activityId,
                            onEdit = { go(EditActivity(it)) }, onTrack = { go(Tracking(it)) },
                            onProgress = { go(Progress(it)) }, onReport = { go(Reports(it)) },
                            onDeleted = { nav.navigate(Activities) { popUpTo(Home); launchSingleTop = true } }
                        )
                    }
                    composable<Tracking> { entry ->
                        TrackingScreen(initialActivityId = entry.toRoute<Tracking>().activityId, onSession = { go(SessionDetail(it)) })
                    }
                    composable<SessionDetail> { entry ->
                        SessionDetailScreen(sessionId = entry.toRoute<SessionDetail>().sessionId, onDeleted = { nav.popBackStack() })
                    }
                    composable<Reports> { entry ->
                        ReportsScreen(initialActivityId = entry.toRoute<Reports>().activityId, onEdit = { go(EditActivity(it)) })
                    }
                    composable<Schedule> {
                        ScheduleScreen(onEdit = { go(EditActivity(it)) }, onActivity = { go(ActivityDetail(it)) }, onTrack = { go(Tracking(it)) })
                    }
                    composable<Progress> { entry ->
                        ProgressScreen(initialActivityId = entry.toRoute<Progress>().activityId, onSession = { go(SessionDetail(it)) }, onEdit = { go(EditActivity(it)) })
                    }
                    composable<Settings> { SettingsScreen() }
                    composable<More> {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.more_heading), style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(R.string.more_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MoreCard(R.string.nav_schedule, R.string.more_schedule_description) { go(Schedule) }
                            MoreCard(R.string.nav_progress, R.string.more_progress_description) { go(Progress()) }
                            MoreCard(R.string.nav_settings, R.string.more_settings_description) { go(Settings) }
                        }
                    }
                }
            }
        }
    }
}

private fun selected(destination: NavDestination?, route: Any): Boolean = when (route) {
    Home -> destination?.hasRoute<Home>() == true
    Activities -> destination?.hasRoute<Activities>() == true || destination?.hasRoute<ActivityDetail>() == true ||
        destination?.hasRoute<CreateActivity>() == true || destination?.hasRoute<EditActivity>() == true
    is Tracking -> destination?.hasRoute<Tracking>() == true || destination?.hasRoute<SessionDetail>() == true
    is Reports -> destination?.hasRoute<Reports>() == true
    More -> destination?.hasRoute<More>() == true || destination?.hasRoute<Schedule>() == true ||
        destination?.hasRoute<Progress>() == true || destination?.hasRoute<Settings>() == true
    else -> false
}

@Composable
private fun MoreCard(title: Int, description: Int, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
