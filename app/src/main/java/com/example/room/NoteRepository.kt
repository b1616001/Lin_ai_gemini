package com.example.room

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * 記事資料儲存庫 (NoteRepository)
 *
 * 封裝對 NoteDao 的操作，確保資料庫在背景 IO 執行緒中安全執行
 */
class NoteRepository(private val noteDao: NoteDao) {

    /**
     * 即時監聽所有記事清單
     */
    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllNotes().flowOn(Dispatchers.IO)

    /**
     * 關鍵字搜尋記事清單
     */
    fun searchNotes(query: String): Flow<List<NoteEntity>> {
        return noteDao.searchNotes(query).flowOn(Dispatchers.IO)
    }

    /**
     * 取得單筆記事
     */
    suspend fun getNoteById(id: Long): NoteEntity? = withContext(Dispatchers.IO) {
        noteDao.getNoteById(id)
    }

    /**
     * 新增記事
     */
    suspend fun insert(note: NoteEntity): Long = withContext(Dispatchers.IO) {
        noteDao.insert(note)
    }

    /**
     * 修改更新既有記事
     */
    suspend fun update(note: NoteEntity): Int = withContext(Dispatchers.IO) {
        noteDao.update(note)
    }

    /**
     * 刪除指定記事
     */
    suspend fun delete(note: NoteEntity): Int = withContext(Dispatchers.IO) {
        noteDao.delete(note)
    }

    /**
     * 依主鍵 ID 刪除記事
     */
    suspend fun deleteById(id: Long): Int = withContext(Dispatchers.IO) {
        noteDao.deleteById(id)
    }

    /**
     * 清空資料庫
     */
    suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        noteDao.clearAll()
    }
}
