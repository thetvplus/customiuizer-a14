package tv.withaibuild.customiuizer.ui

import android.annotation.SuppressLint
import android.util.LongSparseArray
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.preference.*
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import tv.withaibuild.customiuizer.PreferenceGroupChrome
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.preferenceGroupInputRow
import tv.withaibuild.customiuizer.prefs.PreferenceEx
import tv.withaibuild.customiuizer.prefs.SeekBarPreference
import tv.withaibuild.customiuizer.utils.AppHelper

/** Observe the existing persistence / dependency model, without constructing RecyclerView rows. */
@SuppressLint("RestrictedApi")
internal class SettingsPreferenceList(screen: PreferenceScreen) : AutoCloseable {
    class Row(val preference: Preference, val id: Long, input: PreferenceGroupChrome.InputRow) {
        var input = input
            private set
        val revision = mutableIntStateOf(0)
        fun refreshInput(): Boolean {
            val next = preferenceGroupInputRow(preference, input)
            if (next === input) return false
            input = next
            return true
        }
    }
    private val adapter = PreferenceGroupAdapter(screen)
    var rows by mutableStateOf<List<Row>>(emptyList())
        private set
    var chrome by mutableStateOf<List<PreferenceGroupChrome.Chrome>>(emptyList())
        private set
    private var chromeInputs = emptyList<PreferenceGroupChrome.InputRow>()
    private var closed = false
    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = rebuild()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            if (closed) return
            val end = (positionStart + itemCount).coerceAtMost(rows.size)
            var layoutChanged = false
            for (index in positionStart until end) {
                val row = rows[index]
                if (row.refreshInput()) layoutChanged = true
                row.revision.intValue++
            }
            if (layoutChanged) updateChrome()
        }
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) =
            onItemRangeChanged(positionStart, itemCount)
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = rebuild()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = rebuild()
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = rebuild()
    }
    init { adapter.registerAdapterDataObserver(observer); rebuild() }
    private fun rebuild() {
        if (closed) return
        // Reuse the visible owners across native hierarchy changes. The temporary primitive
        // index avoids boxed keys and keeps removed/hidden preferences out of a permanent cache.
        val previous = if (rows.isEmpty()) null else LongSparseArray<Row>(rows.size).apply {
            rows.forEach { put(it.id, it) }
        }
        rows = List(adapter.itemCount) { index ->
            val preference = requireNotNull(adapter.getItem(index))
            val id = adapter.getItemId(index)
            previous?.get(id)?.takeIf { it.preference === preference }?.also {
                if (it.refreshInput()) it.revision.intValue++
            } ?: Row(preference, id, preferenceGroupInputRow(preference))
        }
        updateChrome()
    }
    private fun updateChrome() {
        if (rows.size == chromeInputs.size && rows.indices.all { rows[it].input == chromeInputs[it] }) return
        val inputs = rows.map { it.input }
        chromeInputs = inputs
        chrome = PreferenceGroupChrome.layout(inputs, chrome)
    }
    override fun close() {
        if (closed) return
        closed = true
        adapter.unregisterAdapterDataObserver(observer)
    }
}

@Composable
internal fun SettingsPreferencePage(model: SettingsPreferenceList, scrollTarget: String?,
    consumeScroll: () -> Unit, onDialog: (Preference) -> Unit,
) {
    val state = rememberLazyListState()
    val rows = model.rows
    LaunchedEffect(scrollTarget, rows) {
        if (scrollTarget != null) {
            val index = rows.indexOfFirst { it.preference.key == scrollTarget }
            if (index >= 0) { state.scrollToItem(index); consumeScroll() }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("settings.list"), state = state,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
            items(rows.size, key = { rows[it].id }) { index ->
                val row = rows[index]
                val chrome = model.chrome[index]
                PreferenceRow(row.preference, row.revision.intValue, chrome, onDialog)
            }
        }
    }
}

