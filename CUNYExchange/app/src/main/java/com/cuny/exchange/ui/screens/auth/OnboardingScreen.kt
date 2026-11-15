package com.cuny.exchange.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuny.exchange.data.model.Campus
import com.cuny.exchange.ui.theme.CunyBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    uid: String,
    viewModel: AuthViewModel,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.onboardingUiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var campusDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            viewModel.onOnboardingNavigated()
            onOnboardingComplete()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { errorMsg ->
            snackbarHostState.showSnackbar(errorMsg)
            viewModel.clearErrorMessages()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Complete Your Profile",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Set up your student details to buy, sell, and swap safely across CUNY.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Display Name
            OutlinedTextField(
                value = uiState.displayName,
                onValueChange = viewModel::onDisplayNameChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Full Name or Display Name input" },
                label = { Text("Display Name") },
                placeholder = { Text("e.g. Samuel Hernandez") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CunyBlue,
                    focusedLabelColor = CunyBlue
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Major
            OutlinedTextField(
                value = uiState.major,
                onValueChange = viewModel::onMajorChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Major input field" },
                label = { Text("Major") },
                placeholder = { Text("e.g. Computer Science") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CunyBlue,
                    focusedLabelColor = CunyBlue
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Campus Dropdown
            ExposedDropdownMenuBox(
                expanded = campusDropdownExpanded,
                onExpandedChange = { campusDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = uiState.selectedCampus,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Your CUNY Campus") },
                    placeholder = { Text("Select campus") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = campusDropdownExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .semantics { contentDescription = "Campus selector dropdown" },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CunyBlue,
                        focusedLabelColor = CunyBlue
                    )
                )

                ExposedDropdownMenu(
                    expanded = campusDropdownExpanded,
                    onDismissRequest = { campusDropdownExpanded = false }
                ) {
                    Campus.DEFAULT_CAMPUSES.forEach { campus ->
                        DropdownMenuItem(
                            text = { Text(campus.name) },
                            onClick = {
                                viewModel.onCampusSelected(campus.name)
                                campusDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Submit Button
            Button(
                onClick = { viewModel.completeOnboarding(uid) },
                enabled = uiState.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics { contentDescription = "Join CUNYExchange button" },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CunyBlue,
                    contentColor = MaterialTheme.colorScheme.surface
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Join CUNYExchange",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
