package com.arnolee.moye

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs
import kotlin.math.roundToInt

object ReaderZoom {
    fun scale(value: Float)=value.coerceIn(1f,4f)
    fun pan(value: Float,width: Float,scale: Float): Float {
        val limit=(width*(scale-1f)/2f).coerceAtLeast(0f)
        return value.coerceIn(-limit,limit)
    }
    fun anchorOffset(oldOffset: Int,oldScale: Float,newScale: Float,focusY: Float)=
        ((oldOffset+focusY)*newScale/oldScale-focusY).roundToInt()
}

/** Consume pinches and deliberate horizontal drags only. Vertical drags belong to LazyColumn. */
suspend fun PointerInputScope.verticalZoomGestures(
    scale: ()->Float,
    transform: (zoom: Float,panX: Float,focus: Offset)->Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
        var total=Offset.Zero
        var horizontal=false
        var vertical=false
        var pinched=false
        do {
            val event=awaitPointerEvent(PointerEventPass.Initial)
            val pressed=event.changes.count { it.pressed }
            val pan=event.calculatePan()
            if(pressed>=2) {
                pinched=true
                val focus=event.calculateCentroid(useCurrent=false)
                if(focus!=Offset.Unspecified) transform(event.calculateZoom(),pan.x,focus)
                event.changes.forEach { if(it.positionChanged()) it.consume() }
            } else if(pinched) {
                // Do not let the remaining finger fling the list when the pinch ends.
                event.changes.forEach { if(it.positionChanged()) it.consume() }
            } else if(scale()>1.01f) {
                total+=pan
                if(!horizontal && !vertical) {
                    if(abs(total.x)>viewConfiguration.touchSlop && abs(total.x)>abs(total.y)) horizontal=true
                    else if(abs(total.y)>viewConfiguration.touchSlop) vertical=true
                }
                if(horizontal) {
                    transform(1f,pan.x,Offset.Zero)
                    event.changes.forEach { if(it.positionChanged()) it.consume() }
                }
            }
        } while(event.changes.any { it.pressed })
    }
}
