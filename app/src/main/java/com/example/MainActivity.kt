package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.MainMenuScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.EditorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                val editorViewModel: EditorViewModel = viewModel()
                val currentScreen by editorViewModel.currentScreen.collectAsStateWithLifecycle()

                when (currentScreen) {
                    AppScreen.MAIN_MENU -> {
                        MainMenuScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = editorViewModel,
                            onOpenEditor = { editorViewModel.navigateTo(AppScreen.EDITOR) }
                        )
                    }
                    AppScreen.EDITOR -> {
                        BackHandler {
                            editorViewModel.navigateTo(AppScreen.MAIN_MENU)
                        }
                        EditorScreen(
                            modifier = Modifier.fillMaxSize(),
                            viewModel = editorViewModel,
                            onBackToMenu = { editorViewModel.navigateTo(AppScreen.MAIN_MENU) }
                        )
                    }
                }
            }
        }
    }
}
