package com.infinidata.ecoutemoncours.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String = "Divers",
    val source: String = "scan",          // scan | image | pdf | docx | texte | web
    val text: String,
    val charCount: Int = text.length,
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    /** Position de reprise, en nombre de caracteres depuis le debut du texte. */
    val resumeOffset: Int = 0,
    val summary: String? = null,
    val ocrEngine: String = "local",      // local | cloud
    val attribution: String? = null       // licence / source pour les contenus web
)
