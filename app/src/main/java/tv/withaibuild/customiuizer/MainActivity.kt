package tv.withaibuild.customiuizer

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.core.content.ContextCompat
import android.view.KeyEvent
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.foundation.layout.consumeWindowInsets
import android.widget.LinearLayout
import androidx.fragment.app.FragmentContainerView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import tv.withaibuild.customiuizer.ui.SettingsActionBar
import tv.withaibuild.customiuizer.ui.SettingsTheme
import tv.withaibuild.customiuizer.ui.SettingsToolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.PredictiveBackControl
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.Helpers

class MainActivity : AppCompatActivity() {

    val settingsBar = SettingsActionBar(this)
    private var mainFrag: MainFragment? = null
    private var windowInsetsController: WindowInsetsControllerCompat? = null

    @OptIn(PredictiveBackControl::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the pre-upgrade, committed-back behavior. Fragment 1.9's seeking
        // cancellation can leave our custom page translation at its partial value.
        FragmentManager.enablePredictiveBack(false)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val toolbar = ComposeView(this).apply {
            id = R.id.mainActionBar
            consumeWindowInsets = false
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { SettingsTheme { SettingsToolbar(settingsBar,
                onBack = { onBackPressedDispatcher.onBackPressed() }, onAction = ::dispatchSettingsAction) } }
        }
        val content = FragmentContainerView(this).apply { id = R.id.fragment_container }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, -2))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        applySystemBarsAppearance()

        if (savedInstanceState != null) {
            mainFrag = supportFragmentManager.getFragment(savedInstanceState, "mainFrag") as? MainFragment
        } else if (mainFrag == null) {
            mainFrag = MainFragment()
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, mainFrag!!)
                .commit()
        }

    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBarsAppearance()
    }

    @Suppress("DEPRECATION") // The supported runtime is Android 14.
    private fun applySystemBarsAppearance() {
        val background = ContextCompat.getColor(this, R.color.color_window_background)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = background
        window.navigationBarColor = background
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        (windowInsetsController ?: WindowInsetsControllerCompat(window, window.decorView).also {
            windowInsetsController = it
        }).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = !isNightMode
        }
    }

    fun navToSubFragment(
        fragment: Fragment,
        args: Bundle,
        settingsType: AppHelper.SettingsType,
        abType: AppHelper.ActionBarType,
        titleResId: Int,
        contentResId: Int
    ) {
        navToSubFragment(fragment, args, settingsType, abType, resources.getString(titleResId), contentResId)
    }

    fun navToSubFragment(
        fragment: Fragment,
        args: Bundle,
        settingsType: AppHelper.SettingsType,
        abType: AppHelper.ActionBarType,
        title: String,
        contentResId: Int
    ) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is PreferenceFragmentBase) {
            currentFragment.openSubFragment(fragment, args, settingsType, abType, title, contentResId)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mainFrag?.let { supportFragmentManager.putFragment(outState, "mainFrag", it) }
        super.onSaveInstanceState(outState)
    }

    private fun dispatchSettingsAction(item: MenuItem) {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment?.onOptionsItemSelected(item) != true) onOptionsItemSelected(item)
        if (fragment != null && fragment === supportFragmentManager.findFragmentById(R.id.fragment_container) && fragment.isAdded)
            settingsBar.bind(fragment)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (fragment == null) {
                    finish()
                } else if (fragment is MainFragment) {
                    finish()
                } else if (fragment is SubFragment) {
                    fragment.finish()
                } else if (fragment is AboutFragment) {
                    supportFragmentManager.popBackStackImmediate()
                }
                true
            }
            R.id.resetsettings -> {
                showResetSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showResetSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_settings)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                AppHelper.appPrefs!!.edit().clear().apply()
                AlertDialog.Builder(this)
                    .setTitle(R.string.reset_settings_done)
                    .setCancelable(true)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        finishAffinity()
                    }
                    .show()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_MENU) true else super.onKeyDown(keyCode, event)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (grantResults.isEmpty()) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            return
        }

        when (requestCode) {
            Helpers.REQUEST_PERMISSIONS_WIFI -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION))
                        Toast.makeText(this, R.string.permission_scan, Toast.LENGTH_LONG).show()
                    else
                        Toast.makeText(this, R.string.permission_permanent, Toast.LENGTH_LONG).show()
                }
            }
            Helpers.REQUEST_PERMISSIONS_BLUETOOTH -> {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT))
                        Toast.makeText(this, R.string.permission_scan, Toast.LENGTH_LONG).show()
                    else
                        Toast.makeText(this, R.string.permission_permanent, Toast.LENGTH_LONG).show()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}
