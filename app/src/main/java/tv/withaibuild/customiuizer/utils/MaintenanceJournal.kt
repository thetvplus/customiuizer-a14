package tv.withaibuild.customiuizer.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import tv.withaibuild.customiuizer.mods.GlobalActions

/** Cold-path write-ahead recovery, separate from transferable preference backups. */
internal class MaintenanceJournal(private val storage: SharedPreferences, private val backend: Backend) {
    data class Target(val packageName: String, val component: String? = null)
    data class Snapshot(val targets: List<Target>, val original: IntArray)
    interface Backend {
        fun identity(packageName: String): String
        fun state(target: Target): Int
    }

    private fun record(key: String): JSONObject? = storage.getString(key, null)?.let(::JSONObject)
    fun contains(key: String) = storage.contains(key)

    fun snapshot(key: String): Snapshot? = record(key)?.let { json ->
        val entries = json.getJSONArray("targets")
        require(entries.length() in 1..32)
        val targets = (0 until entries.length()).map { index ->
            val item = entries.getJSONObject(index)
            Target(item.getString("package"), item.optString("component").takeIf { it.isNotEmpty() })
        }
        Snapshot(targets, IntArray(entries.length()) { entries.getJSONObject(it).getInt("original") })
    }

    /** Existing legacy snapshots remain explicit restore inputs; never invent DEFAULT. */
    fun prepare(key: String, intent: Intent, enable: Boolean): Attempt {
        require(key in PROFILES)
        val targets = targets(intent)
        val requested = states(intent)
        require(targets.size in 1..32 && targets.size == requested.size && targets.distinct().size == targets.size)
        require(requested.all { it in 0..4 })
        val previous = record(key)
        require(!enable || previous == null) { "Recovery must finish before another restriction" }
        if (previous != null) {
            val saved = requireNotNull(snapshot(key))
            require(saved.targets == targets && saved.original.contentEquals(requested))
        }
        val identities = JSONObject()
        for (name in targets.map { it.packageName }.distinct()) identities.put(name, backend.identity(name))
        if (previous != null) {
            val saved = previous.getJSONObject("identity")
            require(saved.length() == identities.length())
            for (name in identities.keys()) require(saved.getString(name) == identities.getString(name))
        }
        val originals = if (enable) IntArray(targets.size) { backend.state(targets[it]) } else requested.copyOf()
        require(originals.all { it in 0..4 })
        val entries = JSONArray()
        targets.forEachIndexed { index, target -> entries.put(JSONObject().apply {
            put("package", target.packageName)
            target.component?.let { put("component", it) }
            put("original", originals[index])
        }) }
        val json = JSONObject().put("targets", entries).put("identity", identities)
            .put("operation", UUID.randomUUID().toString())
        check(storage.edit().putString(key, json.toString()).commit()) { "Recovery record was not saved" }
        intent.putExtra(EXTRA_IDENTITY, identities.toString())
        return Attempt(key, json, targets, originals, requested, enable)
    }

    inner class Attempt(private val key: String, private val json: JSONObject, private val targets: List<Target>,
        private val originals: IntArray, private val requested: IntArray, private val enable: Boolean) {
        private fun current() = record(key)?.getString("operation") == json.getString("operation")
        fun verify(): Boolean = current() && targets.indices.all {
            backend.identity(targets[it].packageName) == json.getJSONObject("identity").getString(targets[it].packageName) &&
                backend.state(targets[it]) == requested[it]
        }
        fun finish() {
            check(current()) { "A newer maintenance operation owns recovery" }
            val editor = storage.edit()
            if (enable) editor.putString(key, json.toString()) else editor.remove(key)
            check(editor.commit()) { "Recovery completion was not saved" }
        }
        fun restored(): Boolean = current() && targets.indices.all {
            backend.identity(targets[it].packageName) == json.getJSONObject("identity").getString(targets[it].packageName) &&
                backend.state(targets[it]) == originals[it]
        }
        fun rollbackIntent(request: Intent): Intent? {
            if (!enable || !current() || restored()) return null
            return Intent(request).apply {
                when (action) {
                    GlobalActions.SET_MIUI_DAEMON_STATE_ACTION -> putExtra(GlobalActions.EXTRA_APPLICATION_STATE, originals.single())
                    GlobalActions.SET_UPDATER_SERVICES_ACTION -> putExtra(GlobalActions.EXTRA_UPDATER_SERVICE_STATES, originals)
                    else -> putExtra(GlobalActions.EXTRA_COMPONENT_STATES, originals)
                }
            }
        }
        fun recovered() {
            check(restored())
            check(storage.edit().remove(key).commit()) { "Recovery record was not cleared" }
        }
        fun failed() {
            if (enable && restored()) {
                check(storage.edit().remove(key).commit()) { "Recovery record was not cleared" }
            }
        }
    }

