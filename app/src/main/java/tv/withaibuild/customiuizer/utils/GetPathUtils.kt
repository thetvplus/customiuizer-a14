package tv.withaibuild.customiuizer.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.nio.file.InvalidPathException

object GetPathUtils {
    // Cloud providers can use local-looking IDs without filesystem semantics.
    // Keep local file/raw results, but validate their actual storage-volume boundary.
    fun getDirectoryPathFromUri(context: Context, uri: Uri?): String? {
        if (uri?.scheme == "file") return uri.path?.takeIf { volumeLocation(context, it) != null }
        if (uri?.scheme != "content" || uri.authority !in setOf("com.android.externalstorage.documents", "com.android.providers.downloads.documents") ||
            !DocumentsContract.isTreeUri(uri)) return null
        val id = try { DocumentsContract.getTreeDocumentId(uri) } catch (_: IllegalArgumentException) { return null }
        if (id.startsWith("raw:")) return id.substring(4).takeIf { volumeLocation(context, it) != null }
        if (uri.authority != "com.android.externalstorage.documents") return null
        val parts = id.split(":", limit = 2)
        if (parts.size != 2) return null
        val root = if (parts[0].equals("primary", true)) Environment.getExternalStorageDirectory()
            else context.getSystemService(StorageManager::class.java).storageVolumes
                .firstOrNull { it.uuid.equals(parts[0], true) }?.directory ?: return null
        val relative = parts[1]
        if (File(relative).isAbsolute || relative.split('/').any { it == ".." || it == "." }) return null
        return File(root, relative).path
    }

    fun imageLocation(context: Context, directory: String): Pair<Uri, String>? {
        val location = volumeLocation(context, directory) ?: return null
        val topLevel = location.second.substringBefore('/')
        // HyperOS 1 MediaProvider enforces this for the actual screenshot UID too.
        // Reject before changing the native request; no provider retry or mkdirs.
        return location.takeIf { topLevel == Environment.DIRECTORY_PICTURES || topLevel == Environment.DIRECTORY_DCIM }
    }

    private fun volumeLocation(context: Context, directory: String): Pair<Uri, String>? {
        if (!File(directory).isAbsolute) return null
        val path = try { File(directory).toPath().normalize() } catch (_: InvalidPathException) { return null }
        val primary = Environment.getExternalStorageDirectory().toPath().normalize()
        val root: java.nio.file.Path
        val collection: Uri
        if (path.startsWith(primary)) {
            root = primary
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        } else {
            val volume = context.getSystemService(StorageManager::class.java).storageVolumes.firstOrNull {
                it.directory?.toPath()?.normalize()?.let(path::startsWith) == true
            } ?: return null
            root = volume.directory?.toPath()?.normalize() ?: return null
            collection = MediaStore.Images.Media.getContentUri(volume.mediaStoreVolumeName ?: return null)
        }
        val relative = root.relativize(path).toString().replace(File.separatorChar, '/')
        if (relative.isEmpty()) return null
        return collection to "$relative/"
    }
}
