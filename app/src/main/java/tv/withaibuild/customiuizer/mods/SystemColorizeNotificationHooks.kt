package tv.withaibuild.customiuizer.mods

import android.app.Notification
import android.app.WallpaperColors
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.HookUtils

object SystemColorizeNotificationHooks {
    private data class Snapshot(val mode: Int = 1, val apps: Set<String> = emptySet()) {
        fun includes(pkg: String) = (mode == 2 && pkg !in apps) || (mode == 3 && pkg in apps)
    }
    @Volatile private var snapshot = Snapshot()
    private var observerRegistered = false
    private const val PALETTE = "a14NotificationPalette"

    private fun refresh() {
        val prefs = MainModule.mPrefs
        snapshot = Snapshot(prefs.getStringAsInt("system_colorizenotifs", 1), HashSet(prefs.getStringSet("system_colorizenotifs_apps")))
    }

    private fun installSnapshot() {
        refresh()
        if (observerRegistered) return
        observerRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null || key == "system_colorizenotifs" || key == "system_colorizenotifs_apps") refresh()
            }
        })
    }

    private fun palette(n: Notification, context: Context): NotificationPalette? {
        val info = n.extras.getParcelable("android.appInfo", ApplicationInfo::class.java) ?: return null
        if (!snapshot.includes(info.packageName)) return null
        return (XposedHelpers.getAdditionalInstanceField(n, PALETTE) as? NotificationPaletteCache)
            ?.current(info.uid, info.packageName, context.resources.configuration.isNightModeActive)
    }

    @JvmStatic
    fun ColorizeNotificationCardHook(lpparam: PackageReadyParam) {
        installSnapshot()
        val loader = lpparam.classLoader
        val scheme = XposedHelpers.findClass("com.android.systemui.monet.ColorScheme", loader)
        val style = XposedHelpers.findClass("com.android.systemui.monet.Style", loader)
        val contentStyle = checkNotNull(style.enumConstants) { "Monet Style is not an enum" }
            .singleOrNull { (it as Enum<*>).name == "CONTENT" }
            ?: error("Monet Style is missing CONTENT")
        val schemeConstructor = XposedHelpers.findConstructorExact(scheme, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, style)
        val accent = XposedHelpers.findField(scheme, "accent1")
        val neutral1 = XposedHelpers.findField(scheme, "neutral1")
        val neutral2 = XposedHelpers.findField(scheme, "neutral2")
        val shades = XposedHelpers.findField(accent.type, "allShades")
        val builderClass = Notification.Builder::class.java
        val builderNotification = XposedHelpers.findField(builderClass, "mN")
        val params = XposedHelpers.findField(builderClass, "mParams")
        val colors = XposedHelpers.findField(builderClass, "mColors")
        val getColors = XposedHelpers.findMethodExact(builderClass, "getColors", params.type)
        val reset = XposedHelpers.findMethodExact(params.type, "reset", *emptyArray<Any>())
        val protection = XposedHelpers.findField(colors.type, "mProtectionColor")
        val primary = XposedHelpers.findField(colors.type, "mPrimaryTextColor")
        val secondary = XposedHelpers.findField(colors.type, "mSecondaryTextColor")
        val colorized = XposedHelpers.findMethodExact(Notification::class.java, "isColorized", *emptyArray<Any>())
        val media = XposedHelpers.findMethodExact(Notification::class.java, "isMediaNotification", *emptyArray<Any>())
        val helper = XposedHelpers.findClass("com.android.systemui.statusbar.notification.NotificationSettingsHelper", loader)
        val miuiStyle = XposedHelpers.findMethodExact(helper, "showMiuiStyle", *emptyArray<Any>())
        val rowClass = XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", loader)
        val rowEntry = XposedHelpers.findField(rowClass, "mEntry")
        val sbn = XposedHelpers.findField(rowEntry.type, "mSbn")
        val notificationColor = XposedHelpers.findField(rowClass, "mNotificationColor")
        val tint = XposedHelpers.findField(rowClass, "mCurrentBackgroundTint")
        val setTint = XposedHelpers.findMethodExact(XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.ActivatableNotificationView", loader), "setBackgroundTintColor", Int::class.javaPrimitiveType!!)
        fun rowPalette(row: View): NotificationPalette? {
            val entry = rowEntry.get(row) ?: return null
            val posted = sbn.get(entry) as? android.service.notification.StatusBarNotification ?: return null
            return palette(posted.notification, row.context)
        }
        fun apply(builder: Any, palette: NotificationPalette) {
            val p = params.get(builder)
            getColors.invoke(builder, p)
            val target = colors.get(builder)
            protection.setInt(target, palette.protection)
            primary.setInt(target, palette.primary)
            secondary.setInt(target, palette.secondary)
        }
        ModuleHelper.findAndHookConstructor("android.app.Notification\$Builder", loader, Context::class.java, Notification::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                ModuleHelper.guarded {
                    val n = chain.getArg(1) as? Notification ?: return@guarded
                    palette(n, chain.getArg(0) as Context)?.let { apply(chain.thisObject!!, it) }
                }
                return result
            }
        })
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.row.MiuiExpandableNotificationRow", loader,
            "updateBlurBg", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val custom = ModuleHelper.guarded(false) { rowPalette(chain.thisObject as View) != null }
                    if (!custom) return chain.proceed()
                    val args = XposedHelpers.getArgsArray(chain)
                    args[2] = false
                    return chain.proceed(args)
                }
            })
        ModuleHelper.findAndHookMethod(rowClass, "onNotificationUpdated", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                ModuleHelper.guarded {
                    val row = chain.thisObject as View
                    val palette = rowPalette(row) ?: return@guarded
                    notificationColor.setInt(row, palette.secondary)
                    if (tint.getInt(row) != palette.tint) setTint.invoke(row, palette.tint)
                }
                return result
            }
        })
        // Native setTint(0) and wrapper summary-background rules must remain active on Row reuse.
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.row.NotificationContentView", loader,
            "updateAllSingleLineViews", object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    ModuleHelper.guarded {
                        val content = chain.thisObject as View
                        val entry = XposedHelpers.getObjectField(content, "mNotificationEntry") ?: return@guarded
                        val notification = (sbn.get(entry) as android.service.notification.StatusBarNotification).notification
                        val palette = palette(notification, content.context) ?: return@guarded
                        val singleLine = XposedHelpers.getObjectField(content, "mSingleLineView") ?: return@guarded
                        (XposedHelpers.getObjectField(singleLine, "mTitleView") as TextView).setTextColor(palette.primary)
                        (XposedHelpers.getObjectField(singleLine, "mTextView") as TextView).setTextColor(palette.secondary)
                    }
                    return result
                }
            })
        val inflater = XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.NotificationContentInflaterInjector", loader)
        var icons: Any? = null // Native process-scoped manager, never a View or short-lived controller.
        ModuleHelper.findAndHookMethod(inflater, "handle3thThemeColor", builderClass, Context::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val handled = ModuleHelper.guarded(false) {
                    val builder = chain.getArg(0) as Notification.Builder
                    val n = builderNotification.get(builder) as Notification
                    val info = n.extras.getParcelable("android.appInfo", ApplicationInfo::class.java)
                    if (info == null || !snapshot.includes(info.packageName) || colorized.invoke(n) == true || media.invoke(n) == true) {
                        XposedHelpers.removeAdditionalInstanceField(n, PALETTE)
                        return@guarded false
                    }
                    val context = chain.getArg(1) as Context
                    val iconManager = icons ?: ModuleHelper.getDepInstance(loader, "com.miui.systemui.graphics.AppIconsManager").also { icons = it }
                    val bitmap = XposedHelpers.callMethod(iconManager, "getAppIconBitmap", info.uid / 100000, info.packageName) as Bitmap
                    val dark = context.resources.configuration.isNightModeActive
                    val cache = XposedHelpers.getAdditionalInstanceField(n, PALETTE) as? NotificationPaletteCache ?: NotificationPaletteCache()
                    val palette = cache.resolve(bitmap, info.uid, info.packageName, dark) {
                        val wallpaper = WallpaperColors.fromBitmap(bitmap)
                        var seed = wallpaper.primaryColor.toArgb()
                        if (Color.luminance(seed) > 0.9) seed = wallpaper.secondaryColor?.toArgb() ?: seed
                        val cs = schemeConstructor.newInstance(seed, dark, contentStyle)
                        @Suppress("UNCHECKED_CAST") val a = shades.get(accent.get(cs)) as List<Int>
                        @Suppress("UNCHECKED_CAST") val n1 = shades.get(neutral1.get(cs)) as List<Int>
                        @Suppress("UNCHECKED_CAST") val n2 = shades.get(neutral2.get(cs)) as List<Int>
                        val background = a[if (dark) 5 else 6]
                        NotificationPalette(ColorUtils.blendARGB(n1[1], background, 0.7f), n1[if (dark) 1 else 10], n2[if (dark) 3 else 8], background)
                    }
                    reset.invoke(params.get(builder))
                    apply(builder, palette)
                    XposedHelpers.setAdditionalInstanceField(n, PALETTE, cache)
                    true
                }
                return if (handled) null else chain.proceed()
            }
        })
        val progress = XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.NotificationContentInflater\$InflationProgress", loader)
        val remoteFields = arrayOf("newPublicView", "newContentView", "newExpandedView").map { XposedHelpers.findField(progress, it) }
        var titleId = 0
        var textId = 0
        ModuleHelper.findAndHookMethod(inflater, "createRemoteViews", Int::class.javaPrimitiveType!!, builderClass,
            Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, Context::class.java, Context::class.java, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    ModuleHelper.guarded {
                        if (result == null) return@guarded
                        val builder = chain.getArg(1) as Notification.Builder
                        val n = builderNotification.get(builder) as Notification
                        val context = chain.getArg(6) as Context
                        val palette = palette(n, context) ?: return@guarded
                        if (media.invoke(n) == true) return@guarded
                        if (miuiStyle.invoke(null) != true && !n.extras.getBoolean("miui_unimportant", false)) return@guarded
                        val style = builder.style
                        if (style != null && style !is Notification.BigPictureStyle && style !is Notification.BigTextStyle && style !is Notification.InboxStyle) return@guarded
                        if (titleId == 0 || textId == 0) {
                            titleId = HookUtils.getResId(context.resources, "title", "id", "com.android.systemui")
                            textId = HookUtils.getResId(context.resources, "text", "id", "com.android.systemui")
                        }
                        if (titleId == 0 || textId == 0) return@guarded
                        for (field in remoteFields) (field.get(result) as? RemoteViews)?.let {
                            it.setTextColor(titleId, palette.primary)
                            it.setTextColor(textId, palette.secondary)
                        }
                    }
                    return result
                }
            })
    }
}
