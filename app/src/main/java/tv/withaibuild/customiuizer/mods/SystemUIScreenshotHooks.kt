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
import java.lang.ref.WeakReference

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
            @Volatile private var owner = WeakReference<Any>(null)
            private var receiverContext = WeakReference<Context>(null)
            override fun after(param: AfterHookCallback) {
                if (param.getThrowable() != null) return
                val native = param.getThisObject() ?: return
                owner = WeakReference(native)
                val mContext = XposedHelpers.getObjectField(native, "mContext") as Context
                if (receiverContext.get() !== mContext) {
                    val intentFilter = IntentFilter()
                    intentFilter.addAction("miui.intent.TAKE_SCREENSHOT")
                    val registered = ModuleHelper.registerModuleReceiver(mContext, "pipScreenshotReceiver", object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                            val action = intent.action ?: return@guarded
                            if (action == "miui.intent.TAKE_SCREENSHOT") {
                                val thisObject = owner.get() ?: return@guarded
                                val state = intent.getBooleanExtra("IsFinished", true)
                                val shown = SystemPipActions.screenshotVisibility(thisObject, state)
                                val mState = XposedHelpers.getObjectField(thisObject, "mPipTransitionState")
                                val isPip = XposedHelpers.callMethod(mState, "isInPip") as Boolean
                                if (isPip) {
                                    val mSurfaceControlTransactionFactory = XposedHelpers.getObjectField(thisObject, "mSurfaceControlTransactionFactory")
                                    val transaction = XposedHelpers.callMethod(mSurfaceControlTransactionFactory, "getTransaction") as SurfaceControl.Transaction
                                    val mLeash = XposedHelpers.getObjectField(thisObject, "mLeash") as SurfaceControl
                                    transaction.use {
                                        it.setVisibility(mLeash, shown)
                                        it.apply()
                                    }
                                }
                            }
                        }
                    }, intentFilter, Context.RECEIVER_EXPORTED)
                    if (registered) receiverContext = WeakReference(mContext)
                }
            }
        })
    }

    private class ScreenshotVisibilityReceiver(
        private val view: View,
        private val restorePreviousVisibility: Boolean
    ) : BroadcastReceiver(), View.OnAttachStateChangeListener {
        private var registered = false
        private var hiding = false
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
            if (!registered || intent.action != SCREENSHOT_ACTION) return@guarded
            val finished = intent.getBooleanExtra("IsFinished", true)
            if (finished) {
                restoreVisibility()
            } else {
                if (hiding) return@guarded
                hiding = true
                if (restorePreviousVisibility) visibleState = view.visibility
                view.visibility = View.INVISIBLE
            }
        }

        private fun restoreVisibility() {
            if (!hiding) return
            hiding = false
            // Restore only the state this receiver wrote; preserve a later host change.
            if (view.visibility == View.INVISIBLE) {
                view.visibility = if (restorePreviousVisibility) visibleState else View.VISIBLE
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
            restoreVisibility()
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
                val view = param.getArg(0) as View
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
