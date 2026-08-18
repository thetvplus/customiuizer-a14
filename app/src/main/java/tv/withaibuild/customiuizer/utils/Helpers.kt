package tv.withaibuild.customiuizer.utils

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.LruCache
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import miui.util.HapticFeedbackUtil
import org.xmlpull.v1.XmlPullParser
import tv.withaibuild.customiuizer.BuildConfig
import tv.withaibuild.customiuizer.PrefsProvider
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.io.File
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

@Suppress("WeakerAccess")
object Helpers {

    // const, not @JvmField val: a const read is inlined at the call site, so the twelve hook-side
    // references to it no longer touch this class and cannot trigger its object initialiser.
    const val modulePkg = BuildConfig.APPLICATION_ID

    // public static final String versionFile = "xposed_version";
    // public static final String wallpaperFile = "lockscreen_wallpaper";

    const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    const val MIUIZER_NS = "http://schemas.android.com/apk/res-auto"

    const val ACCESS_SECURITY_CENTER = "com.miui.securitycenter.permission.ACCESS_SECURITY_CENTER_PROVIDER"

    const val NEW_MODS_SEARCH_QUERY = "\uD83C\uDD95"

    @JvmField
    var shareAppsList: ArrayList<AppData>? = null

    @JvmField
    var openWithAppsList: ArrayList<AppData>? = null

    @JvmField
    var launchableAppsList: ArrayList<AppData>? = null

    @JvmField
    val allModsList = ArrayList<ModData>()

    @JvmField
    val markColor = Color.rgb(205, 73, 97)

    @JvmField
    val markColorVibrant = Color.rgb(255, 0, 0)

    const val REQUEST_PERMISSIONS_WIFI = 3

    const val REQUEST_PERMISSIONS_REPORT = 4

    const val REQUEST_PERMISSIONS_BLUETOOTH = 5

    const val REQUEST_PERMISSIONS_SECURITY_CENTER = 6

    @Volatile
    @JvmField
    var withinAppContext = false

    @Volatile
    @JvmField
    var appContentResolver: ContentResolver? = null

    private val ICON_CACHE_KB = (
        Runtime.getRuntime().maxMemory() / 1024 / 16
        ).toInt().coerceIn(512, 8 * 1024)

    @JvmField
    val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(ICON_CACHE_KB) {
        override fun sizeOf(key: String, icon: Bitmap): Int {
            return icon.allocationByteCount / 1024
        }
    }

    @JvmField
    var showNewMods = true

    @JvmField
    val newMods = HashSet(listOf("pref_key_launcher_nozoomanim"))

    object MimeType {
        const val IMAGE = 1
        const val AUDIO = 2
        const val VIDEO = 4
        const val DOCUMENT = 8
        const val ARCHIVE = 16
        const val LINK = 32
        const val OTHERS = 64
        const val ALL = IMAGE or AUDIO or VIDEO or DOCUMENT or ARCHIVE or LINK or OTHERS
    }

    fun interface InputCallback {
        fun onInputFinished(key: String?, text: String?)
    }

    @JvmStatic
    fun setMiuiPrefItem(item: View?) {
        item ?: return
        item.setBackgroundResource(R.drawable.list_item_bg)
        val title = item.findViewById<TextView>(android.R.id.title)
        var resId = item.resources.getIdentifier("preference_item_bg", "drawable", "miui")
        if (resId != 0) item.setBackgroundResource(resId)
        resId = item.resources.getIdentifier("normal_text_size", "dimen", "miui")
        if (resId != 0 && title != null) {
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, item.resources.getDimensionPixelSize(resId).toFloat())
        }
        resId = item.resources.getIdentifier("secondary_text_size", "dimen", "miui")
        if (resId != 0) {
            val summary = item.findViewById<TextView>(android.R.id.summary)
            val text1 = item.findViewById<TextView>(android.R.id.text1)
            val text2 = item.findViewById<TextView>(android.R.id.text2)
            val size = item.resources.getDimensionPixelSize(resId).toFloat()
            summary?.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
            text1?.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
            text2?.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        }
        if (title != null && "header" == title.tag) {
            val resIdSize = item.resources.getIdentifier("preference_category_text_size", "dimen", "miui")
            if (resIdSize != 0) title.setTextSize(TypedValue.COMPLEX_UNIT_PX, item.resources.getDimensionPixelSize(resIdSize).toFloat())
        }

