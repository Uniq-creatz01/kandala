package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.ChatEntity
import com.example.model.MemeEntity
import com.example.model.MessageEntity
import com.example.model.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KandalaDao {

    // --- Users ---
    @Query("SELECT * FROM users ORDER BY isCurrentUser DESC, followerCount DESC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE isCurrentUser = 0 AND (username LIKE '%' || :query || '%' OR displayName LIKE '%' || :query || '%')")
    fun searchUsers(query: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE isCurrentUser = 1 LIMIT 1")
    fun getCurrentUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun getUserById(userId: String): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET isFollowing = :isFollowing, followerCount = MAX(0, followerCount + :followerDelta) WHERE id = :userId")
    suspend fun updateFollowStatus(userId: String, isFollowing: Boolean, followerDelta: Int)

    // --- Memes ---
    @Query("SELECT * FROM memes ORDER BY createdAt DESC")
    fun getAllMemes(): Flow<List<MemeEntity>>

    @Query("""
        SELECT memes.* FROM memes
        INNER JOIN users ON memes.creatorId = users.id
        WHERE users.isFollowing = 1
        ORDER BY memes.createdAt DESC
    """)
    fun getFollowingMemes(): Flow<List<MemeEntity>>

    @Query("SELECT * FROM memes WHERE creatorId = :creatorId ORDER BY createdAt DESC")
    fun getMemesByCreator(creatorId: String): Flow<List<MemeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeme(meme: MemeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemes(memes: List<MemeEntity>)

    @Query("UPDATE memes SET isLiked = :isLiked, likesCount = MAX(0, likesCount + :delta) WHERE id = :memeId")
    suspend fun updateLikeStatus(memeId: String, isLiked: Boolean, delta: Int)

    @Query("UPDATE memes SET downloadsCount = downloadsCount + 1 WHERE id = :memeId")
    suspend fun incrementDownloads(memeId: String)

    @Query("DELETE FROM memes WHERE id = :memeId")
    suspend fun deleteMeme(memeId: String)

    // --- Chats & Messages ---
    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    fun getChatById(chatId: String): Flow<ChatEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Query("UPDATE chats SET lastMessage = :lastMessage, lastMessageTime = :timestamp WHERE id = :chatId")
    suspend fun updateChatLastMessage(chatId: String, lastMessage: String, timestamp: Long)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)
}
