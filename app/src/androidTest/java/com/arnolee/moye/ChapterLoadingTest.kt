package com.arnolee.moye

import android.graphics.Bitmap
import coil.imageLoader
import coil.ImageLoader
import coil.disk.DiskCache
import coil.request.SuccessResult
import coil.decode.DataSource
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ChapterLoadingTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun supersededRequestCannotClearNewChapterLoadingOrShowError() {
        lateinit var vm: AppViewModel
        ui.runOnIdle { vm=ViewModelProvider(ui.activity)[AppViewModel::class.java] }
        val app=vm.app;val original=app.source
        app.imageLoader // Initialize production image transport before injecting chapter-only test responses.
        val first=CountDownLatch(1);val second=CountDownLatch(1);val release=CountDownLatch(1)
        val releaseFirst=CountDownLatch(1);val firstFinished=CountDownLatch(1)
        val calls=AtomicInteger()
        val book=Manga("997601","切章请求竞争测试",chapters=listOf(Chapter("997602","一"),Chapter("997603","二")))
        // Force fresh manifests while keeping generated images local to avoid unrelated networking.
        app.store.writableDatabase.delete("manifests","book=?",arrayOf(book.id))
        val bitmap=Bitmap.createBitmap(100,200,Bitmap.Config.ARGB_8888)
        book.chapters.forEach { ch ->
            app.store.pageFile(book.id,ch.id,0).also { it.parentFile!!.mkdirs();it.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG,100,out) } }
        }
        bitmap.recycle()
        app.source=MangaSource(OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            val id=chain.request().url.pathSegments.last().substringBefore('.')
            if(id=="997602") { first.countDown();releaseFirst.await(10,TimeUnit.SECONDS);firstFinished.countDown() }
            else { second.countDown();release.await(10,TimeUnit.SECONDS) }
            val data="""SMH.reader({"bookId":997601,"chapterId":$id,"chapterTitle":"test","count":1,"images":["/local.png"],"sl":{},"prevId":0,"nextId":0})"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(data.toResponseBody()).build()
        }.build())
        try {
            ui.runOnIdle { vm.read(book,book.chapters[0]) }
            assertTrue(first.await(5,TimeUnit.SECONDS))
            ui.runOnIdle { vm.read(book,book.chapters[1]) }
            assertTrue(second.await(5,TimeUnit.SECONDS))
            releaseFirst.countDown();assertTrue(firstFinished.await(5,TimeUnit.SECONDS))
            // Wait until the older request has unwound on the main dispatcher.
            ui.waitUntil(5_000) { app.source.client.dispatcher.runningCallsCount()==1 }
            ui.waitForIdle()
            ui.runOnIdle {
                assertTrue("An old request must not close the new loading state",vm.readerLoading)
                assertNull(vm.readerError);assertNull(vm.pages)
            }
            release.countDown()
            ui.waitUntil(10_000) { !vm.readerLoading && vm.pages?.chapter?.id=="997603" }
            assertEquals(2,calls.get())
        } finally { releaseFirst.countDown();release.countDown();ui.runOnIdle { vm.back() };app.source=original }
    }
    @Test fun fivePreloadedPagesAreReusableAtFullSizeWithoutNetwork()=runBlocking {
        val app=ui.activity.application as MoyeApplication
        val bitmap=Bitmap.createBitmap(800,1200,Bitmap.Config.ARGB_8888)
        val bytes=ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
        bitmap.recycle()
        val calls=AtomicInteger();var offline=false
        val client=OkHttpClient.Builder().addInterceptor { chain ->
            if(offline) throw java.io.IOException("test network disabled")
            calls.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type","image/png").body(bytes.toResponseBody()).build()
        }.build()
        val dir=File(app.cacheDir,"preload-test-${System.nanoTime()}")
        val loader=ImageLoader.Builder(app).okHttpClient(client).respectCacheHeaders(false)
            .diskCache { DiskCache.Builder().directory(dir).maxSizeBytes(10L*1024*1024).build() }.build()
        val p=ChapterPages("997610",Chapter("997611","预加载"),(0..5).map { "/test-$it.png" },emptyMap(),null,null)
        try {
            for(i in ReaderPreloadWindow.following(0,6)) {
                assertTrue(loader.execute(ReaderImages.request(app,p,i,1,preload=true)) is SuccessResult)
            }
            assertEquals(5,calls.get());offline=true
            for(i in 1..5) {
                val result=loader.execute(ReaderImages.request(app,p,i,1200,retry=1))
                assertTrue("Preloaded page $i must render offline",result is SuccessResult)
                assertEquals(DataSource.DISK,(result as SuccessResult).dataSource)
                assertTrue(result.drawable.intrinsicWidth>1)
            }
            assertEquals(5,calls.get())
        } finally { loader.shutdown();dir.deleteRecursively() }
    }
    @Test fun liveEndSwipeLoadsNextChapterAndItsFirstImage() {
        lateinit var vm: AppViewModel
        ui.runOnIdle { vm=ViewModelProvider(ui.activity)[AppViewModel::class.java];vm.setMode("ltr");vm.updateDualPage(false);vm.openBook("43847") }
        ui.waitUntil(60_000) { !vm.detailLoading && vm.selected?.chapters?.isNotEmpty()==true }
        val book=vm.selected!!
        ui.runOnIdle { vm.store.saveProgress(book.id,book.chapters[0],16,17);vm.store.saveProgress(book.id,book.chapters[1],0,17);vm.read(book,book.chapters[0]) }
        ui.waitUntil(60_000) { !vm.readerLoading && vm.pages!=null }
        assertNull(vm.readerError);assertEquals(16,vm.readerPage)
        ui.onNodeWithTag("reader-pager").performTouchInput { swipeLeft() }
        ui.waitUntil(60_000) { !vm.readerLoading && vm.pages?.chapter?.id=="633620" }
        assertNull(vm.readerError)
        ui.waitUntil(90_000) { ui.onAllNodes(hasTestTag("page-0") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"loaded")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("628732",vm.pages!!.prevId)
        assertEquals(16,vm.store.progress(book.id,"628732")!!.page)
    }
}
