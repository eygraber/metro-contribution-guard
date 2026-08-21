@file:Suppress("unused", "FunctionName", "FunctionNaming", "UNUSED_PARAMETER")

package metro.hints

import com.eygraber.metro.guard.fixtures.ContainerContribution
import com.eygraber.metro.guard.fixtures.MapContribution
import com.eygraber.metro.guard.fixtures.RealRepository
import com.eygraber.metro.guard.fixtures.SetContribution
import com.eygraber.metro.guard.fixtures.SettingsDependencies

fun com_eygraber_metro_guard_fixtures_AppScope(contributed: SettingsDependencies) {}

fun com_eygraber_metro_guard_fixtures_AppScope(contributed: RealRepository) {}

fun com_eygraber_metro_guard_fixtures_UserScope(contributed: RealRepository) {}

fun com_eygraber_metro_guard_fixtures_UserScope(contributed: SetContribution) {}

fun com_eygraber_metro_guard_fixtures_AppScope(contributed: MapContribution) {}

fun com_eygraber_metro_guard_fixtures_AppScope(contributed: ContainerContribution) {}
