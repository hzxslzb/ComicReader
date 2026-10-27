package com.arnolee.moye

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File

class LibraryStore(context: Context) : SQLiteOpenHelper(context, "library.db", null, 1) {
    val revision=MutableStateFlow(0)
    private val root=File(context.filesDir,"chapters").apply { mkdirs() }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE books(id TEXT PRIMARY KEY, data TEXT NOT NULL, favorite INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE progress(book TEXT, chapter TEXT, title TEXT, page INTEGER, total INTEGER, time INTEGER, PRIMARY KEY(book,chapter))")
        db.execSQL("CREATE TABLE manifests(book TEXT, chapter TEXT, data TEXT, PRIMARY KEY(book,chapter))")
        db.execSQL("CREATE TABLE downloads(book TEXT, chapter TEXT, title TEXT, state TEXT, done INTEGER DEFAULT 0, total INTEGER DEFAULT 0, error TEXT DEFAULT '', created INTEGER, PRIMARY KEY(book,chapter))")
    }
    override fun onUpgrade(db: SQLiteDatabase,oldVersion: Int,newVersion: Int) { error("Missing database migration") }
    private fun changed() { revision.update { it+1 } }
    @Synchronized fun putBook(book: Manga) {
        val old=book(book.id)
        // Search cards must never erase the cached chapter catalog used offline.
        val merged=if(book.chapters.isEmpty() && old!=null) old else book
        writableDatabase.execSQL("INSERT INTO books(id,data,favorite) VALUES(?,?,0) ON CONFLICT(id) DO UPDATE SET data=excluded.data", arrayOf(merged.id,merged.json().toString()))
        changed()
    }
    @Synchronized fun book(id: String): Manga? = readableDatabase.rawQuery("SELECT data FROM books WHERE id=?",arrayOf(id)).use { if(it.moveToFirst()) Manga.from(JSONObject(it.getString(0))) else null }
    @Synchronized fun books(): List<Manga> = readableDatabase.rawQuery("SELECT data FROM books ORDER BY rowid DESC",null).use { c -> buildList { while(c.moveToNext()) add(Manga.from(JSONObject(c.getString(0)))) } }
    @Synchronized fun favorites(): Set<String> = readableDatabase.rawQuery("SELECT id FROM books WHERE favorite=1",null).use { c -> buildSet { while(c.moveToNext()) add(c.getString(0)) } }
    @Synchronized fun toggleFavorite(id: String) { writableDatabase.execSQL("UPDATE books SET favorite=1-favorite WHERE id=?",arrayOf(id));changed() }
    @Synchronized fun saveProgress(book: String,chapter: Chapter,page: Int,total: Int) {
        if(total<=0 || page !in 0 until total) return
        writableDatabase.execSQL("INSERT OR REPLACE INTO progress VALUES(?,?,?,?,?,?)",arrayOf(book,chapter.id,chapter.title,page,total,System.currentTimeMillis()))
        changed()
    }
    @Synchronized fun progress(book: String,chapter: String?=null): Progress? = readableDatabase.rawQuery(if(chapter==null) "SELECT * FROM progress WHERE book=? ORDER BY time DESC,rowid DESC LIMIT 1" else "SELECT * FROM progress WHERE book=? AND chapter=?", if(chapter==null) arrayOf(book) else arrayOf(book,chapter)).use { c -> if(c.moveToFirst()) Progress(c.getString(0),c.getString(1),c.getString(2),c.getInt(3),c.getInt(4),c.getLong(5)) else null }
    @Synchronized fun chapterHistory(book: String): List<Progress> = readableDatabase.rawQuery("SELECT * FROM progress WHERE book=? ORDER BY time DESC,rowid DESC",arrayOf(book)).use { c -> buildList { while(c.moveToNext()) add(Progress(c.getString(0),c.getString(1),c.getString(2),c.getInt(3),c.getInt(4),c.getLong(5))) } }
    @Synchronized fun history(): List<Progress> = readableDatabase.rawQuery("SELECT p.* FROM progress p WHERE p.rowid=(SELECT q.rowid FROM progress q WHERE q.book=p.book ORDER BY q.time DESC,q.rowid DESC LIMIT 1) ORDER BY p.time DESC",null).use { c -> buildList { while(c.moveToNext()) add(Progress(c.getString(0),c.getString(1),c.getString(2),c.getInt(3),c.getInt(4),c.getLong(5))) } }
    @Synchronized fun putManifest(p: ChapterPages) { writableDatabase.execSQL("INSERT OR REPLACE INTO manifests VALUES(?,?,?)",arrayOf(p.mangaId,p.chapter.id,p.json().toString()));changed() }
    @Synchronized fun manifest(book: String,chapter: String): ChapterPages? = readableDatabase.rawQuery("SELECT data FROM manifests WHERE book=? AND chapter=?",arrayOf(book,chapter)).use { if(it.moveToFirst()) ChapterPages.from(JSONObject(it.getString(0))) else null }
    fun pageFile(book: String,chapter: String,page: Int): File { require(book.matches(Regex("\\d+")) && chapter.matches(Regex("\\d+")) && page>=0);return File(root,"$book/$chapter/${page.toString().padStart(4,'0')}.img") }
    fun available(book: String,chapter: String,page: Int)=pageFile(book,chapter,page).let { it.isFile && it.length()>0 }
    fun complete(p: ChapterPages) = p.paths.indices.all { available(p.mangaId,p.chapter.id,it) }
    @Synchronized fun enqueue(book: Manga,chapters: List<Chapter>) {
        putBook(book)
        writableDatabase.beginTransaction()
        try {
            chapters.forEach { c ->
                val old=download(book.id,c.id)
                if(old?.state!="complete" && old?.state!="downloading") writableDatabase.execSQL("INSERT INTO downloads(book,chapter,title,state,created) VALUES(?,?,?,'queued',?) ON CONFLICT(book,chapter) DO UPDATE SET state='queued',error=''",arrayOf(book.id,c.id,c.title,System.currentTimeMillis()))
            }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        changed()
    }
    @Synchronized fun downloads(): List<Download> = readableDatabase.rawQuery("SELECT book,chapter,title,state,done,total,error FROM downloads ORDER BY created ASC,rowid ASC",null).use { c -> buildList { while(c.moveToNext()) add(Download(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4),c.getInt(5),c.getString(6))) } }
    fun download(book: String,chapter: String)=downloads().firstOrNull { it.bookId==book && it.chapterId==chapter }
    @Synchronized fun updateDownload(book: String,chapter: String,state: String,done: Int?=null,total: Int?=null,error: String="",onlyIf: String?=null) {
        val v=ContentValues().apply { put("state",state);put("error",error.take(300));done?.let { put("done",it) };total?.let { put("total",it) } }
        writableDatabase.update("downloads",v,"book=? AND chapter=?"+(if(onlyIf!=null) " AND state=?" else ""),if(onlyIf!=null) arrayOf(book,chapter,onlyIf) else arrayOf(book,chapter));changed()
    }
    @Synchronized fun recoverInterrupted() { writableDatabase.execSQL("UPDATE downloads SET state='paused',error='上次下载已中断，点击继续' WHERE state IN ('downloading','queued')");changed() }
    @Synchronized fun removeDownload(d: Download) {
        writableDatabase.delete("downloads","book=? AND chapter=?",arrayOf(d.bookId,d.chapterId))
        pageFile(d.bookId,d.chapterId,0).parentFile?.deleteRecursively();changed()
    }
    /** Final rename and task-state check share the deletion lock. */
    @Synchronized fun commitPage(d: Download,index: Int,temp: File): Boolean {
        if(download(d.bookId,d.chapterId)?.state!="downloading") { temp.delete();return false }
        val dest=pageFile(d.bookId,d.chapterId,index)
        if(!temp.renameTo(dest)) { temp.delete();throw java.io.IOException("无法保存图片，请检查可用空间") }
        return true
    }
    fun bytesUsed(): Long=root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
