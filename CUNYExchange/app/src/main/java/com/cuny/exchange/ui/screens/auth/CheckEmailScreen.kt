package com.cuny.exchange.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuny.exchange.data.repository.isCunyEmail
import com.cuny.exchange.ui.theme.CunyBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckEmailScreen(
    email: String,
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val checkEmailState by viewModel.checkEmailUiState.collectAsStateWithLifecycle()
    val signInState by viewModel.signInUiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val displayEmail = checkEmailState.email.ifBlank { email }

    LaunchedEffect(email, signInState.showCrossDeviceEmailPrompt) {
        val trimmed = email.trim()
        if (isCunyEmail(trimmed)) {
            viewModel.restoreCheckEmail(trimmed)
        } else if (!signInState.showCrossDeviceEmailPrompt) {
            onNavigateBack()
        }
    }

    LaunchedEffect(checkEmailState.errorMessage, signInState.errorMessage) {
        val errorMsg = checkEmailState.errorMessage ?: signInState.errorMessage
        if (errorMsg != null) {
            snackbarHostState.showSnackbar(errorMsg)
            viewModel.clearErrorMessages()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { contentDescription = "Back to sign in" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Email Illustration Emblem
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CunyBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MarkEmailRead,
                    contentDescription = null,
                    tint = CunyBlue,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Check your email",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "We sent a magic sign-in link to your CUNY email.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Highlighted email badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = displayEmail,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = CunyBlue
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Tap the link in your email to sign in automatically. You can safely close this screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Resend Button with Cooldown
            val isCooldownActive = checkEmailState.cooldownSeconds > 0
            OutlinedButton(
                onClick = viewModel::resendSignInLink,
                enabled = !isCooldownActive && !checkEmailState.isResending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics { contentDescription = "Resend sign-in link button" },
                shape = RoundedCornerShape(12.dp)
            ) {
                if (checkEmailState.isResending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCooldownActive) {
                                "Resend Link in ${checkEmailState.cooldownSeconds}s"
                            } else {
                                "Resend Link"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // Cross-Device Link Confirmation Dialog
    if (signInState.showCrossDeviceEmailPrompt) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCrossDevicePrompt,
            title = {
                Text(
                    text = "Confirm Your CUNY Email",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "You opened this sign-in link on a new device. Please confirm your CUNY email to complete sign in.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = signInState.crossDeviceEmail,
                        onValueChange = viewModel::onCrossDeviceEmailChanged,
                        label = { Text("CUNY Email") },
                        placeholder = { Text("name@login.cuny.edu") },
                        singleLine = true,
                        isError = signInState.dialogError != null,
                        supportingText = {
                            if (signInState.dialogError != null) {
                                Text(
                                    text = signInState.dialogError ?: "",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::submitCrossDeviceEmail,
                    enabled = !signInState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = CunyBlue)
                ) {
                    Text("Verify & Sign In")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCrossDevicePrompt) {
                    Text("Cancel")
                }
            }
        )
    }
}
