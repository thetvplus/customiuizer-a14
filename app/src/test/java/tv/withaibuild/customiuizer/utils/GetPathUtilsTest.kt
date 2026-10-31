package tv.withaibuild.customiuizer.utils

import android.app.Application
import android.net.Uri
import android.os.Environment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GetPathUtilsTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun tree(authority: String, id: String) = Uri.parse("content://$authority/tree/" + Uri.encode(id))
    @Test fun onlyLocalDocumentIdsResolveAndColonsArePreserved() {
        val primary = Environment.getExternalStorageDirectory().path
        assertEquals(java.io.File(primary, "Pictures/测试:相册").path, GetPathUtils.getDirectoryPathFromUri(app, tree("com.android.externalstorage.documents", "primary:Pictures/测试:相册")))
        assertNull(GetPathUtils.getDirectoryPathFromUri(app, tree("example.cloud", "primary:Pictures/Fake")))
        assertNull(GetPathUtils.getDirectoryPathFromUri(app, tree("com.android.externalstorage.documents", "unknown:Pictures/Test")))
        assertNull(GetPathUtils.getDirectoryPathFromUri(app, tree("com.android.externalstorage.documents", "primary:../outside")))
    }
    @Test fun mediaPathsUseABoundedVolumeRelativeDirectory() {
        val primary = Environment.getExternalStorageDirectory().path
        assertEquals("Pictures/Test/", GetPathUtils.imageLocation(app, "$primary/Pictures/Test")?.second)
        assertNull(GetPathUtils.imageLocation(app, "$primary-other/Pictures/Test"))
        assertNull(GetPathUtils.imageLocation(app, "$primary/Pictures/../../outside"))
        assertNull(GetPathUtils.imageLocation(app, "$primary/Download/Test"))
        assertNull(GetPathUtils.imageLocation(app, "$primary/Documents/Test"))
        assertNull(GetPathUtils.imageLocation(app, "$primary/Pictures/Bad\u0000Path"))
        assertNull(GetPathUtils.imageLocation(app, primary))
    }
    @Test fun localRawAndFileResultsKeepTheSameVolumeBoundary() {
        val folder = java.io.File(Environment.getExternalStorageDirectory(), "Download/Test").path
        assertEquals(folder, GetPathUtils.getDirectoryPathFromUri(app, tree("com.android.providers.downloads.documents", "raw:$folder")))
        val file = Uri.Builder().scheme("file").path(folder).build()
        assertEquals(file.path, GetPathUtils.getDirectoryPathFromUri(app, file))
        assertNull(GetPathUtils.getDirectoryPathFromUri(app, tree("example.cloud", "raw:$folder")))
    }
}