@Composable
private fun PreferenceRow(preference: Preference, revision: Int, chrome: PreferenceGroupChrome.Chrome,
    onDialog: (Preference) -> Unit,
) {
    if (chrome.kind == PreferenceGroupChrome.Kind.HIDDEN_HEADER) return
    if (chrome.kind == PreferenceGroupChrome.Kind.SECTION_HEADER) {
        SettingsSection(preference.title?.toString().orEmpty())
        return
    }
    val radius = dimensionResource(R.dimen.preference_group_radius)
    val top = if (chrome.cardStart) radius else 0.dp
    val bottom = if (chrome.cardEnd) radius else 0.dp
    val extras = preference.extras
    var highlighted by remember(preference) { mutableStateOf(false) }
    LaunchedEffect(revision, preference) {
        if (extras.getBoolean("ui.highlight")) {
            extras.remove("ui.highlight")
            highlighted = true
            try { delay(1000) } finally { highlighted = false }
        }
    }
    val background by animateColorAsState(if (highlighted) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface, label = "setting search highlight")
    Surface(Modifier.fillMaxWidth().padding(top = if (chrome.cardStart) 4.dp else 0.dp),
        color = background, shape = RoundedCornerShape(top, top, bottom, bottom)) {
        Column {
            if (chrome.kind == PreferenceGroupChrome.Kind.INNER_HEADER) {
                Text(preference.title?.toString().orEmpty(), Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { heading() }, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else PreferenceContent(preference, revision, onDialog)
            if (chrome.dividerBelow) HorizontalDivider(Modifier.padding(start = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
        }
    }
}

// Dispatch the existing model click contract exactly once from the Compose row.
@SuppressLint("RestrictedApi")
@Composable
private fun PreferenceContent(preference: Preference, revision: Int, onDialog: (Preference) -> Unit) {
    val enabled = preference.isEnabled
    val extras = preference.extras
    val summary = remember(preference, revision) { preference.summary?.toString().orEmpty() }
    val value = remember(preference, revision) {
        val specialized = (preference as? PreferenceEx)?.composeSummary()
        when {
            specialized != null -> specialized
            preference is ListPreference -> (preference.entry?.toString() ?: preference.value.orEmpty()).takeIf { it != summary }.orEmpty()
            extras.getBoolean("ui.countAsSummary") -> (AppHelper.getStringSetOfAppPrefs(preference.key, emptySet()).size +
                AppHelper.getStringSetOfAppPrefs(preference.key + "_black", emptySet()).size).toString()
            else -> ""
        }
    }
    val click = {
        if (enabled && preference.isSelectable) {
            if (preference is SeekBarPreference) onDialog(preference) else preference.performClick()
        }
    }
    val indent = (extras.getInt("ui.indentLevel") * 12 + 16).dp
    var modifier = Modifier.fillMaxWidth().testTag(preference.key ?: "setting")
    modifier = when {
        preference is TwoStatePreference -> modifier.toggleable(preference.isChecked, enabled = enabled,
            role = Role.Switch, onValueChange = { click() })
        preference.isSelectable -> modifier.clickable(enabled = enabled, onClick = click)
        else -> modifier
    }
    Column(modifier.heightIn(min = 56.dp).padding(start = indent, end = 16.dp,
        top = dimensionResource(R.dimen.preference_item_padding_top),
        bottom = dimensionResource(R.dimen.preference_item_padding_bottom))
        .alpha(if (enabled) 1f else .45f), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                val title = buildString {
                    append(preference.title ?: "")
                    if (extras.getBoolean("ui.unsupported")) append(" · ").append(stringResource(R.string.preference_unavailable))
                }
                val status = when {
                    extras.getBoolean("ui.unsupported") -> stringResource(R.string.preference_unavailable)
                    extras.getBoolean("ui.dynamic") -> stringResource(R.string.preference_applies_immediately)
                    else -> null
                }
                if (title.isNotEmpty()) Text(title, Modifier.semantics { if (status != null) stateDescription = status }, style = MaterialTheme.typography.bodyLarge,
                    color = if (extras.getBoolean("ui.warning")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (summary.isNotEmpty() && value.isNotEmpty()) Text(value, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (summary.isEmpty() && value.isNotEmpty()) Text(value, Modifier.weight(.55f),
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (extras.getBoolean("ui.new")) Text(stringResource(R.string.miuizer_new_mod),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            when (preference) {
                is TwoStatePreference -> Switch(preference.isChecked, onCheckedChange = null, enabled = enabled)
                is SeekBarPreference -> TextButton(onClick = click, enabled = enabled, modifier = Modifier.weight(.55f)) {
                    Text(preference.composeDisplay(), Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                else -> {
                    if (preference.isSelectable) SettingsChevron()
                }
            }
        }
        if (preference is SeekBarPreference) {
            DisposableEffect(preference) { onDispose { preference.cancelComposeChange() } }
            Slider(value = preference.composeProgress.toFloat(),
                onValueChange = { preference.previewComposeValue(kotlin.math.round(it).toInt()) },
                onValueChangeFinished = { preference.finishComposeChange() }, enabled = enabled && preference.composeMaximum > 0,
                valueRange = 0f..preference.composeMaximum.coerceAtLeast(1).toFloat(),
                steps = 0)
            preference.composeNote?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun SettingsPreferenceEditor(preference: Preference, dismiss: () -> Unit) {
    key(preference.key ?: preference) { SettingsPreferenceDialog(preference, dismiss) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsInputDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    text: @Composable () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest,
        modifier = Modifier.imePadding().safeDrawingPadding().padding(vertical = 12.dp),
        properties = DialogProperties(decorFitsSystemWindows = false)) {
        SettingsInputForm(title, onDismissRequest, confirmButton, text)
    }
}

@Composable
internal fun SettingsInputForm(
    title: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    text: @Composable () -> Unit,
) {
    Surface(shape = AlertDialogDefaults.shape, color = AlertDialogDefaults.containerColor,
        tonalElevation = AlertDialogDefaults.TonalElevation) {
        // In landscape the IME may leave less room than the title and buttons.
        // Scroll the complete form so focused input and actions remain reachable.
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = AlertDialogDefaults.titleContentColor)
            CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.textContentColor) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
            }
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismissRequest) { Text(stringResource(android.R.string.cancel)) }
                confirmButton()
            }
        }
    }
}

@Composable
internal fun SettingsPreferenceDialog(preference: Preference, dismiss: () -> Unit) {
    val title = ((preference as? DialogPreference)?.dialogTitle?.takeIf { it.isNotBlank() }
        ?: preference.title)?.toString().orEmpty()
    when (preference) {
        is ListPreference -> AlertDialog(onDismissRequest = dismiss, title = { Text(title) },
            textContentColor = MaterialTheme.colorScheme.onSurface,
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    val entries = preference.entries.orEmpty()
                    items(entries.size) { index ->
                        val value = preference.entryValues?.getOrNull(index)?.toString()
                        Row(Modifier.fillMaxWidth().selectable(preference.value == value, enabled = value != null,
                            role = Role.RadioButton, onClick = {
                                if (value != null && preference.callChangeListener(value)) preference.value = value
                                dismiss()
                            }).heightIn(min = 48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(preference.value == value, onClick = null)
                            Text(entries[index].toString(), Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(android.R.string.cancel)) } })
        is EditTextPreference -> {
            var text by rememberSaveable(preference.key) { mutableStateOf(preference.text.orEmpty()) }
            SettingsInputDialog(title = title, onDismissRequest = dismiss, text = {
                Column {
                    preference.dialogMessage?.let { Text(it.toString()) }
                    OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth())
                }
            }, confirmButton = { TextButton(onClick = {
                if (preference.callChangeListener(text)) preference.text = text
                dismiss()
            }) { Text(stringResource(android.R.string.ok)) } })
        }
        is MultiSelectListPreference -> {
            var selected by rememberSaveable(preference.key) { mutableStateOf<List<String>>(preference.values.toList()) }
            AlertDialog(onDismissRequest = dismiss, title = { Text(title) },
                textContentColor = MaterialTheme.colorScheme.onSurface, text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    val entries = preference.entries.orEmpty()
                    items(entries.size) { index ->
                        val value = preference.entryValues?.getOrNull(index)?.toString()
                        Row(Modifier.fillMaxWidth().toggleable(value in selected, enabled = value != null,
                            role = Role.Checkbox, onValueChange = { checked -> if (value != null) {
                                selected = if (checked) selected + value else selected - value
                            } }).heightIn(min = 48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(value in selected, null)
                            Text(entries[index].toString(), Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = {
                val values = selected.toSet()
                if (preference.callChangeListener(values)) preference.values = values
                dismiss()
            }) { Text(stringResource(android.R.string.ok)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(android.R.string.cancel)) } })
        }
        is SeekBarPreference -> {
            var text by rememberSaveable(preference.key, stateSaver = TextFieldValue.Saver) {
                val value = preference.composeInput
                mutableStateOf(TextFieldValue(value, TextRange(0, value.length)))
            }
            var error by remember { mutableStateOf(false) }
            val focus = remember { FocusRequester() }
            val submit = { if (preference.submitComposeInput(text.text)) dismiss() else error = true }
            SettingsInputDialog(title = title, onDismissRequest = dismiss, text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(preference.composeGuidance, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(text, { if (it.text.length <= 64) { text = it; error = false } else error = true },
                        modifier = Modifier.fillMaxWidth().testTag("setting.input").focusRequester(focus),
                        isError = error, singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (preference.composeSignedInput) KeyboardType.Text else KeyboardType.Decimal,
                            imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        supportingText = { if (error) Text(stringResource(R.string.seekbar_value_error)) })
                    LaunchedEffect(preference) {
                        // Request input from the dialog's own composition after its first layout.
                        withFrameNanos { }
                        focus.requestFocus()
                    }
                    preference.composeDefaultInput?.let { initial ->
                        TextButton(onClick = { if (preference.submitComposeInput(initial)) dismiss() }) {
                            Text(preference.composeDefaultLabel)
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = submit) { Text(stringResource(android.R.string.ok)) } })
        }
    }
}
