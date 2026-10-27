package com.arnolee.moye

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ReaderImagePreloaderTest {
    @Test fun slidingFivePageWindowsFetchEachImageOnlyOnce()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val calls=mutableListOf<Int>()
            val queue=ReaderImagePreloader<Int>(scope,{ it.toString() },{ calls.add(it) })
            for(page in 0..10) queue.update((page+1..page+5).toList())
            assertEquals((1..15).toList(),calls)
        } finally { scope.cancel() }
    }
    @Test fun overlappingInFlightImageSurvivesAndOutdatedImageIsCancelled()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val started=mutableListOf<Int>();val cancelled=mutableListOf<Int>()
            val queue=ReaderImagePreloader<Int>(scope,{ it.toString() },{ n ->
                started.add(n)
                try { awaitCancellation() } finally { cancelled.add(n) }
            })
            queue.update(listOf(1,2,3));queue.update(listOf(1,2,3,4))
            assertEquals(listOf(1),started);assertTrue(cancelled.isEmpty())
            queue.update(listOf(4,5,6))
            assertEquals(listOf(1),cancelled);assertEquals(listOf(1,4),started)
            queue.clear();assertEquals(listOf(1,4),cancelled)
        } finally { scope.cancel() }
    }
    @Test fun foregroundTakeoverSurvivesOptOutButBackgroundClearCancels()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val gate=CompletableDeferred<Unit>();var calls=0
            val queue=ReaderImagePreloader<Int>(scope,{ it.toString() },{ calls++;gate.await() })
            queue.update(listOf(1,2,3))
            val foreground=async(start=CoroutineStart.UNDISPATCHED) { queue.awaitExisting("1") }
            queue.clear()
            assertFalse(foreground.isCompleted);assertEquals(1,calls)
            gate.complete(Unit);foreground.await();assertEquals(1,calls)
            val stopped=ReaderImagePreloader<Int>(scope,{ it.toString() },{ awaitCancellation() })
            stopped.update(listOf(1))
            val visible=async(start=CoroutineStart.UNDISPATCHED) { stopped.awaitExisting("1") }
            stopped.clear(cancelVisible=true)
            visible.await() // Cancelled speculation falls back to the ordinary display request.
        } finally { scope.cancel() }
    }
    @Test fun failedSpeculationDoesNotSpinAndCanRetryAfterRestart()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            var calls=0
            val queue=ReaderImagePreloader<Int>(scope,{ it.toString() },{ calls++;throw IOException("offline") })
            queue.update(listOf(1,2));queue.update(listOf(1,2,3))
            assertEquals(3,calls)
            queue.clear();queue.update(listOf(1));assertEquals(4,calls)
        } finally { scope.cancel() }
    }
    @Test fun visiblePageIsRetainedEvenWhenItLeavesFutureWindow()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            var cancelled=false
            val queue=ReaderImagePreloader<Int>(scope,{ it.toString() },{
                try { awaitCancellation() } finally { cancelled=true }
            })
            queue.update(listOf(1,2,3));queue.update(listOf(2,3,4),setOf("1"))
            assertFalse(cancelled)
            queue.clear();assertTrue(cancelled)
        } finally { scope.cancel() }
    }
    @Test fun policyCancellationDoesNotRestartInATightLoop()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            var calls=0
            val queue=ReaderImagePreloader<Int>(scope,{ it.toString() },{ calls++;throw CancellationException("policy") })
            queue.update(listOf(1,2,3));assertEquals(3,calls)
            queue.clear();queue.update(listOf(1));assertEquals(4,calls)
        } finally { scope.cancel() }
    }
    @Test fun decodeBucketsFitCoverInnerAndZoomWithoutExceedingExistingLimit() {
        assertEquals(640,ReaderDecodeSize.width(540))
        assertEquals(1280,ReaderDecodeSize.width(1080))
        assertEquals(1920,ReaderDecodeSize.width(1890))
        assertEquals(2400,ReaderDecodeSize.width(4320))
        assertEquals(ReaderDecodeSize.width(1080),ReaderDecodeSize.width(1100))
    }
}
