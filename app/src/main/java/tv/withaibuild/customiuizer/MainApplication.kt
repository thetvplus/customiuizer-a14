package tv.withaibuild.customiuizer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.utils.AppLocaleController
import tv.withaibuild.customiuizer.utils.CurrentPreferenceContract
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.utils.XposedServiceManager

class MainApplication : Application() {

    override fun attachBaseContext(base: Context) {
        Helpers.withinAppContext = true
        Helpers.appContentResolver = base.contentResolver
        val sp: SharedPreferences = AppHelper.getSharedPrefs(base, false)
        AppHelper.appPrefs = sp
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        // Returns immediately unless the user actually chose a language: the language
        // setting is optional, so it must not put work on the start-up path when it is off.
        // Pass the Application context: without it the locale is applied through
        // AppCompat, which silently does nothing this early. See AppLocaleController.
        AppHelper.appPrefs?.let { prefs ->
            CurrentPreferenceContract.pruneOrphanPreferences(prefs)
            AppLocaleController.apply(prefs, this)
        }
        XposedServiceManager.init(AppHelper.appPrefs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(
                NotificationChannel("customiuizer_default", getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
        registerPackageChangeReceiver()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            clearAppListsAndIconCache()
        }
    }

    private fun registerPackageChangeReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } catch (_: Throwable) {
        }
    }

    private fun clearAppListsAndIconCache() {
        Helpers.memoryCache.evictAll()
        AppHelper.installedAppsList = null
        Helpers.shareAppsList = null
        Helpers.openWithAppsList = null
        Helpers.launchableAppsList = null
    }

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            clearAppListsAndIconCache()
        }
    }
}
