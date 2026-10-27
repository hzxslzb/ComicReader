package com.arnolee.moye

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException

/** Data-only discovery; remote scripts and advertisements are never executed. */
data class MangaCategory(val slug: String,val title: String) {
    val group get()=when(slug) {
        "","lianzai","wanjie" -> "连载状态"
        "japan","hongkong","other","europe","china","korea" -> "地区"
        "shaonv","shaonian","qingnian","ertong","tongyong" -> "读者群"
        else -> "题材"
    }
}
data class HomeSection(val id: String,val title: String,val books: List<Manga>)
data class DiscoverHome(val banners: List<Manga>,val sections: List<HomeSection>)
data class BrowseEntry(val book: Manga,val status: String,val updated: String,val rank: Int?)
data class BrowseKey(val kind: String="list",val category: String="",val order: String="") {
    init {
        require(kind in setOf("list","update","rank"))
        require(category.matches(Regex("[a-z]*")))
        require(order in setOf("","update","view"))
        require(kind=="list" || (category.isEmpty() && order.isEmpty()))
    }
    val basePath get()=if(kind=="list") "/list/"+if(category.isEmpty()) "" else "$category/" else "/$kind/"
    val firstPath get()=basePath+if(kind=="list" && order.isNotEmpty()) "$order.html" else ""
    fun url(page: Int): String {
        require(page>0)
        return if(page==1) MangaSource.BASE+firstPath else okhttp3.HttpUrl.Builder()
            .scheme("https").host("m.manhuagui.com").encodedPath(basePath)
            .addQueryParameter("page",page.toString()).addQueryParameter("ajax","1")
            .addQueryParameter("order",if(kind=="list") order else "1")
            .apply { if(kind=="list") addQueryParameter("catid","0") }.build().toString()
    }
}
data class BrowsePage(val entries: List<BrowseEntry>,val totalPages: Int?,val categories: List<MangaCategory>)

class DiscoverSource(private val source: MangaSource) {
    private suspend fun get(url: String): String=withContext(Dispatchers.IO) {
        source.client.newCall(source.request(url).build()).execute().use { r ->
            if(!r.isSuccessful) throw IOException("站点请求失败（${r.code}），请稍后重试")
            val body=r.body ?: throw IOException("站点返回为空")
            if(body.contentLength()>4_000_000) throw IOException("页面过大")
            body.string().also { if(it.length>4_000_000) throw IOException("页面过大") }
        }
    }
    suspend fun home()=withContext(Dispatchers.IO) { parseHome(get(MangaSource.BASE+"/")) }
    suspend fun browse(key: BrowseKey,page: Int)=withContext(Dispatchers.IO) { parseBrowse(get(key.url(page)),page) }

    companion object {
        private val bookPath=Regex("^/comic/(\\d+)/$")
        private fun field(a: Element,key: String)=a.select("dl").firstOrNull { it.select("dt").text().replace(" ","").contains(key) }?.select("dd")?.text().orEmpty()
        private fun book(a: Element,banner: Boolean=false): Manga? {
            val id=bookPath.matchEntire(a.attr("href"))?.groupValues?.get(1) ?: return null
            val title=a.selectFirst(if(banner) "p" else "h3")?.text()?.trim().orEmpty()
            if(title.isBlank()) return null
            val img=a.selectFirst("img")
            val cover=img?.attr("data-src")?.ifBlank { img.attr("src") }.orEmpty()
            return Manga(id,title,MangaSource.absolute(cover),field(a,"作者"),category=field(a,"类别"),latest=field(a,"更新至").ifBlank { a.selectFirst("p span")?.text().orEmpty() })
        }
        fun parseHome(html: String): DiscoverHome {
            val doc=Jsoup.parse(html,MangaSource.BASE)
            val sections=doc.select(".main-list").mapNotNull { list ->
                val title=list.previousElementSibling()?.selectFirst("h2")?.text() ?: return@mapNotNull null
                val books=list.select("li a[href]").mapNotNull { book(it) }.distinctBy { it.id }
                if(books.isEmpty()) return@mapNotNull null
                HomeSection(list.id().ifBlank { title },title,books)
            }
            if(sections.isEmpty()) throw IOException("首页暂时无法解析，请重试")
            return DiscoverHome(doc.select("#topSlider a[href]").mapNotNull { book(it,true) }.distinctBy { it.id },sections)
        }
        fun parseBrowse(html: String,page: Int): BrowsePage {
            val doc=Jsoup.parse(html,MangaSource.BASE)
            val list=doc.selectFirst("#detail") ?: doc.body()
            val entries=list.select("li a[href]").mapNotNull { a ->
                val b=book(a) ?: return@mapNotNull null
                BrowseEntry(b,a.selectFirst(".thumb i")?.text().orEmpty(),field(a,"更新于"),a.parent()?.selectFirst(".rank, .order")?.text()?.toIntOrNull())
            }.distinctBy { it.book.id }
            val count=Regex("pageCount\\s*[:=]\\s*(\\d+)").find(html)?.groupValues?.get(1)?.toIntOrNull()
            // An empty AJAX fragment is a legitimate last page. An HTML error/challenge is not.
            if(entries.isEmpty() && !(html.isBlank() && page>1) && count!=0 && !doc.text().contains("没有") && !doc.text().contains("未找到")) throw IOException("列表暂时无法解析，请重试")
            val categories=doc.select("#popCat2 a[href]").mapNotNull { a ->
                val m=Regex("^/list/(?:([a-z]+)/)?$").matchEntire(a.attr("href")) ?: return@mapNotNull null
                MangaCategory(m.groupValues[1],a.text().replace(Regex("\\s+"),""))
            }.distinctBy { it.slug }
            return BrowsePage(entries,count,categories)
        }
    }
}
