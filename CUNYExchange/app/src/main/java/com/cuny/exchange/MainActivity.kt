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
import com.cuny.exchange.data.repository.FirebaseStorageRepository
import com.cuny.exchange.data.repository.FirestoreChatRepository
import com.cuny.exchange.data.repository.FirestoreListingRepository
import com.cuny.exchange.data.repository.SharedPreferencesPreferencesRepository
import com.cuny.exchange.data.repository.StorageRepository
import com.cuny.exchange.ui.navigation.AppNavGraph
import com.cuny.exchange.ui.screens.auth.AuthViewModel
import com.cuny.exchange.ui.screens.create.CreateListingViewModel
import com.cuny.exchange.ui.screens.create.CreateListingViewModelFactory
import com.cuny.exchange.ui.screens.home.HomeViewModel
import com.cuny.exchange.ui.screens.home.HomeViewModelFactory
import com.cuny.exchange.ui.theme.CUNYExchangeTheme

class MainActivity : ComponentActivity() {

    private lateinit var authRepository: FirebaseAuthRepository
    private lateinit var listingRepository: FirestoreListingRepository
    private lateinit var storageRepository: StorageRepository
    private lateinit var preferencesRepository: SharedPreferencesPreferencesRepository
    private lateinit var chatRepository: FirestoreChatRepository
    private var lastHandledIntentUri: String? = null

    private val authViewModel: AuthViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(authRepository) as T
            }
        }
    }

    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(listingRepository, authRepository)
    }

    private val createListingViewModel: CreateListingViewModel by viewModels {
        CreateListingViewModelFactory(listingRepository, storageRepository, authRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        authRepository = FirebaseAuthRepository(applicationContext)
        listingRepository = FirestoreListingRepository(applicationContext)
        storageRepository = FirebaseStorageRepository(applicationContext)
        preferencesRepository = SharedPreferencesPreferencesRepository(applicationContext)
        chatRepository = FirestoreChatRepository(applicationContext)

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
                        homeViewModel = homeViewModel,
                        createListingViewModel = createListingViewModel,
                        listingRepository = listingRepository,
                        authRepository = authRepository,
                        preferencesRepository = preferencesRepository,
                        chatRepository = chatRepository,
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
