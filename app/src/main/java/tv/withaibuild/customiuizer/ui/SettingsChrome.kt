package tv.withaibuild.customiuizer.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.appcompat.view.menu.MenuBuilder
import tv.withaibuild.customiuizer.MainFragment
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextMotion
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import tv.withaibuild.customiuizer.PreferenceFragmentBase
import tv.withaibuild.customiuizer.R

/** Settings-only theme. No Compose types are referenced by Hook entry points. */
@Composable
fun SettingsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val background = colorResource(R.color.color_window_background)
    val foreground = colorResource(R.color.preference_primary_text_color)
    val secondary = colorResource(R.color.preference_secondary_text_color)
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val surface = colorResource(R.color.color_surface_container)
    val colors = remember(context, configuration, dark, background, foreground, secondary, surface) {
        val base = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        val selection = base.primary.copy(alpha = .16f).compositeOver(background)
        base.copy(
            primaryContainer = selection, secondaryContainer = selection,
            onPrimaryContainer = foreground, onSecondaryContainer = foreground,
            background = background, onBackground = foreground, surface = surface,
            surfaceContainerLow = surface, surfaceContainer = surface,
            surfaceContainerHigh = surface, surfaceContainerHighest = surface,
            onSurface = foreground, onSurfaceVariant = secondary)
    }
    MaterialTheme(colorScheme = colors, typography = settingsTypography, content = content)
}

// Stable glyph advances during native scrolling/stretch and page translation; no extra layers.
private val settingsTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(textMotion = TextMotion.Animated),
        displayMedium = base.displayMedium.copy(textMotion = TextMotion.Animated),
        displaySmall = base.displaySmall.copy(textMotion = TextMotion.Animated),
        headlineLarge = base.headlineLarge.copy(textMotion = TextMotion.Animated),
        headlineMedium = base.headlineMedium.copy(textMotion = TextMotion.Animated),
        headlineSmall = base.headlineSmall.copy(textMotion = TextMotion.Animated),
        titleLarge = base.titleLarge.copy(textMotion = TextMotion.Animated),
        titleMedium = base.titleMedium.copy(textMotion = TextMotion.Animated),
        titleSmall = base.titleSmall.copy(textMotion = TextMotion.Animated),
        bodyLarge = base.bodyLarge.copy(textMotion = TextMotion.Animated),
        bodyMedium = base.bodyMedium.copy(textMotion = TextMotion.Animated),
        bodySmall = base.bodySmall.copy(textMotion = TextMotion.Animated),
        labelLarge = base.labelLarge.copy(textMotion = TextMotion.Animated),
        labelMedium = base.labelMedium.copy(textMotion = TextMotion.Animated),
        labelSmall = base.labelSmall.copy(textMotion = TextMotion.Animated),
    )
}

/** Detach native ripple nodes when a page leaves, so feedback cannot travel with it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(content: @Composable () -> Unit) {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val ripple = LocalRippleConfiguration.current
    CompositionLocalProvider(LocalRippleConfiguration provides
        if (state.isAtLeast(Lifecycle.State.RESUMED)) ripple else null) {
        Surface(Modifier.fillMaxSize().imePadding(), color = MaterialTheme.colorScheme.background, content = content)
    }
}

/** A small bridge for existing feature controllers; the toolbar itself is Compose. */
class SettingsActionBar(private val context: Context) {
    var titleText by mutableStateOf<CharSequence>("")
        private set
    var showBack by mutableStateOf(false)
        private set
    var owner by mutableStateOf<Fragment?>(null)
        private set
    val items = mutableStateListOf<MenuItem>()

    fun setTitle(title: CharSequence?) { titleText = title ?: "" }
    fun setTitle(resource: Int) { titleText = context.getText(resource) }
    fun setDisplayHomeAsUpEnabled(enabled: Boolean) { showBack = enabled }

    @SuppressLint("RestrictedApi")
    fun bind(fragment: Fragment) {
        owner = fragment
        val menu = MenuBuilder(context)
        (fragment as? PreferenceFragmentBase)?.createSettingsMenu(menu)
        items.clear()
        for (index in 0 until menu.size()) items.add(menu.getItem(index))
    }
}

