package tv.withaibuild.customiuizer.subs

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle

import java.io.File
import java.io.FileOutputStream

import tv.withaibuild.customiuizer.SubFragmentWithSearch
import tv.withaibuild.customiuizer.utils.AppHelper
import tv.withaibuild.customiuizer.AppSelectionRow
import tv.withaibuild.customiuizer.utils.AppData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("ClickableViewAccessibility")
class ShortcutSelector : SubFragmentWithSearch() {

    private var key: String? = null
    private var keyContents: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val args = arguments
        key = args?.getString("key")
        keyContents = AppHelper.getStringOfAppPrefs(key, null)

        val ctx = context ?: return
        val shortcutIntent = Intent(Intent.ACTION_CREATE_SHORTCUT)
        val pm = ctx.packageManager
        viewLifecycleOwner.lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                pm.queryIntentActivities(shortcutIntent, 0).map { info ->
                    AppData().apply {
                        pkgName = info.activityInfo.packageName; actName = info.activityInfo.name
                        label = info.loadLabel(pm).toString(); enabled = info.activityInfo.enabled; prepareForList()
                    }
                }
            }
            appRows = entries.map { AppSelectionRow(it) }
            loading = false
        }
    }

    override fun selectApp(row: AppSelectionRow) {
        val app = row.app
        createShortcutIntent.action = Intent.ACTION_CREATE_SHORTCUT
        createShortcutIntent.component = ComponentName(app.pkgName, app.actName)
        keyContents = "${app.pkgName}|${app.actName}"
        startActivityForResult(createShortcutIntent, 7350)
    }

    private val createShortcutIntent = Intent()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != 7350) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        if (resultCode == Activity.RESULT_OK && data != null) {
            val context = context ?: return
            val receiver = targetFragment ?: return
            val request = targetRequestCode
            val contents = keyContents
            viewLifecycleOwner.lifecycleScope.launch {
                var pending: File? = null
                var delivered = false
                try {
                    val iconPath = withContext(Dispatchers.IO) {
                        val resource = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource::class.java)
                        var owned = false
                        val bitmap = try {
                            resource?.let {
                                val provider = context.createPackageContext(it.packageName, Context.CONTEXT_IGNORE_SECURITY)
                                val id = provider.resources.getIdentifier(it.resourceName, "drawable", it.packageName)
                                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeResource(provider.resources, id, bounds)
                                val options = BitmapFactory.Options().apply {
                                    while (bounds.outWidth / inSampleSize > 256 || bounds.outHeight / inSampleSize > 256) inSampleSize *= 2
                                }
                                BitmapFactory.decodeResource(provider.resources, id, options)?.also { owned = true }
                            } ?: data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
                        } catch (failure: Exception) { null }
                        if (bitmap == null || key == null) return@withContext null
                        val scale = minOf(1f, 256f / maxOf(bitmap.width, bitmap.height))
                        var scaled: Bitmap? = null
                        try {
                            scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap,
                                (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
                            val file = File(context.filesDir, "shortcuts/tmp.png")
                            file.parentFile?.mkdirs(); pending = file
                            FileOutputStream(file, false).use { stream ->
                                if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw java.io.IOException("Unable to encode icon")
                            }
                            file.absolutePath
                        } catch (failure: Exception) {
                            pending?.delete(); pending = null; null
                        } finally {
                            if (scaled !== bitmap) scaled?.recycle()
                            if (owned) bitmap.recycle()
                        }
                    }
                    if (!isAdded || !receiver.isAdded) return@launch
                    val result = Intent().putExtra("shortcut_contents", contents)
                        .putExtra("shortcut_name", data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME))
                        .putExtra("shortcut_intent", data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java))
                    iconPath?.let { result.putExtra("shortcut_icon", it) }
                    receiver.onActivityResult(request, Activity.RESULT_OK, result)
                    delivered = true
                    finish()
                } finally {
                    if (!delivered) withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { pending?.delete() }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
