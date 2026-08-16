package tv.withaibuild.customiuizer.mods

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources
import android.os.PowerManager
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.util.ArrayList

/**
 * SystemUI hooks that belong to no larger surface.
 *
 * The status bar, control centre, lock screen, notification shade and screenshot
 * hooks live in their own `SystemUI*Hooks` objects. What remains here is the power
 * menu and the charge animation; neither surface shares runtime state.
 */
object SystemUI {

    @JvmStatic
    fun ExtendedPowerMenuHook(lpparam: PackageReadyParam) {
        val fastbootTitleId = MainModule.resHooks.addFakeResource("epm_fastboot_title", R.string.system_epm_action_fastboot_title, "string")
        val recoveryTitleId = MainModule.resHooks.addFakeResource("epm_recovery_title", R.string.system_epm_action_recovery_title, "string")

        var actionId = -1
        val DialogClass = XposedHelpers.findClass("com.android.systemui.globalactions.GlobalActionsDialogLite", lpparam.classLoader)
        ModuleHelper.findAndHookConstructor("com.android.systemui.globalactions.GlobalActionsDialogLite\$SinglePressAction", lpparam.classLoader, DialogClass, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                if (actionId == 1) {
                    param.getArgs()[2] = fastbootTitleId
                } else if (actionId == 2) {
                    param.getArgs()[2] = recoveryTitleId
                }
            }
            override fun after(param: AfterHookCallback) {
                actionId = -1
            }
        })
        val PowerActionClass = XposedHelpers.findClass("com.android.systemui.globalactions.GlobalActionsDialogLite\$PowerOptionsAction", lpparam.classLoader)
        ModuleHelper.findAndHookMethod("com.android.systemui.globalactions.GlobalActionsDialogLite", lpparam.classLoader, "createActionItems", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mItems = XposedHelpers.getObjectField(param.getThisObject(), "mItems") as ArrayList<Any>
                actionId = 1
                val fastbootAction = XposedHelpers.newInstance(PowerActionClass, param.getThisObject())
                actionId = 2
                val recoveryAction = XposedHelpers.newInstance(PowerActionClass, param.getThisObject())
                mItems.add(fastbootAction)
                mItems.add(recoveryAction)
            }
        })

        ModuleHelper.findAndHookMethod(PowerActionClass, "onPress", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val mMessageResId = XposedHelpers.getIntField(param.getThisObject(), "mMessageResId")
                if (mMessageResId == fastbootTitleId || mMessageResId == recoveryTitleId) {
                    val actionsDialog = XposedHelpers.getSurroundingThis(param.getThisObject())
                    val mContext = XposedHelpers.getObjectField(actionsDialog, "mContext") as Context
                    val modRes = ModuleHelper.getModuleRes(mContext)
                    val SystemUIDialogClass = XposedHelpers.findClass("com.android.systemui.statusbar.phone.SystemUIDialog", lpparam.classLoader)
                    val confirmDlg = XposedHelpers.newInstance(SystemUIDialogClass, mContext) as AlertDialog
                    confirmDlg.setTitle(
                        modRes.getString(
                            if (mMessageResId == recoveryTitleId) R.string.system_epm_action_recovery_confirm_title else R.string.system_epm_action_fastboot_confirm_title
                        )
                    )
                    confirmDlg.setButton(-1, Resources.getSystem().getString(android.R.string.ok), DialogInterface.OnClickListener { _, _ ->
                        ModuleHelper.guarded {
                            val pm = mContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                            val mService = XposedHelpers.getObjectField(pm, "mService")
                            if (mMessageResId == recoveryTitleId) {
                                XposedHelpers.callMethod(mService, "reboot", false, "recovery", false)
                            } else {
                                XposedHelpers.callMethod(mService, "reboot", false, "bootloader", false)
                            }
                        }
                    })
                    confirmDlg.setButton(-2, Resources.getSystem().getString(android.R.string.cancel), DialogInterface.OnClickListener { _, _ -> ModuleHelper.guarded { } })
                    confirmDlg.show()
                    param.returnAndSkip(null)
                }
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.plugins.PluginEnablerImpl", lpparam.classLoader, "isEnabled", ComponentName::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val componentName = param.getArg(0) as ComponentName
                if (componentName.className.contains("GlobalActions")) {
                    param.returnAndSkip(false)
                }
            }
        })
    }

    @JvmStatic
    fun NoLightUpOnChargeHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.charge.MiuiChargeController", lpparam.classLoader, "shouldShowChargeAnim", HookerClassHelper.returnConstant(false))
    }

}
