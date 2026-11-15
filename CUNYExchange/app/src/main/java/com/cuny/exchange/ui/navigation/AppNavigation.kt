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
import com.cuny.exchange.data.repository.ListingRepository
import com.cuny.exchange.data.repository.PreferencesRepository
import com.cuny.exchange.ui.screens.auth.AuthState
import com.cuny.exchange.ui.screens.auth.AuthViewModel
import com.cuny.exchange.ui.screens.auth.CheckEmailScreen
import com.cuny.exchange.ui.screens.auth.OnboardingScreen
import com.cuny.exchange.ui.screens.auth.SignInScreen
import com.cuny.exchange.ui.screens.create.CreateListingScreen
import com.cuny.exchange.ui.screens.create.CreateListingViewModel
import com.cuny.exchange.ui.screens.detail.ListingDetailScreen
import com.cuny.exchange.ui.screens.detail.ListingDetailViewModel
import com.cuny.exchange.ui.screens.detail.ListingDetailViewModelFactory
import com.cuny.exchange.ui.screens.home.HomeScreen
import com.cuny.exchange.ui.screens.home.HomeViewModel
import com.cuny.exchange.ui.screens.search.SearchScreen
import com.cuny.exchange.ui.screens.search.SearchViewModel
import com.cuny.exchange.ui.screens.search.SearchViewModelFactory

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
    modifier: Modifier = Modifier
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    LaunchedEffect(authState) {
        val currentRoute = navController.currentDestination?.route
        when (val state = authState) {
            is AuthState.Authenticated -> {
                if (currentRoute != Screen.Home.route &&
                    currentRoute != Screen.CreateListing.route &&
                    currentRoute != Screen.Search.route &&
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
                    homeViewModel.toggleSave(listingId)
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
            ListingDetailScreen(
                viewModel = detailViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onMessageSeller = { listing ->
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
