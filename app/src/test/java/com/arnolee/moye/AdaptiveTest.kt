package com.arnolee.moye

import org.junit.Assert.*
import org.junit.Test

class AdaptiveTest {
    @Test fun allPagesOccurExactlyOnceInOddAndEvenChapters() {
        for(total in listOf(1,2,3,16,17,100)) for(dual in listOf(true,false)) {
            val pages=(0 until ReaderSpreads.count(total,dual)).flatMap { ReaderSpreads.pages(it,total,dual) }
            assertEquals((0 until total).toList(),pages)
            assertEquals(listOf(0),ReaderSpreads.pages(0,total,dual))
        }
    }
    @Test fun exactPageMapsToContainingSpreadThenBackToSinglePage() {
        for(page in 0..16) {
            val spread=ReaderSpreads.group(page,true)
            assertTrue(page in ReaderSpreads.pages(spread,17,true))
            assertEquals(page,ReaderSpreads.first(ReaderSpreads.group(page,false),false))
        }
    }
    @Test fun widthsAndHeightsHandleCoverInnerAndSplitWindow() {
        assertFalse(ReaderSpreads.enabled(393f,860f,true,false))
        assertTrue(ReaderSpreads.enabled(750f,780f,true,false))
        assertTrue(ReaderSpreads.enabled(832f,700f,true,false))
        assertFalse(ReaderSpreads.enabled(360f,780f,true,false))
        assertFalse(ReaderSpreads.enabled(916f,320f,true,false))
        assertFalse(ReaderSpreads.enabled(750f,780f,true,true))
        assertFalse(ReaderSpreads.enabled(750f,780f,false,false))
    }
}
