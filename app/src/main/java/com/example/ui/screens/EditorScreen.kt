package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.SettingsBottomSheet
import com.example.ui.components.TopMinimalAppBar
import com.example.ui.components.WritingCanvas
import com.example.ui.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = viewModel(),
    onBackToMenu: () -> Unit = {}
) {
    val editorText by viewModel.editorText.collectAsStateWithLifecycle()
    val selectedTier by viewModel.selectedTier.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val documentTitle by viewModel.documentTitle.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopMinimalAppBar(
                title = documentTitle,
                onTitleChange = { viewModel.onTitleChanged(it) },
                selectedTier = selectedTier,
                onTierChange = { viewModel.onTierChanged(it) },
                onSettingsClick = { viewModel.setSettingsOpen(true) },
                onBackClick = onBackToMenu
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF121212))
        ) {
            // Full-screen dynamic editor canvas (occupies 90%+ viewport)
            WritingCanvas(
                text = editorText,
                onTextChange = { viewModel.onTextChanged(it) },
                modifier = Modifier.fillMaxSize()
            )

            // Floating Action Anchor: Anchored at bottom-right above keyboard
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(20.dp)
            ) {
                FloatingActionButton(
                    onClick = { viewModel.onGenerateTriggered() },
                    shape = CircleShape,
                    containerColor = if (isGenerating) Color(0xFF333333) else MaterialTheme.colorScheme.primary,
                    contentColor = if (isGenerating) Color.White else MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp, 8.dp),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("generate_fab")
                ) {
                    if (isGenerating) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.5.dp
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Generation",
                                modifier = Modifier.size(14.dp),
                                tint = Color.White
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Trigger Generation",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Settings Modal Bottom Sheet
            if (isSettingsOpen) {
                SettingsBottomSheet(
                    apiKey = apiKey,
                    onApiKeyChange = { viewModel.onApiKeyChanged(it) },
                    selectedTier = selectedTier,
                    onTierChange = { viewModel.onTierChanged(it) },
                    onDismiss = { viewModel.setSettingsOpen(false) }
                )
            }
        }
    }
}
