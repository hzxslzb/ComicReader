@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.arnolee.moye

import android.view.WindowManager
import android.app.Activity
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable fun ReaderScreen(vm: AppViewModel,revision: Int) {
    val book=vm.selected ?: return
    val p=vm.pages
    val activity=LocalContext.current as Activity
    DisposableEffect(Unit) { activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    if(p==null) {
        Column {
            IconButton({ vm.back() }) { Icon(Icons.Outlined.ArrowBack,"返回") }
            if(vm.readerLoading) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Column(horizontalAlignment=Alignment.CenterHorizontally) { CircularProgressIndicator(Modifier.size(30.dp),strokeWidth=2.dp);Spacer(Modifier.height(20.dp));Text("正在翻开 ${vm.activeChapter?.title.orEmpty()}",color=MaterialTheme.colorScheme.onSurfaceVariant) } }
            else ErrorPanel(vm.readerError ?: "章节暂时不可用") { vm.read(book,vm.activeChapter) }
        }
        return
    }
    key(p.mangaId,p.chapter.id) { ReaderContent(vm,book,p,revision) }
}

@Composable private fun ReaderContent(vm: AppViewModel,book: Manga,p: ChapterPages,revision: Int) {
    var chrome by rememberSaveable { mutableStateOf(true) }
    var zoomAmount by remember { mutableFloatStateOf(1f) }
    var zoomReset by remember { mutableIntStateOf(0) }
    var showChapters by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val pane=LocalPaneSize.current
    val vertical=vm.readingMode=="vertical"
    val dual=ReaderSpreads.enabled(pane.width.value,pane.height.value,vm.dualPage,vertical)
    val current=vm.readerPage.coerceIn(p.paths.indices)
    // A page is canonical state; single/dual layouts merely project it into a group.
    var jumpRequest by remember { mutableStateOf<Pair<Int,Long>?>(null) }
    val lifecycle=LocalLifecycleOwner.current
    fun save() { vm.recordPage(p,vm.readerPage) }
    DisposableEffect(lifecycle,p.chapter.id) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_PAUSE) save() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { save();lifecycle.lifecycle.removeObserver(observer) }
    }
    val complete=remember(revision,p) { vm.store.complete(p) }
    val visible=ReaderSpreads.pages(ReaderSpreads.group(current,dual),p.paths.size,dual)
    val pageLabel=if(visible.size==2) "${visible.first()+1}–${visible.last()+1}" else "${current+1}"
    val chapterButtons: @Composable ()->Unit = {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            TextButton({ save();vm.adjacent(false) },enabled=p.prevId!=null) { Icon(Icons.Outlined.SkipPrevious,null,Modifier.size(18.dp));Text("上一章",fontSize=13.sp) }
            TextButton({ showChapters=true }) { Icon(Icons.Outlined.FormatListBulleted,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("目录",fontSize=13.sp) }
            TextButton({ save();vm.adjacent(true) },enabled=p.nextId!=null) { Text("下一章",fontSize=13.sp);Icon(Icons.Outlined.SkipNext,null,Modifier.size(18.dp)) }
        }
    }
    val progressControl: @Composable ()->Unit = {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(pageLabel,fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
            var dragging by remember { mutableStateOf<Float?>(null) }
            Slider(value=dragging ?: current.toFloat(),onValueChange={ dragging=it },onValueChangeFinished={ dragging?.let { jumpRequest=it.toInt() to System.nanoTime() };dragging=null },valueRange=0f..maxOf(1,p.paths.lastIndex).toFloat(),enabled=p.paths.size>1,modifier=Modifier.weight(1f).padding(horizontal=10.dp))
            Text("${p.paths.size}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).testTag(if(dual) "reader-dual" else "reader-single")) {
        if(chrome) Surface(color=MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().height(if(pane.shortHeight) 48.dp else 62.dp).padding(end=8.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton({ save();vm.back() }) { Icon(Icons.Outlined.ArrowBack,"返回漫画详情") }
                Column(Modifier.weight(1f)) {
                    Text(book.title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                    Text(p.chapter.title+(if(complete) " · 离线" else "")+(if(dual) " · 双页" else ""),fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
                }
                IconButton({ if(complete) vm.message="本章已下载，可离线阅读" else vm.enqueue(book,listOf(p.chapter)) }) { Icon(if(complete) Icons.Outlined.OfflinePin else Icons.Outlined.Download,"下载当前章节",Modifier.size(21.dp)) }
                IconButton({ showSettings=true }) { Icon(Icons.Outlined.Tune,"阅读设置",Modifier.size(21.dp)) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("reader-viewport"),contentAlignment=Alignment.Center) {
            key(vertical,dual) {
                ReaderViewport(vm,p,vertical,dual,jumpRequest,zoomReset,{ zoomAmount=it },{ jumpRequest=null },{ chrome=!chrome })
            }

        }
        // This row occupies layout space below the clipped viewport; it never overlays a page.
        if(zoomAmount>1.01f || !chrome || (!vertical && visible.lastOrNull()==p.paths.lastIndex)) Surface(color=MaterialTheme.colorScheme.surface,modifier=Modifier.testTag("reader-zoom-bar")) {
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(!vertical && visible.lastOrNull()==p.paths.lastIndex && zoomAmount<=1.01f) (if(p.nextId!=null) "再${if(vm.readingMode=="rtl") "右" else "左"}滑一次 · 下一章" else "已到最新章节") else "$pageLabel / ${p.paths.size}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
                if(zoomAmount>1.01f) {
                    Text("${(zoomAmount*100).toInt()}%",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    TextButton({ zoomReset++ },modifier=Modifier.testTag("reader-reset-zoom")) {
                        Icon(Icons.Outlined.ZoomOutMap,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("恢复默认")
                    }
                }
            }
        }
        if(chrome) Surface(color=MaterialTheme.colorScheme.surface) {
            if(pane.shortHeight && pane.width>=600.dp) Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { progressControl() };Spacer(Modifier.width(24.dp));Box(Modifier.width(310.dp)) { chapterButtons() }
            } else Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                Column(Modifier.widthIn(max=840.dp).fillMaxWidth().padding(horizontal=18.dp,vertical=6.dp)) { progressControl();chapterButtons() }
            }
        }
    }
    if(showChapters) ModalBottomSheet(onDismissRequest={ showChapters=false }) {
        Text("章节目录",Modifier.padding(horizontal=24.dp,vertical=8.dp),style=MaterialTheme.typography.titleLarge)
        val start=book.chapters.indexOfFirst { it.id==p.chapter.id }.coerceAtLeast(0)
        LazyColumn(state=rememberLazyListState(start),modifier=Modifier.fillMaxWidth().heightIn(max=minOf(480.dp,pane.height*.7f)),contentPadding=PaddingValues(bottom=32.dp)) {
            items(book.chapters,key={ it.id }) { ch ->
                val active=ch.id==p.chapter.id
                val downloaded=vm.store.download(book.id,ch.id)?.state=="complete"
                ListItem(headlineContent={ Text(ch.title,color=if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },leadingContent={ Icon(if(active) Icons.Outlined.PlayArrow else Icons.Outlined.MenuBook,null) },trailingContent={ if(downloaded) Icon(Icons.Outlined.OfflinePin,"已下载",tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp)) },modifier=Modifier.clickable { save();showChapters=false;vm.read(book,ch) },colors=ListItemDefaults.colors(containerColor=if(active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent))
            }
        }
    }
    if(showSettings) ModalBottomSheet(onDismissRequest={ showSettings=false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text("阅读设置",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(16.dp))
            Text("翻页方向",style=MaterialTheme.typography.titleMedium)
            listOf("vertical" to "上下滚动","ltr" to "从左向右翻页","rtl" to "从右向左翻页（日漫）").forEach { (value,label) ->
                Row(Modifier.fillMaxWidth().clickable { save();vm.setMode(value);showSettings=false }.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) { RadioButton(vm.readingMode==value,null);Text(label) }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("宽屏自动双页");Text("窄窗口自动恢复单页，首张封面独立展示",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(vm.dualPage,{ vm.updateDualPage(it) })
            }
            HorizontalDivider(Modifier.padding(vertical=12.dp))
            Text("主题",style=MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("light" to "浅色","dark" to "深色","system" to "跟随系统").forEach { (value,label) -> FilterChip(vm.theme==value,{ vm.setAppearance(value) },{ Text(label) }) } }
            Text("轻点图片隐藏工具栏；双击或双指缩放图片。\n上下滚动放大后仍可上下阅读、左右拖动；底栏可一键恢复默认尺寸，按钮不遮挡漫画。\n到达章节末尾后，再按下一页方向滑动一次即可进入下一章。\n自动预加载后续 5 页，接近章末时包含下一章；使用临时缓存，也会消耗移动数据。\n折叠、展开和旋转时保留当前章节与页码。",Modifier.padding(top=14.dp,bottom=24.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun ReaderViewport(vm: AppViewModel,p: ChapterPages,vertical: Boolean,dual: Boolean,jump: Pair<Int,Long>?,zoomReset: Int,onZoomChanged: (Float)->Unit,onJumpHandled: ()->Unit,onTap: ()->Unit) {
    val initial=vm.readerPage.coerceIn(p.paths.indices)
    val pager=rememberPagerState(initialPage=ReaderSpreads.group(initial,dual),pageCount={ ReaderSpreads.count(p.paths.size,dual) })
    val zoomed=remember { mutableStateMapOf<Int,Float>() }
    val canScroll=zoomed.values.none { it>1.01f }
    val currentCanScroll by rememberUpdatedState(canScroll)
    LaunchedEffect(vertical) { if(!vertical) snapshotFlow { zoomed.values.maxOrNull() ?: 1f }.collect { onZoomChanged(it) } }
    LaunchedEffect(vertical,dual) {
        if(!vertical) snapshotFlow { pager.settledPage }.collect { group ->
            // Preserve the exact page on fold/unfold, even if it is the second page of a spread.
            if(ReaderSpreads.group(vm.readerPage,dual)!=group) vm.recordPage(p,ReaderSpreads.first(group,dual))
        }
    }
    LaunchedEffect(jump) {
        if(!vertical) jump?.let { (page,_) ->
            val target=page.coerceIn(p.paths.indices)
            pager.scrollToPage(ReaderSpreads.group(target,dual))
            vm.recordPage(p,target)
            onJumpHandled()
        }
    }
    if(vertical) VerticalReaderViewport(vm,p,jump,zoomReset,onZoomChanged,onJumpHandled,onTap) else HorizontalPager(pager,Modifier.fillMaxSize().testTag("reader-pager")
        .pointerInput(p.chapter.id) {
            nextChapterSwipe(false,{ vm.readingMode=="rtl" },
                { p.nextId!=null && currentCanScroll && !pager.isScrollInProgress && pager.settledPage==pager.pageCount-1 },
                { currentCanScroll && pager.currentPage==pager.pageCount-1 },
                { vm.advanceFromEnd(p) })
        },reverseLayout=vm.readingMode=="rtl",userScrollEnabled=canScroll,beyondViewportPageCount=1) { group ->
        val indices=ReaderSpreads.pages(group,p.paths.size,dual).let { if(vm.readingMode=="rtl") it.reversed() else it }
        Row(Modifier.fillMaxSize().padding(horizontal=if(dual) 12.dp else 0.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            indices.forEach { index -> key(index) { Box(Modifier.weight(1f).fillMaxHeight()) { ComicImage(vm,p,index,false,onTap,{ zoomed[index]=it },zoomReset) } } }
        }
    }
}

@Composable private fun VerticalReaderViewport(vm: AppViewModel,p: ChapterPages,jump: Pair<Int,Long>?,zoomReset: Int,onZoomChanged: (Float)->Unit,onJumpHandled: ()->Unit,onTap: ()->Unit) {
    val list=rememberLazyListState(vm.readerPage.coerceIn(p.paths.indices))
    var scale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(1) }
    val scope=rememberCoroutineScope()
    var anchorJob by remember { mutableStateOf<Job?>(null) }
    fun transform(factor: Float,pan: Float,focus: Offset) {
        val old=scale
        val next=ReaderZoom.scale(old*factor)
        if(next!=old) {
            val index=list.firstVisibleItemIndex
            val offset=ReaderZoom.anchorOffset(list.firstVisibleItemScrollOffset,old,next,focus.y)
            panX=ReaderZoom.pan((panX-(focus.x-widthPx/2f))*next/old+(focus.x-widthPx/2f)+pan,widthPx.toFloat(),next)
            scale=next
            anchorJob?.cancel()
            anchorJob=scope.launch { list.scrollToItem(index,offset) }
        } else panX=ReaderZoom.pan(panX+pan,widthPx.toFloat(),scale)
        onZoomChanged(scale)
    }
    LaunchedEffect(Unit) { snapshotFlow { list.firstVisibleItemIndex }.collect { vm.recordPage(p,it.coerceAtMost(p.paths.lastIndex)) } }
    LaunchedEffect(zoomReset) { transform(1f/scale,0f,Offset.Zero) }
    LaunchedEffect(widthPx) { panX=ReaderZoom.pan(panX,widthPx.toFloat(),scale) }
    LaunchedEffect(jump) {
        jump?.let { (page,_) ->
            anchorJob?.cancel()
            val target=page.coerceIn(p.paths.indices)
            list.scrollToItem(target)
            vm.recordPage(p,target);onJumpHandled()
        }
    }
    fun atChapterEnd(): Boolean {
        val layout=list.layoutInfo
        val last=layout.visibleItemsInfo.firstOrNull { it.index==p.paths.lastIndex }
        return (last!=null && last.offset+last.size<=layout.viewportEndOffset+1) || list.firstVisibleItemIndex>p.paths.lastIndex
    }
    val currentTransform by rememberUpdatedState<(Float,Float,Offset)->Unit>(::transform)
    val currentTap by rememberUpdatedState(onTap)
    BoxWithConstraints(Modifier.widthIn(max=720.dp).fillMaxSize().clipToBounds().testTag("reader-vertical-zoom")
        .semantics { stateDescription="scale=$scale;pan=$panX" }
        .onSizeChanged { widthPx=it.width }
        .pointerInput(p.chapter.id) {
            nextChapterSwipe(true,{ false },
                { p.nextId!=null && atChapterEnd() && !list.isScrollInProgress && anchorJob?.isActive!=true },
                { atChapterEnd() && anchorJob?.isActive!=true },
                { vm.advanceFromEnd(p) })
        }
        .pointerInput(Unit) { verticalZoomGestures({ scale }) { zoom,pan,focus -> currentTransform(zoom,pan,focus) } }
        .pointerInput(Unit) { detectTapGestures(onTap={ currentTap() },onDoubleTap={ currentTransform((if(scale>1.01f) 1f else 2.5f)/scale,0f,it) }) },contentAlignment=Alignment.TopCenter) {
        // Real layout scaling increases page height. Vertical scrolling stays enabled at every zoom.
        LazyColumn(state=list,modifier=Modifier.requiredWidth(maxWidth*scale).fillMaxHeight().graphicsLayer { translationX=panX }.testTag("reader-list"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            items(p.paths.size,key={ it }) { index -> ComicImage(vm,p,index,true,{}, {}) }
            item {
                Column(Modifier.fillMaxWidth().padding(30.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(if(p.nextId!=null) "再向上滑动一次，进入下一章" else "已到最新章节",style=MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp));Button({ vm.adjacent(true) },enabled=p.nextId!=null) { Text(if(p.nextId!=null) "阅读下一章" else "已到最新章节") }
                }
            }
        }
    }
}

@Composable private fun ComicImage(vm: AppViewModel,p: ChapterPages,index: Int,vertical: Boolean,onTap: ()->Unit,onZoom: (Float)->Unit,zoomReset: Int=0) {
    val context=LocalContext.current
    var retry by remember { mutableIntStateOf(0) }
    var error by remember(retry) { mutableStateOf(false) }
    var loading by remember(retry) { mutableStateOf(true) }
    var ratio by remember { mutableFloatStateOf(.70f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var width by remember { mutableIntStateOf(1) };var height by remember { mutableIntStateOf(1) }
    val transform=rememberTransformableState { zoom,pan,_ ->
        scale=(scale*zoom).coerceIn(1f,4f)
        offset=if(scale==1f) Offset.Zero else Offset((offset.x+pan.x).coerceIn(-width*(scale-1)/2,width*(scale-1)/2),(offset.y+pan.y).coerceIn(-height*(scale-1)/2,height*(scale-1)/2))
        onZoom(scale)
    }
    DisposableEffect(Unit) { onDispose { onZoom(1f) } }
    LaunchedEffect(zoomReset) { scale=1f;offset=Offset.Zero;onZoom(1f) }
    val decodeWidth=if(vertical) 2400 else width.coerceIn(640,2400)
    val request=remember(p,index,retry,decodeWidth) { ReaderImages.request(vm.app,p,index,decodeWidth,retry) }
    Box(Modifier.testTag("page-$index").semantics { stateDescription=if(error) "error" else if(loading) "loading" else "loaded" }.fillMaxWidth().then(if(vertical) Modifier.aspectRatio(ratio) else Modifier.fillMaxHeight()).clip(RoundedCornerShape(0.dp)).onSizeChanged { width=it.width;height=it.height }
        .then(if(vertical) Modifier else Modifier.pointerInput(Unit) { detectTapGestures(onTap={ onTap() },onDoubleTap={ scale=if(scale>1f)1f else 2.5f;offset=Offset.Zero;onZoom(scale) }) }
            .transformable(transform,canPan={ scale>1f })),contentAlignment=Alignment.Center) {
        AsyncImage(request,"第 ${index+1} 页",Modifier.fillMaxSize().graphicsLayer { scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y },contentScale=if(vertical) ContentScale.FillWidth else ContentScale.Fit,onSuccess={ result ->
            val d=result.result.drawable
            if(d.intrinsicWidth>0 && d.intrinsicHeight>0) ratio=(d.intrinsicWidth.toFloat()/d.intrinsicHeight).coerceAtLeast(.05f)
            loading=false;error=false
        },onError={ loading=false;error=true })
        if(loading) Column(horizontalAlignment=Alignment.CenterHorizontally) { CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp);Spacer(Modifier.height(12.dp));Text("第 ${index+1} 页",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        if(error) Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(24.dp)) {
            Icon(Icons.Outlined.BrokenImage,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp));Text("第 ${index+1} 页暂未加载",style=MaterialTheme.typography.bodyMedium)
            Text("未下载的图片需要网络连接",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton({ retry++ }) { Text("重新加载") }
        }
    }
}
