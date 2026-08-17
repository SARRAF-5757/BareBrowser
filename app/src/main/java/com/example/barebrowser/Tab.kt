package com.example.barebrowser

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Tab(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "about:blank",
    val isPinned: Boolean = false,
    val lastAccessed: Long = System.currentTimeMillis()
)
