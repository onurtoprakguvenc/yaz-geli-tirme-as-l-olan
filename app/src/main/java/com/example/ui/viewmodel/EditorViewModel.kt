package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.DocumentEntity
import com.example.data.model.AnalysisTier
import com.example.data.model.NarrativeStateVector
import com.example.engine.ShadowAnalysisCore
import com.example.engine.StaccatoExecutionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    MAIN_MENU,
    EDITOR
}

data class EditorUiState(
    val currentDocId: Long = 0L,
    val documentTitle: String = "Draft 1",
    val editorText: String = "",
    val selectedTier: AnalysisTier = AnalysisTier.K2,
    val isGenerating: Boolean = false,
    val apiKey: String = "",
    val isSettingsOpen: Boolean = false,
    val narrativeState: NarrativeStateVector = NarrativeStateVector()
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("editor_prefs", Context.MODE_PRIVATE)
    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val documentDao = db.documentDao()
    private val executionEngine = StaccatoExecutionEngine()

    // Navigation State
    private val _currentScreen = MutableStateFlow(AppScreen.MAIN_MENU)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Documents Flow
    val allDocuments: StateFlow<List<DocumentEntity>> = documentDao.getAllDocuments()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(
        EditorUiState(
            apiKey = prefs.getString("gemini_api_key", "") ?: "",
            selectedTier = getSavedTier()
        )
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    // Required Public Architectural StateFlows
    val editorText: StateFlow<String> get() = _editorText
    private val _editorText = MutableStateFlow("")

    val selectedTier: StateFlow<AnalysisTier> get() = _selectedTier
    private val _selectedTier = MutableStateFlow(_uiState.value.selectedTier)

    val isGenerating: StateFlow<Boolean> get() = _isGenerating
    private val _isGenerating = MutableStateFlow(false)

    val documentTitle: StateFlow<String> get() = _documentTitle
    private val _documentTitle = MutableStateFlow("Draft 1")

    val apiKey: StateFlow<String> get() = _apiKey
    private val _apiKey = MutableStateFlow(_uiState.value.apiKey)

    val isSettingsOpen: StateFlow<Boolean> get() = _isSettingsOpen
    private val _isSettingsOpen = MutableStateFlow(false)

    private val _eventFlow = MutableSharedFlow<String>()
    val eventFlow: SharedFlow<String> = _eventFlow.asSharedFlow()

    private var debounceJob: Job? = null
    private var streamingJob: Job? = null
    private var lastKeystrokeTime = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            allDocuments.collect { docs ->
                if (docs.isNotEmpty() && _uiState.value.currentDocId == 0L) {
                    // Preload latest document into background state
                    loadDocument(docs.first())
                }
            }
        }
    }

    private fun getSavedTier(): AnalysisTier {
        val tierNum = prefs.getInt("analysis_tier", 2)
        return AnalysisTier.entries.find { it.tierNumber == tierNum } ?: AnalysisTier.K2
    }

    fun navigateTo(screen: AppScreen) {
        if (_currentScreen.value == AppScreen.EDITOR && screen == AppScreen.MAIN_MENU) {
            saveImmediately()
        }
        _currentScreen.value = screen
    }

    fun openDocument(doc: DocumentEntity) {
        loadDocument(doc)
        _currentScreen.value = AppScreen.EDITOR
    }

    fun createNewDocument(title: String = "") {
        viewModelScope.launch {
            val count = documentDao.getCount()
            val docTitle = if (title.isNotBlank()) title else "Draft ${count + 1}"
            val newDoc = DocumentEntity(
                title = docTitle,
                content = "",
                genre = "Draft",
                tierId = _selectedTier.value.tierNumber,
                wordCount = 0,
                updatedAt = System.currentTimeMillis()
            )
            val newId = documentDao.insertDocument(newDoc)
            loadDocument(newDoc.copy(id = newId))
            _currentScreen.value = AppScreen.EDITOR
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            documentDao.deleteDocument(doc)
            if (_uiState.value.currentDocId == doc.id) {
                _uiState.value = _uiState.value.copy(currentDocId = 0L, editorText = "", documentTitle = "Draft 1")
                _editorText.value = ""
                _documentTitle.value = "Draft 1"
            }
        }
    }

    private fun loadDocument(doc: DocumentEntity) {
        val tier = AnalysisTier.entries.find { it.tierNumber == doc.tierId } ?: _selectedTier.value
        val state = ShadowAnalysisCore.analyze(doc.content, 180L)

        _editorText.value = doc.content
        _documentTitle.value = doc.title
        _selectedTier.value = tier

        _uiState.value = _uiState.value.copy(
            currentDocId = doc.id,
            documentTitle = doc.title,
            editorText = doc.content,
            selectedTier = tier,
            narrativeState = state
        )
    }

    /**
     * Editor Text Input Boundary
     */
    fun onTextChanged(newText: String) {
        val now = System.currentTimeMillis()
        val latency = if (lastKeystrokeTime > 0) (now - lastKeystrokeTime).coerceIn(40, 3000) else 180L
        lastKeystrokeTime = now

        _editorText.value = newText
        _uiState.value = _uiState.value.copy(editorText = newText)

        // Debounce shadow analysis and auto-save (1.5s smart debounce)
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(1500)
            runShadowAnalysis(newText, latency)
            saveCurrentDocument()
        }
    }

    /**
     * Analysis Tier Selector Boundary (K1, K2, K3)
     */
    fun onTierChanged(tier: AnalysisTier) {
        _selectedTier.value = tier
        _uiState.value = _uiState.value.copy(selectedTier = tier)
        prefs.edit().putInt("analysis_tier", tier.tierNumber).apply()

        viewModelScope.launch {
            runShadowAnalysis(_editorText.value, 180L)
            saveCurrentDocument()
        }
    }

    /**
     * Floating Action Anchor: Trigger Generation / Next Beat
     */
    fun onGenerateTriggered() {
        if (_isGenerating.value) {
            onCancelGeneration()
            return
        }

        val currentContent = _editorText.value
        val vector = _uiState.value.narrativeState
        val activeContext = ShadowAnalysisCore.extractActiveContext(currentContent)

        _isGenerating.value = true
        _uiState.value = _uiState.value.copy(isGenerating = true)

        streamingJob = viewModelScope.launch {
            try {
                // Formatting spacer if needed
                val leadingSpacer = if (currentContent.isNotEmpty() && !currentContent.endsWith("\n") && !currentContent.endsWith(" ")) " " else ""
                var updatedText = currentContent + leadingSpacer

                val keyToUse = _apiKey.value.trim()
                executionEngine.executeStream(
                    activeContext = activeContext,
                    nextBeat = "",
                    vector = vector,
                    customApiKey = keyToUse
                ).collect { token ->
                    updatedText += token
                    _editorText.value = updatedText
                    _uiState.value = _uiState.value.copy(editorText = updatedText)
                }

                _isGenerating.value = false
                _uiState.value = _uiState.value.copy(isGenerating = false)

                // Re-run shadow analysis and save
                runShadowAnalysis(updatedText, 120L)
                saveCurrentDocument()

            } catch (e: Exception) {
                _isGenerating.value = false
                _uiState.value = _uiState.value.copy(isGenerating = false)
                _eventFlow.emit("Generation issue: ${e.message}")
            }
        }
    }

    /**
     * Cancel ongoing generation stream
     */
    fun onCancelGeneration() {
        streamingJob?.cancel()
        _isGenerating.value = false
        _uiState.value = _uiState.value.copy(isGenerating = false)
    }

    /**
     * Document Title Changed
     */
    fun onTitleChanged(newTitle: String) {
        _documentTitle.value = newTitle
        _uiState.value = _uiState.value.copy(documentTitle = newTitle)
        saveCurrentDocument()
    }

    /**
     * Settings Bottom Sheet: API Key Changed
     */
    fun onApiKeyChanged(newKey: String) {
        _apiKey.value = newKey
        _uiState.value = _uiState.value.copy(apiKey = newKey)
        prefs.edit().putString("gemini_api_key", newKey).apply()
    }

    /**
     * Toggle Settings Bottom Sheet
     */
    fun setSettingsOpen(isOpen: Boolean) {
        _isSettingsOpen.value = isOpen
        _uiState.value = _uiState.value.copy(isSettingsOpen = isOpen)
    }

    private suspend fun runShadowAnalysis(text: String, latencyMs: Long) = withContext(Dispatchers.Default) {
        val state = ShadowAnalysisCore.analyze(text, latencyMs)
        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(narrativeState = state)
        }
    }

    fun saveImmediately() {
        debounceJob?.cancel()
        saveCurrentDocument()
    }

    private fun saveCurrentDocument() {
        val docId = _uiState.value.currentDocId
        if (docId == 0L) return

        viewModelScope.launch(Dispatchers.IO) {
            val words = _editorText.value.split(Regex("""\s+""")).filter { it.isNotBlank() }.size
            val updated = DocumentEntity(
                id = docId,
                title = _documentTitle.value.ifBlank { "Draft 1" },
                content = _editorText.value,
                updatedAt = System.currentTimeMillis(),
                tierId = _selectedTier.value.tierNumber,
                genre = "Draft",
                wordCount = words
            )
            documentDao.updateDocument(updated)
        }
    }
}
