package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memes")
data class MemeEntity(
    @PrimaryKey val id: String,
    val creatorId: String,
    val creatorUsername: String,
    val creatorDisplayName: String,
    val creatorAvatar: String = "",
    val creatorAvatarColorHex: String = "#8B5CF6",
    val imageUri: String, // file path, drawable name, or content uri
    val topText: String = "",
    val bottomText: String = "",
    val caption: String = "",
    val likesCount: Int = 0,
    val downloadsCount: Int = 0,
    val isLiked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
