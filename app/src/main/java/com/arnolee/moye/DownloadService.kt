package com.arnolee.moye

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException

class DownloadService : Service() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var worker: Job?=null
    private var wake: PowerManager.WakeLock?=null
    private val app get()=application as MoyeApplication
    private val store get()=app.store
    companion object {
        fun start(context: Context) { context.startForegroundService(Intent(context,DownloadService::class.java)) }
    }
    override fun onBind(intent: Intent?): IBinder?=null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("downloads","漫画下载",NotificationManager.IMPORTANCE_LOW))
        wake=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"moye:downloads").apply { acquire(6*60*60*1000L) }
    }
    private fun notification(text: String,done: Int=0,total: Int=0): Notification = Notification.Builder(this,"downloads").setSmallIcon(R.drawable.ic_notification).setContentTitle("ComicReader · 离线下载").setContentText(text).setContentIntent(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).putExtra("downloads",true),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).setOngoing(true).setOnlyAlertOnce(true).setProgress(total,done,total==0).build()
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(Build.VERSION.SDK_INT>=29) startForeground(7,notification("正在准备下载"),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(7,notification("正在准备下载"))
        if(worker?.isActive!=true) worker=scope.launch {
            try {
                while(isActive) {
                    val d=store.downloads().firstOrNull { it.state=="queued" } ?: break
                    download(d)
                }
            } finally { withContext(Dispatchers.Main) { worker=null; if(store.downloads().none { it.state=="queued" }) { stopForeground(STOP_FOREGROUND_REMOVE);stopSelfResult(startId) } else onStartCommand(null,0,startId) } }
        }
        return START_NOT_STICKY
    }
    private suspend fun download(d: Download) {
        store.updateDownload(d.bookId,d.chapterId,"downloading",onlyIf="queued")
        try {
            val p=app.source.pages(d.bookId,Chapter(d.chapterId,d.title))
            ensureRunning(d)
            store.putManifest(p)
            var done=p.paths.indices.count { store.available(d.bookId,d.chapterId,it) }
            store.updateDownload(d.bookId,d.chapterId,"downloading",done,p.paths.size,onlyIf="downloading")
            for(index in p.paths.indices) {
                ensureRunning(d)
                if(store.available(d.bookId,d.chapterId,index)) continue
                var last: Exception?=null
                for(attempt in 0..2) {
                    ensureRunning(d)
                    try { fetch(p,d,index, listOf("eu","eu1","eu2")[attempt]);last=null;break }
                    catch(e: CancellationException) { throw e }
                    catch(e: Exception) { last=e;if(attempt<2) delay(800L*(attempt+1)) }
                }
                last?.let { throw it }
                ensureRunning(d)
                done++
                store.updateDownload(d.bookId,d.chapterId,"downloading",done,p.paths.size,onlyIf="downloading")
                getSystemService(NotificationManager::class.java).notify(7,notification("${store.book(d.bookId)?.title} · ${d.title} · $done/${p.paths.size}",done,p.paths.size))
                delay(120)
            }
            ensureRunning(d)
            check(store.complete(p)) { "部分图片尚未保存" }
            store.updateDownload(d.bookId,d.chapterId,"complete",p.paths.size,p.paths.size,onlyIf="downloading")
        } catch(e: CancellationException) {
            store.updateDownload(d.bookId,d.chapterId,"paused",onlyIf="downloading")
        } catch(e: Exception) {
            store.updateDownload(d.bookId,d.chapterId,"failed",error=e.localizedMessage ?: "网络异常，请重试",onlyIf="downloading")
        }
    }
    private suspend fun ensureRunning(d: Download) { currentCoroutineContext().ensureActive();if(store.download(d.bookId,d.chapterId)?.state!="downloading") throw CancellationException("任务已暂停") }
    private suspend fun fetch(p: ChapterPages,d: Download,index: Int,host: String) {
        val dest=store.pageFile(d.bookId,d.chapterId,index)
        dest.parentFile!!.mkdirs()
        val temp=File.createTempFile("page-",".part",dest.parentFile)
        try {
            app.source.client.newCall(app.source.request(p.url(index,host),p.referer).build()).execute().use { r ->
                if(!r.isSuccessful) throw IOException("图片请求失败（${r.code}）")
                val body=r.body ?: throw IOException("图片为空")
                if(body.contentLength()>60L*1024*1024) throw IOException("单张图片过大")
                body.byteStream().use { input -> temp.outputStream().use { out ->
                    val buffer=ByteArray(16*1024);var length=0L
                    while(true) { ensureRunning(d);val n=input.read(buffer);if(n<0) break;length+=n;if(length>60L*1024*1024) throw IOException("单张图片过大");out.write(buffer,0,n) }
                    out.fd.sync()
                } }
                val opts=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(temp.path,opts)
                if(opts.outWidth<=0 || opts.outHeight<=0) throw IOException("图片校验失败，请重试")
                if(!store.commitPage(d,index,temp)) throw CancellationException()
            }
        } finally { temp.delete() }
    }
    override fun onTimeout(startId: Int,fgsType: Int) { worker?.cancel();stopSelf() }
    override fun onDestroy() { scope.cancel();wake?.let { if(it.isHeld) it.release() };super.onDestroy() }
}
