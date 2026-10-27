package com.arnolee.moye

import org.junit.Assert.*
import org.junit.Test

class DiscoverSourceTest {
    private fun html(name: String)=javaClass.getResource("/discover-$name.html")!!.readText()
    @Test fun homepageIncludesEveryCarouselBatchAndUsesLazyImages() {
        val home=DiscoverSource.parseHome(html("home"))
        assertEquals(5,home.banners.size)
        assertEquals(listOf("热门连载漫画","经典完结漫画","漫迷推荐漫画","最新上架漫画"),home.sections.map { it.title })
        assertTrue(home.sections.all { it.books.size==12 })
        assertEquals("7580",home.sections.first().books.first().id)
        assertTrue(home.sections.flatMap { it.books }.all { it.cover.startsWith("https://cf.mhgui.com/") && it.latest.isNotEmpty() })
    }
    @Test fun allWebsiteCategoriesAreReadIncludingStatusRegionAndAudience() {
        val list=DiscoverSource.parseBrowse(html("list"),1)
        assertEquals(52,list.categories.size)
        assertEquals(52,list.categories.map { it.slug }.distinct().size)
        assertEquals(3,list.categories.count { it.group=="连载状态" })
        assertEquals(6,list.categories.count { it.group=="地区" })
        assertEquals(5,list.categories.count { it.group=="读者群" })
        assertEquals(38,list.categories.count { it.group=="题材" })
        assertEquals("热血",list.categories.single { it.slug=="rexue" }.title)
        assertEquals(3102,list.totalPages)
    }
    @Test fun rankAjaxPreservesActualPositionsAndUpdateMetadata() {
        val first=DiscoverSource.parseBrowse(html("rank"),1)
        val next=DiscoverSource.parseBrowse(html("rank2"),2)
        assertEquals(5,first.totalPages)
        assertNull(next.totalPages)
        assertEquals((21..40).toList(),next.entries.map { it.rank })
        assertTrue(first.entries.map { it.book.id }.intersect(next.entries.map { it.book.id }.toSet()).isEmpty())
        val updates=DiscoverSource.parseBrowse(html("update"),1)
        assertEquals(20,updates.entries.size)
        assertTrue(updates.entries.all { it.updated.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && it.status.isNotBlank() })
    }
    @Test fun categorySortAndPagingKeepSameFilter() {
        val key=BrowseKey("list","rexue","view")
        assertEquals("https://m.manhuagui.com/list/rexue/view.html",key.url(1))
        assertEquals("https://m.manhuagui.com/list/rexue/?page=2&ajax=1&order=view&catid=0",key.url(2))
        assertEquals("https://m.manhuagui.com/update/?page=2&ajax=1&order=1",BrowseKey("update").url(2))
        assertEquals(93,DiscoverSource.parseBrowse(html("category"),1).totalPages)
    }
    @Test fun emptyFinalFragmentIsValidButChallengeIsNot() {
        assertTrue(DiscoverSource.parseBrowse("",2).entries.isEmpty())
        for(page in listOf(1,2)) {
            assertThrows(java.io.IOException::class.java) { DiscoverSource.parseBrowse("<html><h1>Access denied</h1></html>",page) }
        }
        assertThrows(java.io.IOException::class.java) { DiscoverSource.parseHome("<html>Access denied</html>") }
    }
    @Test fun unsupportedPathsCannotBecomeRequests() {
        assertThrows(IllegalArgumentException::class.java) { BrowseKey(category="../rank") }
        assertThrows(IllegalArgumentException::class.java) { BrowseKey("https://evil.invalid") }
        assertThrows(IllegalArgumentException::class.java) { BrowseKey(order="invalid") }
    }
}
