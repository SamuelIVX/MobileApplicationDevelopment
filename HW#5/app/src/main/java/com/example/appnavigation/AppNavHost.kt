package com.example.appnavigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = AppScreen.HOME.route) {
        composable(AppScreen.HOME.route) {
            HomeScreen(
                onNavigateToContact = { navController.navigate(AppScreen.CONTACT.route) }
            )
        }
        composable(AppScreen.CONTACT.route) {
            ContactScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}