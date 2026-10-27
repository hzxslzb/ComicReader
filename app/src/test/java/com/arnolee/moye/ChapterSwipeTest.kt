package com.arnolee.moye

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class ChapterSwipeTest {
    @Test fun directionsMatchVerticalLtrAndRtlReaders() {
        assertTrue(ChapterSwipe.forward(Offset(4f,-150f),true,false,64f))
        assertTrue(ChapterSwipe.forward(Offset(-150f,4f),false,false,64f))
        assertTrue(ChapterSwipe.forward(Offset(150f,4f),false,true,64f))
        assertFalse(ChapterSwipe.forward(Offset(150f,4f),false,false,64f))
        assertFalse(ChapterSwipe.forward(Offset(-150f,4f),false,true,64f))
    }
    @Test fun tapsSmallDragsAndCrossAxisMovementsDoNotAdvance() {
        assertFalse(ChapterSwipe.forward(Offset.Zero,true,false,64f))
        assertFalse(ChapterSwipe.forward(Offset(0f,-30f),true,false,64f))
        assertFalse(ChapterSwipe.forward(Offset(130f,-100f),true,false,64f))
        assertFalse(ChapterSwipe.forward(Offset(-100f,130f),false,false,64f))
    }
    @Test fun previousDirectionsAreTheReverseOfNext() {
        assertTrue(ChapterSwipe.matches(Offset(4f,150f),true,false,64f,false))
        assertTrue(ChapterSwipe.matches(Offset(150f,4f),false,false,64f,false))
        assertTrue(ChapterSwipe.matches(Offset(-150f,4f),false,true,64f,false))
        assertFalse(ChapterSwipe.matches(Offset(4f,-150f),true,false,64f,false))
        assertFalse(ChapterSwipe.matches(Offset(-150f,4f),false,false,64f,false))
        assertFalse(ChapterSwipe.matches(Offset(150f,4f),false,true,64f,false))
    }
    @Test fun previousAlsoRejectsTapsShortAndCrossAxisDrags() {
        assertFalse(ChapterSwipe.matches(Offset.Zero,true,false,64f,false))
        assertFalse(ChapterSwipe.matches(Offset(0f,30f),true,false,64f,false))
        assertFalse(ChapterSwipe.matches(Offset(130f,100f),true,false,64f,false))
        assertFalse(ChapterSwipe.matches(Offset(100f,130f),false,false,64f,false))
    }
}
