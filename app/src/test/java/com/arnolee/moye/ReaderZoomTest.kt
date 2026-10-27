package com.arnolee.moye

import org.junit.Assert.*
import org.junit.Test

class ReaderZoomTest {
    @Test fun fitAndMaximumNeverExposeHorizontalBlankSpace() {
        assertEquals(1f,ReaderZoom.scale(.2f),0f)
        assertEquals(4f,ReaderZoom.scale(8f),0f)
        assertEquals(0f,ReaderZoom.pan(300f,1000f,1f),0f)
        assertEquals(750f,ReaderZoom.pan(900f,1000f,2.5f),0f)
        assertEquals(-750f,ReaderZoom.pan(-900f,1000f,2.5f),0f)
    }
    @Test fun zoomAnchorAndResetKeepTheSameContentCoordinate() {
        val offset=ReaderZoom.anchorOffset(200,1f,2.5f,300f)
        assertEquals(950,offset)
        assertEquals(200,ReaderZoom.anchorOffset(offset,2.5f,1f,300f))
        assertEquals(380,ReaderZoom.anchorOffset(offset,2.5f,1f,0f))
    }
}
