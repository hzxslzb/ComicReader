@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.arnolee.moye

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable fun MoyeApp(vm: AppViewModel) {
    MoyeTheme(vm.theme) {
        val revision by vm.store.revision.collectAsState()
        val snackbar=remember { SnackbarHostState() }
        val homeStates=rememberSaveableStateHolder()
        LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it);vm.message=null } }
        BackHandler(vm.screen!="home") { vm.back() }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rail=maxWidth>=600.dp
            Scaffold(
                containerColor=MaterialTheme.colorScheme.background,
                snackbarHost={ SnackbarHost(snackbar) },
                bottomBar={ if(vm.screen=="home" && !rail) BottomNavigation(vm) },
                contentWindowInsets=WindowInsets.safeDrawing
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if(vm.screen=="home" && rail) SideNavigation(vm)
                    Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.TopCenter) {
                        BoxWithConstraints(Modifier.widthIn(max=if(vm.screen=="home" && vm.tab==3) 720.dp else if(vm.screen=="reader") 2400.dp else 1200.dp).fillMaxSize()) {
                            CompositionLocalProvider(LocalPaneSize provides PaneSize(maxWidth,maxHeight)) {
                                when(vm.screen) {
                                    "reader" -> ReaderScreen(vm,revision)
                                    "detail" -> DetailScreen(vm,revision)
                                    else -> homeStates.SaveableStateProvider(vm.tab) { when(vm.tab) { 0 -> LibraryScreen(vm,revision);1 -> SearchScreen(vm);2 -> DownloadScreen(vm,revision);4 -> DiscoverScreen(vm);else -> SettingsScreen(vm,revision) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SideNavigation(vm: AppViewModel) {
    NavigationRail(Modifier.width(80.dp).fillMaxHeight().testTag("side-navigation").verticalScroll(rememberScrollState()),containerColor=MaterialTheme.colorScheme.surfaceContainerLow,windowInsets=WindowInsets(0,0,0,0)) {
        Spacer(Modifier.height(12.dp))
        BrandIcon(Modifier.size(38.dp))
        Spacer(Modifier.height(18.dp))
        val labels=listOf("发现","书架","搜索","下载","设置")
    val destinations=listOf(4,0,1,2,3)
        val icons=listOf(Icons.Outlined.Explore,Icons.Outlined.AutoStories,Icons.Outlined.Search,Icons.Outlined.DownloadForOffline,Icons.Outlined.Tune)
        labels.forEachIndexed { index,label -> NavigationRailItem(selected=vm.tab==destinations[index],onClick={ vm.tab=destinations[index] },icon={ Icon(icons[index],label) },label={ Text(label) }) }
    }
}

@Composable private fun BrandIcon(modifier: Modifier=Modifier) {
    AsyncImage(R.drawable.comicreader_icon_art,"ComicReader 图标",modifier.clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop)
}

@Composable private fun BottomNavigation(vm: AppViewModel) {
    val labels=listOf("发现","书架","搜索","下载","设置")
    val destinations=listOf(4,0,1,2,3)
    val icons=listOf(Icons.Outlined.Explore,Icons.Outlined.AutoStories,Icons.Outlined.Search,Icons.Outlined.DownloadForOffline,Icons.Outlined.Tune)
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shadowElevation=8.dp) {
        Row(Modifier.testTag("bottom-navigation").fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp,vertical=9.dp),horizontalArrangement=Arrangement.SpaceAround) {
            labels.forEachIndexed { i,label ->
                val active=vm.tab==destinations[i]
                Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable { vm.tab=destinations[i] }.padding(vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(if(active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).padding(horizontal=12.dp,vertical=6.dp)) {
                        Icon(icons[i],label,tint=if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(23.dp))
                    }
                    Text(label,fontSize=11.sp,fontWeight=if(active) FontWeight.Bold else FontWeight.Normal,color=if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable fun PageHeader(eyebrow: String,title: String,action: (@Composable ()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().padding(top=20.dp,bottom=22.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp));Text(title,style=MaterialTheme.typography.headlineLarge)
        }
        action?.invoke()
    }
}
@Composable fun Cover(book: Manga,modifier: Modifier=Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
        Icon(Icons.Outlined.AutoStories,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.outline)
        AsyncImage(book.cover,book.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
    }
}
@Composable fun EmptyPanel(icon: ImageVector,title: String,description: String,action: String?=null,onAction: ()->Unit={}) {
    Column(Modifier.fillMaxWidth().padding(vertical=38.dp,horizontal=20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) { Icon(icon,null,Modifier.size(38.dp),tint=MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(20.dp));Text(title,style=MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp));Text(description,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        if(action!=null) { Spacer(Modifier.height(22.dp));Button(onAction,shape=RoundedCornerShape(14.dp)) { Text(action);Spacer(Modifier.width(8.dp));Icon(Icons.Outlined.ArrowForward,null,Modifier.size(17.dp)) } }
    }
}
@Composable fun ErrorPanel(error: String,retry: ()->Unit) { EmptyPanel(Icons.Outlined.CloudOff,"暂时没能加载",error,"重新试试",retry) }
@Composable fun SectionLabel(title: String,note: String="") {
    Row(Modifier.fillMaxWidth().padding(top=14.dp,bottom=14.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
        Text(note,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun LibraryScreen(vm: AppViewModel,revision: Int) {
    var historyTab by rememberSaveable { mutableStateOf(false) }
    val favorites=remember(revision) { vm.store.favorites() }
    val history=remember(revision) { vm.store.history() }
    val books=remember(revision,historyTab) { if(historyTab) history.mapNotNull { vm.store.book(it.bookId) } else vm.store.books().filter { it.id in favorites } }
    val last=history.firstOrNull();val recent=last?.let { vm.store.book(it.bookId) }
    LazyVerticalGrid(columns=GridCells.Adaptive(if(LocalPaneSize.current.width>=600.dp) 128.dp else 100.dp),modifier=Modifier.fillMaxSize().testTag("library-grid"),horizontalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(horizontal=24.dp,vertical=8.dp)) {
        fullItem { PageHeader("YOUR QUIET CORNER","ComicReader",{ IconButton({ vm.tab=1 },Modifier.background(MaterialTheme.colorScheme.surfaceContainer,CircleShape)) { Icon(Icons.Outlined.Search,"搜索漫画") } }) }
        fullItem {
            if(last!=null && recent!=null) {
                Surface(Modifier.fillMaxWidth().clickable { vm.read(recent) },shape=RoundedCornerShape(26.dp),color=Color(0xFF244A3B)) {
                    Row(Modifier.padding(22.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("续上你的故事",color=Color(0xFFBED5C1),fontSize=12.sp,letterSpacing=2.sp)
                            Spacer(Modifier.height(12.dp));Text(recent.title,color=Color.White,style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Spacer(Modifier.height(10.dp));Text("${last.chapterTitle} · 第 ${last.page+1} / ${last.total} 页",color=Color(0xFFCBD9CA),fontSize=12.sp)
                            Spacer(Modifier.height(16.dp));Row(verticalAlignment=Alignment.CenterVertically) { Text("继续阅读",color=Color(0xFFE8D9B4),fontWeight=FontWeight.SemiBold);Spacer(Modifier.width(8.dp));Icon(Icons.Outlined.ArrowForward,null,Modifier.size(18.dp),tint=Color(0xFFE8D9B4)) }
                        }
                        Spacer(Modifier.width(16.dp));Cover(recent,Modifier.size(86.dp,122.dp))
                    }
                }
            } else {
                Surface(shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        Text("把时间，留给故事。",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(8.dp));Text("收藏喜欢的漫画，让每次阅读都有归处。",color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=.75f),style=MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp));TextButton({ vm.tab=1 },contentPadding=PaddingValues(0.dp)) { Text("探索第一本漫画");Spacer(Modifier.width(8.dp));Icon(Icons.Outlined.ArrowForward,null,Modifier.size(18.dp)) }
                    }
                }
            }
        }
        fullItem {
            Row(Modifier.padding(top=24.dp,bottom=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(!historyTab,{ historyTab=false },{ Text("我的收藏  ${favorites.size}") })
                FilterChip(historyTab,{ historyTab=true },{ Text("最近阅读  ${history.size}") })
            }
        }
        if(books.isEmpty()) fullItem { EmptyPanel(if(historyTab) Icons.Outlined.History else Icons.Outlined.BookmarkBorder,if(historyTab) "故事会留在这里" else "书架还是空的",if(historyTab) "读过的章节和页码会自动记录。" else "搜索漫画，点击收藏即可加入书架。",if(historyTab) null else "去搜索",{ vm.tab=1 }) }
        items(books,key={ it.id }) { b ->
            Column(Modifier.fillMaxWidth().padding(bottom=20.dp).clickable { vm.openBook(b.id,b) }) {
                Cover(b,Modifier.fillMaxWidth().aspectRatio(.7f));Spacer(Modifier.height(8.dp))
                Text(b.title,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
                val p=vm.store.progress(b.id)
                Text(p?.let { "${it.chapterTitle} · ${it.page+1} 页" } ?: "尚未开始",maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        fullItem { Spacer(Modifier.height(14.dp)) }
    }
}

@Composable private fun SearchScreen(vm: AppViewModel) {
    val keyboard=LocalSoftwareKeyboardController.current
    fun submit() { keyboard?.hide();vm.search() }
    LazyVerticalGrid(columns=GridCells.Adaptive(300.dp),modifier=Modifier.fillMaxSize().testTag("SearchScreen-grid"),horizontalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(horizontal=24.dp,vertical=8.dp)) {
        fullItem { PageHeader("DISCOVER  /  FIND YOUR NEXT STORY","发现故事") }
        fullItem {
            OutlinedTextField(vm.query,{ vm.query=it },Modifier.fillMaxWidth(),placeholder={ Text("搜索漫画名，或粘贴漫画链接",fontSize=14.sp) },leadingIcon={ Icon(Icons.Outlined.Search,null) },trailingIcon={ if(vm.query.isNotEmpty()) IconButton({ vm.query="" }) { Icon(Icons.Outlined.Close,"清空搜索") } },singleLine=true,shape=RoundedCornerShape(18.dp),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={ submit() }),colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant,unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLow))
            Spacer(Modifier.height(12.dp));Button({ submit() },Modifier.fillMaxWidth().height(48.dp),enabled=vm.query.isNotBlank() && !vm.searching,shape=RoundedCornerShape(14.dp)) { Text(if(vm.searching) "正在寻找…" else "搜索漫画") }
        }
        if(!vm.searched) {
            fullItem {
                val history=vm.recentSearches()
                SectionLabel(if(history.isEmpty()) "试试这些关键词" else "最近搜索")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { (history.ifEmpty { listOf("脑洞学生会","葬送的芙莉莲","海贼王","间谍过家家") }).forEach { word -> SuggestionChip({ vm.query=word;submit() },{ Text(word) }) } }
            }
            fullItem {
                Spacer(Modifier.height(26.dp))
                Surface(Modifier.fillMaxWidth().clickable { vm.openBook("43847") },shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(22.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoStories,null,Modifier.size(34.dp),tint=MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)) { Text("从这本开始",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.secondary);Spacer(Modifier.height(6.dp));Text("脑洞学生会",style=MaterialTheme.typography.titleMedium);Text("搞笑 · 校园",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        Icon(Icons.Outlined.ArrowForward,null,tint=MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        } else {
            fullItem { SectionLabel("搜索结果", "${vm.results.size} 本") }
            items(vm.results,key={ it.id }) { b ->
                Surface(Modifier.fillMaxWidth().padding(bottom=12.dp).clickable { vm.openBook(b.id,b) },shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Cover(b,Modifier.size(78.dp,110.dp));Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) { Text(b.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis);Spacer(Modifier.height(6.dp));Text(b.author,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(10.dp));Text(b.category,fontSize=11.sp,color=MaterialTheme.colorScheme.primary);Text("更新至 ${b.latest}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1) }
                        Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.outline,modifier=Modifier.size(20.dp))
                    }
                }
            }
            if(vm.searching) fullItem { Box(Modifier.fillMaxWidth().padding(30.dp),contentAlignment=Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp),strokeWidth=2.dp) } }
            vm.searchError?.let { error -> fullItem { ErrorPanel(error) { vm.search() } } }
            if(!vm.searching && vm.searchError==null && vm.results.isEmpty()) fullItem { EmptyPanel(Icons.Outlined.SearchOff,"还没有找到这本漫画","试试更短的关键词，或输入漫画的其他译名。") }
            if(vm.hasMore && !vm.searching) fullItem { TextButton({ vm.search(true) },Modifier.fillMaxWidth()) { Text("加载更多结果") } }
        }
    }
}

fun stateLabel(state: String)=when(state) { "complete"->"已下载";"queued"->"等待下载";"downloading"->"下载中";"paused"->"已暂停";"failed"->"下载失败";else->state }
@Composable private fun DownloadScreen(vm: AppViewModel,revision: Int) {
    val tasks=remember(revision) { vm.store.downloads() }
    var onlyComplete by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Download?>(null) }
    LazyVerticalGrid(columns=GridCells.Adaptive(300.dp),modifier=Modifier.fillMaxSize().testTag("DownloadScreen-grid"),horizontalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(horizontal=24.dp,vertical=8.dp)) {
        fullItem { PageHeader("OFFLINE  /  STORIES TO GO","随身漫画") }
        fullItem {
            Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(22.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudDone,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp));Column { Text("${tasks.count { it.state=="complete" }} 章已备好",style=MaterialTheme.typography.titleLarge);Text("没有网络，也能接着看。",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp) }
                }
            }
            Row(Modifier.padding(top=16.dp,bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(!onlyComplete,{ onlyComplete=false },{ Text("全部任务") });FilterChip(onlyComplete,{ onlyComplete=true },{ Text("已下载") })
                if(tasks.any { it.state=="downloading" || it.state=="queued" }) TextButton({ tasks.filter { it.state in listOf("downloading","queued") }.forEach { vm.store.updateDownload(it.bookId,it.chapterId,"paused") } }) { Text("全部暂停",fontSize=12.sp) }
            }
        }
        val filtered=tasks.filter { !onlyComplete || it.state=="complete" }
        if(filtered.isEmpty()) fullItem { EmptyPanel(Icons.Outlined.DownloadForOffline,"把故事装进口袋","在漫画详情页下载章节，完成后即可离线阅读。","去找漫画",{ vm.tab=1 }) }
        items(filtered,key={ it.key }) { d ->
            val book=vm.store.book(d.bookId)
            Surface(Modifier.fillMaxWidth().padding(bottom=12.dp),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        if(book!=null) { Cover(book,Modifier.size(56.dp,78.dp).clickable { vm.openBook(book.id,book) });Spacer(Modifier.width(12.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(book?.title ?: "漫画",style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(d.title,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp));Text("${stateLabel(d.state)}${if(d.total>0) " · ${d.done}/${d.total} 页" else ""}",fontSize=11.sp,color=if(d.state=="failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                        IconButton({ when(d.state) { "complete" -> book?.let { vm.read(it,Chapter(d.chapterId,d.title)) };"downloading","queued" -> vm.store.updateDownload(d.bookId,d.chapterId,"paused");else -> vm.resume(d) } }) { Icon(when(d.state) { "complete"->Icons.Outlined.MenuBook;"downloading","queued"->Icons.Outlined.Pause;else->Icons.Outlined.PlayArrow },when(d.state) { "complete"->"离线阅读";"downloading","queued"->"暂停下载";else->"继续下载" },tint=MaterialTheme.colorScheme.primary) }
                        IconButton({ deleting=d }) { Icon(Icons.Outlined.DeleteOutline,"删除下载",Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if(d.state=="downloading" || d.state=="paused") LinearProgressIndicator(progress={ if(d.total==0) 0f else d.done.toFloat()/d.total },modifier=Modifier.fillMaxWidth().padding(top=12.dp).height(3.dp).clip(CircleShape))
                    if(d.error.isNotBlank()) Text(d.error,Modifier.padding(top=8.dp),fontSize=11.sp,color=MaterialTheme.colorScheme.error,maxLines=3)
                }
            }
        }
    }
    deleting?.let { d -> AlertDialog(onDismissRequest={ deleting=null },title={ Text("删除离线章节？") },text={ Text("将删除「${d.title}」的下载文件。书架收藏和阅读进度仍会保留。") },confirmButton={ TextButton({ vm.store.removeDownload(d);deleting=null }) { Text("删除") } },dismissButton={ TextButton({ deleting=null }) { Text("取消") } }) }
}

@Composable private fun SettingsScreen(vm: AppViewModel,revision: Int) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var used by remember { mutableStateOf("正在统计…") }
    LaunchedEffect(revision) { used=withContext(Dispatchers.IO) { String.format(Locale.US,"%.1f MB",vm.store.bytesUsed()/1024.0/1024.0) } }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=24.dp,vertical=8.dp)) {
        item { PageHeader("PREFERENCES  /  MAKE IT YOURS","阅读偏好") }
        item {
            SectionLabel("外观主题")
            Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(18.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        listOf("light" to "浅色","dark" to "深色","system" to "跟随系统").forEach { (value,label) ->
                            val chosen=vm.theme==value
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(15.dp)).border(if(chosen) 2.dp else 1.dp,if(chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(15.dp)).clickable { vm.setAppearance(value) }.padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(8.dp)).background(if(value=="dark") Color(0xFF17251C) else Color(0xFFEAEDE2))) {
                                    Box(Modifier.padding(8.dp).fillMaxWidth().height(18.dp).clip(RoundedCornerShape(4.dp)).background(if(value=="dark") Color(0xFF3C5846) else Color(0xFFB7CCB9)))
                                    Box(Modifier.padding(start=8.dp,top=32.dp,end=16.dp).fillMaxWidth().height(4.dp).background(Color(0xFF94AA96)))
                                    if(value=="system") Icon(Icons.Outlined.BrightnessAuto,null,Modifier.align(Alignment.BottomEnd).padding(5.dp).size(20.dp),tint=Pine)
                                }
                                Spacer(Modifier.height(8.dp));Text(label,fontSize=12.sp,color=if(chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    Text("漫画原图保持原色，夜间阅读更舒适。",Modifier.padding(top=14.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionLabel("默认阅读方式")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("vertical" to "上下滚动","ltr" to "左 → 右","rtl" to "右 → 左").forEach { (value,label) -> FilterChip(vm.readingMode==value,{ vm.setMode(value) },{ Text(label) }) } }
            Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("宽屏自动双页",style=MaterialTheme.typography.titleMedium);Text("展开或横屏时，翻页模式并排显示两页",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(vm.dualPage,{ vm.updateDualPage(it) })
            }
            SectionLabel("阅读预加载")
            Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(18.dp)) { PreloadControls(vm) }
            }
            SectionLabel("本地存储")
            Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(18.dp)) {
                    Row { Text("离线漫画",Modifier.weight(1f));Text(used,color=MaterialTheme.colorScheme.primary) }
                    Text("在下载页按章节管理，不受缓存清理影响。",Modifier.padding(top=6.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(vertical=14.dp),color=MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("阅读图片缓存");Text("临时保存最近看过的图片",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) };TextButton({ scope.launch { withContext(Dispatchers.IO) { context.imageLoader.diskCache?.clear() };context.imageLoader.memoryCache?.clear();vm.message="图片缓存已清理，离线章节已保留" } }) { Text("清理") } }
                }
            }
            Spacer(Modifier.height(28.dp))
            Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                BrandIcon(Modifier.size(64.dp));Spacer(Modifier.height(10.dp));Text("ComicReader",style=MaterialTheme.typography.titleMedium);Text("${BuildConfig.VERSION_NAME} · 把时间留给故事",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp));Text("数据源：漫画柜 · 本地保存书架与阅读历史",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


@Composable fun PreloadControls(vm: AppViewModel) {
    val settings=vm.preloadPreferences
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("预加载",style=MaterialTheme.typography.titleMedium)
                Text("提前准备后续图片，减少翻页等待",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(settings.enabled,{ vm.updatePreload(settings.copy(enabled=it)) },modifier=Modifier.testTag("preload-enabled"))
        }
        Spacer(Modifier.height(12.dp))
        Text("提前加载页数",style=MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (3..5).forEach { count ->
                FilterChip(settings.pages==count,{ vm.updatePreload(settings.copy(pages=count)) },
                    { Text("$count 页") },enabled=settings.enabled,modifier=Modifier.testTag("preload-pages-$count"))
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("仅 Wi-Fi 下预加载")
                Text("移动网络仍可正常阅读当前页",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(settings.wifiOnly,{ vm.updatePreload(settings.copy(wifiOnly=it)) },enabled=settings.enabled,modifier=Modifier.testTag("preload-wifi"))
        }
        Text(when {
            !settings.enabled -> "预加载已关闭，已缓存图片仍可使用。"
            settings.wifiOnly && !vm.preloadWifi -> "等待 Wi-Fi 连接后预加载；临时缓存不计入离线下载。"
            settings.wifiOnly -> "Wi-Fi 下预加载后续 ${settings.pages} 页，接近章末时包含下一章。"
            else -> "预加载后续 ${settings.pages} 页，接近章末时包含下一章，也会使用移动数据。"
        },Modifier.padding(top=8.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
