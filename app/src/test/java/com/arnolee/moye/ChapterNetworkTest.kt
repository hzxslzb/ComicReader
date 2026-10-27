package com.arnolee.moye

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class ChapterNetworkTest {
    @Test fun retriesTransientChapterFailureAndUsesBookReferer()=runBlocking {
        val calls=AtomicInteger()
        val fixture=javaClass.getResource("/chapter.html")!!.readText()
        val client=OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("https://m.manhuagui.com/comic/43847/",chain.request().header("Referer"))
            val n=calls.incrementAndGet()
            if(n==1) throw IOException("connection reset")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if(n==2) 503 else 200)
                .message("test").body((if(n==2) "busy" else fixture).toResponseBody()).build()
        }.build()
        assertEquals(17,MangaSource(client).pages("43847",Chapter("628732","一")).paths.size)
        assertEquals(3,calls.get())
    }
    @Test fun accessDeniedIsNotRetried()=runBlocking {
        val calls=AtomicInteger()
        val client=OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(403).message("denied").body("denied".toResponseBody()).build()
        }.build()
        try { MangaSource(client).pages("1",Chapter("2","二"));fail() }
        catch(e: IOException) { assertTrue(e.message!!.contains("403")) }
        assertEquals(1,calls.get())
    }
}
