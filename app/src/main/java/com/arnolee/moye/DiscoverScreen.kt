@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.arnolee.moye

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable fun DiscoverScreen(vm: AppViewModel) {
    val d=vm.discover
    val tabs=listOf("home" to "首页热门","categories" to "所有分类","update" to "最近更新","list" to "漫画大全","rank" to "排行榜")
    val holder=rememberSaveableStateHolder()
    LaunchedEffect(d.section,d.key) {
        when(d.section) {
            "home" -> d.loadHome()
            "categories" -> if(d.categories.isEmpty()) d.load(BrowseKey())
            else -> d.load(d.key)
        }
    }
    Column(Modifier.fillMaxSize().testTag("discover-screen")) {
        Row(Modifier.padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PageHeader("A WORLD BETWEEN PAGES","发现") }
            IconButton({ vm.tab=1 }) { Icon(Icons.Outlined.Search,"搜索漫画") }
            IconButton({
                when(d.section) {
                    "home" -> d.loadHome(true)
                    "categories" -> d.load(BrowseKey(),refresh=true)
                    else -> d.load(d.key,refresh=true)
                }
            }) { Icon(Icons.Outlined.Refresh,"刷新发现") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            tabs.forEach { (id,title) -> FilterChip(d.section==id,{ d.section=id },{ Text(title) },modifier=Modifier.testTag("discover-tab-$id")) }
        }
        Spacer(Modifier.height(8.dp))
        val stateKey=if(d.section=="list") "list:${d.category.slug}:${d.order}" else d.section
        holder.SaveableStateProvider(stateKey) {
            when(d.section) {
                "home" -> HomeContent(vm)
                "categories" -> CategoryContent(vm)
                else -> CatalogContent(vm)
            }
        }
    }
}

@Composable private fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(32.dp),contentAlignment=Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp),strokeWidth=2.dp) }
}
@Composable private fun InlineError(message: String,retry: ()->Unit) {
    Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.errorContainer,modifier=Modifier.fillMaxWidth().padding(vertical=8.dp)) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(message,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onErrorContainer)
            TextButton(retry) { Text("重试") }
        }
    }
}

