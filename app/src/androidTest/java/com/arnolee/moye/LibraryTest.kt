package com.arnolee.moye

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryTest {
    @Test fun multipleBooksKeepIndependentChapterHistoryEvenWithIdenticalChapterIdsAndTimes() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LibraryStore(context)
        val a=Manga("999999991","漫画 A",chapters=listOf(Chapter("1","A 第一章"),Chapter("2","A 第二章")))
        val b=Manga("999999992","漫画 B",chapters=listOf(Chapter("1","B 第一章")))
        db.putBook(a);db.putBook(b)
        db.saveProgress(a.id,a.chapters[0],4,10);db.saveProgress(a.id,a.chapters[1],7,12)
        db.saveProgress(b.id,b.chapters[0],2,20)
        db.writableDatabase.execSQL("UPDATE progress SET time=123 WHERE book IN (?,?)",arrayOf(a.id,b.id))
        db.close()
        val reopened=LibraryStore(context)
        try {
            assertEquals(listOf("2","1"),reopened.chapterHistory(a.id).map { it.chapterId })
            assertEquals(listOf(7,4),reopened.chapterHistory(a.id).map { it.page })
            assertEquals(2,reopened.chapterHistory(b.id).single().page)
            assertEquals("2",reopened.progress(a.id)!!.chapterId)
            assertEquals("1",reopened.progress(b.id)!!.chapterId)
            assertTrue(reopened.history().any { it.bookId==a.id })
            assertTrue(reopened.history().any { it.bookId==b.id })
        } finally {
            for(id in listOf(a.id,b.id)) {
                reopened.writableDatabase.delete("progress","book=?",arrayOf(id))
                reopened.writableDatabase.delete("books","id=?",arrayOf(id))
            }
            reopened.close()
        }
    }
    @Test fun chapterProgressSurvivesReopenAndDeletingDownloadsKeepsLibrary() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LibraryStore(context)
        val b=Manga("999999999","测试漫画",chapters=listOf(Chapter("111111","第一章"),Chapter("222222","第二章")))
        db.putBook(b)
        if(b.id !in db.favorites()) db.toggleFavorite(b.id)
        db.saveProgress(b.id,b.chapters[0],4,17)
        db.saveProgress(b.id,b.chapters[1],2,20)
        db.enqueue(b,b.chapters)
        db.updateDownload(b.id,"111111","downloading",1,17)
        db.close()
        val reopened=LibraryStore(context)
        assertEquals(4,reopened.progress(b.id,"111111")!!.page)
        assertEquals(2,reopened.progress(b.id,"222222")!!.page)
        assertTrue(b.id in reopened.favorites())
        reopened.putBook(b.copy(chapters=emptyList()))
        assertEquals(2,reopened.book(b.id)!!.chapters.size)
        reopened.recoverInterrupted()
        assertEquals("paused",reopened.download(b.id,"111111")!!.state)
        reopened.downloads().filter { it.bookId==b.id }.forEach { reopened.removeDownload(it) }
        assertEquals(4,reopened.progress(b.id,"111111")!!.page)
        assertTrue(b.id in reopened.favorites())
        reopened.writableDatabase.delete("progress","book=?",arrayOf(b.id))
        reopened.writableDatabase.delete("books","id=?",arrayOf(b.id))
        reopened.close()
    }
}
