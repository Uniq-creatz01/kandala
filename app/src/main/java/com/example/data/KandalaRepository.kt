package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.R
import com.example.model.ChatEntity
import com.example.model.MemeEntity
import com.example.model.MessageEntity
import com.example.model.UserEntity
import com.example.utils.StorageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class KandalaRepository(
    private val dao: KandalaDao,
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    init {
        scope.launch {
            seedInitialDataIfNeeded()
        }
    }

    val allMemes: Flow<List<MemeEntity>> = dao.getAllMemes()
    val followingMemes: Flow<List<MemeEntity>> = dao.getFollowingMemes()
    val allUsers: Flow<List<UserEntity>> = dao.getAllUsers()
    val currentUser: Flow<UserEntity?> = dao.getCurrentUser()
    val allChats: Flow<List<ChatEntity>> = dao.getAllChats()

    fun searchUsers(query: String): Flow<List<UserEntity>> = dao.searchUsers(query)

    fun getChatMessages(chatId: String): Flow<List<MessageEntity>> = dao.getMessagesForChat(chatId)

    suspend fun toggleFollow(user: UserEntity) {
        val newStatus = !user.isFollowing
        val delta = if (newStatus) 1 else -1
        dao.updateFollowStatus(user.id, newStatus, delta)
    }

    suspend fun toggleLike(meme: MemeEntity) {
        val newStatus = !meme.isLiked
        val delta = if (newStatus) 1 else -1
        dao.updateLikeStatus(meme.id, newStatus, delta)
    }

    suspend fun downloadMeme(meme: MemeEntity): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val bitmap = loadBitmapForMeme(meme)
                ?: return@withContext Result.failure(Exception("Could not load meme image for download"))

            val result = StorageUtils.saveMemeToDeviceStorage(
                context = context,
                bitmap = bitmap,
                title = "Kandala_${meme.creatorUsername}"
            )
            if (result.isSuccess) {
                dao.incrementDownloads(meme.id)
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun postCreatedMeme(
        bitmap: Bitmap,
        topText: String,
        bottomText: String,
        caption: String
    ): MemeEntity = withContext(Dispatchers.IO) {
        val localPath = StorageUtils.saveMemeLocally(context, bitmap)
        val current = dao.getCurrentUser().firstOrNull()
            ?: UserEntity(
                id = "current_user",
                username = "kandala_creator",
                displayName = "Kandala Artist",
                bio = "Meme enthusiast & creator on Kandala Hub 🔥",
                isCurrentUser = true
            )

        val newMeme = MemeEntity(
            id = UUID.randomUUID().toString(),
            creatorId = current.id,
            creatorUsername = current.username,
            creatorDisplayName = current.displayName,
            creatorAvatar = current.avatarUrl,
            creatorAvatarColorHex = current.avatarColorHex,
            imageUri = localPath,
            topText = topText,
            bottomText = bottomText,
            caption = caption.ifBlank { "Fresh drop on Kandala Hub! 🚀" },
            likesCount = 1,
            downloadsCount = 0,
            isLiked = true,
            createdAt = System.currentTimeMillis()
        )

        dao.insertMeme(newMeme)
        // Update user meme count
        dao.updateUser(current.copy(memeCount = current.memeCount + 1))
        newMeme
    }

    suspend fun updateProfile(displayName: String, username: String, bio: String) {
        val current = dao.getCurrentUser().firstOrNull() ?: return
        val cleanUsername = username.trim().removePrefix("@").replace(" ", "_").lowercase()
        dao.updateUser(
            current.copy(
                displayName = displayName.trim(),
                username = cleanUsername,
                bio = bio.trim()
            )
        )
    }

    suspend fun createDirectChat(targetUser: UserEntity): String = withContext(Dispatchers.IO) {
        val current = dao.getCurrentUser().firstOrNull()
        val currentId = current?.id ?: "current_user"

        // Check if chat already exists
        val existingChats = dao.getAllChats().firstOrNull().orEmpty()
        val found = existingChats.find { !it.isGroup && it.participantIds.contains(targetUser.id) }
        if (found != null) {
            return@withContext found.id
        }

        val newChatId = "chat_${UUID.randomUUID()}"
        val newChat = ChatEntity(
            id = newChatId,
            title = targetUser.displayName,
            isGroup = false,
            participantIds = "$currentId,${targetUser.id}",
            lastMessage = "Started conversation with @${targetUser.username}",
            lastMessageTime = System.currentTimeMillis(),
            avatarColorHex = targetUser.avatarColorHex
        )
        dao.insertChat(newChat)

        // Initial welcome message from the creator
        val welcomeMsg = MessageEntity(
            id = UUID.randomUUID().toString(),
            chatId = newChatId,
            senderId = targetUser.id,
            senderName = targetUser.displayName,
            senderUsername = targetUser.username,
            text = "Hey there! Thanks for reaching out on Kandala Hub! Got any dank memes today? 😂",
            mediaUri = null,
            timestamp = System.currentTimeMillis(),
            isFromMe = false
        )
        dao.insertMessage(welcomeMsg)
        newChatId
    }

    suspend fun createGroupChat(title: String, memberUsers: List<UserEntity>): String = withContext(Dispatchers.IO) {
        val current = dao.getCurrentUser().firstOrNull()
        val currentId = current?.id ?: "current_user"
        val memberIds = (listOf(currentId) + memberUsers.map { it.id }).joinToString(",")

        val newChatId = "group_${UUID.randomUUID()}"
        val newChat = ChatEntity(
            id = newChatId,
            title = title.trim(),
            isGroup = true,
            participantIds = memberIds,
            lastMessage = "Group created with ${memberUsers.size + 1} members",
            lastMessageTime = System.currentTimeMillis(),
            avatarColorHex = "#EC4899"
        )
        dao.insertChat(newChat)

        // Initial system message
        val intro = MessageEntity(
            id = UUID.randomUUID().toString(),
            chatId = newChatId,
            senderId = "system",
            senderName = "Kandala System",
            senderUsername = "system",
            text = "Welcome to the group '$title'! Share top-tier memes and photos with everyone! 🚀",
            mediaUri = null,
            timestamp = System.currentTimeMillis(),
            isFromMe = false
        )
        dao.insertMessage(intro)
        newChatId
    }

    suspend fun sendMessage(chatId: String, text: String, mediaUri: String?) = withContext(Dispatchers.IO) {
        val current = dao.getCurrentUser().firstOrNull()
        val currentId = current?.id ?: "current_user"

        val msg = MessageEntity(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = currentId,
            senderName = current?.displayName ?: "Kandala Artist",
            senderUsername = current?.username ?: "kandala_creator",
            text = text,
            mediaUri = mediaUri,
            timestamp = System.currentTimeMillis(),
            isFromMe = true
        )
        dao.insertMessage(msg)

        val snippet = when {
            text.isNotBlank() -> text
            mediaUri != null -> "📷 Shared media"
            else -> "Sent a meme"
        }
        dao.updateChatLastMessage(chatId, snippet, System.currentTimeMillis())

        // Trigger realistic reply
        triggerAutoReply(chatId, text, mediaUri != null)
    }

    private fun triggerAutoReply(chatId: String, userText: String, hasMedia: Boolean) {
        scope.launch {
            delay(1200) // Realistic interactive delay
            val chat = dao.getChatById(chatId).firstOrNull() ?: return@launch
            val users = dao.getAllUsers().firstOrNull().orEmpty()

            val responder = if (!chat.isGroup) {
                users.firstOrNull { it.id != "current_user" && chat.participantIds.contains(it.id) }
            } else {
                val groupMembers = users.filter { it.id != "current_user" && chat.participantIds.contains(it.id) }
                groupMembers.randomOrNull()
            } ?: users.firstOrNull { it.id != "current_user" } ?: return@launch

            val replies = if (hasMedia) {
                listOf(
                    "Bro this meme is pure gold!! 💀🔥",
                    "Saving this directly to my gallery right now! 💯",
                    "Certified hood classic on Kandala Hub 😂",
                    "10/10 quality meme, posting this on my story!"
                )
            } else {
                when {
                    userText.contains("hi", ignoreCase = true) || userText.contains("hello", ignoreCase = true) ->
                        listOf("Yo what's up! Ready to create some viral memes?", "Heyy! Welcome to Kandala Hub chat 🚀", "What's good fellow meme lord?")
                    userText.contains("meme", ignoreCase = true) ->
                        listOf("Memes are the true currency of the internet 💎", "Just opened the Meme Studio to cook something spicy!", "Check out the Following feed for my latest drop!")
                    else ->
                        listOf(
                            "Haha totally agree with you! 😂",
                            "That's so real honestly 💀",
                            "Kandala Hub community never misses 💯",
                            "Can't wait to see your next meme creation!"
                        )
                }
            }

            val replyText = replies.random()
            val replyMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                senderId = responder.id,
                senderName = responder.displayName,
                senderUsername = responder.username,
                text = replyText,
                mediaUri = null,
                timestamp = System.currentTimeMillis(),
                isFromMe = false
            )
            dao.insertMessage(replyMsg)
            dao.updateChatLastMessage(chatId, replyText, System.currentTimeMillis())
        }
    }

    private suspend fun loadBitmapForMeme(meme: MemeEntity): Bitmap? = withContext(Dispatchers.IO) {
        try {
            when {
                meme.imageUri.startsWith("drawable/") -> {
                    val resName = meme.imageUri.removePrefix("drawable/")
                    val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
                    if (resId != 0) {
                        BitmapFactory.decodeResource(context.resources, resId)
                    } else null
                }
                meme.imageUri.startsWith("content://") -> {
                    val uri = Uri.parse(meme.imageUri)
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
                else -> {
                    val file = File(meme.imageUri)
                    if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    } else null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun seedInitialDataIfNeeded() {
        val existingUsers = dao.getAllUsers().firstOrNull()
        if (existingUsers.isNullOrEmpty()) {
            val currentUser = UserEntity(
                id = "current_user",
                username = "kandala_creator",
                displayName = "Kandala Artist",
                bio = "Crafting daily laughs and dank memes on Kandala Hub 🔥",
                avatarUrl = "",
                avatarColorHex = "#8B5CF6",
                isCurrentUser = true,
                isFollowing = false,
                followerCount = 420,
                memeCount = 3
            )

            val seedUsers = listOf(
                currentUser,
                UserEntity(
                    id = "u1",
                    username = "kandala_king",
                    displayName = "Kandala King",
                    bio = "The reigning meme monarch of Kandala Hub 👑 Over 100M views!",
                    avatarColorHex = "#F59E0B",
                    isFollowing = true,
                    followerCount = 18400,
                    memeCount = 52
                ),
                UserEntity(
                    id = "u2",
                    username = "meme_queen",
                    displayName = "Zoe Meme Queen",
                    bio = "High voltage humor & relatable chaos ✨ Daily meme creator!",
                    avatarColorHex = "#EC4899",
                    isFollowing = true,
                    followerCount = 12900,
                    memeCount = 41
                ),
                UserEntity(
                    id = "u3",
                    username = "dank_master",
                    displayName = "Dank Master X",
                    bio = "Turning everyday struggles into viral memes 🚀 Follow for laughs!",
                    avatarColorHex = "#06B6D4",
                    isFollowing = false,
                    followerCount = 28500,
                    memeCount = 89
                ),
                UserEntity(
                    id = "u4",
                    username = "pixel_clown",
                    displayName = "Pixel Clown",
                    bio = "Meme engineer & certified clown 🤡 Humor is mandatory.",
                    avatarColorHex = "#10B981",
                    isFollowing = false,
                    followerCount = 6300,
                    memeCount = 24
                ),
                UserEntity(
                    id = "u5",
                    username = "chill_vibes",
                    displayName = "Luna Chill",
                    bio = "Late night vibes, funny cats & wholesome memes 🌙",
                    avatarColorHex = "#8B5CF6",
                    isFollowing = false,
                    followerCount = 9400,
                    memeCount = 33
                )
            )
            dao.insertUsers(seedUsers)

            // Seed Memes
            val seedMemes = listOf(
                MemeEntity(
                    id = "m1",
                    creatorId = "u1",
                    creatorUsername = "kandala_king",
                    creatorDisplayName = "Kandala King",
                    creatorAvatarColorHex = "#F59E0B",
                    imageUri = "drawable/img_template_cat",
                    topText = "WHEN THE COMPILER PASSES",
                    bottomText = "WITHOUT A SINGLE ERROR",
                    caption = "We made it through the storm! Never doubt the process 🚀",
                    likesCount = 1420,
                    downloadsCount = 390,
                    isLiked = true,
                    createdAt = System.currentTimeMillis() - 1000 * 60 * 30
                ),
                MemeEntity(
                    id = "m2",
                    creatorId = "u2",
                    creatorUsername = "meme_queen",
                    creatorDisplayName = "Zoe Meme Queen",
                    creatorAvatarColorHex = "#EC4899",
                    imageUri = "drawable/img_template_doge",
                    topText = "ME PROMISES TO SLEEP AT 10",
                    bottomText = "STILL MAKING MEMES AT 3 AM",
                    caption = "Who else is wide awake on Kandala Hub tonight? 😂💤",
                    likesCount = 980,
                    downloadsCount = 215,
                    isLiked = false,
                    createdAt = System.currentTimeMillis() - 1000 * 60 * 90
                ),
                MemeEntity(
                    id = "m3",
                    creatorId = "u1",
                    creatorUsername = "kandala_king",
                    creatorDisplayName = "Kandala King",
                    creatorAvatarColorHex = "#F59E0B",
                    imageUri = "drawable/img_kandala_banner",
                    topText = "WELCOME TO KANDALA HUB",
                    bottomText = "THE MEME CAPITAL OF THE WORLD",
                    caption = "Tag your friends and start creating your own memes now! 👑✨",
                    likesCount = 3850,
                    downloadsCount = 910,
                    isLiked = true,
                    createdAt = System.currentTimeMillis() - 1000 * 60 * 180
                ),
                MemeEntity(
                    id = "m4",
                    creatorId = "u3",
                    creatorUsername = "dank_master",
                    creatorDisplayName = "Dank Master X",
                    creatorAvatarColorHex = "#06B6D4",
                    imageUri = "drawable/img_template_cat",
                    topText = "MY LAST TWO BRAIN CELLS",
                    bottomText = "DURING A MATH EXAM",
                    caption = "Pure blank stare energy right here 💀",
                    likesCount = 1730,
                    downloadsCount = 440,
                    isLiked = false,
                    createdAt = System.currentTimeMillis() - 1000 * 60 * 320
                )
            )
            dao.insertMemes(seedMemes)

            // Seed Chats
            val groupChat = ChatEntity(
                id = "chat_group_1",
                title = "Kandala Meme Elite 🚀",
                isGroup = true,
                participantIds = "current_user,u1,u2,u3",
                lastMessage = "Who has the freshest template for today?",
                lastMessageTime = System.currentTimeMillis() - 1000 * 60 * 12,
                unreadCount = 1,
                avatarColorHex = "#EC4899"
            )
            val directChat1 = ChatEntity(
                id = "chat_dm_u1",
                title = "Kandala King",
                isGroup = false,
                participantIds = "current_user,u1",
                lastMessage = "Your latest meme was top notch brother! 🔥",
                lastMessageTime = System.currentTimeMillis() - 1000 * 60 * 45,
                unreadCount = 0,
                avatarColorHex = "#F59E0B"
            )
            dao.insertChat(groupChat)
            dao.insertChat(directChat1)

            // Seed messages
            val groupMsgs = listOf(
                MessageEntity(
                    id = "gm_1",
                    chatId = groupChat.id,
                    senderId = "u1",
                    senderName = "Kandala King",
                    senderUsername = "kandala_king",
                    text = "Welcome everyone to the Kandala Meme Elite chatroom! 🔥",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 20,
                    isFromMe = false
                ),
                MessageEntity(
                    id = "gm_2",
                    chatId = groupChat.id,
                    senderId = "u2",
                    senderName = "Zoe Meme Queen",
                    senderUsername = "meme_queen",
                    text = "Ready to drop the funniest memes of the week! ✨",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 16,
                    isFromMe = false
                ),
                MessageEntity(
                    id = "gm_3",
                    chatId = groupChat.id,
                    senderId = "u3",
                    senderName = "Dank Master X",
                    senderUsername = "dank_master",
                    text = "Who has the freshest template for today?",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 12,
                    isFromMe = false
                )
            )
            dao.insertMessages(groupMsgs)

            val dmMsgs = listOf(
                MessageEntity(
                    id = "dm_1",
                    chatId = directChat1.id,
                    senderId = "u1",
                    senderName = "Kandala King",
                    senderUsername = "kandala_king",
                    text = "Yo! Welcome to Kandala Hub!",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 50,
                    isFromMe = false
                ),
                MessageEntity(
                    id = "dm_2",
                    chatId = directChat1.id,
                    senderId = "current_user",
                    senderName = "Kandala Artist",
                    senderUsername = "kandala_creator",
                    text = "Thanks man, excited to start posting memes!",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 48,
                    isFromMe = true
                ),
                MessageEntity(
                    id = "dm_3",
                    chatId = directChat1.id,
                    senderId = "u1",
                    senderName = "Kandala King",
                    senderUsername = "kandala_king",
                    text = "Your latest meme was top notch brother! 🔥",
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 45,
                    isFromMe = false
                )
            )
            dao.insertMessages(dmMsgs)
        }
    }
}
