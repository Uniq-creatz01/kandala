package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.MemeViewerDialog
import com.example.ui.components.UserProfileDialog
import com.example.ui.screens.ChatConversationScreen
import com.example.ui.screens.ChatListScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.MemeCreatorScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.viewmodel.KandalaViewModel

enum class NavItem {
    FEED,
    CREATE,
    SEARCH,
    CHATS,
    PROFILE
}

@Composable
fun MainScreen(
    viewModel: KandalaViewModel = viewModel()
) {
    var selectedNav by remember { mutableStateOf(NavItem.FEED) }
    val activeChatId by viewModel.activeChatId.collectAsState()
    val selectedCreator by viewModel.selectedCreator.collectAsState()
    val selectedMeme by viewModel.selectedMeme.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val chats by viewModel.allChats.collectAsState()

    val totalUnread = remember(chats) {
        chats.sumOf { it.unreadCount }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Handle back button when inside a chat or creator screen
    if (activeChatId != null) {
        BackHandler {
            viewModel.closeChat()
        }
        ChatConversationScreen(
            chatId = activeChatId!!,
            viewModel = viewModel,
            onBackClick = { viewModel.closeChat() }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                // 1. Feed
                NavigationBarItem(
                    selected = selectedNav == NavItem.FEED,
                    onClick = { selectedNav = NavItem.FEED },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Feed") },
                    label = { Text("Feed", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("nav_feed")
                )

                // 2. Meme Studio Create
                NavigationBarItem(
                    selected = selectedNav == NavItem.CREATE,
                    onClick = { selectedNav = NavItem.CREATE },
                    icon = {
                        Icon(
                            Icons.Filled.AddCircle,
                            contentDescription = "Create",
                            tint = if (selectedNav == NavItem.CREATE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    label = { Text("Create", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("nav_create")
                )

                // 3. Search
                NavigationBarItem(
                    selected = selectedNav == NavItem.SEARCH,
                    onClick = { selectedNav = NavItem.SEARCH },
                    icon = { Icon(Icons.Filled.Search, contentDescription = "Discover") },
                    label = { Text("Discover", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_search")
                )

                // 4. Chats
                NavigationBarItem(
                    selected = selectedNav == NavItem.CHATS,
                    onClick = { selectedNav = NavItem.CHATS },
                    icon = {
                        BadgedBox(badge = {
                            if (totalUnread > 0) {
                                Badge { Text(totalUnread.toString()) }
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chats")
                        }
                    },
                    label = { Text("Chats", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_chats")
                )

                // 5. Profile
                NavigationBarItem(
                    selected = selectedNav == NavItem.PROFILE,
                    onClick = { selectedNav = NavItem.PROFILE },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                    label = { Text("Profile", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_profile")
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedNav,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "nav_transition"
            ) { target ->
                when (target) {
                    NavItem.FEED -> FeedScreen(
                        viewModel = viewModel,
                        onCreateMemeClick = { selectedNav = NavItem.CREATE },
                        onSearchClick = { selectedNav = NavItem.SEARCH },
                        onNavigateToChat = { selectedNav = NavItem.CHATS }
                    )
                    NavItem.CREATE -> MemeCreatorScreen(
                        viewModel = viewModel,
                        onMemePosted = { selectedNav = NavItem.FEED },
                        onBackClick = { selectedNav = NavItem.FEED }
                    )
                    NavItem.SEARCH -> SearchScreen(
                        viewModel = viewModel,
                        onNavigateToChat = { selectedNav = NavItem.CHATS }
                    )
                    NavItem.CHATS -> ChatListScreen(
                        viewModel = viewModel,
                        onChatSelected = { chatId -> viewModel.openChat(chatId) }
                    )
                    NavItem.PROFILE -> ProfileScreen(
                        viewModel = viewModel,
                        onCreateMemeClick = { selectedNav = NavItem.CREATE }
                    )
                }
            }

            // Creator Profile Dialog
            selectedCreator?.let { creator ->
                UserProfileDialog(
                    user = creator,
                    onDismiss = { viewModel.closeCreatorProfile() },
                    onToggleFollow = { viewModel.toggleFollow(creator) },
                    onDirectChatClick = {
                        viewModel.startDirectChat(creator) {
                            selectedNav = NavItem.CHATS
                        }
                    }
                )
            }

            // Meme Detail / Fullscreen Viewer Dialog
            selectedMeme?.let { meme ->
                MemeViewerDialog(
                    meme = meme,
                    onDismiss = { viewModel.closeMemeViewer() },
                    onDownload = { viewModel.downloadMeme(meme) },
                    onLike = { viewModel.toggleLike(meme) }
                )
            }
        }
    }
}
