package tv.withaibuild.customiuizer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import tv.withaibuild.customiuizer.R

@Composable
fun SettingsSection(title: String) {
    Text(title, Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
        .semantics { heading() }, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SettingsActionRow(title: String, summary: String = "", value: String = "", enabled: Boolean = true,
    tag: String = title, onClick: (() -> Unit)? = null,
) {
    val modifier = Modifier.fillMaxWidth().testTag(tag)
    val shape = RoundedCornerShape(dimensionResource(R.dimen.preference_group_radius))
    val content: @Composable () -> Unit = {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp,
            vertical = dimensionResource(R.dimen.preference_item_padding_top)).alpha(if (enabled) 1f else .45f),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (summary.isNotEmpty() && value.isNotEmpty()) Text(value, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (summary.isEmpty() && value.isNotEmpty()) Text(value, Modifier.weight(.55f),
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onClick != null) SettingsChevron()
        }
    }
    if (onClick == null) Surface(modifier, shape = shape, content = content)
    else Surface(onClick = onClick, modifier = modifier, enabled = enabled, shape = shape, content = content)
}

@Composable
internal fun SettingsChevron() {
    Icon(painterResource(R.drawable.ic_settings_chevron), null, Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
