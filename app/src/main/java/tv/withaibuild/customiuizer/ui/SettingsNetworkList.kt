package tv.withaibuild.customiuizer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.withaibuild.customiuizer.R

data class NetworkSelectionRow(val id: String, val name: String, val selected: Boolean = false, val current: Boolean = false)

@Composable
fun SettingsNetworkList(actionTitle: String, actionSummary: String, action: () -> Unit,
    selectedTitle: String, availableTitle: String, rows: List<NetworkSelectionRow>, loading: Boolean,
    toggle: (NetworkSelectionRow) -> Unit,
    refresh: (() -> Unit)? = null,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { SettingsActionRow(actionTitle, actionSummary, onClick = action) }
        item { SettingsSection(selectedTitle) }
        items(rows.filter { it.selected }, key = { "selected:" + it.id }) { row ->
            SettingsActionRow(row.name, row.id, value = "−", tag = "network.selected:" + row.id, onClick = { toggle(row) })
        }
        item { SettingsSection(availableTitle) }
        if (refresh != null) item {
            TextButton(onClick = refresh, enabled = !loading, modifier = Modifier.testTag("network.refresh")) {
                Text(stringResource(R.string.wifi_refresh))
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag("network.loading")) }
        items(rows.filter { !it.selected }, key = { "available:" + it.id }) { row ->
            SettingsActionRow(row.name, row.id, value = if (row.current) "●" else "+", tag = "network.available:" + row.id,
                onClick = { toggle(row) })
        }
    }
}
