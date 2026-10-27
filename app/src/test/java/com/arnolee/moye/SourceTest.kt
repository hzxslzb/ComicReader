package com.arnolee.moye

import org.junit.Assert.*
import org.junit.Test

class SourceTest {
    private fun fixture(name: String)=javaClass.getResource("/$name.html")!!.readText()
    @Test fun realSearchHandlesLazyCoversAndMultipleMatches() {
        val result=MangaSource.parseSearch(fixture("search"),1)
        assertEquals(2,result.books.size)
        val b=result.books.single { it.id=="43847" }
        assertEquals("脑洞学生会",b.title)
        assertTrue(b.cover.startsWith("https://cf.mhgui.com/"))
        assertTrue(b.author.contains("むちまろ"))
        assertFalse(result.hasMore)
    }
    @Test fun chapterOrderRetainsSpecialsAndStartsAtFirstChapter() {
        val b=MangaSource.parseDetail("43847",fixture("detail"))
        assertEquals("628732",b.chapters.first().id)
        assertEquals("第01话",b.chapters.first().title)
        assertTrue(b.chapters.size>200)
        assertTrue(b.chapters.any { it.title.contains("番外") })
        assertEquals(b.chapters.size,b.chapters.distinctBy { it.id }.size)
    }
    @Test fun realPackedChapterDecodesWithoutExecutingJavascript() {
        val p=ChapterDecoder.decode("43847",Chapter("628732","第01话"),fixture("chapter"))
        assertEquals(17,p.paths.size)
        assertNull(p.prevId)
        assertEquals("633620",p.nextId)
        assertEquals(setOf("e","m"),p.query.keys)
        assertTrue(p.url(0).startsWith("https://eu.hamreus.com/"))
        assertTrue(p.paths.first().endsWith(".webp"))
    }
    @Test(expected=IllegalArgumentException::class) fun refusesWrongChapter() {
        ChapterDecoder.decode("43847",Chapter("123","错误章节"),fixture("chapter"))
    }
    @Test(expected=IllegalArgumentException::class) fun refusesTruncatedCompressedDictionary() { LzBase64.decode("D7Q") }
    @Test fun paginationFragmentUsesResultCountWhenPageMetadataAbsent() {
        val item="<li><a href='/comic/%d/'><h3>漫画%d</h3><img data-src='//cf.mhgui.com/1.jpg'/></a></li>"
        val result=MangaSource.parseSearch((1..20).joinToString("") { item.format(it,it) },2)
        assertTrue(result.hasMore)
        assertEquals(20,result.books.size)
    }
    @Test fun onlySupportedLinksAreAccepted() {
        assertEquals("43847",MangaSource.mangaId("https://m.manhuagui.com/comic/43847/"))
        assertNull(MangaSource.mangaId("https://example.com/comic/43847/"))
        assertNull(MangaSource.mangaId("file:///etc/passwd"))
    }
}
