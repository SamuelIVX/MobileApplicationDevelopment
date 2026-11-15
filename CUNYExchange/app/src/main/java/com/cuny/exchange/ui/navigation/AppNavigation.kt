package com.cuny.exchange.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.cuny.exchange.ui.screens.auth.AuthState
import com.cuny.exchange.ui.screens.auth.AuthViewModel
import com.cuny.exchange.ui.screens.auth.CheckEmailScreen
import com.cuny.exchange.ui.screens.auth.OnboardingScreen
import com.cuny.exchange.ui.screens.auth.SignInScreen

sealed class Screen(val route: String) {
    data object SignIn : Screen("auth/signin")
    data object CheckEmail : Screen("auth/check_email/{email}") {
        fun createRoute(email: String): String = "auth/check_email/${Uri.encode(email)}"
    }
    data object Onboarding : Screen("auth/onboarding/{uid}") {
        fun createRoute(uid: String): String = "auth/onboarding/$uid"
    }
    data object Home : Screen("home")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    LaunchedEffect(authState) {
        val currentRoute = navController.currentDestination?.route
        when (val state = authState) {
            is AuthState.Authenticated -> {
                if (currentRoute != Screen.Home.route) {
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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Welcome to CUNYExchange Home Feed",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}
