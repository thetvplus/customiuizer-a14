package tv.withaibuild.customiuizer.mods

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/** A14's existing fallback power menu. Only our two actions own a reboot confirmation. */
internal object ExtendedPowerMenu {
    internal enum class Target(val title: Int, val confirmation: Int, val reason: String) {
        FASTBOOT(R.string.system_epm_action_fastboot_title, R.string.system_epm_action_fastboot_confirm_title, "bootloader"),
        RECOVERY(R.string.system_epm_action_recovery_title, R.string.system_epm_action_recovery_confirm_title, "recovery")
    }
    private const val ACTION = "customiuizer_extended_power_action"
    private const val GENERATION = "customiuizer_extended_power_generation"
    private const val CONFIRMATION = "customiuizer_extended_power_confirmation"
    private data class Construction(val owner: Any, val title: Int)
    private val constructing = ThreadLocal<Construction>()
    private data class Lease(val owner: WeakReference<Any>, val items: WeakReference<MutableList<Any>>,
        val generation: Any, val target: Target)

    internal fun plugin(component: ComponentName) = component.packageName == "miui.systemui.plugin" &&
        component.className == "miui.systemui.globalactions.GlobalActionsPlugin"

    internal fun constructorTitle(owner: Any?): Int? = constructing.get()?.takeIf { it.owner === owner }?.title
    internal fun <T> construct(owner: Any, title: Int, factory: () -> T): T {
        val previous = constructing.get()
        constructing.set(Construction(owner, title))
        return try { factory() } finally {
            if (previous == null) constructing.remove() else constructing.set(previous)
        }
    }

    internal fun append(owner: Any, items: MutableList<Any>, factory: (Target) -> Any) {
        val existing = XposedHelpers.getAdditionalInstanceField(owner, GENERATION)
        if (items.any { (XposedHelpers.getAdditionalInstanceField(it, ACTION) as? Lease)?.let { lease ->
                lease.owner.get() === owner && lease.generation === existing } == true }) return
        // Prepare both before publishing either; a failed constructor leaves native items intact.
        val actions = Target.entries.map { target -> factory(target) to target }
        val generation = Any()
        for ((action, target) in actions) XposedHelpers.setAdditionalInstanceField(action, ACTION,
            Lease(WeakReference(owner), WeakReference(items), generation, target))
        @Suppress("UNCHECKED_CAST")
        (XposedHelpers.getAdditionalInstanceField(owner, CONFIRMATION) as? WeakReference<AlertDialog>)?.get()?.dismiss()
        XposedHelpers.setAdditionalInstanceField(owner, GENERATION, generation)
        items.addAll(actions.map { it.first })
    }

    private fun current(action: Any, lease: Lease): Boolean {
        val owner = lease.owner.get() ?: return false
        return XposedHelpers.getAdditionalInstanceField(owner, GENERATION) === lease.generation &&
            lease.items.get()?.any { it === action } == true
    }

