package com.arnolee.moye

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object UpdateVersions {
    fun parts(value: String): List<Long>? {
        val match=Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)").matchEntire(value) ?: return null
        return match.groupValues.drop(1).map { it.toLongOrNull() ?: return null }
    }
    fun newer(candidate: String,current: String): Boolean {
        val a=parts(candidate) ?: return false
        val b=parts(current.substringBefore('-')) ?: return false
        return a.zip(b).firstOrNull { it.first!=it.second }?.let { it.first>it.second } ?: false
    }
}

data class AppRelease(val version: String,val notes: String,val url: String,val size: Long,val sha256: String) {
    companion object {
        const val REPO="https://github.com/hzxslzb/ComicReader"
        const val API="https://api.github.com/repos/hzxslzb/ComicReader/releases/latest"
        fun parse(json: String): AppRelease {
            val root=JSONObject(json)
            require(!root.optBoolean("draft") && !root.optBoolean("prerelease")) { "暂未发布正式版本" }
            val tag=root.getString("tag_name")
            require(UpdateVersions.parts(tag)!=null) { "版本信息格式不正确" }
            val version=tag.removePrefix("v")
            val name="ComicReader-$version.apk"
            val assets=root.getJSONArray("assets")
            val apk=(0 until assets.length()).map { assets.getJSONObject(it) }.singleOrNull { it.optString("name")==name }
                ?: throw IOException("该版本尚未上传完整安装包，请稍后重试")
            val url=apk.getString("browser_download_url")
            require(url=="$REPO/releases/download/$tag/$name") { "安装包来源不正确" }
            val digest=apk.optString("digest").removePrefix("sha256:").lowercase()
            require(digest.matches(Regex("[0-9a-f]{64}"))) { "该版本缺少 SHA-256 校验信息，请稍后重试" }
            val size=apk.getLong("size")
            require(size in 1..150L*1024*1024) { "安装包大小异常" }
            return AppRelease(version,root.optString("body").take(40000),url,size,digest)
        }
    }
}

