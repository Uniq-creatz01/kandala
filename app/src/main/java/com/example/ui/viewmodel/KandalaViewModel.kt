package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.KandalaDatabase
import com.example.data.KandalaRepository
import com.example.model.ChatEntity
import com.example.model.MemeEntity
import com.example.model.MessageEntity
import com.example.model.UserEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FeedTab {
    FOR_YOU,
    FOLLOWING
}

class KandalaViewModel(application: Application) : AndroidViewModel(application) {

    private val database = KandalaDatabase.getDatabase(application)
    private val repository = KandalaRepository(database.dao(), application.applicationContext)

    private val _feedTab = MutableStateFlow(FeedTab.FOR_YOU)
    val feedTab: StateFlow<FeedTab> = _feedTab.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val feedMemes: StateFlow<List<MemeEntity>> = _feedTab.flatMapLatest { tab ->
        when (tab) {
            FeedTab.FOR_YOU -> repository.allMemes
            FeedTab.FOLLOWING -> repository.followingMemes
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMemes: StateFlow<List<MemeEntity>> = repository.allMemes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentUser: StateFlow<UserEntity?> = repository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allChats: StateFlow<List<ChatEntity>> = repository.allChats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<UserEntity>> = combine(allUsers, _searchQuery) { users, query ->
        val q = query.trim().removePrefix("@").lowercase()
        if (q.isEmpty()) {
            users.filter { !it.isCurrentUser }
        } else {
            users.filter {
                !it.isCurrentUser && (
                    it.username.lowercase().contains(q) ||
                    it.displayName.lowercase().contains(q) ||
                    it.bio.lowercase().contains(q)
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Chat
    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

    val activeChat: StateFlow<ChatEntity?> = combine(allChats, _activeChatId) { chats, id ->
        chats.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeChatMessages: StateFlow<List<MessageEntity>> = _activeChatId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getChatMessages(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Overlays & Dialogs
    private val _selectedCreator = MutableStateFlow<UserEntity?>(null)
    val selectedCreator: StateFlow<UserEntity?> = _selectedCreator.asStateFlow()

    private val _selectedMeme = MutableStateFlow<MemeEntity?>(null)
    val selectedMeme: StateFlow<MemeEntity?> = _selectedMeme.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun setFeedTab(tab: FeedTab) {
        _feedTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFollow(user: UserEntity) {
        viewModelScope.launch {
            repository.toggleFollow(user)
            val action = if (user.isFollowing) "Unfollowed @${user.username}" else "Following @${user.username} ✨"
            _snackbarMessage.value = action
        }
    }

    fun toggleLike(meme: MemeEntity) {
        viewModelScope.launch {
            repository.toggleLike(meme)
        }
    }

    fun downloadMeme(meme: MemeEntity) {
        viewModelScope.launch {
            _snackbarMessage.value = "Saving meme to your device storage..."
            val result = repository.downloadMeme(meme)
            if (result.isSuccess) {
                _snackbarMessage.value = "✅ Meme saved directly to Pictures/KandalaHub!"
            } else {
                _snackbarMessage.value = "❌ Could not save meme: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun postCreatedMeme(
        bitmap: Bitmap,
        topText: String,
        bottomText: String,
        caption: String,
        saveToDeviceImmediately: Boolean = true,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val meme = repository.postCreatedMeme(bitmap, topText, bottomText, caption)
            if (saveToDeviceImmediately) {
                repository.downloadMeme(meme)
            }
            _snackbarMessage.value = "🎉 Meme posted & saved to device gallery!"
            onComplete()
        }
    }

    fun openCreatorProfile(user: UserEntity) {
        _selectedCreator.value = user
    }

    fun closeCreatorProfile() {
        _selectedCreator.value = null
    }

    fun openMemeViewer(meme: MemeEntity) {
        _selectedMeme.value = meme
    }

    fun closeMemeViewer() {
        _selectedMeme.value = null
    }

    fun openChat(chatId: String) {
        _activeChatId.value = chatId
    }

    fun closeChat() {
        _activeChatId.value = null
    }

    fun startDirectChat(user: UserEntity, onNavigateToChat: () -> Unit) {
        viewModelScope.launch {
            val chatId = repository.createDirectChat(user)
            _activeChatId.value = chatId
            closeCreatorProfile()
            onNavigateToChat()
        }
    }

    fun createGroupChat(title: String, members: List<UserEntity>, onNavigateToChat: () -> Unit) {
        viewModelScope.launch {
            val chatId = repository.createGroupChat(title, members)
            _activeChatId.value = chatId
            onNavigateToChat()
        }
    }

    fun sendMessage(chatId: String, text: String, mediaUri: String? = null) {
        if (text.isBlank() && mediaUri == null) return
        viewModelScope.launch {
            repository.sendMessage(chatId, text.trim(), mediaUri)
        }
    }

    fun updateProfile(name: String, username: String, bio: String) {
        viewModelScope.launch {
            repository.updateProfile(name, username, bio)
            _snackbarMessage.value = "Profile updated successfully!"
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}