    internal fun confirmation(context: Context, title: String, allowed: () -> Boolean,
        reboot: () -> Unit, create: (Context) -> AlertDialog): AlertDialog {
        val dialog = create(context)
        val consumed = AtomicBoolean()
        dialog.setTitle(title)
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(android.R.string.ok)) { _, _ ->
            ModuleHelper.guarded {
                if (dialog.isShowing && allowed() && consumed.compareAndSet(false, true)) reboot()
            }
        }
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, context.getString(android.R.string.cancel)) { _, _ -> ModuleHelper.guarded { consumed.set(true) } }
        dialog.setOnDismissListener { ModuleHelper.guarded { consumed.set(true) } }
        dialog.show()
        return dialog
    }

    fun install(loader: ClassLoader) {
        val dialogType = XposedHelpers.findClass("com.android.systemui.globalactions.GlobalActionsDialogLite", loader)
        val single = XposedHelpers.findClass("com.android.systemui.globalactions.GlobalActionsDialogLite\$SinglePressAction", loader)
        val power = XposedHelpers.findClass("com.android.systemui.globalactions.GlobalActionsDialogLite\$PowerOptionsAction", loader)
        val systemDialog = XposedHelpers.findClass("com.android.systemui.statusbar.phone.SystemUIDialog", loader)
        val create = dialogType.getDeclaredMethod("createActionItems")
        val press = power.getDeclaredMethod("onPress")
        val constructor = power.getDeclaredConstructor(dialogType).apply { isAccessible = true }
        val singleConstructor = single.getDeclaredConstructor(dialogType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val confirmConstructor = systemDialog.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
        val itemsField = XposedHelpers.findField(dialogType, "mItems")
        val contextField = XposedHelpers.findField(dialogType, "mContext")
        val messageField = XposedHelpers.findField(single, "mMessageResId")
        val ready = AtomicBoolean()
        val titles = mapOf(
            Target.FASTBOOT to MainModule.resHooks.addFakeResource("epm_fastboot_title", Target.FASTBOOT.title, "string"),
            Target.RECOVERY to MainModule.resHooks.addFakeResource("epm_recovery_title", Target.RECOVERY.title, "string"))
        val titleHook = ModuleHelper.findAndHookConstructor(singleConstructor.declaringClass.name, loader, dialogType,
            Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun before(callback: BeforeHookCallback) {
                if (callback.getThisObject()?.let { !power.isInstance(it) } == true) return
                constructorTitle(callback.getArg(0))?.let { callback.getArgs()[2] = it }
            }
        }) ?: return
        val pressHook = ModuleHelper.hookMethod(press, object : MethodHook() {
            override fun before(callback: BeforeHookCallback) {
                val action = callback.getThisObject() ?: return
                val lease = XposedHelpers.getAdditionalInstanceField(action, ACTION) as? Lease ?: return
                callback.returnAndSkip(null)
                ModuleHelper.guarded {
                    if (!ready.get() || !current(action, lease)) return@guarded
                    val owner = lease.owner.get() ?: return@guarded
                    @Suppress("UNCHECKED_CAST")
                    val previous = XposedHelpers.getAdditionalInstanceField(owner, CONFIRMATION) as? WeakReference<AlertDialog>
                    if (previous?.get()?.isShowing == true) return@guarded
                    val context = contextField.get(owner) as Context
                    val dialog = confirmation(context, ModuleHelper.getModuleRes(context).getString(lease.target.confirmation),
                        { current(action, lease) }, {
                            val manager = context.getSystemService(PowerManager::class.java)
                            val service = XposedHelpers.getObjectField(manager, "mService")
                            XposedHelpers.callMethod(service, "reboot", false, lease.target.reason, false)
                        }, { confirmConstructor.newInstance(it) as AlertDialog })
                    XposedHelpers.setAdditionalInstanceField(owner, CONFIRMATION, WeakReference(dialog))
                }
            }
        }) ?: return
        val createHook = ModuleHelper.hookMethod(create, object : MethodHook() {
            override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                if (!ready.get() || callback.getThrowable() != null) return@guarded
                val owner = callback.getThisObject() ?: return@guarded
                @Suppress("UNCHECKED_CAST")
                val items = itemsField.get(owner) as? MutableList<Any> ?: return@guarded
                append(owner, items) { target ->
                    val title = titles.getValue(target)
                    construct(owner, title) { constructor.newInstance(owner) }.also {
                        check(messageField.getInt(it) == title) { "Extended power action title was not accepted" }
                    }
                }
            }
        }) ?: return
        // Switching to the fallback UI is the final commit, after its complete action path exists.
        ModuleHelper.findAndHookMethod("com.android.systemui.plugins.PluginEnablerImpl", loader, "isEnabled",
            ComponentName::class.java, object : MethodHook() {
                override fun before(callback: BeforeHookCallback) {
                    if (ready.get() && (callback.getArg(0) as? ComponentName)?.let(::plugin) == true)
                        callback.returnAndSkip(false)
                }
            }) ?: return
        ready.set(true)
    }
}