class UpdateRepository(private val client: OkHttpClient=OkHttpClient.Builder()
    .connectTimeout(20,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(10,TimeUnit.MINUTES).build()) {
    private suspend fun <T> request(url: String,read: (Response)->T): T = suspendCancellableCoroutine { continuation ->
        val call=client.newCall(Request.Builder().url(url).header("Accept","application/vnd.github+json").header("User-Agent","ComicReader/${BuildConfig.VERSION_NAME}").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object: Callback {
            override fun onFailure(call: Call,e: IOException) { if(continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call,response: Response) {
                try {
                    val result=response.use {
                        if(!it.isSuccessful) throw IOException(when(it.code) { 403,429 -> "GitHub 请求频率受限，请稍后重试";else -> "更新服务器返回 ${it.code}，请稍后重试" })
                        read(it)
                    }
                    if(continuation.isActive) continuation.resume(result)
                } catch(e: Exception) { if(continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }
    suspend fun latest(): AppRelease = request(AppRelease.API) { response ->
        val body=response.body ?: throw IOException("更新信息为空")
        val bytes=body.byteStream().readBytesLimited(2*1024*1024)
        AppRelease.parse(bytes.toString(Charsets.UTF_8))
    }
    suspend fun download(release: AppRelease,file: File,progress: (Long)->Unit) = request(release.url) { response ->
        val body=response.body ?: throw IOException("安装包为空")
        require(body.contentLength()<0 || body.contentLength()==release.size) { "安装包长度不匹配，请重试" }
        val digest=MessageDigest.getInstance("SHA-256")
        var total=0L;var lastReport=0L
        try {
            file.parentFile!!.mkdirs()
            body.byteStream().use { input -> file.outputStream().use { output ->
                val buffer=ByteArray(64*1024)
                while(true) {
                    val count=input.read(buffer);if(count<0) break
                    total+=count
                    require(total<=release.size) { "安装包大小不匹配" }
                    output.write(buffer,0,count);digest.update(buffer,0,count)
                    val now=System.nanoTime()
                    if(now-lastReport>200_000_000L) { progress(total);lastReport=now }
                }
            } }
            require(total==release.size && digest.digest().hex()==release.sha256) { "安装包校验失败，请重新下载" }
            progress(total)
        } catch(e: Exception) { file.delete();throw e }
    }
}
private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true) { val n=read(buffer);if(n<0) break;require(out.size()+n<=limit) { "更新信息过大" };out.write(buffer,0,n) }
    return out.toByteArray()
}
private fun ByteArray.hex()=joinToString("") { "%02x".format(it) }

object UpdateInstaller {
    @Suppress("DEPRECATION") private fun signatures(info: PackageInfo): Set<String> =
        (if(Build.VERSION.SDK_INT>=28) info.signingInfo?.apkContentsSigners else info.signatures)
            ?.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }?.toSet() ?: emptySet()
    @Suppress("DEPRECATION") fun verify(context: Context,file: File,release: AppRelease) {
        require(file.isFile && file.length()==release.size) { "安装包不存在或不完整，请重新下载" }
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer=ByteArray(64*1024);while(true) { val n=input.read(buffer);if(n<0) break;digest.update(buffer,0,n) } }
        require(digest.digest().hex()==release.sha256) { "安装包校验失败，请重新下载" }
        val pm=context.packageManager
        val flags=if(Build.VERSION.SDK_INT>=28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val apk=pm.getPackageArchiveInfo(file.absolutePath,flags) ?: throw IOException("无法读取安装包")
        val installed=pm.getPackageInfo(context.packageName,flags)
        require(apk.packageName==context.packageName) { "安装包与当前应用不匹配；开发版请使用对应构建" }
        require(apk.versionName==release.version) { "安装包版本不匹配" }
        val next=if(Build.VERSION.SDK_INT>=28) apk.longVersionCode else apk.versionCode.toLong()
        val old=if(Build.VERSION.SDK_INT>=28) installed.longVersionCode else installed.versionCode.toLong()
        require(next>old) { "安装包版本不高于当前版本" }
        require(signatures(installed).isNotEmpty() && signatures(apk)==signatures(installed)) { "安装包签名与当前版本不同，无法覆盖安装" }
    }
    fun intent(context: Context,file: File): Intent {
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.updates",file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

class AppUpdateController(private val context: Context,private val scope: CoroutineScope,private val repository: UpdateRepository=UpdateRepository()) {
    private val prefs=context.getSharedPreferences("updates",Context.MODE_PRIVATE)
    private val dir=File(context.filesDir,"updates")
    val apk get()=File(dir,"update.apk")
    var release by mutableStateOf<AppRelease?>(null);private set
    var checking by mutableStateOf(false);private set
    var downloading by mutableStateOf(false);private set
    var downloaded by mutableStateOf(false);private set
    var bytes by mutableLongStateOf(0);private set
    var status by mutableStateOf("");private set
    var showDialog by mutableStateOf(false)
    var installRequested by mutableStateOf(false)
    var autoCheck by mutableStateOf(prefs.getBoolean("autoCheck",true));private set
    private var task: Job?=null
    private val restoreJob=scope.launch {
            // Recover a completed download after process death, but never auto-install on restart.
            val restored=withContext(Dispatchers.IO) { runCatching {
                val raw=File(dir,"release.json").takeIf { it.isFile }?.readText() ?: return@runCatching null
                val r=AppRelease.parse(raw)
                if(!UpdateVersions.newer(r.version,BuildConfig.VERSION_NAME)) { dir.deleteRecursively();return@runCatching null }
                UpdateInstaller.verify(context,apk,r);r
            }.getOrNull() }
            if(release==null && restored!=null) { release=restored;downloaded=true;status="安装包已下载，可继续安装" }
    }
    fun updateAutoCheck(value: Boolean) { autoCheck=value;prefs.edit().putBoolean("autoCheck",value).apply() }
    fun check(manual: Boolean=true) {
        if(task?.isActive==true) return
        if(!manual && (!autoCheck || System.currentTimeMillis()-prefs.getLong("lastCheck",0)<24*60*60*1000L)) return
        prefs.edit().putLong("lastCheck",System.currentTimeMillis()).apply()
        task=scope.launch {
            restoreJob.join()
            checking=true;status="正在检查更新…"
            try {
                val latest=repository.latest()
                if(UpdateVersions.newer(latest.version,BuildConfig.VERSION_NAME)) {
                    if(release!=latest) downloaded=false
                    release=latest;status="发现新版本 ${latest.version}";showDialog=true
                } else { release=null;downloaded=false;status="当前已是最新版本" }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { status=if(manual) "检查失败：${e.localizedMessage ?: "请检查网络后重试"}" else "自动检查未完成，可手动重试" }
            finally { checking=false }
        }
    }
    fun download() {
        val target=release ?: return
        if(task?.isActive==true) return
        task=scope.launch {
            downloading=true;downloaded=false;bytes=0;status="正在下载安装包…"
            val partial=File(dir,"update.part")
            try {
                repository.download(target,partial) { value -> scope.launch { if(downloading) bytes=value } }
                withContext(Dispatchers.IO) {
                    UpdateInstaller.verify(context,partial,target)
                    apk.delete();check(partial.renameTo(apk)) { "无法保存安装包，请重试" }
                    val metadata=JSONObject().put("tag_name","v${target.version}").put("body",target.notes)
                        .put("assets",org.json.JSONArray().put(JSONObject().put("name","ComicReader-${target.version}.apk").put("browser_download_url",target.url).put("size",target.size).put("digest","sha256:${target.sha256}")))
                    File(dir,"release.json").writeText(metadata.toString())
                }
                downloaded=true;status="下载完成，等待安装确认";installRequested=true
            } catch(e: CancellationException) { status="下载已取消，可重新下载";throw e }
            catch(e: Exception) { status="下载失败：${e.localizedMessage ?: "请重试"}" }
            finally { downloading=false;withContext(NonCancellable+Dispatchers.IO) { partial.delete() } }
        }
    }
    fun cancelDownload() { if(downloading) task?.cancel() }
    fun requestInstall() { if(downloaded) installRequested=true }
    fun installError(text: String) { status=text;showDialog=true }
    suspend fun verifyForInstall(): File {
        val target=release ?: throw IOException("请先检查并下载更新")
        return withContext(Dispatchers.IO) { UpdateInstaller.verify(context,apk,target);apk }
    }
}
