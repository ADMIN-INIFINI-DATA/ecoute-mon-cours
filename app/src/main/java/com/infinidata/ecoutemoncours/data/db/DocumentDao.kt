package com.infinidata.ecoutemoncours.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun byId(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE title LIKE '%' || :q || '%' OR text LIKE '%' || :q || '%' ORDER BY lastOpenedAt DESC")
    fun search(q: String): Flow<List<DocumentEntity>>

    @Insert
    suspend fun insert(doc: DocumentEntity): Long

    @Update
    suspend fun update(doc: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE documents SET resumeOffset = :offset, lastOpenedAt = :ts WHERE id = :id")
    suspend fun saveProgress(id: Long, offset: Int, ts: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET summary = :summary WHERE id = :id")
    suspend fun saveSummary(id: Long, summary: String)

    @Query("UPDATE documents SET title = :title, subject = :subject WHERE id = :id")
    suspend fun rename(id: Long, title: String, subject: String)
}
