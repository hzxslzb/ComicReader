@file:OptIn(coil.annotation.ExperimentalCoilApi::class)
package com.arnolee.moye

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import coil.imageLoader

class AppViewModel(application: Application): AndroidViewModel(application) {
    val updates=AppUpdateController(application,viewModelScope)
    val app=application as MoyeApplication
    val store=app.store
    val prefs=application.getSharedPreferences("settings",0)
    var theme by mutableStateOf(prefs.getString("theme","system")!!)
    var readingMode by mutableStateOf(prefs.getString("readingMode","vertical")!!)
    var dualPage by mutableStateOf(prefs.getBoolean("dualPage",true))
    var readerPage by mutableIntStateOf(0)
    var readerPendingEndAnchor=false
    var readerChrome by mutableStateOf(true)
    private var readerVisible=false
    fun updateDualPage(value: Boolean) { dualPage=value;prefs.edit().putBoolean("dualPage",value).apply() }
    fun recordPage(p: ChapterPages,index: Int) {
        if(activeChapter?.id!=p.chapter.id || selected?.id!=p.mangaId) return
        readerPage=index.coerceIn(p.paths.indices)
        store.saveProgress(p.mangaId,p.chapter,readerPage,p.paths.size)
        preload(p,readerPage)
    }
    val discover=DiscoverState(DiscoverSource(app.source),viewModelScope)
    var tab by mutableIntStateOf(4)
    var screen by mutableStateOf("home")
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Manga>>(emptyList())
    var searched by mutableStateOf(false)
    var searching by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)
    var hasMore by mutableStateOf(false)
    private var searchPage=1
    private var submittedQuery=""
    private var searchJob: Job?=null
    var selected by mutableStateOf<Manga?>(null)
    var detailLoading by mutableStateOf(false)
    var detailError by mutableStateOf<String?>(null)
    private var detailJob: Job?=null
    var pages by mutableStateOf<ChapterPages?>(null)
    var readerLoading by mutableStateOf(false)
    var readerError by mutableStateOf<String?>(null)
    var readerStart by mutableIntStateOf(0)
    var activeChapter by mutableStateOf<Chapter?>(null)
    private var readerJob: Job?=null
    private var readerGeneration=0
    private var preloadJob: Job?=null
    private var preloadKey: String?=null
    private var preloadChapterKey: String?=null
    private var preloadNext: ChapterPages?=null
    private data class ImagePage(val pages: ChapterPages,val index: Int)
    private val imagePreloader=ReaderImagePreloader<ImagePage>(viewModelScope,
        { ReaderImages.key(it.pages,it.index) }, { image ->
            checkPreloadPolicy()
            val p=image.pages;val index=image.index
            if(!store.available(p.mangaId,p.chapter.id,index)) {
                val cached=withContext(Dispatchers.IO) {
                    app.imageLoader.diskCache?.openSnapshot(ReaderImages.key(p,index))?.use { true } ?: false
                }
                checkPreloadPolicy()
                if(!cached) app.imageLoader.execute(ReaderImages.request(app,p,index,1,preload=true))
            }
        })
    suspend fun awaitPreloadedImage(p: ChapterPages,index: Int) = imagePreloader.awaitExisting(ReaderImages.key(p,index))
    fun readerVisibility(visible: Boolean) {
        if(readerVisible==visible) return
        readerVisible=visible
        if(visible) restartPreload() else cancelPreload(cancelVisible=true)
    }
    private fun checkPreloadPolicy() {
        if(!readerVisible || screen!="reader" || !preloadPreferences.allowed(preloadWifi && readerNetwork.isWifi()))
            throw CancellationException("预加载已暂停")
    }
    private fun cancelPreload(cancelVisible: Boolean=false) {
        preloadJob?.cancel();preloadJob=null;preloadKey=null;preloadChapterKey=null;preloadNext=null
        imagePreloader.clear(cancelVisible)
    }
    private val chapterLoader=ReaderChapterLoader(viewModelScope,
        { id,ch -> app.source.pages(id,ch) },store::manifest,store::putManifest,store::complete)
    var preloadPreferences by mutableStateOf(PreloadPreferences.load(prefs))
        private set
    private val readerNetwork=ReaderNetwork(application)
    var preloadWifi by mutableStateOf(readerNetwork.isWifi())
        private set
    init {
        viewModelScope.launch {
            readerNetwork.wifiChanges.collect { wifi ->
                preloadWifi=wifi
                restartPreload()
            }
        }
    }
    fun updatePreload(value: PreloadPreferences) {
        preloadPreferences=value.copy(pages=value.pages.coerceIn(3,5))
        preloadPreferences.save(prefs)
        restartPreload()
    }
    private fun restartPreload() {
        cancelPreload()
        pages?.takeIf { screen=="reader" && readerVisible }?.let { preload(it,readerPage) }
    }
    var message by mutableStateOf<String?>(null)
    private fun stopReader() {
        readerGeneration++;readerJob?.cancel();cancelPreload(cancelVisible=true);chapterLoader.clear()
        readerLoading=false
    }
    private fun preload(p: ChapterPages,index: Int) {
        if(!readerVisible || screen!="reader" || !preloadPreferences.allowed(preloadWifi && readerNetwork.isWifi())) return
        val settings=preloadPreferences
        val key="${p.mangaId}/${p.chapter.id}/$index"
        if(preloadKey==key) return
        preloadKey=key
        val current=ReaderPreloadWindow.following(index,p.paths.size,settings.pages).map { ImagePage(p,it) }
        val visible=setOf(ReaderImages.key(p,index))
        val count=ReaderPreloadWindow.nextChapterCount(index,p.paths.size,settings.pages)
        val next=p.nextId
        val chapterKey=if(count>0 && next!=null) "${p.mangaId}/$next" else null
        // Start the replacement waiter before releasing the old one for the same next chapter.
        val previous=preloadJob
        val sameChapter=preloadChapterKey==chapterKey && chapterKey!=null
        if(!sameChapter) previous?.cancel()
        preloadChapterKey=chapterKey
        if(!sameChapter) preloadNext=null
        fun upcomingImages(upcoming: ChapterPages): List<ImagePage> {
            val start=(store.progress(p.mangaId,upcoming.chapter.id)?.page ?: 0).coerceIn(upcoming.paths.indices)
            return (start until minOf(start+count,upcoming.paths.size)).map { ImagePage(upcoming,it) }
        }
        imagePreloader.update(current+(preloadNext?.let(::upcomingImages) ?: emptyList()),visible)
        preloadJob=if(chapterKey!=null && next!=null) viewModelScope.launch(start=CoroutineStart.UNDISPATCHED) {
            try {
                checkPreloadPolicy()
                val chapter=selected?.chapters?.firstOrNull { it.id==next } ?: Chapter(next,"下一章")
                val upcoming=chapterLoader.get(p.mangaId,chapter)
                checkPreloadPolicy()
                if(preloadKey!=key) return@launch
                preloadNext=upcoming
                imagePreloader.update(current+upcomingImages(upcoming),visible)
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { /* Speculation never replaces the current reader with an error. */ }
        } else null
        if(sameChapter) previous?.cancel()
    }
    fun setAppearance(value: String) { theme=value;prefs.edit().putString("theme",value).apply() }
    fun setMode(value: String) { readingMode=value;prefs.edit().putString("readingMode",value).apply() }
    fun search(more: Boolean=false) {
        val text=if(more) submittedQuery else query.trim()
        if(text.isBlank()) return
        MangaSource.mangaId(text)?.let { openBook(it);return }
        searchJob?.cancel()
        if(!more) { results=emptyList();searchPage=1;submittedQuery=text }
        searchJob=viewModelScope.launch {
            searching=true;searchError=null;searched=true
            try {
                val page=if(more) searchPage+1 else 1
                val r=app.source.search(text,page)
                results=(if(more) results+r.books else r.books).distinctBy { it.id };hasMore=r.hasMore;searchPage=page
                val history=(listOf(text)+recentSearches()).distinct().take(8)
                prefs.edit().putString("searches",org.json.JSONArray(history).toString()).apply()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { searchError=e.localizedMessage ?: "搜索失败，请重试" }
            finally { searching=false }
        }
    }
    fun recentSearches(): List<String> = runCatching { val a=org.json.JSONArray(prefs.getString("searches","[]"));(0 until a.length()).map { a.getString(it) } }.getOrDefault(emptyList())
    fun openBook(id: String,card: Manga?=null) {
        detailJob?.cancel();stopReader()
        selected=store.book(id) ?: card;screen="detail";detailError=null
        detailJob=viewModelScope.launch {
            detailLoading=true
            try { val book=app.source.detail(id);store.putBook(book);selected=book }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { detailError=if(selected?.chapters?.isNotEmpty()==true) "当前使用已保存的目录" else (e.localizedMessage ?: "加载失败") }
            finally { detailLoading=false }
        }
    }
    fun read(book: Manga,chapter: Chapter?=null,startAtEnd: Boolean=false) {
        if(screen!="reader" || selected?.id!=book.id) readerChrome=true
        val generation=++readerGeneration
        readerJob?.cancel();val previousPreload=preloadJob;preloadKey=null
        if(selected?.id!=book.id) chapterLoader.clear()
        selected=book;store.putBook(book)
        val last=store.progress(book.id)
        val target=chapter ?: last?.let { Chapter(it.chapterId,it.chapterTitle) } ?: book.chapters.firstOrNull()
        if(target==null) { message="请等待章节目录加载完成";return }
        activeChapter=target;pages=null;screen="reader";readerError=null;readerLoading=true
        readerJob=viewModelScope.launch {
            try {
                val p=chapterLoader.get(book.id,target)
                if(generation!=readerGeneration) return@launch
                readerStart=if(startAtEnd) p.paths.lastIndex else (store.progress(book.id,target.id)?.page ?: 0).coerceIn(0,p.paths.lastIndex)
                readerPage=readerStart
                readerPendingEndAnchor=startAtEnd && readingMode=="vertical"
                pages=p
                preload(p,readerPage)
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(generation==readerGeneration) readerError=e.localizedMessage ?: "章节加载失败，请重试" }
            finally { if(generation==readerGeneration) readerLoading=false }
        }
        // Join the requested manifest before releasing the speculative waiter.
        previousPreload?.cancel()
    }
    fun adjacent(next: Boolean) {
        val p=pages ?: return;val book=selected ?: return
        val id=if(next) p.nextId else p.prevId
        if(id==null) { message=if(next) "已经是最后一章" else "已经是第一章";return }
        read(book,book.chapters.firstOrNull { it.id==id } ?: Chapter(id,if(next) "下一章" else "上一章"))
    }
    fun advanceFromEnd(p: ChapterPages) {
        if(readerLoading || selected?.id!=p.mangaId || activeChapter?.id!=p.chapter.id || p.nextId==null) return
        recordPage(p,p.paths.lastIndex)
        adjacent(true)
    }
    fun retreatFromStart(p: ChapterPages) {
        val book=selected ?: return
        val id=p.prevId ?: return
        if(readerLoading || book.id!=p.mangaId || activeChapter?.id!=p.chapter.id) return
        recordPage(p,0)
        read(book,book.chapters.firstOrNull { it.id==id } ?: Chapter(id,"上一章"),startAtEnd=true)
    }
    fun enqueue(book: Manga,chapters: List<Chapter>) {
        if(chapters.isEmpty()) return
        store.enqueue(book,chapters)
        try { DownloadService.start(app);message="已加入下载队列 · ${chapters.size} 章" }
        catch(e: Exception) { store.recoverInterrupted();message="下载未能启动，请在下载页点击继续" }
    }
    fun resume(d: Download) { store.book(d.bookId)?.let { enqueue(it,listOf(Chapter(d.chapterId,d.title))) } }
    fun back() { if(screen=="reader") { stopReader();screen="detail" } else { detailJob?.cancel();screen="home" } }
}
