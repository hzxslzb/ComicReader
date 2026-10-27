package com.arnolee.moye

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Window constraints, not device names or physical DPI, drive every breakpoint. */
data class PaneSize(val width: Dp=400.dp,val height: Dp=800.dp) {
    val useRail get()=width>=600.dp
    val splitDetail get()=width>=600.dp && height>=480.dp
    val shortHeight get()=height<480.dp
}
val LocalPaneSize=staticCompositionLocalOf { PaneSize() }

fun LazyGridScope.fullItem(key: Any?=null,content: @Composable LazyGridItemScope.()->Unit) {
    item(key=key,span={ GridItemSpan(maxLineSpan) }) {
        val scope=this
        Column(Modifier.fillMaxWidth()) { content(scope) }
    }
}

/** Keep the cover alone, then pair consecutive pages. No phantom page at the end. */
object ReaderSpreads {
    fun enabled(width: Float,height: Float,preference: Boolean,vertical: Boolean)=preference && !vertical && width>=600f && height>=360f
    fun count(total: Int,dual: Boolean)=if(dual) 1+total/2 else total
    fun group(page: Int,dual: Boolean)=if(dual) (page+1)/2 else page
    fun first(group: Int,dual: Boolean)=if(dual && group>0) group*2-1 else group
    fun pages(group: Int,total: Int,dual: Boolean): List<Int> {
        val first=first(group,dual)
        return if(dual && group>0) (first..minOf(first+1,total-1)).toList() else listOf(first).filter { it<total }
    }
}
