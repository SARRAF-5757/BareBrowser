package com.barebrowser.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabs")
data class Tab(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val isPinned: Boolean = false,
    val previewPath: String? = null,
    val lastAccessed: Long = System.currentTimeMillis()
)
