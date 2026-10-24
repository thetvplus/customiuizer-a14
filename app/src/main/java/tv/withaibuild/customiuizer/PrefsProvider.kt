package tv.withaibuild.customiuizer

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.utils.AppHelper
import java.io.FileNotFoundException
import java.io.IOException

class PrefsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".provider.sharedprefs"
        const val COMMITTED_HEIGHT = "committed_statusbar_height"
        const val HEIGHT_DP = "height_dp"
    }

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, "test/*", 5)
    }

    override fun onCreate(): Boolean = true

    /** Cold, system-only fallback for frameworks whose hook-side preference cache stays stale.
     * Read the acknowledged remote mirror, never an uncommitted UI value or an Intent payload.
     * This provider stays non-exported and exposes no complete preference map.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != COMMITTED_HEIGHT) return super.call(method, arg, extras)
        if (Binder.getCallingUid() != 1000) throw SecurityException("System UID required")
        val remote = AppHelper.remotePrefs ?: return null
        val dp = remote.getInt("pref_key_" + StatusBarHeightConfig.PREF_KEY, StatusBarHeightConfig.DEFAULT_SENTINEL)
        if (dp !in StatusBarHeightConfig.DEFAULT_SENTINEL..80) return null
        return Bundle().apply { putInt(HEIGHT_DP, dp) }
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    @Throws(FileNotFoundException::class)
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (mode != "r" || uri.scheme != "content" || uriMatcher.match(uri) != 5) {
            throw FileNotFoundException("Unsupported sample request")
        }
        val filename = when (uri.pathSegments[1]) {
            "0" -> "test0.png"
            "1" -> "test1.mp3"
            "2" -> "test2.mp4"
            "3", "5" -> "test3.txt"
            "4" -> "test4.zip"
            else -> throw FileNotFoundException("Unknown sample")
        }
        val assets = context?.assets ?: throw FileNotFoundException("Provider unavailable")
        return try {
            assets.openFd(filename)
        } catch (e: IOException) {
            throw FileNotFoundException("Sample unavailable").apply { initCause(e) }
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0
}
