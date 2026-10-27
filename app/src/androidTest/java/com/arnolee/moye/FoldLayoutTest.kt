package com.arnolee.moye

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual activity/window resizes, not just mocked layout classifications. Uses local fixtures. */
@RunWith(AndroidJUnit4::class)
class FoldLayoutTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun resize(size: String,density: Int) {
        device.executeShellCommand("wm size $size")
        device.executeShellCommand("wm density $density")
        device.waitForIdle()
    }
    private fun tag(name: String) { ui.waitUntil(20_000) { ui.onAllNodesWithTag(name).fetchSemanticsNodes().isNotEmpty() };ui.onNodeWithTag(name).assertIsDisplayed() }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        val dir=File(ui.activity.getExternalFilesDir(null),"fold-screenshots").apply { mkdirs() }
        device.takeScreenshot(File(dir,"$name.png"))
    }
    @Test fun coverInnerLandscapeSplitAndRecreationKeepExactPage() {
        lateinit var vm: AppViewModel
        ui.runOnIdle { vm=ViewModelProvider(ui.activity)[AppViewModel::class.java];vm.setAppearance("light");vm.setMode("ltr");vm.updateDualPage(true) }
        val book=Manga("990001","折叠屏布局验证",author="本地测试样本",summary="验证外屏、展开内屏、横屏与分屏窗口的自适应布局。",chapters=(1..40).map { Chapter((990100+it).toString(),"第 ${it} 章") })
        val p=ChapterPages(book.id,book.chapters.first(),(0..16).map { "/test/$it.webp" },emptyMap(),null,book.chapters[1].id)
        val bitmap=Bitmap.createBitmap(600,850,Bitmap.Config.ARGB_8888)
        for(i in 0..16) {
            val canvas=Canvas(bitmap);canvas.drawColor(if(i%2==0) Color.rgb(225,236,226) else Color.rgb(241,231,211))
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(35,76,63);textSize=42f }
            canvas.drawText("MOYE · PAGE ${i+1}",70f,150f,paint)
            canvas.drawText("Fold7 layout test",70f,230f,paint)
            val file=vm.store.pageFile(book.id,p.chapter.id,i);file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        bitmap.recycle()
        val fixture=book.copy(cover=vm.store.pageFile(book.id,p.chapter.id,0).path)
        ui.runOnIdle {
            vm.store.putBook(fixture);vm.store.putManifest(p)
            if(fixture.id !in vm.store.favorites()) vm.store.toggleFavorite(fixture.id)
            vm.store.saveProgress(fixture.id,p.chapter,2,17)
            vm.screen="home";vm.tab=0
        }
        try {
            resize("1080x2520",440)
            tag("bottom-navigation")
            ui.runOnIdle { vm.read(fixture,p.chapter) }
            tag("reader-single")
            assertEquals(2,vm.readerPage)
            screenshot("01-cover-single")
            resize("1968x2184",420)
            tag("reader-dual")
            assertEquals(2,vm.readerPage)
            ui.onNodeWithText("2–3").assertExists()
            screenshot("02-inner-dual")
            ui.runOnIdle { vm.setMode("rtl") }
            tag("reader-dual")
            ui.waitForIdle()
            val left=ui.onNodeWithTag("page-2").fetchSemanticsNode().boundsInRoot.left
            val right=ui.onNodeWithTag("page-1").fetchSemanticsNode().boundsInRoot.left
            assertTrue("RTL pages must reverse within the spread",left<right)
            resize("2184x1968",420)
            tag("reader-dual")
            assertEquals(2,vm.readerPage)
            screenshot("03-inner-landscape")
            // Startup notification/deep-link actions must not replay on activity recreation.
            ui.activityRule.scenario.onActivity { it.intent.putExtra("downloads",true) }
            ui.activityRule.scenario.recreate()
            tag("reader-dual")
            assertEquals("reader",vm.screen)
            assertEquals(2,vm.readerPage)
            resize("1080x2520",440)
            tag("reader-single")
            assertEquals(2,vm.readerPage)
            ui.onNodeWithText("3").assertExists()
            resize("990x2184",440)
            tag("reader-single")
            assertEquals(2,vm.readerPage)
            screenshot("04-split-window")
            // Returning to the same window size must not replay an old explicit jump or reset pages.
            ui.runOnIdle { vm.back();vm.back();vm.tab=1;vm.query="保留的搜索词";vm.results=listOf(fixture,fixture.copy(id="990002",title="第二本测试漫画"));vm.searched=true }
            resize("1968x2184",420)
            tag("side-navigation")
            assertEquals("保留的搜索词",vm.query)
            assertEquals(2,vm.results.size)
            screenshot("05-inner-search")
            ui.runOnIdle { vm.selected=fixture;vm.screen="detail" }
            tag("detail-split")
            screenshot("06-inner-detail")
            resize("1968x2184",480)
            tag("detail-split")
            screenshot("08-inner-display-zoom")
            resize("1080x2520",440)
            tag("detail-compact")
            ui.runOnIdle { vm.back();vm.tab=3 }
            ui.onNodeWithText("阅读偏好").assertExists()
            resize("2184x1968",420)
            tag("side-navigation")
            ui.onNodeWithText("阅读偏好").assertExists()
            ui.runOnIdle { vm.setAppearance("dark");vm.read(fixture,p.chapter) }
            tag("reader-dual")
            assertEquals(2,vm.readerPage)
            screenshot("07-inner-dark")
        } finally {
            device.executeShellCommand("wm size reset");device.executeShellCommand("wm density reset")
        }
    }
}
