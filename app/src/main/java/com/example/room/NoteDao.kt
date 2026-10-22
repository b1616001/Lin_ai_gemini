package com.example.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 記事資料存取物件 (NoteDao)
 *
 * 提供所有對 notes 資料表的 CRUD 異動與即時 Flow 監聽
 */
@Dao
interface NoteDao {

    /**
     * 取得所有記事清單（依時間戳記由新至舊排序）
     */
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    /**
     * 關鍵字搜尋標題與內容
     */
    @Query("SELECT * FROM notes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    /**
     * 取得單筆記事
     */
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): NoteEntity?

    /**
     * 新增記事（若主鍵衝突則替換）
     *
     * @return 插入後產生的新 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    /**
     * 修改更新既有記事
     *
     * @return 影響筆數
     */
    @Update
    suspend fun update(note: NoteEntity): Int

    /**
     * 刪除指定記事
     *
     * @return 影響筆數
     */
    @Delete
    suspend fun delete(note: NoteEntity): Int

    /**
     * 依據主鍵 ID 刪除
     */
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /**
     * 清空所有記事
     */
    @Query("DELETE FROM notes")
    suspend fun clearAll(): Int
}
