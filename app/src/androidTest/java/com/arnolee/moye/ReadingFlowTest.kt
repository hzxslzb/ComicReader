package com.arnolee.moye

import android.graphics.BitmapFactory
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Live source integration: search, reader, download pause/resume, then real offline rendering. */
@RunWith(AndroidJUnit4::class)
class ReadingFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun shot(name: String) {
        ui.waitForIdle()
        val dir=File(ui.activity.getExternalFilesDir(null),"test-screenshots").apply { mkdirs() }
        device.takeScreenshot(File(dir,"$name.png"))
    }
    @Test fun liveReadingDownloadOfflineAndHistory() {
        lateinit var vm: AppViewModel
        ui.runOnIdle { vm=ViewModelProvider(ui.activity)[AppViewModel::class.java];vm.screen="home";vm.tab=1;vm.setAppearance("light");vm.setMode("ltr") }
        ui.onNode(hasSetTextAction()).performTextInput("脑洞学生会")
        ui.onNodeWithText("搜索漫画").performClick()
        ui.waitUntil(60_000) { !vm.searching && vm.searched }
        assertNull(vm.searchError)
        assertTrue(vm.results.any { it.id=="43847" })
        shot("03-search-light")
        ui.onNode(hasText("脑洞学生会") and !hasSetTextAction(),useUnmergedTree=true).performClick()
        ui.waitUntil(60_000) { !vm.detailLoading && vm.selected?.chapters?.isNotEmpty()==true }
        val book=vm.selected!!
        ui.runOnIdle { if(book.id !in vm.store.favorites()) vm.store.toggleFavorite(book.id);vm.store.saveProgress(book.id,book.chapters.first(),0,17) }
        ui.onNodeWithText("继续阅读").performClick()
        ui.waitUntil(60_000) { !vm.readerLoading }
        assertNull(vm.readerError)
        assertEquals(17,vm.pages!!.paths.size)
        ui.waitUntil(60_000) { ui.onAllNodes(hasTestTag("page-0") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"loaded")).fetchSemanticsNodes().isNotEmpty() }
        shot("04-reader-online")
        ui.onNodeWithTag("reader-pager").performTouchInput { swipeLeft() }
        ui.waitUntil(10_000) { vm.store.progress(book.id,"628732")?.page==1 }
        ui.onNodeWithText("下一章").performClick()
        ui.waitUntil(60_000) { vm.pages?.chapter?.id=="633620" && !vm.readerLoading }
        assertEquals("628732",vm.pages!!.prevId)
        ui.onNodeWithText("上一章").performClick()
        ui.waitUntil(60_000) { vm.pages?.chapter?.id=="628732" && !vm.readerLoading }
        assertEquals(1,vm.readerStart)
        ui.onNodeWithContentDescription("下载当前章节").performClick()
        ui.waitUntil(90_000) { vm.store.download(book.id,"628732")?.let { it.done>=1 || it.state=="failed" }==true }
        assertNotEquals("failed",vm.store.download(book.id,"628732")!!.state)
        val first=vm.store.pageFile(book.id,"628732",0)
        assertTrue(first.length()>0)
        val stamp=first.lastModified()
        ui.runOnIdle { vm.store.updateDownload(book.id,"628732","paused") }
        ui.waitUntil(10_000) { vm.store.download(book.id,"628732")?.state=="paused" }
        ui.runOnIdle { vm.resume(vm.store.download(book.id,"628732")!!) }
        ui.waitUntil(240_000) { vm.store.download(book.id,"628732")?.state in listOf("complete","failed") }
        val completed=vm.store.download(book.id,"628732")!!
        assertEquals(completed.error,"complete",completed.state)
        assertEquals(17,completed.done)
        assertEquals(stamp,first.lastModified())
        for(i in 0 until 17) {
            val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(vm.store.pageFile(book.id,"628732",i).path,options)
            assertTrue("Page $i is a decoded image",options.outWidth>0)
        }
        ui.onNodeWithContentDescription("阅读设置").performClick()
        ui.onNodeWithText("深色",useUnmergedTree=true).performClick()
        device.pressBack()
        ui.waitForIdle()
        assertEquals("dark",vm.theme)
        ui.runOnIdle { vm.back();vm.back();vm.tab=2 }
        shot("05-downloads-dark")
        try {
            device.executeShellCommand("svc wifi disable")
            device.executeShellCommand("svc data disable")
            // Recreate ensures history is read from the database, rather than retained UI state.
            ui.activityRule.scenario.recreate()
            ui.runOnIdle { vm=ViewModelProvider(ui.activity)[AppViewModel::class.java];vm.screen="home";vm.tab=0 }
            shot("06-library-dark")
            ui.onNodeWithText("继续阅读").performClick()
            ui.waitUntil(15_000) { vm.pages?.chapter?.id=="628732" && !vm.readerLoading }
            assertEquals(1,vm.readerStart)
            ui.waitUntil(20_000) { ui.onAllNodes(hasTestTag("page-1") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"loaded")).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText("第01话 · 离线").assertExists()
            shot("07-reader-offline-dark")
            ui.onNodeWithContentDescription("阅读设置").performClick()
            ui.onNodeWithText("上下滚动").performClick()
            ui.onNodeWithTag("reader-list").performTouchInput { swipeUp() }
            ui.waitUntil(10_000) { (vm.store.progress(book.id,"628732")?.page ?: 0)>=1 }
        } finally {
            device.executeShellCommand("svc wifi enable")
            device.executeShellCommand("svc data enable")
        }
    }
}
