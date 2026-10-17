package tv.withaibuild.customiuizer.mods

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Parcel
import android.provider.Settings
import android.util.ArrayMap
import android.view.View
import android.widget.Switch
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import miui.telephony.TelephonyManager
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.DeviceInfoMonitor
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap

@Suppress("MemberVisibilityCanBePrivate")
object SystemUIMonitorAndTileHooks {

    @JvmStatic
    fun MonitorDeviceInfoHook(lpparam: PackageReadyParam, mPrefs: PrefMap) {
        DeviceInfoMonitor.hook(lpparam, mPrefs)
    }

    @JvmStatic
    @SuppressLint("DiscouragedApi")
    fun AddCustomTileHook(lpparam: PackageReadyParam) {
        val enable5G = MainModule.mPrefs.getBoolean("system_fivegtile")
        val enableFps = MainModule.mPrefs.getBoolean("system_cc_fpstile")
        val enableFloatingTime = MainModule.mPrefs.getBoolean("system_cc_floatingtimetile")
        ModuleHelper.findAndHookMethod("com.android.systemui.SystemUIApplication", lpparam.classLoader, "onCreate", object : MethodHook() {
            private var isListened = false
            override fun after(param: AfterHookCallback) {
                if (!isListened) {
                    isListened = true
                    val mContext = XposedHelpers.callMethod(param.getThisObject(), "getApplicationContext") as Context
                    val stockTilesResId = mContext.resources.getIdentifier("miui_quick_settings_tiles_stock", "string", lpparam.packageName)
                    val stockTiles = mContext.getString(stockTilesResId)
                    val sb = StringBuilder(stockTiles)
                    if (enable5G) sb.append(",custom_5G")
                    if (enableFps) sb.append(",custom_FPS")
                    if (enableFloatingTime) sb.append(",custom_floatingtime")
                    MainModule.resHooks.setObjectReplacement("com.android.systemui", "string", "miui_quick_settings_tiles_stock", sb.toString())
                }
            }
        })
        val ResourceIconClass = XposedHelpers.findClass("com.android.systemui.qs.tileimpl.QSTileImpl\$ResourceIcon", lpparam.classLoader)
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.MiuiQSFactory", lpparam.classLoader, "createTile", String::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val tileName = param.getArg(0) as String
                if (tileName.startsWith("custom_")) {
                    val nfcField = "nfcTileProvider"
                    val provider = XposedHelpers.getObjectField(param.getThisObject(), nfcField)
                    val tile = XposedHelpers.callMethod(provider, "get")
                    XposedHelpers.setAdditionalInstanceField(tile, "customName", tileName)
                    XposedHelpers.callMethod(tile, "handleInitialize")
                    XposedHelpers.callMethod(tile, "handleStale")
                    param.returnAndSkip(tile)
                }
            }
        })
        val NfcTileCls = "com.android.systemui.qs.tiles.MiuiNfcTile"
        ModuleHelper.findAndHookMethod(NfcTileCls, lpparam.classLoader, "isAvailable", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val tileName = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "customName") as String?
                if (tileName != null) {
                    when (tileName) {
                        "custom_5G" -> param.returnAndSkip(enable5G)
                        "custom_FPS" -> param.returnAndSkip(enableFps)
                        "custom_floatingtime" -> param.returnAndSkip(enableFloatingTime)
                        else -> param.returnAndSkip(false)
                    }
                }
            }
        })
        ModuleHelper.findAndHookMethod(NfcTileCls, lpparam.classLoader, "getTileLabel", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val tileName = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "customName") as String?
                if (tileName != null) {
                    val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                    val modRes = ModuleHelper.getModuleRes(mContext)
                    when (tileName) {
                        "custom_5G" -> param.returnAndSkip(modRes.getString(R.string.qs_toggle_5g))
                        "custom_FPS" -> param.returnAndSkip(modRes.getString(R.string.qs_toggle_fps))
                        "custom_floatingtime" -> param.returnAndSkip(modRes.getString(R.string.qs_toggle_floatingtime))
                    }
                }
            }
        })
        ModuleHelper.findAndHookMethod(NfcTileCls, lpparam.classLoader, "handleSetListening", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val tileName = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "customName") as String?
                if (tileName != null) {
                    val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                    val mListening = param.getArg(0) as Boolean
                    when (tileName) {
                        "custom_5G" -> {
                            val resolver = mContext.contentResolver
                            if (mListening) {
                                val contentObserver = object : ContentObserver(Handler(mContext.mainLooper)) {
                                    override fun onChange(selfChange: Boolean) = ModuleHelper.guarded {
                                        XposedHelpers.callMethod(param.getThisObject(), "refreshState")
                                    }
                                }
                                resolver.registerContentObserver(Settings.Global.getUriFor("fiveg_user_enable"), false, contentObserver)
                                resolver.registerContentObserver(Settings.Global.getUriFor("dual_nr_enabled"), false, contentObserver)
                                ModuleHelper.replaceModuleRegistration("custom_5G_tile_listener") { resolver.unregisterContentObserver(contentObserver) }
                            } else {
                                ModuleHelper.replaceModuleRegistration("custom_5G_tile_listener") {}
                            }
                        }
                        "custom_FPS" -> {
                            if (mListening) {
                                val ServiceManager = XposedHelpers.findClass("android.os.ServiceManager", lpparam.classLoader)
                                val mSurfaceFlinger = XposedHelpers.callStaticMethod(ServiceManager, "getService", "SurfaceFlinger")
                                XposedHelpers.setAdditionalInstanceField(param.getThisObject(), "mSurfaceFlinger", mSurfaceFlinger)
                            } else {
                                XposedHelpers.removeAdditionalInstanceField(param.getThisObject(), "mSurfaceFlinger")
                            }
                        }
                        "custom_floatingtime" -> {
                            val resolver = mContext.contentResolver
                            if (mListening) {
                                val contentObserver = object : ContentObserver(Handler(mContext.mainLooper)) {
                                    override fun onChange(selfChange: Boolean) = ModuleHelper.guarded {
                                        XposedHelpers.callMethod(param.getThisObject(), "refreshState")
                                    }
                                }
                                resolver.registerContentObserver(Settings.System.getUriFor("miui_time_floating_window"), false, contentObserver)
                                ModuleHelper.replaceModuleRegistration("custom_floatingtime_tile_listener") { resolver.unregisterContentObserver(contentObserver) }
                            } else {
                                ModuleHelper.replaceModuleRegistration("custom_floatingtime_tile_listener") {}
                            }
                        }
                    }
                    param.returnAndSkip(null)
                }
            }
        })
        ModuleHelper.findAndHookMethod(NfcTileCls, lpparam.classLoader, "handleShowStateMessage", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val tileName = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "customName") as String?
                if (tileName != null) {
                    param.returnAndSkip(null)
                }
            }
        })
        ModuleHelper.findAndHookMethod(NfcTileCls, lpparam.classLoader, "getLongClickIntent", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val tileName = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "customName") as String?
                if (tileName == "custom_5G") {
                    val intent = Intent(Intent.ACTION_MAIN)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    intent.component = ComponentName("com.android.phone", "com.android.phone.settings.MiuiFiveGNetworkSetting")
                    param.returnAndSkip(intent)
                } else if (tileName != null) {
                    param.returnAndSkip(null)
                }
            }
        })
        ModuleHelper.findAndHookMethod(NfcTileCls, lpparam.classLoader, "handleClick", View::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val tileName = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "customName") as String?
                if (tileName != null) {
                    when (tileName) {
                        "custom_5G" -> {
                            val manager = TelephonyManager.getDefault()
                            manager.setUserFiveGEnabled(!manager.isUserFiveGEnabled())
                        }
                        "custom_FPS" -> {
                            val mSurfaceFlinger = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "mSurfaceFlinger") as IBinder?
                            if (mSurfaceFlinger != null) {
                                val mState = XposedHelpers.getObjectField(param.getThisObject(), "mState")
                                val enabled = XposedHelpers.getBooleanField(mState, "value")
                                val obtain = Parcel.obtain()
                                obtain.writeInterfaceToken("android.ui.ISurfaceComposer")
                                obtain.writeInt(if (enabled) 0 else 1)
                                mSurfaceFlinger.transact(1034, obtain, null, 0)
                                obtain.recycle()
                                XposedHelpers.callMethod(param.getThisObject(), "refreshState")
                            }
                        }
                        "custom_floatingtime" -> {
                            val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                            val isEnable = (XposedHelpers.callStaticMethod(Settings.System::class.java, "getIntForUser", mContext.contentResolver, "miui_time_floating_window", 0, -2) as Int) != 0
                            XposedHelpers.callStaticMethod(Settings.System::class.java, "putIntForUser", mContext.contentResolver, "miui_time_floating_window", if (isEnable) 0 else 1, -2)
                        }
                    }
                    param.returnAndSkip(null)
                }
            }
        })

        val tileOnResMap = ArrayMap<String, Int>()
        val tileOffResMap = ArrayMap<String, Int>()
        if (enable5G) {
            tileOnResMap["custom_5G"] = MainModule.resHooks.addFakeResource("ic_qs_m5g_on", R.drawable.ic_qs_5g_on, "drawable")
            tileOffResMap["custom_5G"] = MainModule.resHooks.addFakeResource("ic_qs_m5g_off", R.drawable.ic_qs_5g_off, "drawable")
        }
        if (enableFps) {
            tileOnResMap["custom_FPS"] = MainModule.resHooks.addFakeResource("ic_qs_mfps_on", R.drawable.ic_qs_fps_on, "drawable")
            tileOffResMap["custom_FPS"] = MainModule.resHooks.addFakeResource("ic_qs_mfps_off", R.drawable.ic_qs_fps_off, "drawable")
        }
        if (enableFloatingTime) {
            tileOnResMap["custom_floatingtime"] = MainModule.resHooks.addFakeResource("ic_qs_mfloatingtime_on", R.drawable.ic_qs_second_off, "drawable")
            tileOffResMap["custom_floatingtime"] = MainModule.resHooks.addFakeResource("ic_qs_mfloatingtime_off", R.drawable.ic_qs_second_on, "drawable")
        }
        ModuleHelper.hookAllMethods(NfcTileCls, lpparam.classLoader, "handleUpdateState", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val tileName = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "customName") as String?
                if (tileName != null) {
                    var isEnable = false
                    when (tileName) {
                        "custom_5G" -> {
                            val manager = TelephonyManager.getDefault()
                            isEnable = manager.isUserFiveGEnabled()
                        }
                        "custom_FPS" -> {
                            val mSurfaceFlinger = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "mSurfaceFlinger") as IBinder?
                            if (mSurfaceFlinger != null) {
                                val obtain = Parcel.obtain()
                                val obtain2 = Parcel.obtain()
                                obtain.writeInterfaceToken("android.ui.ISurfaceComposer")
                                obtain.writeInt(2)
                                mSurfaceFlinger.transact(1034, obtain, obtain2, 0)
                                isEnable = obtain2.readBoolean()
                                obtain2.recycle()
                                obtain.recycle()
                            }
                        }
                        "custom_floatingtime" -> {
                            val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                            isEnable = (XposedHelpers.callStaticMethod(Settings.System::class.java, "getIntForUser", mContext.contentResolver, "miui_time_floating_window", 0, -2) as Int) != 0
                        }
                    }
                    if (tileName.startsWith("custom_")) {
                        val booleanState = param.getArg(0)
                        XposedHelpers.setObjectField(booleanState, "value", isEnable)
                        XposedHelpers.setObjectField(booleanState, "state", if (isEnable) 2 else 1)
                        val tileLabel = XposedHelpers.callMethod(param.getThisObject(), "getTileLabel") as String
                        XposedHelpers.setObjectField(booleanState, "label", tileLabel)
                        XposedHelpers.setObjectField(booleanState, "contentDescription", tileLabel)
                        XposedHelpers.setObjectField(booleanState, "expandedAccessibilityClassName", Switch::class.java.name)
                        val iconResId = if (isEnable) tileOnResMap[tileName] else tileOffResMap[tileName]
                        val mIcon = XposedHelpers.callStaticMethod(ResourceIconClass, "get", iconResId)
                        XposedHelpers.setObjectField(booleanState, "icon", mIcon)
                    }
                    param.returnAndSkip(null)
                }
            }
        })
    }
}
