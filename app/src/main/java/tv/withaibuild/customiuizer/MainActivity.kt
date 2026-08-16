package tv.withaibuild.customiuizer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.Helpers

class MainActivity : AppCompatActivity() {

    private var mainFrag: MainFragment? = null
    private var windowInsetsController: WindowInsetsControllerCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        applySystemBarsAppearance()

        val myToolbar = findViewById<Toolbar>(R.id.mainActionBar)
        setSupportActionBar(myToolbar)
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

    private fun applySystemBarsAppearance() {
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

    override fun onDestroy() {
        super.onDestroy()
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
                    if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION))
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
            Helpers.REQUEST_PERMISSIONS_REPORT -> Toast.makeText(this, ":(", Toast.LENGTH_SHORT).show()
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}
