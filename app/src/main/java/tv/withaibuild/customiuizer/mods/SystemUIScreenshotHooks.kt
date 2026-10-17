package tv.withaibuild.customiuizer.mods

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.SurfaceControl
import android.view.View
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.ResourceHooks
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/**
 * Hooks that hide UI while a screenshot is being captured.
 * The receiver binds to a view's attach state so it unregisters with the view
 * rather than outliving it.
 */
object SystemUIScreenshotHooks {

    private val screenshotReceiverTag = ResourceHooks.getFakeResId("screenshot_visibility_receiver")

    private const val SCREENSHOT_ACTION = "miui.intent.TAKE_SCREENSHOT"

    @JvmStatic
    fun TempHideOverlaySystemUIHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.android.wm.shell.pip.PipTaskOrganizer", lpparam.classLoader, "onTaskAppeared", object : MethodHook() {
            private var isActListened = false
            override fun after(param: AfterHookCallback) {
                val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                if (!isActListened) {
                    isActListened = true
                    val intentFilter = IntentFilter()
                    intentFilter.addAction("miui.intent.TAKE_SCREENSHOT")
                    val thisObject = param.getThisObject()
                    ModuleHelper.registerModuleReceiver(mContext, "pipScreenshotReceiver", object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                            val action = intent.action ?: return@guarded
                            if (action == "miui.intent.TAKE_SCREENSHOT") {
                                val state = intent.getBooleanExtra("IsFinished", true)
                                val mState = XposedHelpers.getObjectField(thisObject, "mPipTransitionState")
                                val isPip = XposedHelpers.callMethod(mState, "isInPip") as Boolean
                                if (isPip) {
                                    val mSurfaceControlTransactionFactory = XposedHelpers.getObjectField(thisObject, "mSurfaceControlTransactionFactory")
                                    val transaction = XposedHelpers.callMethod(mSurfaceControlTransactionFactory, "getTransaction") as SurfaceControl.Transaction
                                    val mLeash = XposedHelpers.getObjectField(thisObject, "mLeash") as SurfaceControl
                                    transaction.setVisibility(mLeash, state)
                                    transaction.apply()
                                }
                            }
                        }
                    }, intentFilter, Context.RECEIVER_EXPORTED)
                }
            }
        })
    }

    private class ScreenshotVisibilityReceiver(
        private val view: View,
        private val restorePreviousVisibility: Boolean
    ) : BroadcastReceiver(), View.OnAttachStateChangeListener {
        private var registered = false
        private var visibleState = View.VISIBLE

        fun bind() {
            if (registered) return
            view.context.registerReceiver(
                this,
                IntentFilter(SCREENSHOT_ACTION),
                Context.RECEIVER_EXPORTED
            )
            registered = true
            view.addOnAttachStateChangeListener(this)
        }

        override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
            if (intent.action != SCREENSHOT_ACTION) return@guarded
            val finished = intent.getBooleanExtra("IsFinished", true)
            if (restorePreviousVisibility) {
                if (!finished) visibleState = view.visibility
                view.visibility = if (finished) visibleState else View.INVISIBLE
            } else {
                view.visibility = if (finished) View.VISIBLE else View.INVISIBLE
            }
        }

        override fun onViewAttachedToWindow(v: View) = ModuleHelper.guarded {
            if (registered) return@guarded
            v.context.registerReceiver(
                this,
                IntentFilter(SCREENSHOT_ACTION),
                Context.RECEIVER_EXPORTED
            )
            registered = true
        }

        override fun onViewDetachedFromWindow(v: View) = ModuleHelper.guarded {
            if (!registered) return@guarded
            registered = false
            v.context.unregisterReceiver(this)
        }
    }

    private fun bindScreenshotVisibility(view: View, restorePreviousVisibility: Boolean) {
        if (view.getTag(screenshotReceiverTag) is ScreenshotVisibilityReceiver) return
        val receiver = ScreenshotVisibilityReceiver(view, restorePreviousVisibility)
        receiver.bind()
        view.setTag(screenshotReceiverTag, receiver)
    }

    @JvmStatic
    fun HideStatusBarWhenCaptureHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment", lpparam.classLoader, "onViewCreated", View::class.java, Bundle::class.java, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val view = param.getArgs()[0] as View
                bindScreenshotVisibility(view, false)
            }
        })
    }

    @JvmStatic
    fun HideNavBarBeforeScreenshotHook(lpparam: PackageReadyParam) {
        val hideNavHook = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val view = XposedHelpers.getObjectField(param.getThisObject(), "mView") as View
                bindScreenshotVisibility(view, true)
            }
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.navigationbar.NavigationBar", lpparam.classLoader, "onInit", hideNavHook)
    }

}
