package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ApiKeyPreferencesManager
import com.example.data.GeminiOrchestrator
import com.example.data.LiveApiDataSource
import com.example.model.ApiKeyItem
import com.example.model.ApiKeyStatus
import com.example.model.InteractiveHotspot
import com.example.model.OmniTopicExplanation
import com.example.model.Part3DSpec
import com.example.model.QUICK_CATEGORIES
import com.example.model.QuickCategorySuggestion
import com.example.util.NaturalArabicVoiceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "OmniExplainViewModel"

sealed interface EngineStatus {
    data object Idle : EngineStatus
    data class FetchingLiveMedia(val query: String) : EngineStatus
    data class Success(val explanation: OmniTopicExplanation) : EngineStatus
    data class Error(val query: String, val errorMessageAr: String) : EngineStatus
}

data class WorkspaceUiState(
    val searchQuery: String = "",
    val selectedCategory: QuickCategorySuggestion = QUICK_CATEGORIES.first(),
    val engineStatus: EngineStatus = EngineStatus.Idle,
    val isAiEnrichingInBackground: Boolean = false,
    val is3DFullScreenMode: Boolean = false,
    val isLightboxZoomOpen: Boolean = false,
    val currentStageIndex: Int = 0,
    val isXRayEnabled: Boolean = false,
    val isExplodeEnabled: Boolean = false,
    val zoomPercent: Int = 100,
    val selectedHotspot: InteractiveHotspot? = null,
    val selectedPart3D: Part3DSpec? = null,
    val isAutoPlaying: Boolean = false,
    val isTtsSpeaking: Boolean = false,
    val showApiSettingsModal: Boolean = false,
    val showThreeJsCodeModal: Boolean = false,
    val apiKeyItems: List<ApiKeyItem> = emptyList(),
    val isTestingApiKeys: Boolean = false,
    val recentSearches: List<String> = listOf(
        "كيف يعمل القلب البشري والدورة الدموية",
        "استبدال بطارية iPhone 15 Pro",
        "كيف يعمل المحرك النفاث التوربيني Turbofan",
        "كيف يعمل الثقب الأسود وأفق الحدث",
        "كيف تعمل العين البشرية والشبكية في الرؤية",
        "تفكيك وإصلاح ذراع تحكم PlayStation 5 DualSense"
    )
) {
    val activeKeysCount: Int
        get() = apiKeyItems.count { it.status.isHealthy }.coerceAtLeast(1)
}

class OmniExplainViewModel(application: Application) : AndroidViewModel(application) {

    val liveApiDataSource = LiveApiDataSource()
    private val apiKeyManager = ApiKeyPreferencesManager(application.applicationContext)
    private val geminiOrchestrator = GeminiOrchestrator(
        httpClient = liveApiDataSource.httpClient,
        apiKeyManager = apiKeyManager
    )

    private val _uiState = MutableStateFlow(
        WorkspaceUiState(
            apiKeyItems = buildKeyItemsList(apiKeyManager.getCustomKeys())
        )
    )
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private val voiceManager = NaturalArabicVoiceManager(
        context = application.applicationContext,
        baseHttpClient = liveApiDataSource.httpClient,
        getApiKeys = { apiKeyManager.getCustomKeys() },
        onSpeakingStateChanged = { speaking ->
            _uiState.update { it.copy(isTtsSpeaking = speaking) }
        }
    )

    private var searchJob: Job? = null
    private var backgroundAiJob: Job? = null
    private var autoPlayJob: Job? = null

    private fun buildKeyItemsList(customKeys: List<String>): List<ApiKeyItem> {
        val builtIn = ApiKeyItem(
            key = "BUILT_IN_FIREBASE_AI",
            isBuiltIn = true,
            status = ApiKeyStatus.ACTIVE,
            latencyMs = 110L
        )
        val customs = customKeys.map { k ->
            ApiKeyItem(
                key = k,
                isBuiltIn = false,
                status = ApiKeyStatus.READY
            )
        }
        return listOf(builtIn) + customs
    }

    /**
     * Central audio stop lifecycle function `stopAllAudio()`:
     * Immediately halts all active System TTS and Neural Audio streams.
     */
    fun stopAllAudio() {
        voiceManager.stopAllAudio()
        _uiState.update { it.copy(isTtsSpeaking = false) }
    }

    /**
     * Toggles human-like Arabic audio narration for the currently active stage.
     */
    fun toggleSpeakCurrentStage() {
        if (_uiState.value.isTtsSpeaking) {
            stopAllAudio()
        } else {
            speakCurrentStage()
        }
    }

