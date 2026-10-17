package tv.withaibuild.customiuizer

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import java.io.FileNotFoundException
import java.io.IOException

class PrefsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".provider.sharedprefs"
    }

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, "test/*", 5)
    }

    override fun onCreate(): Boolean = true

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
