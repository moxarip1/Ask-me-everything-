package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.QUICK_CATEGORIES
import com.example.ui.components.CleanLargeMediaViewerCard
import com.example.ui.components.CleanStepByStepExplanationCard
import com.example.ui.components.CompactTopHeader
import com.example.ui.components.FixedBottomAudioStepDock
import com.example.ui.components.FullScreenLightboxZoomDialog
import com.example.ui.components.MultiApiKeyManagerDialog
import com.example.ui.components.Procedural3DSimulator
import com.example.ui.components.ThreeJsCodeDialog
import com.example.ui.theme.AmberHighlight
import com.example.ui.theme.CoralMedical
import com.example.ui.theme.DeepSlateSurface
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.GlassCardSurface
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SkyCyanDeep
import com.example.ui.theme.SkyCyanGlow
import com.example.ui.theme.TextMutedSlate
import com.example.ui.theme.TextPrimaryLight
import com.example.ui.theme.TextSecondarySlate

@Composable
fun OmniExplainScreen(
    viewModel: OmniExplainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Handle system Back button when in Full-Screen 3D Mode
    BackHandler(enabled = uiState.is3DFullScreenMode) {
        viewModel.set3DFullScreenMode(false)
    }

    // Ensure all audio stops when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAllAudio()
        }
    }

    // Arabic Voice Search Recognizer Intent Launcher
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val bestSpoken = spokenList?.firstOrNull()
            if (!bestSpoken.isNullOrBlank()) {
                viewModel.onSearchQueryChange(bestSpoken)
                viewModel.executeLiveExplain(bestSpoken)
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن بسؤالك العلمي أو التقني...")
            }
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "البحث الصوتي غير متاح حالياً على هذا الجهاز، يمكنك الكتابة مباشرة.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val hasResults = uiState.engineStatus is EngineStatus.Success

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("omni_explain_root")
    ) {
        // 1. Compact Header with [🔑 المفاتيح] & [🧊 عرض المجسم 3D / 🔙 العودة للشرح والصور]
        CompactTopHeader(
            searchQuery = uiState.searchQuery,
            selectedCategory = uiState.selectedCategory,
            activeKeysCount = uiState.activeKeysCount,
            hasResults = hasResults,
            is3DFullScreenMode = uiState.is3DFullScreenMode,
            isAiEnrichingInBackground = uiState.isAiEnrichingInBackground,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onExecuteSearch = viewModel::executeLiveExplain,
            onSelectCategory = viewModel::onSelectCategory,
            onToggle3DFullScreen = viewModel::toggle3DFullScreenMode,
            onVoiceSearchClick = {
                viewModel.stopAllAudio()
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onOpenApiSettings = { viewModel.setShowApiSettingsModal(true) }
        )

        // 2. Main Body Area
        when (val status = uiState.engineStatus) {
            is EngineStatus.Idle -> {
                LiveEngineCommandCenter(
                    recentSearches = uiState.recentSearches,
                    onSelectQuery = { query ->
                        viewModel.executeLiveExplain(query)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            is EngineStatus.FetchingLiveMedia -> {
                SubSecondLiveFetchIndicator(
                    query = status.query,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            is EngineStatus.Error -> {
                LiveEngineErrorView(
                    query = status.query,
                    errorMessageAr = status.errorMessageAr,
                    onRetry = { viewModel.executeLiveExplain(status.query) },
                    onPickSuggested = { query -> viewModel.executeLiveExplain(query) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            is EngineStatus.Success -> {
                val explanation = status.explanation
                val currentStage = explanation.stages.getOrElse(uiState.currentStageIndex) {
                    explanation.stages.first()
                }

                if (uiState.is3DFullScreenMode) {
                    // Isolated Full-Screen 3D Toggle Mode (hides all normal page content)
                    Procedural3DSimulator(
                        topicTitleAr = explanation.arabicTitle,
                        parts3D = explanation.parts3D,
                        currentStageNumber = currentStage.stageNumber,
                        isXRayEnabled = uiState.isXRayEnabled,
                        isExplodeEnabled = uiState.isExplodeEnabled,
                        zoomPercent = uiState.zoomPercent,
                        selectedPart = uiState.selectedPart3D,
                        onToggleXRay = viewModel::toggleXRay,
                        onToggleExplode = viewModel::toggleExplodeView,
                        onZoomIn = { viewModel.adjustZoom(25) },
                        onZoomOut = { viewModel.adjustZoom(-25) },
                        onResetZoom = viewModel::resetZoom,
                        onSelectPart = viewModel::selectPart3D,
                        onShowThreeJsCode = { viewModel.setShowThreeJsCodeModal(true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    // Clean & Scrollable Vertical Layout + Fixed Bottom Audio & Step Dock
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                // pb-28 (112.dp) bottom padding so FixedBottomAudioStepDock never covers the end of the explanation
                                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 112.dp)
                                .testTag("clean_vertical_scroll_page"),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // أ) Large Clean Media Viewer Card + Clean Thumbnails Strip
                            CleanLargeMediaViewerCard(
                                currentStage = currentStage,
                                allStages = explanation.stages,
                                currentStageIndex = uiState.currentStageIndex,
                                selectedHotspot = uiState.selectedHotspot,
                                httpClient = viewModel.liveApiDataSource.httpClient,
                                onSelectStage = viewModel::selectStage,
                                onSelectHotspot = viewModel::selectHotspot,
                                onOpenLightboxZoom = { viewModel.setLightboxZoomOpen(true) }
                            )

                            // ب) Clean Step-by-Step Explanation Card
                            CleanStepByStepExplanationCard(
                                stages = explanation.stages,
                                currentStageIndex = uiState.currentStageIndex,
                                verifiedSource = explanation.verifiedSource
                            )
                        }

                        // ج) Fixed Bottom Audio & Step Dock (pinned at bottom-0 left-0 right-0 z-50)
                        FixedBottomAudioStepDock(
                            currentStageIndex = uiState.currentStageIndex,
                            totalStages = explanation.stages.size,
                            isTtsSpeaking = uiState.isTtsSpeaking,
                            onToggleTtsSpeak = viewModel::toggleSpeakCurrentStage,
                            onPrevStage = viewModel::previousStage,
                            onNextStage = viewModel::nextStage,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .zIndex(50f)
                        )
                    }
                }

                if (uiState.isLightboxZoomOpen) {
                    FullScreenLightboxZoomDialog(
                        stage = currentStage,
                        httpClient = viewModel.liveApiDataSource.httpClient,
                        onDismiss = { viewModel.setLightboxZoomOpen(false) }
                    )
                }

                if (uiState.showThreeJsCodeModal) {
                    ThreeJsCodeDialog(
                        topicTitle = explanation.arabicTitle,
                        codeSnippet = explanation.threeJsCodeSnippet,
                        onDismiss = { viewModel.setShowThreeJsCodeModal(false) }
                    )
                }
            }
        }
    }

    if (uiState.showApiSettingsModal) {
        MultiApiKeyManagerDialog(
            apiKeyItems = uiState.apiKeyItems,
            isTestingKeys = uiState.isTestingApiKeys,
            onAddKeys = viewModel::addSingleOrBulkApiKeys,
            onRemoveKey = viewModel::removeApiKey,
            onTestAllKeys = viewModel::testAllApiKeys,
            onDismiss = { viewModel.setShowApiSettingsModal(false) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveEngineCommandCenter(
    recentSearches: List<String>,
    onSelectQuery: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            color = GlassCardSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GlassBorderCyan, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                SkyCyanGlow.copy(alpha = 0.12f),
                                EmeraldGlow.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FeatureBadgePill(icon = Icons.Default.Public, text = "iFixit & Wikimedia", color = SkyCyanGlow)
                    FeatureBadgePill(icon = Icons.Default.ViewInAr, text = "وضع 3D مستقل", color = EmeraldGlow)
                    FeatureBadgePill(icon = Icons.Default.AutoAwesome, text = "صوت بشري طبيعي", color = AmberHighlight)
                }

                Text(
                    text = "اكتشف كيف يعمل أي شيء وكيف تصلح أي جهاز",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimaryLight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "اختر أي موضوع أدناه أو اكتب سؤالك لعرض الصور والمخططات الحقيقية والشرح الكامل فوراً، مع شريط تحكم صوتي ثابت ووضع مجسم ثلاثي الأبعاد بملء الشاشة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondarySlate,
                    textAlign = TextAlign.Center
                )
            }
        }

        QUICK_CATEGORIES.forEach { category ->
            Surface(
                color = DeepSlateSurface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GlassBorderSubtle, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = category.titleAr,
                        style = MaterialTheme.typography.titleSmall,
                        color = SkyCyanGlow,
                        fontWeight = FontWeight.Bold
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        category.suggestedQueries.forEach { query ->
                            Surface(
                                color = ObsidianBackground,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .border(1.dp, GlassBorderCyan, RoundedCornerShape(10.dp))
                                    .clickable { onSelectQuery(query) }
                            ) {
                                Text(
                                    text = query,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimaryLight,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (recentSearches.isNotEmpty()) {
            Surface(
                color = GlassCardSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GlassBorderSubtle, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = EmeraldGlow,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "عمليات بحث سريعة مقترحة",
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldGlow
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentSearches.forEach { item ->
                            Surface(
                                color = ObsidianBackground,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .border(1.dp, EmeraldGlow.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .clickable { onSelectQuery(item) }
                            ) {
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondarySlate,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureBadgePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SubSecondLiveFetchIndicator(
    query: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("live_engine_loading_view"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = GlassCardSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .border(1.dp, SkyCyanGlow, RoundedCornerShape(20.dp))
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(
                    color = SkyCyanGlow,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        text = "جلب فوري متوازٍ (< 1 ثانية): $query",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimaryLight,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Wikipedia + Wikimedia Commons + iFixit...",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldGlow
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveEngineErrorView(
    query: String,
    errorMessageAr: String,
    onRetry: () -> Unit,
    onPickSuggested: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("live_engine_error_view"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = GlassCardSurface,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, CoralMedical, RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = CoralMedical,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = "تعذر إتمام الجلب اللحظي لسؤال: $query",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimaryLight,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = errorMessageAr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondarySlate,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SkyCyanDeep,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("retry_live_explain_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(text = "إعادة المحاولة الآن")
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "أو جرب أحد المواضيع الموثقة:",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMutedSlate
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("كيف يعمل القلب البشري", "استبدال بطارية iPhone 15 Pro").forEach { sample ->
                        Surface(
                            color = ObsidianBackground,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .border(1.dp, SkyCyanGlow, RoundedCornerShape(10.dp))
                                .clickable { onPickSuggested(sample) }
                        ) {
                            Text(
                                text = sample,
                                style = MaterialTheme.typography.labelSmall,
                                color = SkyCyanGlow,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