/** Menus are declared alongside their renderer, without a second XML UI definition. */
fun populateSettingsMenu(menu: Menu) {
    menu.add(0, R.id.search_btn, 0, androidx.appcompat.R.string.abc_search_hint).isVisible = false
    menu.add(0, R.id.edit_confirm, 1, android.R.string.ok).isVisible = false
    val restart = menu.addSubMenu(0, R.id.restart_menu, 2, R.string.restart_menu)
    for ((id, title) in listOf(R.id.restartlauncher to R.string.restart_launcher,
        R.id.restartsystemui to R.string.restart_systemui,
        R.id.restartsecuritycenter to R.string.restart_securitycenter,
        R.id.softreboot to R.string.soft_reboot)) restart.add(0, id, 0, title)
    menu.add(0, R.id.backuprestore, 3, R.string.backup_restore)
    menu.add(0, R.id.resetsettings, 4, R.string.reset_settings)
    menu.add(0, R.id.about, 5, R.string.app_about)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsToolbar(bar: SettingsActionBar, onBack: () -> Unit, onAction: (MenuItem) -> Unit) {
    if ((bar.owner as? MainFragment)?.isSearching == true) return
    var expanded by remember(bar.owner) { mutableStateOf(false) }
    var submenu by remember(bar.owner) { mutableStateOf<MenuItem?>(null) }
    val actions = bar.items.filter { it.isVisible }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        title = { Text(bar.titleText.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (bar.showBack) IconButton(onClick = onBack) {
                Icon(painterResource(androidx.appcompat.R.drawable.abc_ic_ab_back_material),
                    stringResource(androidx.appcompat.R.string.abc_action_bar_up_description))
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        actions = {
            actions.firstOrNull { it.itemId == R.id.search_btn }?.let { item ->
                IconButton(onClick = { onAction(item) }, enabled = item.isEnabled,
                    modifier = Modifier.testTag("search.expand")) {
                    Icon(painterResource(androidx.appcompat.R.drawable.abc_ic_search_api_material),
                        stringResource(androidx.appcompat.R.string.abc_search_hint))
                }
            }
            actions.firstOrNull { it.itemId == R.id.edit_confirm }?.let { item ->
                IconButton(onClick = { onAction(item) }, enabled = item.isEnabled) {
                    Icon(painterResource(R.drawable.ic_actionbar_done), stringResource(android.R.string.ok))
                }
            }
            val overflow = actions.filter { it.itemId != R.id.edit_confirm && it.itemId != R.id.search_btn }
            if (overflow.isNotEmpty()) Box {
                IconButton(onClick = { submenu = null; expanded = true }, modifier = Modifier.testTag("menu.expand")) {
                    Icon(painterResource(androidx.appcompat.R.drawable.abc_ic_menu_overflow_material),
                        stringResource(androidx.appcompat.R.string.abc_action_menu_overflow_description))
                }
                DropdownMenu(expanded, { expanded = false; submenu = null },
                    shape = RoundedCornerShape(dimensionResource(R.dimen.preference_group_radius)),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp, shadowElevation = 8.dp) {
                    val parent = submenu
                    key(parent?.itemId) {
                        Column {
                            val visibleItems = parent?.subMenu?.let { menu ->
                                List(menu.size()) { menu.getItem(it) }.filter { it.isVisible }
                            } ?: overflow
                            if (parent != null) key(parent.itemId) { DropdownMenuItem(
                                text = { Text(parent.title.toString(), style = MaterialTheme.typography.bodyLarge) },
                                leadingIcon = { Icon(painterResource(androidx.appcompat.R.drawable.abc_ic_ab_back_material), null) },
                                modifier = Modifier.testTag("menu.back"),
                                onClick = { submenu = null }) }
                            for (item in visibleItems) key(item.itemId) { DropdownMenuItem(
                                text = { Text(item.title.toString(), style = MaterialTheme.typography.bodyLarge) },
                                trailingIcon = if (item.hasSubMenu()) ({ SettingsChevron() }) else null,
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier = Modifier.testTag("menu.item:" + item.itemId),
                                enabled = item.isEnabled,
                                onClick = {
                                    if (item.hasSubMenu()) submenu = item else {
                                        expanded = false
                                        submenu = null
                                        onAction(item)
                                    }
                                }) }
                        }
                    }
                }
            }
        })
}

/** One composition lifetime per Fragment view, shared by preference and editor pages. */
fun Fragment.settingsView(content: @Composable () -> Unit): View = ComposeView(requireContext()).apply {
    id = R.id.preference_compose
    consumeWindowInsets = false
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent { SettingsTheme { SettingsPage(content) } }
}
