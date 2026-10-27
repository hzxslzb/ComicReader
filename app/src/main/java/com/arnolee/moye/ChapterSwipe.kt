package com.arnolee.moye

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.dp
import kotlin.math.abs

object ChapterSwipe {
    fun forward(distance: Offset,vertical: Boolean,rtl: Boolean,threshold: Float): Boolean {
        val along=if(vertical) -distance.y else if(rtl) distance.x else -distance.x
        val across=if(vertical) abs(distance.x) else abs(distance.y)
        return along>=threshold && along>across*1.5f
    }
}

/** Only a NEW, single-finger gesture started at the settled boundary can advance a chapter. */
suspend fun PointerInputScope.nextChapterSwipe(
    vertical: Boolean,
    rtl: ()->Boolean,
    ready: ()->Boolean,
    stillAtEnd: ()->Boolean,
    advance: ()->Unit
) {
    awaitEachGesture {
        val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
        val armed=ready()
        var single=true
        var end=down.position
        var released=false
        do {
            val event=awaitPointerEvent(PointerEventPass.Initial)
            if(event.changes.count { it.pressed }>1 || event.changes.any { it.id!=down.id }) single=false
            event.changes.firstOrNull { it.id==down.id }?.let { change ->
                end=change.position
                if(change.previousPressed && !change.pressed) released=true
            }
        } while(event.changes.any { it.pressed })
        if(armed && single && released && stillAtEnd() && ChapterSwipe.forward(end-down.position,vertical,rtl(),maxOf(64.dp.toPx(),viewConfiguration.touchSlop*3f))) advance()
    }
}
