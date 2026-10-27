package com.arnolee.moye

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DiscoverFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var vm: AppViewModel
    private val d get()=vm.discover
    private fun tab(id: String) { ui.onNodeWithTag("discover-tab-$id").performScrollTo().performClick() }
    private fun listing(key: BrowseKey): BrowseState {
        ui.waitUntil(60_000) { d.state(key).let { !it.loading && (it.loaded || it.error!=null) } }
        return d.state(key).also { assertNull(it.error);assertTrue(it.entries.isNotEmpty()) }
    }
    private fun more(key: BrowseKey) {
        val before=d.state(key).page
        ui.onNodeWithTag("discover-list").performScrollToNode(hasTestTag("discover-load-more"))
        ui.onNodeWithTag("discover-load-more").performClick()
        ui.waitUntil(60_000) { d.state(key).let { !it.loading && (it.page>before || it.error!=null) } }
        assertNull(d.state(key).error)
    }
    private fun shot(name: String) {
        ui.waitForIdle()
        device.waitForIdle()
        val dir=File(ui.activity.getExternalFilesDir(null),"discovery-screenshots").apply { mkdirs() }
        device.takeScreenshot(File(dir,"$name.png"))
    }
    @Test fun liveHomeAllCategoriesSortedPagesRanksAndFoldRestore() {
        ui.runOnIdle { vm=ViewModelProvider(ui.activity)[AppViewModel::class.java];vm.screen="home";vm.tab=4;vm.setAppearance("light") }
        try {
            device.executeShellCommand("wm size 1080x2520");device.executeShellCommand("wm density 440")
            ui.waitUntil(60_000) { d.home!=null || d.homeError!=null }
            assertNull(d.homeError);assertEquals(4,d.home!!.sections.size)
            shot("01-home-cover")
            ui.onNodeWithTag("discover-home").performScrollToNode(hasTestTag("batch-slider0"))
            ui.onNodeWithTag("batch-slider0").performClick()
            val second=d.home!!.sections.first().books[6]
            ui.onNodeWithTag("discover-book-${second.id}").assertExists()
            tab("categories")
            listing(BrowseKey());assertEquals(52,d.categories.size)
            shot("02-categories-cover")
            ui.onNodeWithTag("discover-categories").performScrollToNode(hasTestTag("category-rexue"))
            ui.onNodeWithTag("category-rexue").performClick()
            listing(BrowseKey("list","rexue"))
            ui.onNodeWithTag("sort-view").performScrollTo().performClick()
            val categoryKey=BrowseKey("list","rexue","view")
            val category=listing(categoryKey)
            assertTrue(category.entries.all { it.book.category.contains("热血") })
            val ids=category.entries.map { it.book.id }
            more(categoryKey)
            assertEquals(40,category.entries.size);assertEquals(ids,category.entries.take(20).map { it.book.id })
            tab("update")
            val updateKey=BrowseKey("update")
            val updates=listing(updateKey);assertTrue(updates.entries.first().updated.isNotEmpty())
            more(updateKey);assertEquals(40,updates.entries.size)
            tab("rank")
            val rankKey=BrowseKey("rank")
            val rank=listing(rankKey)
            while(rank.hasMore) more(rankKey)
            assertEquals(100,rank.entries.size);assertEquals(5,rank.page);assertFalse(rank.hasMore)
            assertEquals((1..100).toList(),rank.entries.map { it.rank })
            ui.onNodeWithTag("discover-list").performScrollToIndex(0)
            shot("03-rank-cover")
            val first=rank.entries.first().book
            ui.onNodeWithTag("discover-book-${first.id}").performClick()
            ui.waitUntil(60_000) { !vm.detailLoading }
            assertNotNull(vm.selected);assertTrue(vm.selected!!.chapters.isNotEmpty())
            ui.runOnIdle { vm.back() }
            assertEquals("rank",d.section);assertEquals(100,d.state(rankKey).entries.size)
            tab("list")
            assertEquals(categoryKey,d.key);assertEquals(40,category.entries.size)
            device.executeShellCommand("wm size 1968x2184");device.executeShellCommand("wm density 420")
            ui.waitUntil(20_000) { ui.onAllNodesWithTag("side-navigation").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(categoryKey,d.key);assertEquals(40,d.state(categoryKey).entries.size)
            ui.onNodeWithTag("discover-list").performScrollToIndex(0)
            shot("04-category-inner")
            ui.runOnIdle { vm.setAppearance("dark") }
            shot("05-category-inner-dark")
            ui.activityRule.scenario.recreate()
            ui.runOnIdle { vm=ViewModelProvider(ui.activity)[AppViewModel::class.java] }
            assertEquals(categoryKey,d.key);assertEquals(40,d.state(categoryKey).entries.size)
            // A failed refresh keeps already loaded results; retry can recover.
            device.executeShellCommand("svc wifi disable");device.executeShellCommand("svc data disable")
            val connectivity=vm.app.getSystemService(ConnectivityManager::class.java)
            ui.waitUntil(20_000) { connectivity.activeNetwork==null }
            vm.app.source.client.connectionPool.evictAll()
            ui.runOnIdle { d.load(categoryKey,refresh=true) }
            ui.waitUntil(60_000) { !category.loading }
            assertNotNull(category.error);assertEquals(40,category.entries.size)
            device.executeShellCommand("svc wifi enable");device.executeShellCommand("svc data enable")
            ui.waitUntil(30_000) { connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true }
            ui.runOnIdle { d.load(categoryKey,refresh=true) }
            ui.waitUntil(60_000) { !category.loading }
            assertNull(category.error);assertEquals(20,category.entries.size)
        } finally {
            device.executeShellCommand("svc wifi enable");device.executeShellCommand("svc data enable")
            device.executeShellCommand("wm size reset");device.executeShellCommand("wm density reset")
        }
    }
}
