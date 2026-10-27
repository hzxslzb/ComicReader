package com.arnolee.moye

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import org.junit.rules.ExternalResource
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ChapterAdvanceTest {
    @get:Rule(order=0) val notificationPermission=object: ExternalResource() {
        override fun before() {
            if(Build.VERSION.SDK_INT>=33) device.executeShellCommand("pm grant ${InstrumentationRegistry.getInstrumentation().targetContext.packageName} android.permission.POST_NOTIFICATIONS")
        }
    }
    @get:Rule(order=1) val ui=createAndroidComposeRule<MainActivity>()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var vm: AppViewModel
    private val book=Manga("991000","独立阅读记录测试",chapters=listOf(Chapter("991001","第一章"),Chapter("991002","第二章")))
    private fun open(mode: String,page: Int=0,dual: Boolean=false) {
        ui.runOnIdle { vm.setMode(mode);vm.updateDualPage(dual);vm.store.saveProgress(book.id,book.chapters[0],page,3);vm.read(book,book.chapters[0]) }
        ui.waitUntil(10_000) { vm.pages?.chapter?.id=="991001" && !vm.readerLoading }
        ui.waitForIdle()
    }
    private fun settled(page: Int) {
        ui.waitUntil(10_000) { vm.readerPage==page };ui.waitForIdle()
        assertEquals("991001",vm.activeChapter!!.id)
    }
    private fun next() {
        ui.waitUntil(10_000) { vm.pages?.chapter?.id=="991002" && !vm.readerLoading }
        assertEquals(2,vm.store.progress(book.id,"991001")!!.page)
    }
    private fun shot(name: String) {
        ui.waitForIdle();device.waitForIdle()
        val dir=File(ui.activity.getExternalFilesDir(null),"chapter-screenshots").apply { mkdirs() }
        device.takeScreenshot(File(dir,"$name.png"))
    }
    @Test fun extraSwipeOnlyAtSettledEndAndPerBookHistoryResumes() {
        ui.runOnIdle { vm=ViewModelProvider(ui.activity)[AppViewModel::class.java];vm.setAppearance("light") }
        val bitmap=Bitmap.createBitmap(800,2200,Bitmap.Config.ARGB_8888)
        book.chapters.forEachIndexed { ci,ch ->
            val pages=ChapterPages(book.id,ch,(0..2).map { "/local/$ci-$it.png" },emptyMap(),if(ci==1) "991001" else null,if(ci==0) "991002" else null)
            for(i in 0..2) {
                val c=Canvas(bitmap);c.drawColor(Color.rgb(245,240,220));val paint=Paint().apply { color=Color.rgb(35,85,60);textSize=60f }
                c.drawText("CH ${ci+1} / PAGE ${i+1}",40f,100f,paint)
                for(y in 250..2050 step 300) c.drawRect(40f,y.toFloat(),760f,(y+150).toFloat(),paint)
                vm.store.pageFile(book.id,ch.id,i).also { it.parentFile!!.mkdirs();it.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG,100,out) } }
            }
            vm.store.putManifest(pages)
        }
        bitmap.recycle();vm.store.putBook(book)
        try {
            device.executeShellCommand("wm size 1080x2520");device.executeShellCommand("wm density 440")
            open("ltr")
            ui.onNodeWithTag("reader-pager").performTouchInput { swipeLeft() };settled(1)
            ui.onNodeWithTag("reader-pager").performTouchInput { swipeLeft() };settled(2)
            shot("01-last-page-hint")
            ui.onNodeWithTag("page-2").performTouchInput { click() }
            ui.waitUntil(5_000) { !vm.readerChrome }
            ui.onAllNodesWithTag("reader-controls").assertCountEquals(0)
            ui.onNodeWithTag("reader-pager").performTouchInput { swipeLeft() };next()
            ui.onAllNodesWithTag("reader-controls").assertCountEquals(0)
            ui.activityRule.scenario.recreate()
            ui.runOnIdle { vm=ViewModelProvider(ui.activity)[AppViewModel::class.java] }
            ui.onAllNodesWithTag("reader-controls").assertCountEquals(0)
            ui.onNodeWithTag("page-0").performTouchInput { click() }
            ui.waitUntil(5_000) { vm.readerChrome }
            // Last chapter never advances to an unavailable chapter.
            ui.runOnIdle { vm.store.saveProgress(book.id,book.chapters[1],2,3);vm.read(book,book.chapters[1]) }
            ui.waitUntil(10_000) { !vm.readerLoading };ui.waitForIdle()
            ui.onNodeWithTag("reader-pager").performTouchInput { swipeLeft() }
            ui.waitForIdle();assertEquals("991002",vm.activeChapter!!.id)
            open("rtl",1)
            ui.onNodeWithTag("reader-pager").performTouchInput { swipeRight() };settled(2)
            ui.runOnIdle { vm.readerChrome=false }
            ui.onNodeWithTag("reader-pager").performTouchInput { swipeRight() };next()
            ui.onAllNodesWithTag("reader-controls").assertCountEquals(0)
            ui.runOnIdle { vm.readerChrome=true }
            // A pan of an enlarged last page must not turn the chapter.
            open("ltr",2)
            ui.onNodeWithTag("page-2").performTouchInput { doubleClick() }
            ui.waitUntil(5_000) { ui.onAllNodesWithTag("reader-reset-zoom").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("reader-pager").performTouchInput { swipeLeft() }
            ui.waitForIdle();assertEquals("991001",vm.activeChapter!!.id)
            ui.onNodeWithTag("reader-reset-zoom").performClick();ui.waitForIdle()
            ui.onNodeWithTag("reader-pager").performTouchInput { swipeLeft() };next()
            open("vertical",2)
            ui.waitUntil(10_000) { ui.onAllNodes(hasTestTag("page-2") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"loaded")).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("reader-list").performScrollToIndex(2)
            // This gesture reaches the bottom. It must not also advance the chapter.
            ui.onNodeWithTag("reader-vertical-zoom").performTouchInput { swipeUp() }
            ui.waitForIdle();assertEquals("991001",vm.activeChapter!!.id)
            ui.onNodeWithTag("reader-list").performScrollToIndex(3);ui.waitForIdle()
            shot("02-vertical-end")
            ui.runOnIdle { vm.readerChrome=false }
            ui.onNodeWithTag("reader-list").performScrollToIndex(3);ui.waitForIdle()
            ui.onNodeWithTag("reader-vertical-zoom").performTouchInput { swipeUp() };next()
            ui.onAllNodesWithTag("reader-controls").assertCountEquals(0)
            ui.runOnIdle { vm.readerChrome=true }
            device.executeShellCommand("wm size 1968x2184");device.executeShellCommand("wm density 420")
            open("ltr",0,true)
            ui.onNodeWithTag("reader-pager").performTouchInput { swipeLeft() };settled(1)
            ui.runOnIdle { vm.readerChrome=false }
            ui.onNodeWithTag("reader-pager").performTouchInput { swipeLeft() };next()
            ui.onAllNodesWithTag("reader-controls").assertCountEquals(0)
            // The detail history is scoped to this book and resumes the chosen chapter's own page.
            ui.runOnIdle { vm.back() };ui.waitForIdle()
            ui.runOnIdle { vm.store.saveProgress(book.id,book.chapters[0],1,3);vm.store.saveProgress(book.id,book.chapters[1],2,3) }
            ui.onNodeWithTag("book-history").performScrollTo().performClick()
            ui.onNodeWithTag("history-991001").assertExists();ui.onNodeWithTag("history-991002").assertExists()
            shot("03-book-history")
            ui.onNodeWithTag("history-991001").performClick()
            ui.waitUntil(10_000) { vm.pages?.chapter?.id=="991001" && !vm.readerLoading }
            assertEquals(1,vm.readerPage)
            ui.activityRule.scenario.recreate()
            ui.runOnIdle { vm=ViewModelProvider(ui.activity)[AppViewModel::class.java] }
            assertEquals(1,vm.store.progress(book.id,"991001")!!.page)
            assertEquals(2,vm.store.progress(book.id,"991002")!!.page)
        } finally {
            device.executeShellCommand("wm size reset");device.executeShellCommand("wm density reset")
        }
    }
}
