package com.arnolee.moye

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.request.CachePolicy
import coil.request.ImageRequest

/** Stable encoded entries are shared by disk-only prefetches and visible decoded images. */
object ReaderImages {
    fun key(p: ChapterPages,index: Int)="reader/${p.mangaId}/${p.chapter.id}/$index/${p.paths[index]}"
    // Coil 2.7 supports per-request decoders; return no bitmap after the HTTP fetch commits to disk.
    private val diskOnly=Decoder.Factory { _,_,_ -> object: Decoder {
        override suspend fun decode()=DecodeResult(ColorDrawable(Color.TRANSPARENT),false)
    } }
    fun request(app: MoyeApplication,p: ChapterPages,index: Int,width: Int,retry: Int=0,preload: Boolean=false): ImageRequest {
        val local=app.store.pageFile(p.mangaId,p.chapter.id,index)
        val cacheKey=key(p,index)
        return ImageRequest.Builder(app)
            .data(if(local.isFile && local.length()>0) local else p.url(index,listOf("eu","eu1","eu2")[retry%3]))
            .diskCacheKey(cacheKey)
            .memoryCacheKey(cacheKey)
            .addHeader("Referer",p.referer)
            .size(if(preload) 1 else width,if(preload) 1 else 4096)
            .memoryCachePolicy(if(preload) CachePolicy.DISABLED else CachePolicy.ENABLED)
            .apply { if(preload) decoderFactory(diskOnly) else placeholderMemoryCacheKey(cacheKey) }
            .crossfade(false).build()
    }
}
