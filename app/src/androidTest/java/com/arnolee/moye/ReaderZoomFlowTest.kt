package com.arnolee.moye

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
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

@RunWith(AndroidJUnit4::class)
class ReaderZoomFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var vm: AppViewModel
    private fun state()=ui.onNodeWithTag("reader-vertical-zoom").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
    private fun scale()=state().substringAfter("scale=").substringBefore(';').toFloat()
    private fun pan()=state().substringAfter("pan=").toFloat()
    private fun scroll()=ui.onNodeWithTag("reader-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun noOverlap() {
        val viewport=ui.onNodeWithTag("reader-viewport").fetchSemanticsNode().boundsInRoot
        val bar=ui.onNodeWithTag("reader-zoom-bar").fetchSemanticsNode().boundsInRoot
        assertTrue("Reset bar must be outside comic viewport",bar.top>=viewport.bottom-1)
        ui.onNodeWithTag("reader-reset-zoom").assertIsDisplayed()
    }
    private fun shot(name: String) {
        ui.waitForIdle();device.waitForIdle()
        val dir=File(ui.activity.getExternalFilesDir(null),"zoom-screenshots").apply { mkdirs() }
        device.takeScreenshot(File(dir,"$name.png"))
    }
    @Test fun pinchScrollPanAndResetOutsidePagesOnCoverAndInnerScreen() {
        ui.runOnIdle { vm=ViewModelProvider(ui.activity)[AppViewModel::class.java];vm.setAppearance("light");vm.setMode("vertical") }
        val book=Manga("990020","缩放与连续阅读验证",chapters=listOf(Chapter("990021","第一章"),Chapter("990022","第二章")))
        val chapter=ChapterPages(book.id,book.chapters.first(),(0..3).map { "/local/zoom-$it.png" },emptyMap(),null,"990022")
        val bitmap=Bitmap.createBitmap(800,1200,Bitmap.Config.ARGB_8888)
        for(i in 0..3) {
            val c=Canvas(bitmap);c.drawColor(Color.rgb(244,240,225))
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(40,80,60);textSize=48f;strokeWidth=4f }
            c.drawText("PAGE ${i+1}  /  ZOOM TEST",50f,100f,paint)
            for(y in 180..1100 step 120) { c.drawRect(50f,y.toFloat(),750f,(y+80).toFloat(),paint);paint.color=if(paint.color==Color.rgb(40,80,60)) Color.rgb(200,120,70) else Color.rgb(40,80,60) }
            vm.store.pageFile(book.id,chapter.chapter.id,i).also { it.parentFile!!.mkdirs();it.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG,100,out) } }
        }
        bitmap.recycle()
        ui.runOnIdle { vm.store.putBook(book);vm.store.putManifest(chapter);vm.store.saveProgress(book.id,chapter.chapter,0,4);vm.read(book) }
        ui.waitUntil(10_000) { !vm.readerLoading }
        try {
            for((name,size,density) in listOf(Triple("cover","1080x2520",440),Triple("inner","1968x2184",420))) {
                device.executeShellCommand("wm size $size");device.executeShellCommand("wm density $density")
                ui.waitUntil(20_000) { ui.onAllNodesWithTag("reader-vertical-zoom").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithTag("reader-list").performScrollToIndex(0)
                ui.waitUntil(10_000) { ui.onAllNodes(hasTestTag("page-0") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"loaded")).fetchSemanticsNodes().isNotEmpty() }
                // Two-finger pinch, followed by one-finger vertical scrolling and horizontal panning.
                ui.onNodeWithTag("reader-vertical-zoom").performTouchInput {
                    down(0,Offset(width*.4f,height*.45f));down(1,Offset(width*.6f,height*.45f))
                    for(step in 1..12) {
                        moveTo(0,Offset(width*(.4f-step*.015f),height*.45f))
                        moveTo(1,Offset(width*(.6f+step*.015f),height*.45f))
                    }
                    up(0);up(1)
                }
                ui.waitUntil(5_000) { scale()>1.5f }
                noOverlap()
                val start=scroll()
                ui.onNodeWithTag("reader-vertical-zoom").performTouchInput { swipeUp() }
                ui.waitUntil(5_000) { scroll()>start }
                val beforePan=pan()
                ui.onNodeWithTag("reader-vertical-zoom").performTouchInput { swipeLeft() }
                ui.waitUntil(5_000) { kotlin.math.abs(pan()-beforePan)>30f }
                shot("01-$name-zoomed")
                // Hide chrome; reset remains available in a separate row, never floating over the image.
                ui.onNodeWithTag("reader-vertical-zoom").performTouchInput { click() }
                ui.waitUntil(5_000) { ui.onAllNodesWithContentDescription("阅读设置").fetchSemanticsNodes().isEmpty() }
                noOverlap()
                ui.onNodeWithContentDescription("阅读设置").assertDoesNotExist()
                val page=vm.readerPage
                ui.onNodeWithTag("reader-reset-zoom").performClick()
                ui.waitUntil(5_000) { scale()==1f && pan()==0f }
                assertEquals(page,vm.readerPage)
                ui.onNodeWithTag("reader-reset-zoom").assertDoesNotExist()
                shot("02-$name-reset")
                // Double tap also zooms, and page jumps keep the enlarged scroll mode usable.
                ui.onNodeWithTag("reader-vertical-zoom").performTouchInput { doubleClick() }
                ui.waitUntil(5_000) { scale()>2f }
                ui.onNodeWithTag("reader-list").performScrollToIndex(2)
                ui.waitUntil(5_000) { vm.readerPage==2 }
                ui.onNodeWithTag("reader-reset-zoom").performClick()
                ui.waitUntil(5_000) { scale()==1f }
                assertEquals(2,vm.readerPage)
                ui.onNodeWithTag("reader-vertical-zoom").performTouchInput { click() }
                ui.waitUntil(5_000) { ui.onAllNodesWithContentDescription("阅读设置").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithContentDescription("阅读设置").assertExists()
            }
            // Paged mode uses the same reset control and never disables page swiping after reset.
            ui.runOnIdle { vm.setMode("ltr");vm.updateDualPage(false) }
            ui.onNodeWithTag("page-2").performTouchInput { doubleClick() }
            ui.waitUntil(5_000) { ui.onAllNodesWithTag("reader-reset-zoom").fetchSemanticsNodes().isNotEmpty() }
            noOverlap()
            ui.onNodeWithTag("reader-reset-zoom").performClick()
            ui.onNodeWithTag("reader-pager").performTouchInput { swipeLeft() }
            ui.waitUntil(5_000) { vm.readerPage==3 }
        } finally {
            device.executeShellCommand("wm size reset");device.executeShellCommand("wm density reset")
        }
    }
}
