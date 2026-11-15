package com.cuny.exchange

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.cuny.exchange.data.repository.FirebaseAuthRepository
import com.cuny.exchange.ui.navigation.AppNavGraph
import com.cuny.exchange.ui.screens.auth.AuthViewModel
import com.cuny.exchange.ui.theme.CUNYExchangeTheme

class MainActivity : ComponentActivity() {

    private lateinit var authRepository: FirebaseAuthRepository
    private var lastHandledIntentUri: String? = null

    private val authViewModel: AuthViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(authRepository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        authRepository = FirebaseAuthRepository(applicationContext)

        val initialIntent = if (savedInstanceState == null) intent else null

        setContent {
            CUNYExchangeTheme {
                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    handleIncomingDeepLink(initialIntent)
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavGraph(
                        navController = navController,
                        authViewModel = authViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lastHandledIntentUri = null
        handleIncomingDeepLink(intent)
    }

    private fun handleIncomingDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val link = data.toString()
        if (link.isBlank() || link == lastHandledIntentUri) return
        lastHandledIntentUri = link

        val pendingEmail = authRepository.getPendingEmail()
        authViewModel.onDeepLinkReceived(link, pendingEmail)
    }
}
