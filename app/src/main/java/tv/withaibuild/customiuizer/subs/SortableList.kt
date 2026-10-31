package tv.withaibuild.customiuizer.subs

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.isActive
import androidx.compose.ui.zIndex
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.ui.settingsView
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.Helpers
import java.util.UUID

class SortableList : SubFragment() {
    private data class Item(val id: String, val title: String, val summary: String)
    private var key = ""
    private var activities = false
    private var items by mutableStateOf<List<Item>>(emptyList())
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        key = arguments?.getString("key").orEmpty()
        activities = arguments?.getBoolean("activities", false) == true
    }

    override fun onResume() { super.onResume(); refreshItems() }
    override fun onDestroyView() { loadJob?.cancel(); loadJob = null; super.onDestroyView() }
    override fun loadSharedPrefs() = Unit

    private fun refreshItems() {
        loadJob?.cancel()
        val context = context ?: return
        val ids = AppHelper.getStringOfAppPrefs(key, "").orEmpty().trim().split("|").filter { it.isNotEmpty() }
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            items = withContext(Dispatchers.IO) {
                ids.map { id ->
                    val name = AppHelper.getActionNameLocal(context, "${key}_$id")
                    val label = if (activities) Helpers.getAppName(context,
                        AppHelper.getStringOfAppPrefs("${key}_${id}_activity", "").orEmpty(), true) else name?.first
                    Item(id, label?.toString() ?: context.getString(R.string.notselected), name?.second.orEmpty())
                }
            }
        }
    }

    private fun persistOrder() {
        AppHelper.appPrefs?.edit()?.putString(key, items.joinToString("|") { it.id })?.apply()
    }

    private fun move(from: Int, to: Int, persist: Boolean = true) {
        if (from == to || from !in items.indices || to !in items.indices) return
        items = items.toMutableList().apply { add(to, removeAt(from)) }
        if (persist) persistOrder()
    }

    private fun delete(id: String) { items = items.filter { it.id != id }; persistOrder() }
    private fun newId() = UUID.randomUUID().toString().replace("-", "")
    private fun append(id: String) {
        val ids = AppHelper.getStringOfAppPrefs(key, "").orEmpty()
        AppHelper.appPrefs?.edit()?.putString(key, if (ids.isEmpty()) id else "$ids|$id")?.apply()
        refreshItems()
    }

    private fun addItem() {
        if (activities) {
            val args = Bundle().apply { putBoolean("activity", true); putString("key", key) }
            val selector = AppSelector().apply { setTargetFragment(this@SortableList, 2) }
            openSubFragment(selector, args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.HomeUp, R.string.select_app, R.id.prefs_app_selector)
        } else append(newId())
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = settingsView {
        val state = rememberLazyListState()
        var dragging by remember { mutableStateOf<String?>(null) }
        var dragY by remember { mutableFloatStateOf(0f) }
        var initial by remember { mutableStateOf<List<Item>>(emptyList()) }
        val up = getString(R.string.a14_move_up)
        val down = getString(R.string.a14_move_down)
        val remove = getString(R.string.a14_remove_item)
        val edge = with(LocalDensity.current) { 48.dp.toPx() }
        // Only an active drag owns this frame coroutine; idle/hidden pages have no scroll work.
        LaunchedEffect(dragging != null) {
            if (dragging == null) return@LaunchedEffect
            var last = withFrameNanos { it }
            while (isActive && dragging != null) {
                val now = withFrameNanos { it }
                val info = state.layoutInfo
                val velocity = when {
                    dragY < info.viewportStartOffset + edge -> -((info.viewportStartOffset + edge - dragY) / edge).coerceIn(0f, 1f)
                    dragY > info.viewportEndOffset - edge -> ((dragY - info.viewportEndOffset + edge) / edge).coerceIn(0f, 1f)
                    else -> 0f
                }
                if (velocity != 0f) {
                    state.scrollBy(velocity * edge * 8f * ((now - last) / 1_000_000_000f).coerceAtMost(.064f))
                    val id = dragging
                    val own = info.visibleItemsInfo.firstOrNull { it.key == id }
                    val center = dragY + (own?.size ?: 0) / 2f
                    val target = state.layoutInfo.visibleItemsInfo.firstOrNull { center >= it.offset && center < it.offset + it.size }
                    if (target != null) move(items.indexOfFirst { it.id == id }, target.index, persist = false)
                }
                last = now
            }
        }
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = ::addItem, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text(getString(R.string.a14_add_item)) }
            LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    val selected = dragging == item.id
                    val layout = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.id }
                    val visualY = if (selected) dragY - (layout?.offset ?: 0) else 0f
                    Surface(Modifier.fillMaxWidth().zIndex(if (selected) 1f else 0f)
                        .graphicsLayer { translationY = visualY }
                        .semantics {
                            customActions = buildList {
                                if (!activities && index > 0) add(CustomAccessibilityAction(up) { move(index, index - 1); true })
                                if (!activities && index < items.lastIndex) add(CustomAccessibilityAction(down) { move(index, index + 1); true })
                                add(CustomAccessibilityAction(remove) { delete(item.id); true })
                            }
                        }
                        .combinedClickable(onClick = {
                            if (!activities) {
                                val args = Bundle().apply { putString("key", "${key}_${item.id}"); putInt("actions", MultiAction.Actions.LOCKSCREEN.ordinal) }
                                openSubFragment(MultiAction(), args, AppHelper.SettingsType.Edit, AppHelper.ActionBarType.Edit, settingTitle, R.id.prefs_multiaction)
                            }
                        }, onLongClick = { delete(item.id) }), tonalElevation = if (selected) 4.dp else 0.dp) {
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(12.dp)) {
                                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                                if (item.summary.isNotEmpty()) Text(item.summary, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!activities) Text("≡", Modifier.padding(16.dp).pointerInput(item.id) {
                                detectDragGesturesAfterLongPress(onDragStart = {
                                    initial = items; dragging = item.id
                                    dragY = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.id }?.offset?.toFloat() ?: 0f
                                }, onDragCancel = { items = initial; dragging = null },
                                    onDragEnd = { dragging = null; persistOrder() }) { change, amount ->
                                    change.consume(); dragY += amount.y
                                    val visible = state.layoutInfo.visibleItemsInfo
                                    val own = visible.firstOrNull { it.key == item.id }
                                    val center = dragY + (own?.size ?: 0) / 2f
                                    val target = visible.firstOrNull { center >= it.offset && center < it.offset + it.size }
                                    if (target != null) move(items.indexOfFirst { it.id == item.id }, target.index, persist = false)
                                }
                            }.semantics { contentDescription = getString(R.string.a14_reorder_item) })
                            IconButton(onClick = { delete(item.id) }) { Text("×", Modifier.semantics { contentDescription = remove }) }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && requestCode == 2 && data != null) {
            val value = data.getStringExtra("activity") ?: return
            val user = data.getIntExtra("user", 0)
            if (user < 0) return
            val id = newId()
            AppHelper.appPrefs?.edit()?.putInt("${key}_${id}_action", 20)
                ?.putString("${key}_${id}_activity", value)?.putInt("${key}_${id}_activity_user", user)?.apply()
            append(id)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