@Composable private fun HomeContent(vm: AppViewModel) {
    val d=vm.discover
    LazyColumn(Modifier.fillMaxSize().testTag("discover-home"),contentPadding=PaddingValues(start=24.dp,end=24.dp,bottom=24.dp)) {
        d.homeError?.let { item { InlineError(it) { d.loadHome(true) } } }
        if(d.homeLoading) item { LoadingRow() }
        val home=d.home
        if(home!=null) {
            if(home.banners.isNotEmpty()) item {
                val pager=rememberPagerState(pageCount={ home.banners.size })
                HorizontalPager(pager,Modifier.fillMaxWidth().height(if(LocalPaneSize.current.width>=600.dp) 240.dp else 190.dp).clip(RoundedCornerShape(24.dp)),pageSpacing=12.dp) { index ->
                    val b=home.banners[index]
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer).clickable { vm.openBook(b.id) }) {
                        AsyncImage(b.cover,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.8f)))))
                        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                            Text("首页精选  /  ${index+1} · ${home.banners.size}",color=Color.White.copy(alpha=.8f),style=MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(8.dp));Text(b.title,color=Color.White,style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    listOf(Triple("categories","按分类找漫画",Icons.Outlined.GridView),Triple("rank","看看人气榜",Icons.Outlined.Leaderboard)).forEach { (section,title,icon) ->
                        Surface(onClick={ d.section=section },modifier=Modifier.weight(1f),shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.secondaryContainer) {
                            Column(Modifier.padding(16.dp)) { Icon(icon,null,Modifier.size(24.dp));Spacer(Modifier.height(10.dp));Text(title,style=MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
            }
            home.sections.forEach { section ->
                item(key=section.id) {
                    var batch by rememberSaveable(section.id) { mutableIntStateOf(0) }
                    Row(Modifier.fillMaxWidth().padding(top=20.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(section.title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        if(section.books.size>6) TextButton({ batch=(batch+1)%((section.books.size+5)/6) },modifier=Modifier.testTag("batch-${section.id}")) { Text("换一批");Icon(Icons.Outlined.Refresh,null,Modifier.size(16.dp)) }
                    }
                    val books=section.books.drop(batch*6).take(6)
                    key(batch) {
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            items(books,key={ it.id }) { b ->
                                Column(Modifier.width(124.dp).clickable { vm.openBook(b.id,b) }.testTag("discover-book-${b.id}")) {
                                    Cover(b,Modifier.fillMaxWidth().height(172.dp))
                                    Spacer(Modifier.height(8.dp));Text(b.title,style=MaterialTheme.typography.titleSmall,maxLines=2,minLines=2,overflow=TextOverflow.Ellipsis)
                                    Text(b.latest,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            item { Text("与漫画柜首页同步 · 点击封面开始阅读",Modifier.padding(top=28.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable private fun CategoryContent(vm: AppViewModel) {
    val d=vm.discover
    val state=d.state(BrowseKey())
    LazyColumn(Modifier.fillMaxSize().testTag("discover-categories"),contentPadding=PaddingValues(horizontal=24.dp,vertical=8.dp)) {
        item { Text("按题材、地区和读者群，找到你的下一本。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        state.error?.let { item { InlineError(it) { d.load(BrowseKey(),refresh=true) } } }
        if(state.loading && d.categories.isEmpty()) item { LoadingRow() }
        listOf("连载状态","地区","读者群","题材").forEach { group ->
            val categories=d.categories.filter { it.group==group }
            if(categories.isNotEmpty()) item {
                SectionLabel(group,"${categories.size} 项")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    categories.forEach { cat ->
                        FilterChip(false,{ d.chooseCategory(cat) },{ Text(cat.title) },modifier=Modifier.testTag("category-${cat.slug.ifBlank { "all" }}"))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable private fun CatalogContent(vm: AppViewModel) {
    val d=vm.discover
    val key=d.key
    val state=d.state(key)
    var showCategories by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        if(d.section=="list") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                AssistChip({ showCategories=true },{ Text(d.category.title) },leadingIcon={ Icon(Icons.Outlined.FilterList,null,Modifier.size(18.dp)) })
                listOf("" to "添加时间","update" to "更新时间","view" to "浏览次数").forEach { (value,title) ->
                    FilterChip(d.order==value,{ d.order=value },{ Text(title) },modifier=Modifier.testTag("sort-${value.ifBlank { "added" }}"))
                }
            }
        }
        LazyVerticalGrid(GridCells.Adaptive(300.dp),modifier=Modifier.fillMaxSize().testTag("discover-list"),contentPadding=PaddingValues(start=24.dp,end=24.dp,bottom=24.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            fullItem {
                SectionLabel(when(d.section) { "rank" -> "人气排行榜";"update" -> "追更时刻";else -> "${d.category.title}漫画" },"已加载 ${state.entries.size} 本")
                if(d.section=="rank" || d.section=="update") Text("按网站顺序展示",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(state.error!=null) fullItem { InlineError(state.error!!) { d.load(key,more=state.retryMore,refresh=true) } }
            itemsIndexed(state.entries,key={ _,entry -> entry.book.id }) { index,entry ->
                val b=entry.book
                Surface(onClick={ vm.openBook(b.id,b) },modifier=Modifier.fillMaxWidth().testTag("discover-book-${b.id}"),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Box {
                            Cover(b,Modifier.size(82.dp,116.dp))
                            if(d.section=="rank") Box(Modifier.align(Alignment.TopStart).clip(RoundedCornerShape(topStart=12.dp,bottomEnd=10.dp)).background(if(index<3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary).padding(horizontal=9.dp,vertical=5.dp)) { Text("${entry.rank ?: index+1}",color=MaterialTheme.colorScheme.onPrimary,fontWeight=FontWeight.Bold,fontSize=12.sp) }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.title,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp));Text(b.author,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(listOf(entry.status,b.category).filter { it.isNotBlank() }.joinToString(" · "),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp));Text(b.latest,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(entry.updated,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if(state.loading) fullItem { LoadingRow() }
            else if(state.error==null) fullItem {
                when {
                    state.entries.isEmpty() && state.loaded -> EmptyPanel(Icons.Outlined.AutoStories,"这里暂时没有漫画","试试其他分类或排序。")
                    state.hasMore -> OutlinedButton({ d.load(key,more=true) },Modifier.fillMaxWidth().testTag("discover-load-more"),shape=RoundedCornerShape(14.dp)) { Text("加载更多 · 第 ${state.page+1} 页") }
                    state.loaded -> Text("已显示全部 ${state.entries.size} 本",Modifier.fillMaxWidth().padding(12.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if(showCategories) AlertDialog(onDismissRequest={ showCategories=false },title={ Text("选择分类") },text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
            listOf("连载状态","地区","读者群","题材").forEach { group ->
                Text(group,Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    d.categories.filter { it.group==group }.forEach { cat -> FilterChip(d.category.slug==cat.slug,{ d.chooseCategory(cat);showCategories=false },{ Text(cat.title) }) }
                }
            }
        }
    },confirmButton={ TextButton({ showCategories=false }) { Text("完成") } })
}
