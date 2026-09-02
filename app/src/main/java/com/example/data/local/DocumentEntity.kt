package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val tierId: Int = 3, // Default to Deep Semantic
    val genre: String = "Noir Gerilim",
    val wordCount: Int = 0,
    val customBannedWords: String = "" // Comma separated
)