    companion object {
        const val EXTRA_IDENTITY = "maintenance_identity"
        private val PROFILES = setOf("pref_key_various_disable_update_services", "pref_key_various_disable_miui_daemon",
            "pref_key_various_trim_miui_daemon_network", "pref_key_various_disable_xiaomi_analytics",
            "pref_key_various_trim_security_center_marketing", "pref_key_various_remove_security_center_antivirus")

        fun open(context: Context) = MaintenanceJournal(context.getSharedPreferences("maintenance_recovery", Context.MODE_PRIVATE),
            NativeBackend(context))

        fun targets(intent: Intent): List<Target> = when (intent.action) {
            GlobalActions.SET_MIUI_DAEMON_STATE_ACTION -> listOf(Target("com.miui.daemon"))
            GlobalActions.SET_UPDATER_SERVICES_ACTION -> intent.getStringArrayExtra(GlobalActions.EXTRA_UPDATER_SERVICE_NAMES)
                ?.map { Target("com.android.updater", it) } ?: emptyList()
            GlobalActions.SET_XIAOMI_COMPONENTS_ACTION -> {
                val packages = requireNotNull(intent.getStringArrayExtra(GlobalActions.EXTRA_PACKAGE_NAMES))
                val names = requireNotNull(intent.getStringArrayExtra(GlobalActions.EXTRA_COMPONENT_NAMES))
                require(packages.size == names.size)
                packages.indices.map { Target(packages[it], names[it]) }
            }
            GlobalActions.SET_XIAOMI_PACKAGES_ACTION -> intent.getStringArrayExtra(GlobalActions.EXTRA_PACKAGE_NAMES)
                ?.map { Target(it) } ?: emptyList()
            else -> emptyList()
        }

        private fun states(intent: Intent): IntArray = when (intent.action) {
            GlobalActions.SET_MIUI_DAEMON_STATE_ACTION -> intArrayOf(intent.getIntExtra(GlobalActions.EXTRA_APPLICATION_STATE, -1))
            GlobalActions.SET_UPDATER_SERVICES_ACTION -> intent.getIntArrayExtra(GlobalActions.EXTRA_UPDATER_SERVICE_STATES)
            else -> intent.getIntArrayExtra(GlobalActions.EXTRA_COMPONENT_STATES)
        } ?: intArrayOf()

        /** Runs again inside the authenticated bridge, before any privileged mutation. */
        fun matches(context: Context, intent: Intent): Boolean {
            val targets = targets(intent)
            if (targets.size !in 1..32) return false
            val expected = JSONObject(intent.getStringExtra(EXTRA_IDENTITY) ?: return false)
            val names = targets.map { it.packageName }.toSet()
            if (names.size != expected.length()) return false
            val backend = NativeBackend(context)
            return names.all { expected.getString(it) == backend.identity(it) }
        }
    }

    class NativeBackend(private val context: Context) : Backend {
        @Suppress("DEPRECATION")
        override fun identity(packageName: String): String {
            check(Build.VERSION.SDK_INT == 34)
            val info = context.packageManager.getPackageInfo(packageName,
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES or PackageManager.MATCH_DISABLED_COMPONENTS)
            val app = requireNotNull(info.applicationInfo)
            check(app.flags and ApplicationInfo.FLAG_SYSTEM != 0 && app.flags and ApplicationInfo.FLAG_INSTALLED != 0)
            val certificates = info.signingInfo?.apkContentsSigners ?: info.signatures
            check(!certificates.isNullOrEmpty())
            val digest = MessageDigest.getInstance("SHA-256")
            val signatures = certificates.map { certificate -> digest.digest(certificate.toByteArray())
                .joinToString("") { "%02x".format(it) } }.sorted().joinToString(",")
            return "${Build.FINGERPRINT}|${Build.VERSION.SDK_INT}|${app.uid}|${info.longVersionCode}|$signatures"
        }

        override fun state(target: Target): Int = if (target.component == null)
            context.packageManager.getApplicationEnabledSetting(target.packageName)
        else context.packageManager.getComponentEnabledSetting(ComponentName(target.packageName, target.component))
    }
}
