package com.arnolee.moye

import coil.request.CachePolicy
import coil.request.ImageRequest

/** Preloads and visible pages share encoded disk entries, independent of decode size/CDN host. */
object ReaderImages {
    fun request(app: MoyeApplication,p: ChapterPages,index: Int,width: Int,retry: Int=0,preload: Boolean=false): ImageRequest {
        val local=app.store.pageFile(p.mangaId,p.chapter.id,index)
        return ImageRequest.Builder(app)
            .data(if(local.isFile && local.length()>0) local else p.url(index,listOf("eu","eu1","eu2")[retry%3]))
            .diskCacheKey("reader/${p.mangaId}/${p.chapter.id}/$index/${p.paths[index]}")
            .addHeader("Referer",p.referer)
            .size(if(preload) 1 else width,if(preload) 1 else 4096)
            .memoryCachePolicy(if(preload) CachePolicy.DISABLED else CachePolicy.ENABLED)
            .crossfade(false).build()
    }
}
