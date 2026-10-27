package com.arnolee.moye

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class AppUpdateFlowTest {
    @get:Rule val ui=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val bytes="not an apk; tests must not open an installer".toByteArray()
    private fun digest(data: ByteArray)=MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
    private fun release(version: String="9.0.0",size: Long=bytes.size.toLong(),sha: String=digest(bytes))=AppRelease(version,"修复测试公告","${AppRelease.REPO}/releases/download/v$version/ComicReader-$version.apk",size,sha)
    private fun json(r: AppRelease)=JSONObject().put("tag_name","v${r.version}").put("body",r.notes).put("assets",JSONArray().put(JSONObject().put("name","ComicReader-${r.version}.apk").put("browser_download_url",r.url).put("size",r.size).put("digest","sha256:${r.sha256}"))).toString()
    private fun repository(metadata: String=json(release()),data: ByteArray=bytes,code: Int=200)=UpdateRepository(OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .body(if(chain.request().url.host=="api.github.com") metadata.toResponseBody() else data.toResponseBody()).build()
    }.build())
    @Test fun checkShowNotesRetryAndPersistAutomaticPreference() {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val prefs=context.getSharedPreferences("updates",0)
        val original=prefs.getBoolean("autoCheck",true)
        lateinit var controller: AppUpdateController
        ui.runOnIdle { controller=AppUpdateController(context,scope,repository()) }
        ui.setContent { MoyeTheme("dark") { Column { UpdateSettings(controller);UpdateDialog(controller,true) } } }
        try {
            ui.onNodeWithTag("check-update").performClick()
            ui.waitUntil(5000) { controller.showDialog }
            ui.onNodeWithText("修复测试公告").assertExists()
            ui.onNodeWithText("稍后再说").performClick()
            ui.onNodeWithTag("show-update").performClick()
            ui.onNodeWithTag("download-update").performClick()
            ui.waitUntil(5000) { controller.status.startsWith("下载失败") }
            assertFalse(controller.downloaded);assertFalse(controller.installRequested)
            ui.onNodeWithTag("download-update").assertIsEnabled()
            ui.onNodeWithText("稍后再说").performClick()
            ui.runOnIdle { controller.updateAutoCheck(false) }
            assertFalse(prefs.getBoolean("autoCheck",true))
            ui.runOnIdle { controller=AppUpdateController(context,scope,repository(code=403));controller.check() }
            ui.waitUntil(5000) { controller.status.startsWith("检查失败") }
            assertTrue(controller.status.contains("频率受限"))
        } finally { scope.cancel();prefs.edit().putBoolean("autoCheck",original).apply() }
    }
    @Test fun encodedDownloadChecksLengthDigestAndDeletesInvalidFiles()=runBlocking {
        val file=File(context.cacheDir,"update-flow-test.apk")
        try {
            repository().download(release(),file) {}
            assertArrayEquals(bytes,file.readBytes())
            assertTrue(runCatching { repository().download(release(sha="0".repeat(64)),file) {} }.isFailure)
            assertFalse(file.exists())
            assertTrue(runCatching { repository().download(release(size=bytes.size+1L),file) {} }.isFailure)
            assertFalse(file.exists())
        } finally { file.delete() }
    }
    @Test fun installerRejectsSameVersionWrongVersionAndTamperedPackage() {
        val file=File(context.applicationInfo.sourceDir)
        val data=file.readBytes();val r=release(BuildConfig.VERSION_NAME.substringBefore('-'),file.length(),digest(data))
        // Debug versionName includes -dev, which must also be checked, not silently ignored.
        assertTrue(runCatching { UpdateInstaller.verify(context,file,r.copy(version=BuildConfig.VERSION_NAME)) }.exceptionOrNull()?.message.orEmpty().contains("不高于"))
        assertTrue(runCatching { UpdateInstaller.verify(context,file,r.copy(version="99.0.0")) }.exceptionOrNull()?.message.orEmpty().contains("版本不匹配"))
        assertTrue(runCatching { UpdateInstaller.verify(context,file,r.copy(sha256="0".repeat(64))) }.exceptionOrNull()?.message.orEmpty().contains("校验失败"))
        val uri=UpdateInstaller.intent(context,File(context.filesDir,"updates/update.apk")).data!!
        assertEquals("content",uri.scheme);assertEquals("${context.packageName}.updates",uri.authority)
        assertTrue(runCatching { UpdateInstaller.intent(context,File(context.filesDir,"library.db")) }.isFailure)
    }
}
