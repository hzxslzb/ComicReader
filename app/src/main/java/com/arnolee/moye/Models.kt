package com.arnolee.moye

import org.json.JSONArray
import org.json.JSONObject

data class Chapter(val id: String, val title: String) {
    fun json() = JSONObject().put("id", id).put("title", title)
    companion object { fun from(j: JSONObject) = Chapter(j.getString("id"), j.getString("title")) }
}
data class Manga(val id: String, val title: String, val cover: String = "", val author: String = "", val summary: String = "", val category: String = "", val latest: String = "", val chapters: List<Chapter> = emptyList()) {
    fun json() = JSONObject().put("id", id).put("title", title).put("cover", cover).put("author", author).put("summary", summary).put("category", category).put("latest", latest).put("chapters", JSONArray(chapters.map { it.json() }))
    companion object { fun from(j: JSONObject) = Manga(j.getString("id"), j.getString("title"), j.optString("cover"), j.optString("author"), j.optString("summary"), j.optString("category"), j.optString("latest"), j.optJSONArray("chapters")?.let { a -> (0 until a.length()).map { Chapter.from(a.getJSONObject(it)) } } ?: emptyList()) }
}
data class SearchResult(val books: List<Manga>, val hasMore: Boolean)
data class ChapterPages(val mangaId: String, val chapter: Chapter, val paths: List<String>, val query: Map<String, String>, val prevId: String?, val nextId: String?) {
    val referer get() = "https://m.manhuagui.com/comic/$mangaId/${chapter.id}.html"
    fun url(index: Int, host: String = "eu") = okhttp3.HttpUrl.Builder().scheme("https").host("$host.hamreus.com").encodedPath(paths[index]).apply { query.forEach { (k,v) -> addQueryParameter(k,v) } }.build().toString()
    fun json() = JSONObject().put("book", mangaId).put("chapter", chapter.json()).put("paths", JSONArray(paths)).put("query", JSONObject(query)).put("prev", prevId ?: "").put("next", nextId ?: "")
    companion object { fun from(j: JSONObject): ChapterPages {
        val p=j.getJSONArray("paths"); val q=j.getJSONObject("query")
        return ChapterPages(j.getString("book"), Chapter.from(j.getJSONObject("chapter")), (0 until p.length()).map { p.getString(it) }, q.keys().asSequence().associateWith { q.getString(it) }, j.optString("prev").ifBlank { null }, j.optString("next").ifBlank { null })
    } }
}
data class Progress(val bookId: String, val chapterId: String, val chapterTitle: String, val page: Int, val total: Int, val time: Long)
data class Download(val bookId: String, val chapterId: String, val title: String, val state: String, val done: Int, val total: Int, val error: String) { val key get() = "$bookId/$chapterId" }
