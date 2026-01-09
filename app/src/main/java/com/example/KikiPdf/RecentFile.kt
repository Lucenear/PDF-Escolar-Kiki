package com.kikipdf

data class RecentFile(
    val uri: String,
    val name: String,
    val isFavorite: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
