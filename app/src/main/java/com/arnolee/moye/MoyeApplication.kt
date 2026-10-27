package com.arnolee.moye

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import java.io.File
import java.io.IOException

class MoyeApplication : Application(), ImageLoaderFactory {
    lateinit var store: LibraryStore
    lateinit var source: MangaSource
    override fun onCreate() {
        super.onCreate()
        store=LibraryStore(this)
        store.recoverInterrupted()
        source=MangaSource()
    }
    override fun newImageLoader(): ImageLoader {
        val client=source.client.newBuilder().addInterceptor { chain ->
            val request=chain.request().newBuilder().header("User-Agent",MangaSource.UA).apply { if(chain.request().header("Referer")==null) header("Referer",MangaSource.BASE+"/") }.build()
            if(!request.url.host.endsWith(".hamreus.com")) chain.proceed(request)
            else {
                var failure: IOException?=null;var result: okhttp3.Response?=null
                for(host in listOf(request.url.host,"eu1.hamreus.com","eu2.hamreus.com").distinct()) {
                    try {
                        val response=chain.proceed(request.newBuilder().url(request.url.newBuilder().host(host).build()).build())
                        if(response.isSuccessful) { result=response;break }
                        failure=IOException("图片加载失败（${response.code}）");response.close()
                    } catch(e: IOException) { if(chain.call().isCanceled()) throw e;failure=e }
                }
                result ?: throw (failure ?: IOException("图片服务器暂时不可用"))
            }
        }.build()
        return ImageLoader.Builder(this).okHttpClient(client).diskCache { DiskCache.Builder().directory(File(cacheDir,"images")).maxSizeBytes(150L*1024*1024).build() }.crossfade(180).respectCacheHeaders(false).build()
    }
}
