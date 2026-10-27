@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.arnolee.moye

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun DetailScreen(vm: AppViewModel,revision: Int) {
    val b=vm.selected
    var reverse by rememberSaveable(b?.id) { mutableStateOf(false) }
    var choosing by rememberSaveable(b?.id) { mutableStateOf(false) }
    var selection by rememberSaveable(b?.id) { mutableStateOf(listOf<String>()) }
    var showSummary by rememberSaveable(b?.id) { mutableStateOf(false) }
    var showHistory by rememberSaveable(b?.id) { mutableStateOf(false) }
    val pane=LocalPaneSize.current
    val favorite=remember(revision,b?.id) { b?.id in vm.store.favorites() }
    val tasks=remember(revision) { vm.store.downloads() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton({ vm.back() }) { Icon(Icons.Outlined.ArrowBack,"返回") }
            Text("漫画详情",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            if(pane.splitDetail) Text("作品与章节",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(b!=null) IconButton({ vm.openBook(b.id,b) }) { Icon(Icons.Outlined.Refresh,"刷新目录") }
        }
        if(b==null) {
            if(vm.detailLoading) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
            else ErrorPanel(vm.detailError ?: "加载失败") { vm.back() }
            return
        }
        val progress=remember(revision,b.id) { vm.store.progress(b.id) }
        val chapterHistory=remember(revision,b.id) { vm.store.chapterHistory(b.id) }
        val chapters=if(reverse) b.chapters.reversed() else b.chapters
        val overview: @Composable ()->Unit = {
            Column {
                Row(Modifier.padding(top=16.dp,bottom=24.dp),verticalAlignment=Alignment.CenterVertically) {
                    Cover(b,if(pane.splitDetail) Modifier.size(100.dp,145.dp) else Modifier.size(112.dp,160.dp));Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text("MANGA  /  漫画柜",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp));Text(b.title,style=MaterialTheme.typography.headlineMedium,maxLines=3,overflow=TextOverflow.Ellipsis)
                        Spacer(Modifier.height(12.dp));Text(b.author,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp));Text(b.category,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button({ vm.read(b) },Modifier.weight(1f).height(50.dp),enabled=b.chapters.isNotEmpty(),shape=RoundedCornerShape(15.dp),contentPadding=PaddingValues(horizontal=12.dp)) { Icon(Icons.Outlined.PlayArrow,null,Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text(if(progress==null) "开始阅读" else "继续阅读") }
                    OutlinedButton({ vm.store.putBook(b);vm.store.toggleFavorite(b.id) },Modifier.height(50.dp),shape=RoundedCornerShape(15.dp),contentPadding=PaddingValues(horizontal=12.dp)) { Icon(if(favorite) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,null,Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text(if(favorite) "已收藏" else "收藏") }
                }
                if(progress!=null) Text("上次读到 ${progress.chapterTitle} · 第 ${progress.page+1} / ${progress.total} 页",Modifier.padding(top=10.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                TextButton({ showHistory=true },modifier=Modifier.testTag("book-history"),contentPadding=PaddingValues(0.dp)) {
                    Icon(Icons.Outlined.History,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("本书阅读记录 · ${chapterHistory.size} 章")
                }
                if(b.summary.isNotBlank()) {
                    Text(b.summary,Modifier.padding(top=20.dp).clickable { showSummary=!showSummary },maxLines=if(showSummary) 100 else if(pane.splitDetail) 7 else 3,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({ showSummary=!showSummary },contentPadding=PaddingValues(0.dp)) { Text(if(showSummary) "收起简介" else "展开简介",fontSize=12.sp) }
                }
                vm.detailError?.let { Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.secondary) }
                if(vm.detailLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=8.dp))
            }
        }
        val chapterHeader: @Composable ()->Unit = {
            Column {
                Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("章节",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.width(6.dp));Text("${b.chapters.size}",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton({ reverse=!reverse },contentPadding=PaddingValues(horizontal=8.dp)) { Icon(Icons.Outlined.SwapVert,null,Modifier.size(16.dp));Text(if(reverse) "倒序" else "正序",fontSize=12.sp) }
                    TextButton({ choosing=!choosing;selection=emptyList() },contentPadding=PaddingValues(horizontal=8.dp)) { Text(if(choosing) "取消" else "批量下载",fontSize=12.sp) }
                }
                if(choosing) FlowRow(verticalArrangement=Arrangement.Center,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    TextButton({ selection=if(selection.size==b.chapters.size) emptyList() else b.chapters.map { it.id } }) { Text(if(selection.size==b.chapters.size) "取消全选" else "全选") }
                    Button({ vm.enqueue(b,b.chapters.filter { it.id in selection });selection=emptyList();choosing=false },enabled=selection.isNotEmpty()) { Text("下载 ${selection.size} 章") }
                }
            }
        }
        val chapterItem: @Composable (Chapter)->Unit = { ch ->
            val task=tasks.firstOrNull { it.bookId==b.id && it.chapterId==ch.id }
            val p=remember(revision,b.id,ch.id) { vm.store.progress(b.id,ch.id) }
            val active=progress?.chapterId==ch.id
            Surface(Modifier.fillMaxWidth().padding(vertical=4.dp).clickable { if(choosing) selection=if(ch.id in selection) selection-ch.id else selection+ch.id else vm.read(b,ch) },shape=RoundedCornerShape(14.dp),color=if(active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.heightIn(min=60.dp).padding(start=12.dp,end=4.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(choosing) Checkbox(ch.id in selection,{ checked -> selection=if(checked) selection+ch.id else selection-ch.id },modifier=Modifier.size(38.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ch.title,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=14.sp,fontWeight=if(active) FontWeight.Bold else FontWeight.Normal)
                        if(p!=null || task!=null) Text(listOfNotNull(p?.let { "${it.page+1}/${it.total} 页" },task?.let { stateLabel(it.state) }).joinToString(" · "),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(!choosing) IconButton({ if(task?.state=="complete") vm.read(b,ch) else vm.enqueue(b,listOf(ch)) }) { Icon(if(task?.state=="complete") Icons.Outlined.OfflinePin else Icons.Outlined.Download,if(task?.state=="complete") "离线阅读${ch.title}" else "下载${ch.title}",Modifier.size(20.dp),tint=if(task?.state=="complete") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if(showHistory) ModalBottomSheet(onDismissRequest={ showHistory=false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal=24.dp)) {
                Text("本书阅读记录",style=MaterialTheme.typography.titleLarge)
                Text(b.title,Modifier.padding(top=6.dp,bottom=12.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(chapterHistory.isEmpty()) EmptyPanel(Icons.Outlined.History,"还没有阅读记录","阅读后会为这本漫画分别保存章节和页码。")
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max=minOf(520.dp,pane.height*.7f)).testTag("book-history-list"),contentPadding=PaddingValues(bottom=28.dp)) {
                items(chapterHistory,key={ it.chapterId }) { saved ->
                    val chapter=b.chapters.firstOrNull { it.id==saved.chapterId } ?: Chapter(saved.chapterId,saved.chapterTitle)
                    ListItem(
                        headlineContent={ Text(saved.chapterTitle) },
                        supportingContent={ Text("第 ${saved.page+1} / ${saved.total} 页 · "+java.text.SimpleDateFormat("MM-dd HH:mm",java.util.Locale.CHINA).format(java.util.Date(saved.time))) },
                        leadingContent={ Icon(Icons.Outlined.History,null) },
                        trailingContent={ Icon(Icons.Outlined.PlayArrow,"继续阅读${saved.chapterTitle}") },
                        modifier=Modifier.testTag("history-${saved.chapterId}").clickable { showHistory=false;vm.read(b,chapter) }
                    )
                }
            }
        }
        if(pane.splitDetail) {
            Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal=24.dp).testTag("detail-split"),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                LazyColumn(Modifier.width((pane.width*.4f).coerceIn(240.dp,380.dp)).fillMaxHeight(),contentPadding=PaddingValues(bottom=24.dp)) { item { overview() } }
                VerticalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                LazyVerticalGrid(GridCells.Adaptive(158.dp),Modifier.weight(1f).fillMaxHeight(),horizontalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                    fullItem { chapterHeader() }
                    items(chapters,key={ it.id }) { chapterItem(it) }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).testTag("detail-compact"),contentPadding=PaddingValues(start=24.dp,end=24.dp,bottom=24.dp)) {
                item { overview();HorizontalDivider(Modifier.padding(top=12.dp),color=MaterialTheme.colorScheme.outlineVariant);chapterHeader() }
                items(chapters,key={ it.id }) { chapterItem(it) }
            }
        }
    }
}
