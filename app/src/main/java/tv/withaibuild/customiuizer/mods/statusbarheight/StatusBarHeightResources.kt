package tv.withaibuild.customiuizer.mods.statusbarheight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.net.Uri
import android.util.TypedValue
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.PrefsProvider
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.Helpers
import java.lang.ref.WeakReference
import java.io.File

/** One platform-owned definition. Desired enabled is ALWAYS false, including on disk.
 * Native updateState validates target/idmap before this module changes only runtime state 2 -> 3.
 * Without the module, native boot registration recalculates state 2 before applying asset paths.
 * No root helper, app injection, polling, per-height names or boot script is involved.
 */
internal object StatusBarHeightResources {
    internal val overlayName = Helpers.modulePkg.replace('.', '_') + "_statusbar_height"
    internal val dimensions = listOf("status_bar_height", "status_bar_height_default",
        "status_bar_height_portrait", "status_bar_height_landscape")
    private var controller: Controller? = null
    @Volatile private var ready = false

    /** Called only after the existing invalidation receiver authenticates the module sender.
     * Capacity one integer, owned by this SS feature. A failed IPC retains the last known value.
     */
    fun committedPreference(context: Context): Map<String, Any> {
        val current = controller ?: return emptyMap()
        val token = android.os.Binder.clearCallingIdentity()
        try {
            ModuleHelper.guarded {
                val result = context.contentResolver.call(Uri.parse("content://" + PrefsProvider.AUTHORITY),
                    PrefsProvider.COMMITTED_HEIGHT, null, null)
                val value = result?.takeIf { it.containsKey(PrefsProvider.HEIGHT_DP) }?.getInt(PrefsProvider.HEIGHT_DP)
                if (value != null && value in StatusBarHeightConfig.DEFAULT_SENTINEL..80) current.committedHeight = value
            }
        } finally { android.os.Binder.restoreCallingIdentity(token) }
        return mapOf("pref_key_" + StatusBarHeightConfig.PREF_KEY to current.committedHeight)
    }

