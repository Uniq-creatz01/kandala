package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String, // e.g. "kandala_king"
    val displayName: String, // e.g. "Kandala King"
    val bio: String = "",
    val avatarUrl: String = "", // drawable name or uri
    val avatarColorHex: String = "#8B5CF6",
    val isCurrentUser: Boolean = false,
    val isFollowing: Boolean = false,
    val followerCount: Int = 0,
    val memeCount: Int = 0
)