    /**
     * Starts narrating the currently active stage from the beginning, stopping any prior audio first.
     */
    fun speakCurrentStage() {
        val success = _uiState.value.engineStatus as? EngineStatus.Success ?: return
        val stages = success.explanation.stages
        if (stages.isEmpty()) return
        val currentStage = stages.getOrElse(_uiState.value.currentStageIndex) { stages.first() }
        val totalStages = stages.size

        val bulletItems = buildList {
            currentStage.hotspots.forEach { hs ->
                if (hs.labelAr.isNotBlank() && hs.functionAr.isNotBlank()) {
                    add("${hs.labelAr}: ${hs.functionAr}")
                }
            }
            currentStage.toolsOrFacts.forEach { fact ->
                if (fact.isNotBlank() && none { it.contains(fact) }) {
                    add(fact)
                }
            }
        }.take(3)

        val cacheKey = "${success.explanation.arabicTitle}_stage_${currentStage.stageNumber}_${currentStage.titleAr.hashCode()}"
        voiceManager.speakStageNaturally(
            cacheKey = cacheKey,
            stageNumber = currentStage.stageNumber,
            totalStages = totalStages,
            titleAr = currentStage.titleAr,
            explanationAr = currentStage.explanationAr,
            bulletPoints = bulletItems
        )
    }

    fun onSearchQueryChange(newQuery: String) {
        if (newQuery.isEmpty()) {
            // Mandatory audio stop when clearing search input (X button)
            stopAllAudio()
        }
        _uiState.update { it.copy(searchQuery = newQuery) }
    }

