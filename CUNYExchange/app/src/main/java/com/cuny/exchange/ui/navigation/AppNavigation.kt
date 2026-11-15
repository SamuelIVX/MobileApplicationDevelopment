package com.cuny.exchange.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.cuny.exchange.data.repository.AuthRepository
import com.cuny.exchange.data.repository.ChatRepository
import com.cuny.exchange.data.repository.ListingRepository
import com.cuny.exchange.data.repository.PreferencesRepository
import com.cuny.exchange.ui.screens.auth.AuthState
import com.cuny.exchange.ui.screens.auth.AuthViewModel
import com.cuny.exchange.ui.screens.auth.CheckEmailScreen
import com.cuny.exchange.ui.screens.auth.OnboardingScreen
import com.cuny.exchange.ui.screens.auth.SignInScreen
import com.cuny.exchange.ui.screens.chat.ChatScreen
import com.cuny.exchange.ui.screens.chat.ChatViewModel
import com.cuny.exchange.ui.screens.chat.ChatViewModelFactory
import com.cuny.exchange.ui.screens.chat.InboxScreen
import com.cuny.exchange.ui.screens.chat.InboxViewModel
import com.cuny.exchange.ui.screens.chat.InboxViewModelFactory
import com.cuny.exchange.ui.screens.create.CreateListingScreen
import com.cuny.exchange.ui.screens.create.CreateListingViewModel
import com.cuny.exchange.ui.screens.detail.ListingDetailScreen
import com.cuny.exchange.ui.screens.detail.ListingDetailViewModel
import com.cuny.exchange.ui.screens.detail.ListingDetailViewModelFactory
import com.cuny.exchange.ui.screens.home.HomeScreen
import com.cuny.exchange.ui.screens.home.HomeViewModel
import com.cuny.exchange.ui.screens.profile.ProfileScreen
import com.cuny.exchange.ui.screens.profile.ProfileViewModel
import com.cuny.exchange.ui.screens.profile.ProfileViewModelFactory
import com.cuny.exchange.ui.screens.search.SearchScreen
import com.cuny.exchange.ui.screens.search.SearchViewModel
import com.cuny.exchange.ui.screens.search.SearchViewModelFactory
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    data object SignIn : Screen("auth/signin")
    data object CheckEmail : Screen("auth/check_email/{email}") {
        fun createRoute(email: String): String = "auth/check_email/${Uri.encode(email)}"
    }
    data object Onboarding : Screen("auth/onboarding/{uid}") {
        fun createRoute(uid: String): String = "auth/onboarding/$uid"
    }
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Inbox : Screen("inbox")
    data object Chat : Screen("chat/{threadId}") {
        fun createRoute(threadId: String): String = "chat/$threadId"
    }
    data object Profile : Screen("profile")
    data object ListingDetail : Screen("listing/{listingId}") {
        fun createRoute(listingId: String): String = "listing/$listingId"
    }
    data object CreateListing : Screen("create_listing")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    homeViewModel: HomeViewModel,
    createListingViewModel: CreateListingViewModel,
    listingRepository: ListingRepository,
    authRepository: AuthRepository,
    preferencesRepository: PreferencesRepository,
    chatRepository: ChatRepository,
    modifier: Modifier = Modifier
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(authState) {
        val currentRoute = navController.currentDestination?.route
        when (val state = authState) {
            is AuthState.Authenticated -> {
                if (currentRoute != Screen.Home.route &&
                    currentRoute != Screen.CreateListing.route &&
                    currentRoute != Screen.Search.route &&
                    currentRoute != Screen.Inbox.route &&
                    currentRoute?.startsWith("chat/") != true &&
                    currentRoute != Screen.Profile.route &&
                    currentRoute?.startsWith("listing/") != true
                ) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            is AuthState.NeedsOnboarding -> {
                if (currentRoute != Screen.Onboarding.route) {
                    navController.navigate(Screen.Onboarding.createRoute(state.uid)) {
                        popUpTo(Screen.SignIn.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            is AuthState.Unauthenticated -> {
                if (currentRoute != Screen.SignIn.route && currentRoute != Screen.CheckEmail.route) {
                    navController.navigate(Screen.SignIn.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            AuthState.Loading -> {}
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.SignIn.route,
        modifier = modifier
    ) {
        composable(Screen.SignIn.route) {
            SignInScreen(
                viewModel = authViewModel,
                onNavigateToCheckEmail = { email ->
                    navController.navigate(Screen.CheckEmail.createRoute(email))
                }
            )
        }

        composable(
            route = Screen.CheckEmail.route,
            arguments = listOf(navArgument("email") { type = NavType.StringType })
        ) { backStackEntry ->
            val email = Uri.decode(backStackEntry.arguments?.getString("email").orEmpty())
            CheckEmailScreen(
                email = email,
                viewModel = authViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Onboarding.route,
            arguments = listOf(navArgument("uid") { type = NavType.StringType })
        ) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid").orEmpty()
            OnboardingScreen(
                uid = uid,
                viewModel = authViewModel,
                onOnboardingComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                onListingClick = { listingId ->
                    navController.navigate(Screen.ListingDetail.createRoute(listingId))
                },
                onNavigateToSell = {
                    navController.navigate(Screen.CreateListing.route)
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                },
                onNavigateToInbox = {
                    navController.navigate(Screen.Inbox.route)
                }
            )
        }

        composable(Screen.Inbox.route) {
            val inboxViewModel: InboxViewModel = viewModel(
                factory = InboxViewModelFactory(chatRepository, authRepository)
            )
            InboxScreen(
                viewModel = inboxViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onThreadClick = { threadId ->
                    navController.navigate(Screen.Chat.createRoute(threadId))
                }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("threadId") { type = NavType.StringType })
        ) { backStackEntry ->
            val threadId = backStackEntry.arguments?.getString("threadId").orEmpty()
            val chatViewModel: ChatViewModel = viewModel(
                key = "chat_$threadId",
                factory = ChatViewModelFactory(threadId, chatRepository, authRepository)
            )
            ChatScreen(
                viewModel = chatViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Profile.route) {
            val profileViewModel: ProfileViewModel = viewModel(
                factory = ProfileViewModelFactory(authRepository, listingRepository)
            )
            ProfileScreen(
                viewModel = profileViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSell = {
                    navController.navigate(Screen.CreateListing.route)
                },
                onListingClick = { listingId ->
                    navController.navigate(Screen.ListingDetail.createRoute(listingId))
                },
                onSignedOut = {
                    navController.navigate(Screen.SignIn.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Search.route) {
            val searchViewModel: SearchViewModel = viewModel(
                factory = SearchViewModelFactory(listingRepository, preferencesRepository)
            )
            val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
            SearchScreen(
                viewModel = searchViewModel,
                onListingClick = { listingId ->
                    navController.navigate(Screen.ListingDetail.createRoute(listingId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                savedListingIds = homeUiState.savedListingIds,
                onToggleSave = { listingId ->
                    homeViewModel.toggleSave(listingId) { errorMessage ->
                        searchViewModel.setErrorMessage(errorMessage)
                    }
                }
            )
        }

        composable(
            route = Screen.ListingDetail.route,
            arguments = listOf(navArgument("listingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val listingId = backStackEntry.arguments?.getString("listingId").orEmpty()
            val context = LocalContext.current
            val detailViewModel: ListingDetailViewModel = viewModel(
                key = "listing_detail_$listingId",
                factory = ListingDetailViewModelFactory(
                    listingId = listingId,
                    listingRepository = listingRepository,
                    authRepository = authRepository
                )
            )
            var isMessagingSeller by remember { mutableStateOf(false) }
            ListingDetailScreen(
                viewModel = detailViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onMessageSeller = { listing ->
                    if (isMessagingSeller) return@ListingDetailScreen
                    val originEntryId = backStackEntry.id
                    fun stillOnOrigin() = navController.currentBackStackEntry?.id == originEntryId

                    isMessagingSeller = true
                    coroutineScope.launch {
                        try {
                            val currentUserId = authRepository.currentUserId
                            val user = currentUserId?.let { authRepository.getUserProfile(it).getOrNull() }
                            if (user != null) {
                                val threadResult = chatRepository.getOrCreateThread(listing, user)
                                val threadId = threadResult.getOrNull()
                                if (threadId != null) {
                                    if (stillOnOrigin()) {
                                        navController.navigate(Screen.Chat.createRoute(threadId))
                                    }
                                    return@launch
                                }
                            }
                            if (!stillOnOrigin()) return@launch
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:${listing.sellerEmail}")
                                putExtra(Intent.EXTRA_SUBJECT, "Inquiry about ${listing.title} on CUNYExchange")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Hi ${listing.sellerName},\n\nI am interested in your listing '${listing.title}' on CUNYExchange."
                                )
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(Intent.createChooser(intent, "Send Email"))
                                } catch (_: Exception) {}
                            }
                        } finally {
                            isMessagingSeller = false
                        }
                    }
                }
            )
        }

        composable(Screen.CreateListing.route) {
            CreateListingScreen(
                viewModel = createListingViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPublishSuccess = {
                    if (navController.currentDestination?.route == Screen.CreateListing.route) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    }
}
