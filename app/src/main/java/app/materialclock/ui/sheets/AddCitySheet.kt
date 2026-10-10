package app.materialclock.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.materialclock.core.WorldCity
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pick a city.
 *
 * The list is the platform's own `ZoneId.getAvailableZoneIds()` (some six hundred entries) rather
 * than a bundled table of cities, so it is exactly as current as the device's tzdb and needs no
 * maintenance. The tradeoff is that a zone id is not a city name, so [prettify] does the work:
 * `America/Argentina/Buenos_Aires` becomes "Buenos Aires · Argentina".
 *
 * Zones are filtered to those with a region prefix, which drops the legacy aliases (`EST`, `GMT+3`,
 * `US/Pacific`) that would otherwise triple the list with duplicates of entries already in it.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddCitySheet(
    existing: List<WorldCity>,
    nowUtcMillis: Long,
    onAdd: (WorldCity) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val taken = remember(existing) { existing.map { it.zone.id }.toSet() }

    val all = remember {
        ZoneId.getAvailableZoneIds()
            .filter { "/" in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
            .map { id -> prettify(id) }
            .sortedBy { it.city.lowercase() }
    }
    val results = remember(query, taken) {
        val q = query.trim().lowercase()
        all.filter { it.zone.id !in taken }
            .filter { q.isEmpty() || it.city.lowercase().contains(q) || it.region.lowercase().contains(q) }
            .take(200)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // Fixed rather than capped: this sheet is a search field over six hundred rows, so it
        // wants to be as tall as it is allowed to be every time, not to grow as you type.
        Column(Modifier.height(LocalConfiguration.current.screenHeightDp.dp * SHEET_MAX_FRACTION)) {
            SheetTitle("Add a city")
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search cities or countries") },
                // Fully rounded. The default text-field shape is the small extra-small corner,
                // which next to a pill-shaped sheet and pill-shaped rows looks like a leftover.
                shape = CircleShape,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                items(results, key = { it.zone.id }) { city ->
                    ListItem(
                        headlineContent = { Text(city.city) },
                        supportingContent = { Text(city.region) },
                        trailingContent = {
                            Text(
                                localTime(city.zone, nowUtcMillis),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            onAdd(city)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

private fun localTime(zone: ZoneId, nowUtcMillis: Long): String =
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowUtcMillis), zone)
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))

/**
 * `Europe/Paris` → city "Paris", region "Europe". Three-segment ids keep the middle as the region,
 * which is the useful half: `America/Argentina/Buenos_Aires` is in Argentina, not in America.
 */
internal fun prettify(id: String): WorldCity {
    val parts = id.split("/")
    val city = parts.last().replace('_', ' ')
    val region = when (parts.size) {
        3 -> parts[1].replace('_', ' ')
        else -> parts.first().replace('_', ' ')
    }
    return WorldCity(ZoneId.of(id), city, region)
}