    fun onSelectCategory(category: QuickCategorySuggestion) {
        // Mandatory audio stop when selecting any quick category
        stopAllAudio()
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun executeLiveExplain(queryOverride: String? = null) {
        val targetQuery = (queryOverride ?: _uiState.value.searchQuery).trim()
        if (targetQuery.isBlank()) return

        // Mandatory immediate stop of all audio when starting a new search or clicking a suggestion
        stopAllAudio()
        stopAutoPlay()
        searchJob?.cancel()
        backgroundAiJob?.cancel()

        searchJob = viewModelScope.launch {
            _uiState.update { state ->
                val updatedRecents = (listOf(targetQuery) + state.recentSearches)
                    .distinct()
                    .take(8)
                state.copy(
                    searchQuery = targetQuery,
                    selectedHotspot = null,
                    selectedPart3D = null,
                    currentStageIndex = 0,
                    zoomPercent = 100,
                    is3DFullScreenMode = false,
                    isLightboxZoomOpen = false,
                    isAiEnrichingInBackground = false,
                    recentSearches = updatedRecents,
                    engineStatus = EngineStatus.FetchingLiveMedia(query = targetQuery)
                )
            }

            try {
                // Phase A (< 1s): Parallel fetch from Wikipedia AR/EN, Wikimedia Commons, and iFixit
                val liveResult = liveApiDataSource.fetchLiveMultiSourceInstant(targetQuery)
                val instantExplanation = liveApiDataSource.buildInstantExplanation(liveResult)

                // Immediately display the clean media viewer and step-by-step explanation!
                _uiState.update {
                    it.copy(
                        engineStatus = EngineStatus.Success(instantExplanation),
                        isAiEnrichingInBackground = true,
                        currentStageIndex = 0,
                        selectedHotspot = null,
                        selectedPart3D = instantExplanation.parts3D.firstOrNull { p -> p.isInternal }
                            ?: instantExplanation.parts3D.firstOrNull(),
                        zoomPercent = 100
                    )
                }

                // Phase B (Background, Non-Blocking): Enrich with Gemini AI Race (max 10s)
                backgroundAiJob = viewModelScope.launch {
                    try {
                        val enriched = geminiOrchestrator.enrichExplanationInBackground(liveResult)
                        if (enriched != null && enriched.stages.isNotEmpty()) {
                            _uiState.update { current ->
                                val safeStageIdx = current.currentStageIndex
                                    .coerceIn(0, (enriched.stages.size - 1).coerceAtLeast(0))
                                current.copy(
                                    engineStatus = EngineStatus.Success(enriched),
                                    isAiEnrichingInBackground = false,
                                    currentStageIndex = safeStageIdx,
                                    selectedPart3D = enriched.parts3D.firstOrNull { p -> p.isInternal }
                                        ?: enriched.parts3D.firstOrNull()
                                )
                            }
                        } else {
                            _uiState.update { it.copy(isAiEnrichingInBackground = false) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Background enrichment completed with fallback", e)
                        _uiState.update { it.copy(isAiEnrichingInBackground = false) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live fetch failed", e)
                _uiState.update {
                    it.copy(
                        isAiEnrichingInBackground = false,
                        engineStatus = EngineStatus.Error(
                            query = targetQuery,
                            errorMessageAr = e.localizedMessage ?: "حدث خطأ أثناء الاتصال بالمصادر الحية."
                        )
                    )
                }
            }
        }
    }

    fun toggle3DFullScreenMode() {
        // Mandatory audio stop when opening/closing 3D mode
        stopAllAudio()
        _uiState.update { it.copy(is3DFullScreenMode = !it.is3DFullScreenMode) }
    }

    fun set3DFullScreenMode(enabled: Boolean) {
        stopAllAudio()
        _uiState.update { it.copy(is3DFullScreenMode = enabled) }
    }

    fun setLightboxZoomOpen(open: Boolean) {
        _uiState.update { it.copy(isLightboxZoomOpen = open) }
    }

    fun toggleXRay() {
        _uiState.update { it.copy(isXRayEnabled = !it.isXRayEnabled) }
    }

    fun toggleExplodeView() {
        _uiState.update { it.copy(isExplodeEnabled = !it.isExplodeEnabled) }
    }

    fun adjustZoom(deltaPercent: Int) {
        _uiState.update { state ->
            val updated = (state.zoomPercent + deltaPercent).coerceIn(50, 300)
            state.copy(zoomPercent = updated)
        }
    }

    fun resetZoom() {
        stopAllAudio()
        _uiState.update {
            it.copy(
                zoomPercent = 100,
                selectedHotspot = null
            )
        }
    }

    fun selectHotspot(hotspot: InteractiveHotspot?) {
        _uiState.update { state ->
            if (hotspot == null || state.selectedHotspot?.id == hotspot.id) {
                state.copy(selectedHotspot = null)
            } else {
                state.copy(selectedHotspot = hotspot)
            }
        }
    }

    fun selectPart3D(part: Part3DSpec?) {
        _uiState.update { it.copy(selectedPart3D = part) }
    }

    fun selectStage(index: Int) {
        val success = _uiState.value.engineStatus as? EngineStatus.Success ?: return
        val wasSpeaking = _uiState.value.isTtsSpeaking

        // Immediately stop any old stage audio so old text never continues reading
        stopAllAudio()

        val coerced = index.coerceIn(0, (success.explanation.stages.size - 1).coerceAtLeast(0))
        val stageNum = coerced + 1
        val matchingPart = success.explanation.parts3D.firstOrNull {
            it.activeInStages.contains(stageNum) && it.isInternal
        } ?: _uiState.value.selectedPart3D

        _uiState.update {
            it.copy(
                currentStageIndex = coerced,
                selectedHotspot = null,
                zoomPercent = 100,
                selectedPart3D = matchingPart
            )
        }

        // If the user had narration active when switching stages, immediately read the new stage only
        if (wasSpeaking) {
            speakCurrentStage()
        }
    }

    fun nextStage() {
        val success = _uiState.value.engineStatus as? EngineStatus.Success ?: return
        val maxIdx = (success.explanation.stages.size - 1).coerceAtLeast(0)
        val next = if (_uiState.value.currentStageIndex < maxIdx) {
            _uiState.value.currentStageIndex + 1
        } else {
            0
        }
        selectStage(next)
    }

    fun previousStage() {
        val success = _uiState.value.engineStatus as? EngineStatus.Success ?: return
        val maxIdx = (success.explanation.stages.size - 1).coerceAtLeast(0)
        val prev = if (_uiState.value.currentStageIndex > 0) {
            _uiState.value.currentStageIndex - 1
        } else {
            maxIdx
        }
        selectStage(prev)
    }

    fun toggleAutoPlay() {
        if (_uiState.value.isAutoPlaying) {
            stopAutoPlay()
        } else {
            startAutoPlay()
        }
    }

    private fun startAutoPlay() {
        autoPlayJob?.cancel()
        _uiState.update { it.copy(isAutoPlaying = true) }
        autoPlayJob = viewModelScope.launch {
            while (true) {
                delay(5500L)
                val success = _uiState.value.engineStatus as? EngineStatus.Success ?: break
                val maxIdx = (success.explanation.stages.size - 1).coerceAtLeast(0)
                if (_uiState.value.currentStageIndex < maxIdx) {
                    selectStage(_uiState.value.currentStageIndex + 1)
                } else {
                    selectStage(0)
                    _uiState.update { it.copy(isAutoPlaying = false) }
                    break
                }
            }
        }
    }

    private fun stopAutoPlay() {
        autoPlayJob?.cancel()
        autoPlayJob = null
        _uiState.update { it.copy(isAutoPlaying = false) }
    }

    fun setTtsSpeakingState(speaking: Boolean) {
        if (!speaking) {
            voiceManager.stopAllAudio()
        }
        _uiState.update { it.copy(isTtsSpeaking = speaking) }
    }

    fun setShowApiSettingsModal(show: Boolean) {
        _uiState.update { it.copy(showApiSettingsModal = show) }
    }

    fun setShowThreeJsCodeModal(show: Boolean) {
        _uiState.update { it.copy(showThreeJsCodeModal = show) }
    }

    fun addSingleOrBulkApiKeys(input: String) {
        if (input.isBlank()) return
        val updatedKeys = apiKeyManager.addSingleOrBulkKeys(input)
        _uiState.update {
            it.copy(apiKeyItems = buildKeyItemsList(updatedKeys))
        }
    }

    fun removeApiKey(key: String) {
        val updatedKeys = apiKeyManager.removeKey(key)
        _uiState.update {
            it.copy(apiKeyItems = buildKeyItemsList(updatedKeys))
        }
    }

    fun testAllApiKeys() {
        val customKeys = apiKeyManager.getCustomKeys()
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isTestingApiKeys = true,
                    apiKeyItems = state.apiKeyItems.map { item ->
                        if (item.isBuiltIn) item else item.copy(status = ApiKeyStatus.TESTING)
                    }
                )
            }
            val testedItems = geminiOrchestrator.testAllKeysHealth(customKeys)
            _uiState.update {
                it.copy(
                    isTestingApiKeys = false,
                    apiKeyItems = testedItems
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
    }
}
