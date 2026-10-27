package com.arnolee.moye

import kotlinx.coroutines.*

/** Main-thread owned: a foreground chapter switch joins any existing speculative request. */
class ReaderChapterLoader(
    private val scope: CoroutineScope,
    private val load: suspend (String, Chapter) -> ChapterPages,
    private val saved: (String, String) -> ChapterPages?,
    private val save: (ChapterPages) -> Unit,
    private val complete: (ChapterPages) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val fresh=linkedMapOf<String,Pair<Long,ChapterPages>>()
    private val flights=mutableMapOf<String,Deferred<ChapterPages>>()
    suspend fun get(book: String,chapter: Chapter): ChapterPages {
        val key="$book/${chapter.id}"
        fresh[key]?.takeIf { now()-it.first in 0..120_000 }?.let { return it.second }
        val existing=flights[key]
        if(existing!=null) return existing.await()
        val task=scope.async(start=CoroutineStart.LAZY) {
            val cached=saved(book,chapter.id)
            val result=if(cached!=null && complete(cached)) cached else try {
                load(book,chapter).also(save)
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { cached ?: throw e }
            fresh.remove(key);fresh[key]=now() to result
            while(fresh.size>3) fresh.remove(fresh.keys.first())
            result
        }
        flights[key]=task
        task.invokeOnCompletion { if(flights[key]===task) flights.remove(key) }
        task.start()
        return task.await()
    }
    fun clear() {
        val pending=flights.values.toList()
        flights.clear();fresh.clear()
        pending.forEach { it.cancel() }
    }
}

object ReaderPreloadWindow {
    const val SIZE=5
    fun following(page: Int,count: Int): List<Int> =
        ((page+1).coerceAtLeast(0)..minOf(page+SIZE,count-1)).toList()
    fun nextChapterCount(page: Int,count: Int) = (SIZE-following(page,count).size).coerceIn(0,SIZE)
}
