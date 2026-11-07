package tv.withaibuild.customiuizer.subs

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.AppSelectionRow
import tv.withaibuild.customiuizer.SubFragmentWithSearch
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.utils.AppData

class ActivitySelector : SubFragmentWithSearch() {

    override val activityRows = true
    private var pkg: String? = null
    private var key: String? = null
    private var user = 0
    private val activities = ArrayList<AppData>()

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments
        key = args?.getString("key")
        pkg = args?.getString("package")
        user = args?.getInt("user", 0) ?: 0

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            delay(animDur.toLong())
            loadActivities()
            withContext(Dispatchers.Main) {
                if (isAdded) setupList()
            }
        }
    }

    private suspend fun loadActivities() {
        activities.clear()
        val context = context ?: return
        val pm = context.packageManager
        try {
            val pi = pm.getPackageInfo(pkg ?: return, PackageManager.GET_ACTIVITIES)
            if (pi.activities != null) {
                for (info in pi.activities) {
                    val appData = AppData()
                    appData.pkgName = pkg ?: ""
                    appData.actName = info.name ?: ""
                    appData.label = info.loadLabel(pm).toString()
                    appData.enabled = info.enabled
                    appData.prepareForList()
                    activities.add(appData)
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun setupList() {
        val act = activity
        if (act == null || !isAdded) return
        if (activities.isEmpty()) {
            Toast.makeText(act, R.string.no_activities_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        appRows = activities.sortedBy { it.actNameLower }.map { AppSelectionRow(it, it.actName.replace(".", ".\u200B")) }
        loading = false
    }

    override fun selectApp(row: AppSelectionRow) {
        val appData = row.app
        val intent = Intent().putExtra("activity", "${appData.pkgName}|${appData.actName}").putExtra("user", user)
        targetFragment?.onActivityResult(targetRequestCode, Activity.RESULT_OK, intent)
        finish()
    }

    override fun longSelectApp(row: AppSelectionRow): Boolean {
        val appData = row.app
        val intent = Intent().setComponent(ComponentName(appData.pkgName, appData.actName))
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED).putExtra("user", user)
        activity?.sendBroadcast(Intent(GlobalActions.ACTION_PREFIX + "LaunchIntent").putExtra("intent", intent))
        return true
    }
}
