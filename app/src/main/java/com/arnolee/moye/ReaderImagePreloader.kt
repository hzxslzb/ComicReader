package com.arnolee.moye

import kotlinx.coroutines.*

/** Main-thread owned, single-image queue. Sliding a window keeps useful work alive. */
class ReaderImagePreloader<T>(
    private val scope: CoroutineScope,
    private val key: (T)->String,
    private val load: suspend (T)->Unit
) {
    private class Flight(val key: String,val task: Deferred<Unit>,var readers: Int=0)
    private var desired=linkedMapOf<String,T>()
    private val attempted=mutableSetOf<String>()
    private var retained=emptySet<String>()
    private var active: Flight?=null

    fun update(items: List<T>,visible: Set<String> = emptySet()) {
        desired=linkedMapOf<String,T>().apply { items.forEach { put(key(it),it) } }
        retained=visible
        attempted.retainAll(desired.keys)
        active?.let { if(it.key !in desired && it.key !in retained && it.readers==0) it.task.cancel() }
        pump()
    }
    private fun pump() {
        if(active!=null || !scope.isActive) return
        val entry=desired.entries.firstOrNull { it.key !in attempted } ?: return
        val flight=Flight(entry.key,scope.async(start=CoroutineStart.LAZY) { load(entry.value) })
        active=flight
        scope.launch(start=CoroutineStart.UNDISPATCHED) {
            try {
                flight.task.await()
                if(flight.key in desired) attempted.add(flight.key)
            } catch(e: CancellationException) {
                // A policy check may cancel before the network callback clears this window.
                // Do not immediately restart the same disallowed request in a tight loop.
                if(flight.key in desired) attempted.add(flight.key)
            } catch(_: Exception) {
                // Do not spin on a failed speculative image. Visible requests can retry normally.
                if(flight.key in desired) attempted.add(flight.key)
            } finally {
                if(active===flight) active=null
                pump()
            }
        }
    }
    /** A visible image can take over an existing disk fetch without downloading it twice. */
    suspend fun awaitExisting(imageKey: String) {
        val flight=active?.takeIf { it.key==imageKey && !it.task.isCancelled } ?: return
        flight.readers++
        try { flight.task.await() }
        catch(e: CancellationException) { currentCoroutineContext().ensureActive() }
        catch(_: Exception) { /* Let the normal display request handle errors/retries. */ }
        finally {
            flight.readers--
            if(flight.readers==0 && flight.key !in desired && flight.key !in retained) flight.task.cancel()
        }
    }
    fun clear(cancelVisible: Boolean=false) {
        desired.clear();attempted.clear();retained=emptySet()
        active?.let { if(cancelVisible || it.readers==0) it.task.cancel() }
    }
}

/** A few stable decode sizes; zoom gestures don't create a new resolution for each pixel. */
object ReaderDecodeSize {
    fun width(pixels: Int): Int = listOf(640,960,1280,1600,1920,2400).firstOrNull { it>=pixels } ?: 2400
}
