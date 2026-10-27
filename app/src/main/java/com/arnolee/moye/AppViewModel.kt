package com.arnolee.moye

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*

class AppViewModel(application: Application): AndroidViewModel(application) {
    val app=application as MoyeApplication
    val store=app.store
    val prefs=application.getSharedPreferences("settings",0)
    var theme by mutableStateOf(prefs.getString("theme","system")!!)
    var readingMode by mutableStateOf(prefs.getString("readingMode","vertical")!!)
    var dualPage by mutableStateOf(prefs.getBoolean("dualPage",true))
    var readerPage by mutableIntStateOf(0)
    fun updateDualPage(value: Boolean) { dualPage=value;prefs.edit().putBoolean("dualPage",value).apply() }
    fun recordPage(p: ChapterPages,index: Int) {
        if(activeChapter?.id!=p.chapter.id || selected?.id!=p.mangaId) return
        readerPage=index.coerceIn(p.paths.indices)
        store.saveProgress(p.mangaId,p.chapter,readerPage,p.paths.size)
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
    var message by mutableStateOf<String?>(null)
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
        detailJob?.cancel();readerJob?.cancel()
        selected=store.book(id) ?: card;screen="detail";detailError=null
        detailJob=viewModelScope.launch {
            detailLoading=true
            try { val book=app.source.detail(id);store.putBook(book);selected=book }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { detailError=if(selected?.chapters?.isNotEmpty()==true) "当前使用已保存的目录" else (e.localizedMessage ?: "加载失败") }
            finally { detailLoading=false }
        }
    }
    fun read(book: Manga,chapter: Chapter?=null) {
        readerJob?.cancel();selected=book;store.putBook(book)
        val last=store.progress(book.id)
        val target=chapter ?: last?.let { Chapter(it.chapterId,it.chapterTitle) } ?: book.chapters.firstOrNull()
        if(target==null) { message="请等待章节目录加载完成";return }
        activeChapter=target;pages=null;screen="reader";readerError=null;readerLoading=true
        readerJob=viewModelScope.launch {
            try {
                val cached=store.manifest(book.id,target.id)
                val p=if(cached!=null && store.complete(cached)) cached else try { app.source.pages(book.id,target).also { store.putManifest(it) } } catch(e: CancellationException) { throw e } catch(e: Exception) { cached ?: throw e }
                readerStart=(store.progress(book.id,target.id)?.page ?: 0).coerceIn(0,p.paths.lastIndex)
                readerPage=readerStart
                pages=p
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { readerError=e.localizedMessage ?: "章节加载失败" }
            finally { readerLoading=false }
        }
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
    fun enqueue(book: Manga,chapters: List<Chapter>) {
        if(chapters.isEmpty()) return
        store.enqueue(book,chapters)
        try { DownloadService.start(app);message="已加入下载队列 · ${chapters.size} 章" }
        catch(e: Exception) { store.recoverInterrupted();message="下载未能启动，请在下载页点击继续" }
    }
    fun resume(d: Download) { store.book(d.bookId)?.let { enqueue(it,listOf(Chapter(d.chapterId,d.title))) } }
    fun back() { if(screen=="reader") { readerJob?.cancel();screen="detail" } else { detailJob?.cancel();screen="home" } }
}