        val resIdLeft = item.resources.getIdentifier("preference_item_padding_left", "dimen", "miui")
        val resIdRight = item.resources.getIdentifier("preference_item_padding_right", "dimen", "miui")
        val resIdTop = item.resources.getIdentifier("preference_item_padding_top", "dimen", "miui")
        val resIdBottom = item.resources.getIdentifier("preference_item_padding_bottom", "dimen", "miui")
        val paddingLeft = if (resIdLeft == 0) item.paddingLeft else item.resources.getDimensionPixelSize(resIdLeft)
        val paddingRight = if (resIdRight == 0) item.paddingRight else item.resources.getDimensionPixelSize(resIdRight)
        val paddingTop = if (resIdTop == 0) item.paddingTop else item.resources.getDimensionPixelSize(resIdTop)
        val paddingBottom = if (resIdBottom == 0) item.paddingBottom else item.resources.getDimensionPixelSize(resIdBottom)
        item.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
    }

    @JvmStatic
    fun isNightMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    @JvmStatic
    fun getMutableActivityPendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    @JvmStatic
    fun getImmutableActivityPendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    @JvmStatic
    fun isDeviceEncrypted(context: Context?): Boolean {
        context ?: return false
        val policyMgr = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val encryption = policyMgr?.storageEncryptionStatus ?: return false
        return encryption == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
            encryption == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVATING ||
            encryption == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER
    }

    @JvmStatic
    fun launchActivity(act: AppCompatActivity, pkg: String, cmp: String) {
        launchActivity(act, pkg, cmp, false)
    }

    @JvmStatic
    fun launchActivity(act: AppCompatActivity, pkg: String, cmp: String, silent: Boolean): Boolean {
        val pm = act.packageManager
        return try {
            pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            intent.component = ComponentName(pkg, cmp)
            act.startActivity(intent)
            act.overridePendingTransition(R.anim.activity_open_enter, R.anim.activity_open_exit)
            true
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            if (!silent) Toast.makeText(act, R.string.various_hiddenfeatures_not_found, Toast.LENGTH_LONG).show()
            false
        }
    }

    @JvmStatic
    fun hideKeyboard(act: AppCompatActivity?, view: View?) {
        view ?: return
        try {
            val context = act ?: view.context
            val inputManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
            val token = (act?.currentFocus ?: view).windowToken
            if (token != null) inputManager.hideSoftInputFromWindow(token, InputMethodManager.HIDE_NOT_ALWAYS)
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            XposedHelpers.log(t)
        }
    }

    @JvmStatic
    fun showOKDialog(context: Context, title: Int, text: Int) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    @JvmStatic
    fun checkStorageReadable(context: Context): Boolean {
        val state = Environment.getExternalStorageState()
        return if (state == Environment.MEDIA_MOUNTED_READ_ONLY || state == Environment.MEDIA_MOUNTED) {
            true
        } else {
            showOKDialog(context, R.string.warning, R.string.storage_unavailable)
            false
        }
    }

    @JvmStatic
    fun checkSettingsPerm(act: AppCompatActivity): Boolean {
        return act.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun checkPermAndRequest(act: AppCompatActivity, perm: String, action: Int): Boolean {
        return if (act.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(arrayOf(perm), action)
            false
        } else {
            true
        }
    }

    @JvmStatic
    fun updateNewModsMarking(context: Context, opt: Int) {
        try {
            val appInfo = context.packageManager.getApplicationInfo(modulePkg, 0)
            val appInstalled = System.currentTimeMillis() - File(appInfo.sourceDir).lastModified()
            showNewMods = when (opt) {
                0 -> false
                4 -> true
                else -> appInstalled < (if (opt == 1) 1 else if (opt == 2) 3 else 7) * 24 * 60 * 60 * 1000
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    @JvmStatic
    fun appendStatusMarker(text: CharSequence?, unsupported: Boolean, dynamic: Boolean): CharSequence {
        val marker = if (unsupported) " ⨯" else if (dynamic) " ⟲" else ""
        return if (text.isNullOrEmpty()) marker else "$text$marker"
    }

    @JvmStatic
    fun applyNewMod(title: TextView) {
        val titleStr = title.text
        val newModStr = title.resources.getString(R.string.miuizer_new_mod) + " "
        val start = titleStr.length + 3
        val end = start + newModStr.length
        val ssb = SpannableStringBuilder(title.text.toString() + "   " + newModStr)
        ssb.setSpan(ForegroundColorSpan(markColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(RelativeSizeSpan(0.75f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        title.text = ssb
    }

    /**
     * Flashes a row to point out the item the user arrived at from search.
     *
     * This used to animate the view's own `backgroundColor` property. That is destructive:
     * `View.setBackgroundColor` replaces whatever background the row had — for a preference
     * row, the selectable background that draws its pressed state — with a flat
     * `ColorDrawable`, and the animation ends on `TRANSPARENT`, so the row is left with no
     * touch feedback at all. The caller must also treat the flash as one-shot, because a
     * row that restarts it on every bind restarts it on the rebind that its own state change
     * causes.
     *
     * The flash now runs on a private overlay drawable, the original background is put back
     * when it ends, and a flash already running on this view is cancelled first — item views
     * are recycled, so the same view can be handed a second highlight.
     */
    @JvmStatic
    fun applySearchItemHighlight(finalView: View) {
        (finalView.getTag(R.id.search_highlight_animator) as? Animator)?.cancel()

        val original = finalView.getTag(R.id.search_highlight_background) as? Drawable
            ?: finalView.background
        val overlay = ColorDrawable(Color.TRANSPARENT)
        finalView.setTag(R.id.search_highlight_background, original)
        finalView.background = overlay

        val highColor = finalView.resources.getColor(R.color.color_popup_background, finalView.context.theme)
        val colorAnim = ObjectAnimator.ofInt(overlay, "color", highColor, Color.TRANSPARENT)
        colorAnim.duration = 1200
        colorAnim.setEvaluator(ArgbEvaluator())
        colorAnim.repeatCount = 1
        colorAnim.startDelay = 300
        colorAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (finalView.getTag(R.id.search_highlight_animator) !== animation) return
                finalView.background = original
                finalView.setTag(R.id.search_highlight_animator, null)
                finalView.setTag(R.id.search_highlight_background, null)
            }
        })
        finalView.setTag(R.id.search_highlight_animator, colorAnim)
        colorAnim.start()
    }

    @JvmStatic
    fun openURL(context: Context?, url: String) {
        if (context == null) return
        val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(uriIntent)
    }

    @JvmStatic
    @JvmOverloads
    fun getChildViewsRecursive(view: View?, includeContainers: Boolean = true): ArrayList<View> {
        view ?: return ArrayList()
        return if (view is ViewGroup) {
            val list = ArrayList<View>()
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (includeContainers) list.add(view)
                list.addAll(getChildViewsRecursive(child, includeContainers))
            }
            list
        } else {
            val list = ArrayList<View>()
            list.add(view)
            list
        }
    }

    private fun checkMultiUserPermission(context: Context): Boolean {
        return context.packageManager.checkPermission("android.permission.INTERACT_ACROSS_USERS", modulePkg) == PackageManager.PERMISSION_GRANTED
    }

    private fun getAppContentResolver(): ContentResolver? {
        val cached = appContentResolver
        if (cached != null) return cached
        try {
            val appGlobals = Class.forName("android.app.AppGlobals")
            val app = appGlobals.getMethod("getInitialApplication").invoke(null)
            if (app is Context) {
                appContentResolver = app.contentResolver
                return app.contentResolver
            }
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
        }
        try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val app = activityThread.getMethod("currentApplication").invoke(null)
            if (app is Context) {
                appContentResolver = app.contentResolver
                return app.contentResolver
            }
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
        }
        return null
    }

    private fun getAnimationScaleKey(type: Int): String {
        return when (type) {
            0 -> Settings.Global.WINDOW_ANIMATION_SCALE
            1 -> Settings.Global.TRANSITION_ANIMATION_SCALE
            2 -> Settings.Global.ANIMATOR_DURATION_SCALE
            else -> Settings.Global.WINDOW_ANIMATION_SCALE
        }
    }

    @JvmStatic
    fun getAnimationScale(type: Int): Float {
        val resolver = getAppContentResolver() ?: return 1.0f
        return try {
            Settings.Global.getFloat(resolver, getAnimationScaleKey(type), 1.0f)
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            XposedHelpers.log(t)
            1.0f
        }
    }

    @JvmStatic
    fun setAnimationScale(type: Int, value: Float) {
        val resolver = getAppContentResolver() ?: return
        val key = getAnimationScaleKey(type)
        var written = false
        try {
            written = Settings.Global.putFloat(resolver, key, value)
        } catch (e: SecurityException) {
            // app lacks WRITE_SECURE_SETTINGS, fall through to root
        } catch (e: IllegalArgumentException) {
            // app lacks WRITE_SECURE_SETTINGS, fall through to root
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            XposedHelpers.log(t)
            return
        }
        if (!written) try {
            val pb = ProcessBuilder("su", "-c", "settings put global $key $value")
            val p = pb.start()
            p.waitFor()
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            XposedHelpers.log(t)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun getPackageInfoAsUser(): Method? {
        return try {
            PackageManager::class.java.getMethod("getPackageInfoAsUser", String::class.java, Integer.TYPE, Integer.TYPE)
        } catch (t: Throwable) {
            XposedHelpers.log(t)
            null
        }
    }

    private val appLabelComparator = Comparator<AppData> { a, b ->
        a.label.compareTo(b.label, ignoreCase = true)
    }

    private fun getDualUserPackageInfoMethod(context: Context): Method? {
        return if (checkMultiUserPermission(context)) getPackageInfoAsUser() else null
    }

    private fun addAppWithDualUser(
        result: ArrayList<AppData>,
        app: AppData,
        pm: PackageManager,
        dualUserMethod: Method?
    ) {
        result.add(app)
        dualUserMethod ?: return
        try {
            if (dualUserMethod.invoke(pm, app.pkgName, 0, 999) != null) {
                val appDual = AppData().apply {
                    enabled = app.enabled
                    label = app.label
                    pkgName = app.pkgName
                    actName = app.actName
                    user = 999
                }
                result.add(appDual)
            }
        } catch (ignore: Throwable) {
            FatalErrors.rethrowIfFatal(ignore)
        }
    }

    @JvmStatic
    fun getInstalledApps(context: Context) {
        val pm = context.packageManager
        val dualUserMethod = getDualUserPackageInfoMethod(context)

        val packs = pm.getInstalledApplications(PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS)
        val installedApps = ArrayList<AppData>()
        for (pack in packs) try {
            val app = AppData().apply {
                enabled = pack.enabled
                label = pack.loadLabel(pm).toString()
                pkgName = pack.packageName
                actName = "-"
            }
            addAppWithDualUser(installedApps, app, pm, dualUserMethod)
        } catch (e: Throwable) {
            XposedHelpers.log(e)
        }
        installedApps.sortWith(appLabelComparator)
        AppHelper.installedAppsList = installedApps
    }

    @JvmStatic
    fun getLaunchableApps(context: Context) {
        val pm = context.packageManager
        val dualUserMethod = getDualUserPackageInfoMethod(context)

        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val packs = pm.queryIntentActivities(mainIntent, 0)
        val launchable = ArrayList<AppData>()
        for (pack in packs) try {
            val app = AppData().apply {
                pkgName = pack.activityInfo.applicationInfo.packageName
                actName = pack.activityInfo.name
                enabled = pack.activityInfo.enabled
                label = pack.loadLabel(pm).toString()
            }
            addAppWithDualUser(launchable, app, pm, dualUserMethod)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        launchable.sortWith(appLabelComparator)
        launchableAppsList = launchable
    }

    private fun buildUniquePackageAppList(
        pm: PackageManager,
        packs: List<ResolveInfo>,
        dualUserMethod: Method?
    ): ArrayList<AppData> {
        val result = ArrayList<AppData>()
        val seenPackages = HashSet<String>(packs.size)
        for (pack in packs) try {
            val packageName = pack.activityInfo.applicationInfo.packageName
            if (packageName in seenPackages) continue
            val app = AppData().apply {
                pkgName = packageName
                actName = "-"
                enabled = pack.activityInfo.applicationInfo.enabled
                label = pack.activityInfo.applicationInfo.loadLabel(pm).toString()
            }
            seenPackages.add(packageName)
            addAppWithDualUser(result, app, pm, dualUserMethod)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        result.sortWith(appLabelComparator)
        return result
    }

    @JvmStatic
    fun getShareApps(context: Context) {
        val pm = context.packageManager
        val dualUserMethod = getDualUserPackageInfoMethod(context)

        val mainIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "*/*"
            putExtra("CustoMIUIzer", true)
        }
        val packs = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL or PackageManager.MATCH_DISABLED_COMPONENTS)
        shareAppsList = buildUniquePackageAppList(pm, packs, dualUserMethod)
    }

    @JvmStatic
    fun getOpenWithApps(context: Context) {
        val pm = context.packageManager
        val dualUserMethod = getDualUserPackageInfoMethod(context)

        val mainIntent = Intent().apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.parse("content://${PrefsProvider.AUTHORITY}/test/5"), "*/*")
            putExtra("CustoMIUIzer", true)
        }
        val mainIntent2 = Intent().apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("https://github.com")
            putExtra("CustoMIUIzer", true)
        }
        val packs = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL or PackageManager.MATCH_DISABLED_COMPONENTS).toMutableList()
        packs.addAll(pm.queryIntentActivities(mainIntent2, PackageManager.MATCH_ALL))

        openWithAppsList = buildUniquePackageAppList(pm, packs, dualUserMethod)
    }

    @JvmStatic
    fun getAppName(context: Context, pkgActName: String): CharSequence? {
        return getAppName(context, pkgActName, false)
    }

    @JvmStatic
    fun getAppName(context: Context, pkgActName: String, forcePkg: Boolean): CharSequence? {
        val pm = context.packageManager
        val notSelected = context.resources.getString(R.string.notselected)
        val pkgActArray = pkgActName.split(PrefPair.DELIMITER)

        if (pkgActName != notSelected) {
            if (!forcePkg && pkgActArray.size >= 2 && pkgActArray[1].isNotBlank()) {
                return try {
                    pm.getActivityInfo(ComponentName(pkgActArray[0], pkgActArray[1]), 0).loadLabel(pm).toString()
                } catch (e: Throwable) {
                    null
                }
            } else if (pkgActArray[0].isNotBlank()) {
                return try {
                    val ai = pm.getApplicationInfo(pkgActArray[0], 0)
                    pm.getApplicationLabel(ai)
                } catch (e: Throwable) {
                    null
                }
            }
        }
        return null
    }

    @JvmStatic
    fun getAppIcon(context: Context, pkgActName: String): Drawable? {
        return getAppIcon(context, pkgActName, false)
    }

    @JvmStatic
    fun getAppIcon(context: Context, pkgActName: String, forcePkg: Boolean): Drawable? {
        val pm = context.packageManager
        val notSelected = context.resources.getString(R.string.notselected)
        val pkgActArray = pkgActName.split(PrefPair.DELIMITER)

        if (pkgActName != notSelected) {
            if (!forcePkg && pkgActArray.size >= 2 && pkgActArray[1].isNotBlank()) {
                return try {
                    pm.getActivityIcon(ComponentName(pkgActArray[0], pkgActArray[1]))
                } catch (e: Throwable) {
                    null
                }
            } else if (pkgActArray[0].isNotBlank()) {
                return try {
                    pm.getApplicationIcon(pkgActArray[0])
                } catch (e: Throwable) {
                    null
                }
            }
        }
        return null
    }

    @JvmStatic
    fun getShortcutIcon(context: Context, key: String): Drawable {
        val shortcutIconPath = context.filesDir.path + "/shortcuts/" + key + "_shortcut.png"
        val shortcutIconFile = File(shortcutIconPath)
        val shortcutIcon: Drawable? = if (shortcutIconFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(shortcutIconFile.absolutePath)
            if (bitmap != null) BitmapDrawable(context.resources, bitmap) else null
        } else null
        val layers = arrayOf(shortcutIcon ?: ColorDrawable())
        val insetShortcutIcon = LayerDrawable(layers)
        val padding = (5 * context.resources.displayMetrics.density).toInt()
        insetShortcutIcon.setLayerInset(0, padding, padding, padding, padding)
        return insetShortcutIcon
    }

    @Suppress("ConstantConditions")
    @JvmStatic
    fun getActionImageLocal(context: Context, key: String): Drawable? {
        return try {
            val action = AppHelper.getIntOfAppPrefs(key + "_action", 1)
            when (action) {
                8 -> getAppIcon(context, AppHelper.getStringOfAppPrefs(key + "_app", "") ?: "")
                9 -> getShortcutIcon(context, key)
                20 -> getAppIcon(context, AppHelper.getStringOfAppPrefs(key + "_activity", "") ?: "", true)
                else -> null
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun parseModSearchIndex(context: Context) {
        val res = context.resources
        try {
            res.getXml(R.xml.mod_search_index).use { xml ->
                var eventType = xml.eventType
                var category: ModData.ModCat? = null
                var categoryTitleResId = 0
                var routeSub: String? = null
                var breadcrumbSubTitleResId = 0
                var breadcrumbSubSubTitleResId = 0
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        try {
                            when (xml.name) {
                                "category" -> {
                                    category = ModData.ModCat.valueOf(
                                        xml.getAttributeValue(null, "key")
                                    )
                                    categoryTitleResId =
                                        xml.getAttributeResourceValue(null, "title", 0)
                                    routeSub = null
                                    breadcrumbSubTitleResId = 0
                                    breadcrumbSubSubTitleResId = 0
                                }
                                "group" -> {
                                    routeSub = xml.getAttributeValue(null, "routeSub")
                                    breadcrumbSubTitleResId =
                                        xml.getAttributeResourceValue(null, "breadcrumbTitle", 0)
                                    breadcrumbSubSubTitleResId = 0
                                }
                                "section" -> {
                                    breadcrumbSubSubTitleResId =
                                        xml.getAttributeResourceValue(null, "title", 0)
                                }
                                "mod" -> {
                                    val titleResId =
                                        xml.getAttributeResourceValue(null, "title", 0)
                                    val currentCategory = category
                                    if (titleResId > 0 && categoryTitleResId > 0 && currentCategory != null) {
                                        val modData = ModData()
                                        modData.title = res.getString(titleResId)
                                        modData.breadcrumbs = buildString {
                                            append(res.getString(categoryTitleResId))
                                            if (breadcrumbSubTitleResId > 0) {
                                                append('/')
                                                append(res.getString(breadcrumbSubTitleResId))
                                            }
                                            if (breadcrumbSubSubTitleResId > 0) {
                                                append('/')
                                                append(res.getString(breadcrumbSubSubTitleResId))
                                            }
                                        }
                                        modData.key = xml.getAttributeValue(null, "key") ?: ""
                                        modData.cat = currentCategory
                                        modData.sub = routeSub
                                        modData.order = xml.getAttributeIntValue(null, "order", 0)
                                        allModsList.add(modData)
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            FatalErrors.rethrowIfFatal(t)
                            t.printStackTrace()
                        }
                    }
                    eventType = xml.next()
                }
            }
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            t.printStackTrace()
        }
    }

    /**
     * Builds the searchable index of every mod, in the order the search results are shown.
     *
     * Sorting here rather than after each filter pass is what lets the filter be a single
     * linear scan: a subsequence of a sorted list is still sorted, so filtering preserves
     * the order. The list is rebuilt only on an explicit reload, while the filter runs on
     * every keystroke.
     */
    @JvmStatic
    fun getAllMods(context: Context, force: Boolean) {
        if (force) allModsList.clear()
        else if (allModsList.size > 0) return
        parseModSearchIndex(context)
        allModsList.sortWith(MOD_DISPLAY_ORDER)
    }

    /** Breadcrumb first, then title; both case-insensitive. */
    @JvmField
    val MOD_DISPLAY_ORDER = Comparator<ModData> { a, b ->
        val byBreadcrumbs = a.breadcrumbs.compareTo(b.breadcrumbs, ignoreCase = true)
        if (byBreadcrumbs != 0) byBreadcrumbs else a.title.compareTo(b.title, ignoreCase = true)
    }

    @JvmStatic
    fun performCustomVibration(context: Context, vibration: Int, ownPattern: String) {
        if (vibration == 0) return
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        val pattern = when (vibration) {
            1 -> {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                return
            }
            2 -> {
                vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
                return
            }
            3 -> longArrayOf(0, 250, 250, 250)
            4 -> longArrayOf(0, 250, 150, 125, 100, 125)
            5 -> longArrayOf(0, 150, 150, 100, 250, 150, 150, 100)
            6 -> longArrayOf(0, 100, 150, 100, 150, 100)
            7 -> {
                if (TextUtils.isEmpty(ownPattern)) return
                getVibrationPattern(ownPattern)
            }
            else -> return
        }
        try {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (t: Throwable) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }

    @JvmStatic
    fun getVibrationPattern(patternStr: String): LongArray {
        return try {
            if (TextUtils.isEmpty(patternStr)) return LongArray(0)
            val sPattern = patternStr.split(",")
            LongArray(sPattern.size) { i ->
                if (TextUtils.isEmpty(sPattern[i])) 0L else java.lang.Long.parseLong(sPattern[i])
            }
        } catch (t: Throwable) {
            LongArray(0)
        }
    }

    @JvmStatic
    fun getCacheFilePath(filename: String): String? {
        return when {
            File("/cache").canWrite() -> "/cache/$filename"
            File("/data/cache").canWrite() -> "/data/cache/$filename"
            File("/data/tmp").canWrite() -> "/data/tmp/$filename"
            else -> null
        }
    }

    @JvmStatic
    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val mClipData = ClipData.newPlainText("", text)
        clipboard?.setPrimaryClip(mClipData)
    }

}
