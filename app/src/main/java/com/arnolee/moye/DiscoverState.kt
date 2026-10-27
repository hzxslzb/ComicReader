package com.arnolee.moye

import androidx.compose.runtime.*
import kotlinx.coroutines.*

class BrowseState {
    var entries by mutableStateOf<List<BrowseEntry>>(emptyList())
    var loading by mutableStateOf(false)
    var loaded by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var page by mutableIntStateOf(0)
    var totalPages by mutableStateOf<Int?>(null)
    var hasMore by mutableStateOf(false)
    var retryMore=false
}

/** Each filter owns its result state: a slow old request cannot overwrite a new selection. */
class DiscoverState(private val source: DiscoverSource,private val scope: CoroutineScope) {
    var section by mutableStateOf("home")
    var category by mutableStateOf(MangaCategory("","全部"))
    var order by mutableStateOf("")
    var home by mutableStateOf<DiscoverHome?>(null)
    var homeLoading by mutableStateOf(false)
    var homeError by mutableStateOf<String?>(null)
    var categories by mutableStateOf<List<MangaCategory>>(emptyList())
    private val listings=mutableStateMapOf<BrowseKey,BrowseState>()
    val key get()=BrowseKey(if(section in listOf("update","rank")) section else "list",if(section=="list") category.slug else "",if(section=="list") order else "")
    fun state(key: BrowseKey)=listings.getOrPut(key) { BrowseState() }
    fun loadHome(refresh: Boolean=false) {
        if(homeLoading || (home!=null && !refresh)) return
        homeLoading=true;homeError=null
        scope.launch {
            try { home=source.home() }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { homeError=e.localizedMessage ?: "首页加载失败" }
            finally { homeLoading=false }
        }
    }
    fun load(key: BrowseKey,more: Boolean=false,refresh: Boolean=false) {
        val state=state(key)
        if(state.loading || (more && !state.hasMore) || (!more && state.loaded && !refresh)) return
        val page=if(more) state.page+1 else 1
        state.loading=true;state.error=null;state.retryMore=more
        scope.launch {
            try {
                val result=source.browse(key,page)
                state.entries=(if(more) state.entries+result.entries else result.entries).distinctBy { it.book.id }
                state.totalPages=if(more) result.totalPages ?: state.totalPages else result.totalPages
                state.page=page;state.loaded=true
                state.hasMore=result.entries.isNotEmpty() && (state.totalPages?.let { page<it } ?: (result.entries.size>=20))
                if(result.categories.isNotEmpty()) categories=result.categories
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { state.error=e.localizedMessage ?: "列表加载失败" }
            finally { state.loading=false }
        }
    }
    fun chooseCategory(value: MangaCategory) { category=value;section="list" }
}
