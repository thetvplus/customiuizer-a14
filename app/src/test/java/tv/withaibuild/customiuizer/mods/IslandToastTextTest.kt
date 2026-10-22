package tv.withaibuild.customiuizer.mods

import org.junit.Assert.*
import org.junit.Test

class IslandToastTextTest {
    @Test fun completeSaveStatusesWithAbsoluteAndroidDestinationsCanEnterTheIsland() {
        assertEquals("图片已保存", IslandToastText.resolve("图片已保存至/storage/emulated/0/Pictures/WeiXin/mmexport.jpg", true))
        assertEquals("视频已保存", IslandToastText.resolve("视频已保存到： /sdcard/Movies/video.mp4", true))
        assertEquals("文件已保存", IslandToastText.resolve("文件已保存至/storage/emulated/0/Download/report.pdf", true))
        assertEquals("圖片已儲存", IslandToastText.resolve("圖片已儲存至/storage/emulated/0/Pictures/one.jpg", true))
        assertEquals("Image saved", IslandToastText.resolve("Image saved to /storage/emulated/0/Pictures/one.jpg", true))
        val path = "图片已保存至/storage/emulated/0/Pictures/" + "x".repeat(300) + ".jpg"
        assertEquals("图片已保存", IslandToastText.resolve(path, true))
        assertNull(IslandToastText.resolve(path, false))
        assertNull(IslandToastText.resolve("图片已保存至/storage/" + "x".repeat(4096), true))
    }

    @Test fun ProgressFailuresQuestionsAndMixedResultsNeverBecomeSuccessfulFeedback() {
        for (text in listOf("正在保存到/storage/emulated/0/one.jpg", "无法保存到/storage/emulated/0/one.jpg",
            "图片保存至/storage/emulated/0/one.jpg", "图片已保存至相册", "图片已保存至/storage/one.jpg，但上传失败",
            "没有图片已保存至/storage/one.jpg", "Saved to /storage/one.jpg but upload failed", "图片已保存至/storage/one.jpg？"))
            assertEquals(text, IslandToastText.resolve(text, true))
        assertNull(IslandToastText.resolve("图片已保存至/storage/one.jpg\n删除失败", true))
        assertNull(IslandToastText.resolve("图片已保存至/storage/one.jpg\u0001", true))
        val plain = "图片已保存至/storage/emulated/0/Pictures/one.jpg"
        assertEquals(plain, IslandToastText.resolve(plain, false))
    }
}
