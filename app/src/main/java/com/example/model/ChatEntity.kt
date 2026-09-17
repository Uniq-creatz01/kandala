package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val isGroup: Boolean = false,
    val participantIds: String, // comma-separated user IDs
    val lastMessage: String = "",
    val lastMessageTime: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val iconName: String = "",
    val avatarColorHex: String = "#06B6D4"
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val senderUsername: String,
    val text: String = "",
    val mediaUri: String? = null, // high quality media photo / meme sharing
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean = true
)