    fun install(loader: ClassLoader) = ModuleHelper.guarded {
        val abi = Access(loader)
        check(ModuleHelper.hookMethod(abi.updateState, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val info = chain.getArg(0) ?: return chain.proceed()
                val id = ModuleHelper.guarded<Any?>(null) { abi.identifier.invoke(info) }
                if (id == null || !ModuleHelper.guarded(false) { abi.isOurs(id) }) return chain.proceed()
                val settings = ModuleHelper.guarded<Any?>(null) { abi.implSettings.get(chain.thisObject) }
                    ?: return chain.proceed()
                val user = chain.getArg(1) as Int
                return updateState(clearDesired = { abi.setEnabled.invoke(settings, id, user, false) == true },
                    proceed = { chain.proceed() }, enabled = { ready && activeHeight() != null && !removed },
                    readState = { abi.getState.invoke(settings, id, user) as Int },
                    enableRuntime = { abi.setState.invoke(settings, id, user, 3) == true })
            }
        }) != null)
        // Theme changes can move another android overlay above ours. Reorder only this ID,
        // while OMS already owns its lock, before native persistence and PM asset updates.
        check(ModuleHelper.hookMethod(abi.updateTargets, object : MethodHook() {
            override fun before(callback: BeforeHookCallback) {
                if (activeHeight() == null || removed) return
                val owner = callback.getThisObject() ?: return
                val settings = abi.settings.get(owner)
                val targets = callback.getArg(0) as? Set<*> ?: return
                for (target in targets) if (target != null && abi.targetPackage.get(target) == "android") {
                    val user = abi.targetUser.getInt(target)
                    if (abi.getNullableInfo.invoke(settings, abi.id, user) != null)
                        abi.highest.invoke(settings, abi.id, user)
                }
            }
        }) != null)
        check(ModuleHelper.hookMethod(abi.onStart, object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                if (callback.getThrowable() == null) callback.getThisObject()?.let { attach(it, abi) }
            }
        }) != null)
        ready = true
        // Preference bootstrap may finish after OMS.onStart. ServiceManager is local in SS.
        val binder = XposedHelpers.callStaticMethod(abi.serviceManager, "getService", "overlay")
        if (binder != null) ModuleHelper.guarded {
            attach(XposedHelpers.getObjectField(binder, "this\$0"), abi)
        }
    }

    @Volatile private var removed = false
    internal fun updateState(clearDesired: () -> Boolean, proceed: () -> Any?, enabled: () -> Boolean,
        readState: () -> Int, enableRuntime: () -> Boolean): Any? {
        val cleared = ModuleHelper.guarded(false, clearDesired)
        val original = proceed() // Exactly once, outside our safety boundary: native exceptions remain native.
        return ModuleHelper.guarded(original) {
            val changed = if (enabled() && readState() == 2) enableRuntime() else false
            original == true || cleared || changed
        }
    }

    internal fun retireOwnedPath(path: String, referenced: Set<*>, removeIdmap: () -> Boolean,
        idmapExists: () -> Boolean, delete: () -> Boolean, exists: () -> Boolean): Boolean {
        if (!path.startsWith("/data/resource-cache/android-$overlayName-") || !path.endsWith(".frro") ||
            path in referenced) return true
        if (!removeIdmap() && idmapExists()) return false
        return delete() || !exists()
    }
    private fun activeHeight(): Int? = MainModule.mPrefs.getInt(StatusBarHeightConfig.PREF_KEY,
        StatusBarHeightConfig.DEFAULT_SENTINEL).takeIf { it > StatusBarHeightConfig.DEFAULT_SENTINEL }

    private fun attach(owner: Any, abi: Access) {
        if (controller?.owns(owner) == true) return
        controller?.close()
        controller = Controller(owner, abi).also { it.start() }
    }

    private class Controller(serviceOwner: Any, private val abi: Access) : AutoCloseable {
        @Volatile var committedHeight = MainModule.mPrefs.getInt(StatusBarHeightConfig.PREF_KEY,
            StatusBarHeightConfig.DEFAULT_SENTINEL)
        private val owner = WeakReference(serviceOwner)
        private val context = XposedHelpers.callMethod(serviceOwner, "getContext") as Context
        private val handler = XposedHelpers.callStaticMethod(abi.ioThread, "getHandler") as Handler
        private var closed = false
        private var registered = false
        private var applied: Int? = null
        // Normally one old record + one active definition. Revocation may retire both records.
        private val retired = ArrayList<Any>(2)
        private var creationFailed = false
        private val observer = object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) {
                if (key == null || key == StatusBarHeightConfig.PREF_KEY) schedule()
            }
        }
        private val uninstall = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                if (intent.action == Intent.ACTION_PACKAGE_FULLY_REMOVED &&
                    intent.data?.schemeSpecificPart == Helpers.modulePkg &&
                    intent.getIntExtra(Intent.EXTRA_UID, -1).let { it in 0..99999 } &&
                    !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    removed = true
                    schedule()
                }
            }
        }
        private val reconcile = Runnable { ModuleHelper.guarded {
            if (closed) return@guarded
            val service = owner.get() ?: return@guarded
            val height = if (removed) null else activeHeight()
            if (applied == height && registered && retired.isEmpty()) return@guarded
            val token = android.os.Binder.clearCallingIdentity()
            try {
                synchronized(abi.lock.get(service)) {
                    val impl = abi.impl.get(service)
                    val settings = abi.settings.get(service)
                    // Failed native retirement must not let preference changes accumulate files.
                    val iterator = retired.iterator()
                    while (iterator.hasNext()) {
                        val old = iterator.next()
                        if (ModuleHelper.guarded(false) { abi.retire(impl, settings, old) }) iterator.remove()
                    }
                    if (height != null && retired.isNotEmpty()) return@guarded
                    if (applied == height && registered) return@guarded
                    if (height != null && creationFailed) return@guarded
                    val previous = abi.getNullableInfo.invoke(settings, abi.id, 0)
                    val targets = if (height != null) try {
                        abi.register.invoke(impl, abi.fabricate(height))
                    } catch (error: Throwable) {
                        creationFailed = true // No unbounded retries/orphans after an uncertain native failure.
                        throw error
                    } else abi.unregister.invoke(impl, abi.id)
                    try { abi.updateTargets.invoke(service, targets) }
                    catch (error: Throwable) { creationFailed = true; throw error }
                    applied = height
                    registered = true
                    if (previous != null) {
                        check(retired.size < 2)
                        retired.add(previous)
                    }
                    val cleanup = retired.iterator()
                    while (cleanup.hasNext()) {
                        val old = cleanup.next()
                        if (ModuleHelper.guarded(false) { abi.retire(impl, settings, old) }) cleanup.remove()
                    }
                }
                XposedHelpers.log("[StatusBarResources] heightDp=$height desiredEnabled=false")
            } finally { android.os.Binder.restoreCallingIdentity(token) }
        } }
        fun owns(value: Any) = owner.get() === value
        fun start() {
            try {
                ModuleHelper.observePreferenceChange(observer, this)
                context.registerReceiver(uninstall, IntentFilter(Intent.ACTION_PACKAGE_FULLY_REMOVED).apply {
                    addDataScheme("package")
                }, null, handler, Context.RECEIVER_EXPORTED)
                schedule()
            } catch (error: Throwable) { close(); throw error }
        }
        private fun schedule() { handler.removeCallbacks(reconcile); handler.post(reconcile) }
        override fun close() {
            closed = true
            handler.removeCallbacks(reconcile)
            ModuleHelper.unregisterPreferenceObserver(this)
            ModuleHelper.guarded { context.unregisterReceiver(uninstall) }
        }
    }

    /** Cold, exact HyperOS 1/A14 ABI; no reflection discovery in window/frame callbacks. */
    private class Access(private val loader: ClassLoader) {
        private fun type(name: String) = XposedHelpers.findClass(name, loader)
        private fun method(owner: Class<*>, name: String, vararg parameters: Class<*>) =
            owner.getDeclaredMethod(name, *parameters).apply { isAccessible = true }
        private val int = Int::class.javaPrimitiveType!!
        private val bool = Boolean::class.javaPrimitiveType!!
        private val service = type("com.android.server.om.OverlayManagerService")
        private val implementation = type("com.android.server.om.OverlayManagerServiceImpl")
        private val settingType = type("com.android.server.om.OverlayManagerSettings")
        private val infoType = type("android.content.om.OverlayInfo")
        private val idmaps = type("com.android.server.om.IdmapManager")
        private val identifierType = type("android.content.om.OverlayIdentifier")
        private val critical = type("android.content.om.CriticalOverlayInfo")
        private val internal = type("android.os.FabricatedOverlayInternal")
        private val entry = type("android.os.FabricatedOverlayInternalEntry")
        private val builder = type("android.content.om.FabricatedOverlay\$Builder")
        private val overlay = type("android.content.om.FabricatedOverlay")
        val serviceManager = type("android.os.ServiceManager")
        val ioThread = type("com.android.server.IoThread")
        val id = identifierType.getConstructor(String::class.java, String::class.java).newInstance("android", overlayName)
        private val packageName = method(identifierType, "getPackageName")
        private val name = method(identifierType, "getOverlayName")
        val identifier = method(critical, "getOverlayIdentifier")
        val impl = XposedHelpers.findField(service, "mImpl")
        val settings = XposedHelpers.findField(service, "mSettings")
        val implSettings = XposedHelpers.findField(implementation, "mSettings")
        private val idmapManager = XposedHelpers.findField(implementation, "mIdmapManager")
        private val basePath = XposedHelpers.findField(infoType, "baseCodePath")
        private val removeIdmap = method(idmaps, "removeIdmap", infoType, int)
        private val idmapExists = method(idmaps, "idmapExists", infoType)
        private val deleteFabricated = method(idmaps, "deleteFabricatedOverlay", String::class.java)
        private val usedPaths = method(settingType, "getAllBaseCodePaths")
        val lock = XposedHelpers.findField(service, "mLock")
        val updateState = method(implementation, "updateState", critical, int, int)
        val onStart = method(service, "onStart")
        val updateTargets = method(service, "updateTargetPackagesLocked", Set::class.java)
        val register = method(implementation, "registerFabricatedOverlay", internal)
        val unregister = method(implementation, "unregisterFabricatedOverlay", identifierType)
        val getNullableInfo = method(settingType, "getNullableOverlayInfo", identifierType, int)
        val setEnabled = method(settingType, "setEnabled", identifierType, int, bool)
        val getState = method(settingType, "getState", identifierType, int)
        val setState = method(settingType, "setState", identifierType, int, int)
        val highest = method(settingType, "setHighestPriority", identifierType, int)
        private val userPackage = type("android.content.pm.UserPackage")
        val targetPackage = XposedHelpers.findField(userPackage, "packageName")
        val targetUser = XposedHelpers.findField(userPackage, "userId")
        private val constructor = builder.getConstructor(String::class.java, String::class.java, String::class.java)
        private val setValue = method(builder, "setResourceValue", String::class.java, int, int)
        private val build = method(builder, "build")
        private val contents = overlay.declaredFields.single { it.type == internal }.apply { isAccessible = true }
        private val entries = XposedHelpers.findField(internal, "entries")
        private val dataType = XposedHelpers.findField(entry, "dataType")

        fun isOurs(value: Any) = packageName.invoke(value) == "android" && name.invoke(value) == overlayName
        fun retire(impl: Any, settings: Any, previous: Any): Boolean {
            val path = basePath.get(previous) as String
            // Mirror native stale-cache retirement, restricted to this definition's old path.
            // Never remove a referenced path, another overlay, or a caller-supplied filename.
            val manager = idmapManager.get(impl)
            return retireOwnedPath(path, usedPaths.invoke(settings) as Set<*>,
                removeIdmap = { removeIdmap.invoke(manager, previous, 0) == true },
                idmapExists = { idmapExists.invoke(manager, previous) == true },
                delete = { deleteFabricated.invoke(manager, path) == true }, exists = { File(path).exists() })
        }
        fun fabricate(dp: Int): Any {
            val instance = constructor.newInstance("android", overlayName, "android")
            require(dp in 1..0x7fffff)
            val complex = (dp shl 8) or TypedValue.COMPLEX_UNIT_DIP // Signed 23.0 mantissa, dip unit.
            for (dimension in dimensions) setValue.invoke(instance, "android:dimen/$dimension", TypedValue.TYPE_INT_DEC, complex)
            // This ROM's public builder accepts integers only. Change the newly built entries,
            // never a framework static or shared object, to the actual dimension wire type.
            val result = contents.get(build.invoke(instance))
            for (value in entries.get(result) as List<*>) dataType.setInt(value, TypedValue.TYPE_DIMENSION)
            return result
        }
    }
}
