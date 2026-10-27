package com.arnolee.moye

import android.graphics.Bitmap
import android.os.Build
import org.junit.rules.ExternalResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import coil.imageLoader
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PreloadPreferencesTest {
    @get:Rule(order=0) val notificationPermission=object: ExternalResource() {
        override fun before() {
            if(Build.VERSION.SDK_INT>=33) device.executeShellCommand("pm grant ${InstrumentationRegistry.getInstrumentation().targetContext.packageName} android.permission.POST_NOTIFICATIONS")
        }
    }
    @get:Rule(order=1) val ui=createAndroidComposeRule<MainActivity>()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    @Test fun settingsPersistAndWifiLossCancelsOnlySpeculativeRequests() {
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard")
        ui.waitUntil(10_000) { ui.onAllNodes(isRoot()).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty() }
        lateinit var vm: AppViewModel
        ui.runOnIdle { vm=ViewModelProvider(ui.activity)[AppViewModel::class.java];vm.back();vm.screen="home";vm.tab=3 }
        val originalSettings=vm.preloadPreferences
        val originalSource=vm.app.source
        vm.app.imageLoader
        val release=CountDownLatch(1)
        val calls=AtomicInteger()
        val book=Manga("997700","预加载策略测试",chapters=listOf(Chapter("997701","一"),Chapter("997702","二")))
        val p=ChapterPages(book.id,book.chapters[0],listOf("/local-policy.png"),emptyMap(),null,book.chapters[1].id)
        val bitmap=Bitmap.createBitmap(100,200,Bitmap.Config.ARGB_8888)
        book.chapters.forEach { ch -> vm.store.pageFile(book.id,ch.id,0).also { it.parentFile!!.mkdirs();it.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG,100,out) } } }
        bitmap.recycle()
        vm.store.writableDatabase.delete("manifests","book=?",arrayOf(book.id))
        vm.store.putBook(book);vm.store.putManifest(p)
        vm.app.source=MangaSource(OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            while(!release.await(20,TimeUnit.MILLISECONDS)) {
                if(chain.call().isCanceled()) throw IOException("cancelled preload")
            }
            val data="""SMH.reader({"bookId":997700,"chapterId":997702,"chapterTitle":"二","count":1,"images":["/local-policy.png"],"sl":{},"prevId":997701,"nextId":0})"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(data.toResponseBody()).build()
        }.build())
        try {
            ui.runOnIdle { vm.updatePreload(PreloadPreferences(true,5,true)) }
            ui.onNodeWithTag("preload-pages-3").performScrollTo().performClick()
            ui.onNodeWithTag("preload-wifi").performScrollTo().performClick()
            assertEquals(PreloadPreferences(true,3,false),PreloadPreferences.load(vm.prefs))
            device.takeScreenshot(java.io.File(ui.activity.getExternalFilesDir(null),"preload-settings.png"))
            ui.onNodeWithTag("preload-enabled").performScrollTo().performClick()
            ui.onNodeWithTag("preload-pages-3").assertIsNotEnabled()
            ui.onNodeWithTag("preload-wifi").assertIsNotEnabled()
            ui.activityRule.scenario.recreate()
            ui.runOnIdle { vm=ViewModelProvider(ui.activity)[AppViewModel::class.java] }
            assertEquals(PreloadPreferences(false,3,false),vm.preloadPreferences)
            ui.runOnIdle { vm.read(book,book.chapters[0]) }
            ui.waitUntil(5_000) { vm.pages!=null && !vm.readerLoading }
            ui.waitForIdle();assertEquals(0,calls.get())
            device.executeShellCommand("svc wifi enable");device.executeShellCommand("svc data enable")
            ui.waitUntil(20_000) { vm.preloadWifi }
            ui.runOnIdle { vm.updatePreload(PreloadPreferences(true,3,true)) }
            ui.waitUntil(5_000) { calls.get()==1 }
            device.executeShellCommand("svc wifi disable")
            ui.waitUntil(20_000) { !vm.preloadWifi && vm.app.source.client.dispatcher.runningCallsCount()==0 }
            assertEquals(1,calls.get());assertEquals("997701",vm.pages!!.chapter.id);assertNull(vm.readerError)
            device.executeShellCommand("svc wifi enable")
            ui.waitUntil(20_000) { vm.preloadWifi && calls.get()==2 }
            // Joining from foreground must survive a subsequent preload opt-out.
            ui.runOnIdle { vm.adjacent(true);vm.updatePreload(PreloadPreferences(false,4,true)) }
            ui.waitForIdle();assertEquals(2,calls.get());assertTrue(vm.readerLoading)
            assertEquals(1,vm.app.source.client.dispatcher.runningCallsCount())
            release.countDown()
            ui.waitUntil(5_000) { !vm.readerLoading && vm.pages?.chapter?.id=="997702" }
            assertNull(vm.readerError);assertEquals(2,calls.get())
        } finally {
            release.countDown()
            ui.runOnIdle { vm.back();vm.updatePreload(originalSettings) }
            vm.app.source=originalSource
            device.executeShellCommand("svc wifi enable");device.executeShellCommand("svc data enable")
        }
    }
}
