package com.arnolee.moye

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ReaderLoadingTest {
    private val ch=Chapter("2","第二章")
    private val pages=ChapterPages("1",ch,listOf("/one.png"),emptyMap(),"1",null)
    @Test fun foregroundJoinsPreloadAndCancelledWaiterCannotCancelSharedChapter()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            val gate=CompletableDeferred<Unit>();var calls=0
            val loader=ReaderChapterLoader(scope,{ _,_ -> calls++;gate.await();pages },{ _,_ -> null },{},{ false })
            val preload=async(start=CoroutineStart.UNDISPATCHED) { loader.get("1",ch) }
            val foreground=async(start=CoroutineStart.UNDISPATCHED) { loader.get("1",ch) }
            preload.cancelAndJoin();gate.complete(Unit)
            assertEquals(pages,foreground.await());assertEquals(1,calls)
            assertEquals(pages,loader.get("1",ch));assertEquals(1,calls)
        } finally { scope.cancel() }
    }
    @Test fun failedSpeculationRetriesAndOfflineCompleteNeverRequestsNetwork()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            var calls=0
            val loader=ReaderChapterLoader(scope,{ _,_ -> calls++;if(calls==1) throw IOException("temporary");pages },{ _,_ -> null },{},{ false })
            try { loader.get("1",ch);fail() } catch(_: IOException) {}
            assertEquals(pages,loader.get("1",ch));assertEquals(2,calls)
            val offline=ReaderChapterLoader(scope,{ _,_ -> error("network must not run") },{ _,_ -> pages },{},{ true })
            assertEquals(pages,offline.get("1",ch))
        } finally { scope.cancel() }
    }
    @Test fun preloadWindowIsBoundedAndCrossesChapterBoundary() {
        assertEquals(listOf(1,2,3,4,5),ReaderPreloadWindow.following(0,17))
        assertEquals(0,ReaderPreloadWindow.nextChapterCount(0,17))
        assertEquals(listOf(15,16),ReaderPreloadWindow.following(14,17))
        assertEquals(3,ReaderPreloadWindow.nextChapterCount(14,17))
        assertEquals(emptyList<Int>(),ReaderPreloadWindow.following(16,17))
        assertEquals(5,ReaderPreloadWindow.nextChapterCount(16,17))
        assertEquals(5,ReaderPreloadWindow.nextChapterCount(0,1))
    }
    @Test fun staleManifestRefreshesAndLeavingReaderCancelsOutstandingWork()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        try {
            var clock=0L;var calls=0
            val gate=CompletableDeferred<Unit>()
            val loader=ReaderChapterLoader(scope,{ _,_ -> calls++;if(calls>1) gate.await();pages },{ _,_ -> null },{},{ false },{ clock })
            loader.get("1",ch);clock=120_001
            val waiting=async(start=CoroutineStart.UNDISPATCHED) { loader.get("1",ch) }
            assertEquals(2,calls);loader.clear()
            try { waiting.await();fail() } catch(_: CancellationException) {}
        } finally { scope.cancel() }
    }
}
