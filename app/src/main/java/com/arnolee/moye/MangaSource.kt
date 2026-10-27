package com.arnolee.moye

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Parses only data. No JavaScript, advertisements or remote code is executed. */
class MangaSource(val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()) {
    companion object {
        const val BASE = "https://m.manhuagui.com"
        const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
        fun mangaId(input: String): String? = Regex("https://(?:m\\.|www\\.)?manhuagui\\.com/comic/(\\d+)(?:/|$)").find(input.trim())?.groupValues?.get(1)
        fun parseSearch(html: String, page: Int): SearchResult {
            val doc = Jsoup.parse(html, BASE)
            val books = doc.select("li a[href~=/comic/\\d+/]").mapNotNull { a ->
                val id = Regex("/comic/(\\d+)/").find(a.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
                val title = a.selectFirst("h3")?.text() ?: return@mapNotNull null
                val img = a.selectFirst("img")
                fun field(key: String) = a.select("dl").firstOrNull { it.select("dt").text().replace(" ", "").contains(key) }?.select("dd")?.text().orEmpty()
                Manga(id, title, absolute(img?.attr("data-src")?.ifBlank { img.attr("src") }.orEmpty()), field("作者"), category = field("类别"), latest = field("更新至"))
            }.distinctBy { it.id }
            val count = Regex("pageCount\\s*:\\s*(\\d+)").find(html)?.groupValues?.get(1)?.toIntOrNull()
            if (books.isEmpty() && !html.contains("没有") && !html.contains("未找到") && !html.contains("pageCount") && !html.contains("搜索")) throw IOException("搜索页面暂时无法解析，请稍后重试")
            return SearchResult(books, count?.let { page < it } ?: (books.size >= 20))
        }
        fun parseDetail(id: String, html: String): Manga {
            val doc=Jsoup.parse(html,BASE)
            val detail=doc.selectFirst(".book-detail") ?: throw IOException("漫画详情加载失败，站点可能暂时不可用")
            fun field(key: String)=detail.select("dl").firstOrNull { it.select("dt").text().contains(key) }?.select("dd")?.text().orEmpty()
            val chapters=doc.select("#chapterList a[href]").mapNotNull { a ->
                Regex("/comic/$id/(\\d+)\\.html").find(a.attr("href"))?.let { Chapter(it.groupValues[1], a.text()) }
            }.distinctBy { it.id }.reversed()
            if(chapters.isEmpty()) throw IOException("未获取到可阅读章节，请稍后重试")
            return Manga(id,doc.selectFirst("h1")?.text().orEmpty().ifBlank { doc.title().substringBefore("漫画_") },absolute(detail.selectFirst("img")?.attr("src").orEmpty()),field("作者"),detail.selectFirst("#bookIntro")?.text().orEmpty(),field("类别"),field("更新至"),chapters)
        }
        fun absolute(s: String) = when { s.startsWith("//") -> "https:$s"; s.startsWith("/") -> BASE+s; s.startsWith("http://") -> s.replaceFirst("http://","https://"); else -> s }
    }
    private fun text(request: Request): String = client.newCall(request).execute().use { r ->
        if(!r.isSuccessful) throw IOException(if(r.code==403) "站点暂时拒绝访问，请稍后重试" else "网络请求失败（${r.code}）")
        val body=r.body ?: throw IOException("服务器返回为空")
        if(body.contentLength()>4_000_000) throw IOException("页面过大")
        body.string()
    }
    fun request(url: String, referer: String = "$BASE/") = Request.Builder().url(url).header("User-Agent",UA).header("Referer",referer)
    suspend fun search(keyword: String,page: Int=1): SearchResult = withContext(Dispatchers.IO) {
        val url="$BASE/s/${URLEncoder.encode(keyword.trim(),"UTF-8").replace("+","%20")}.html"
        val req=request(url)
        if(page>1) req.post(FormBody.Builder().add("page",page.toString()).add("ajax","1").add("order","0").add("key",keyword.trim()).build())
        parseSearch(text(req.build()),page)
    }
    suspend fun detail(id: String): Manga = withContext(Dispatchers.IO) { require(id.matches(Regex("\\d+"))); parseDetail(id,text(request("$BASE/comic/$id/").build())) }
    suspend fun pages(bookId: String,chapter: Chapter): ChapterPages = withContext(Dispatchers.IO) {
        require(bookId.matches(Regex("\\d+")) && chapter.id.matches(Regex("\\d+")))
        ChapterDecoder.decode(bookId, chapter, text(request("$BASE/comic/$bookId/${chapter.id}.html").build()))
    }
}

object ChapterDecoder {
    fun decode(bookId: String, chapter: Chapter, html: String): ChapterPages {
        require(html.length < 4_000_000) { "章节数据过大" }
        val packed=Regex("\\}\\('([^']*)',(\\d+),(\\d+),'([^']*)'").find(html)
        val data=if(packed!=null) {
            val (payload,radixText,countText,compressed)=packed.destructured
            val radix=radixText.toInt(); val count=countText.toInt()
            require(radix in 2..62 && count in 1..100_000)
            val words = if(compressed.contains('|')) compressed.split('|') else LzBase64.decode(compressed).split('|')
            val alphabet="0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            fun encode(n: Int): String = if(n<radix) alphabet[n].toString() else encode(n/radix)+alphabet[n%radix]
            val map=(0 until count).associate { encode(it) to words.getOrElse(it) { "" } }
            val unpacked=Regex("\\b\\w+\\b").replace(payload) { map[it.value]?.ifBlank { it.value } ?: it.value }
            unpacked.substring(unpacked.indexOf('{'),unpacked.lastIndexOf('}')+1)
        } else Regex("SMH\\.reader\\((\\{.*?\\})\\)",RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1) ?: throw IOException("章节解析失败，数据源可能已更新")
        val j=JSONObject(data)
        require(j.get("bookId").toString()==bookId && j.get("chapterId").toString()==chapter.id) { "章节数据不匹配" }
        val a=j.getJSONArray("images")
        val paths=(0 until a.length()).map { a.getString(it) }
        require(paths.isNotEmpty() && paths.size<=3000 && paths.size==j.getInt("count")) { "章节图片不完整" }
        require(paths.all { it.startsWith("/") && !it.startsWith("//") && !it.contains("..") }) { "图片路径无效" }
        val q=j.optJSONObject("sl") ?: JSONObject()
        fun neighbor(key: String)=j.optString(key).takeIf { it.matches(Regex("[1-9]\\d*")) }
        return ChapterPages(bookId,Chapter(chapter.id,j.optString("chapterTitle",chapter.title)),paths,q.keys().asSequence().associateWith { q.get(it).toString() },neighbor("prevId"),neighbor("nextId"))
    }
}

/** LZ-string Base64 decoder used only on the packed symbol dictionary. */
object LzBase64 {
    fun decode(input: String): String {
        require(input.isNotEmpty() && input.length<=1_000_000)
        val alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="
        val values=input.map { alphabet.indexOf(it).also { v -> require(v>=0) } }; var pos=0
        fun bits(n: Int): Int { var out=0; repeat(n) { j -> require(pos/6<values.size) { "压缩数据截断" }; out=out or (((values[pos/6] shr (5-pos%6)) and 1) shl j); pos++ }; return out }
        val dictionary=mutableMapOf<Int,String>();var size=4;var width=3;var enlarge=4
        val kind=bits(2);require(kind in 0..1)
        var w=bits(if(kind==0)8 else 16).toChar().toString();dictionary[3]=w
        val result=StringBuilder(w)
        while(true) {
            var code=bits(width)
            when(code) {
                0,1 -> { dictionary[size]=bits(if(code==0)8 else 16).toChar().toString();code=size++;enlarge-- }
                2 -> return result.toString()
            }
            if(enlarge==0) { enlarge=1 shl width;width++ }
            val entry=dictionary[code] ?: if(code==size) w+w[0] else throw IOException("压缩字典无效")
            result.append(entry);require(result.length<4_000_000 && size<200_000)
            dictionary[size++]=w+entry[0];enlarge--;w=entry
            if(enlarge==0) { enlarge=1 shl width;width++ }
        }
    }
}
