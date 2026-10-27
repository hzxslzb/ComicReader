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
    private class Flight(val task: Deferred<ChapterPages>,var readers: Int=0)
    private val flights=mutableMapOf<String,Flight>()
    private suspend fun join(key: String,flight: Flight): ChapterPages {
        flight.readers++
        try { return flight.task.await() } finally {
            flight.readers--
            if(flight.readers==0 && flight.task.isActive) {
                if(flights[key]===flight) flights.remove(key)
                flight.task.cancel()
            }
        }
    }
    suspend fun get(book: String,chapter: Chapter): ChapterPages {
        val key="$book/${chapter.id}"
        fresh[key]?.takeIf { now()-it.first in 0..120_000 }?.let { return it.second }
        val existing=flights[key]
        if(existing!=null && !existing.task.isCancelled) return join(key,existing)
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
        val flight=Flight(task)
        flights[key]=flight
        task.invokeOnCompletion { if(flights[key]===flight) flights.remove(key) }
        return join(key,flight)
    }
    fun clear() {
        val pending=flights.values.toList()
        flights.clear();fresh.clear()
        pending.forEach { it.task.cancel() }
    }
}

object ReaderPreloadWindow {
    const val SIZE=5
    fun following(page: Int,count: Int,size: Int=SIZE): List<Int> =
        ((page+1).coerceAtLeast(0)..minOf(page+size.coerceIn(3,5),count-1)).toList()
    fun nextChapterCount(page: Int,count: Int,size: Int=SIZE) = (size.coerceIn(3,5)-following(page,count,size).size).coerceIn(0,size.coerceIn(3,5))
}
