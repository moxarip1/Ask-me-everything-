package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.ApiKeyItem
import com.example.model.ApiKeyStatus
import com.example.model.QUICK_CATEGORIES
import com.example.model.QuickCategorySuggestion
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
fun CompactTopHeader(
    searchQuery: String,
    selectedCategory: QuickCategorySuggestion,
    activeKeysCount: Int,
    hasResults: Boolean,
    is3DFullScreenMode: Boolean,
    isAiEnrichingInBackground: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: (String?) -> Unit,
    onSelectCategory: (QuickCategorySuggestion) -> Unit,
    onToggle3DFullScreen: () -> Unit,
    onVoiceSearchClick: () -> Unit,
    onOpenApiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var isSearchFocused by remember { mutableStateOf(false) }

    Surface(
        color = GlassCardSurface,
        shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = GlassBorderCyan,
                shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: App Logo on the Right + [🔑 المفاتيح] & [🧊 عرض المجسم 3D / 🔙 العودة للشرح والصور] on the Left
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Logo & Title (Right side in RTL)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(SkyCyanGlow, EmeraldGlow)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = ObsidianBackground,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "OmniExplain Pro",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimaryLight,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isAiEnrichingInBackground) {
                            Text(
                                text = "✨ جاري إثراء الشرح بالخلفية...",
                                style = MaterialTheme.typography.labelSmall,
                                color = SkyCyanGlow,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "الموسوعة التفاعلية الذكية",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMutedSlate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Left Action Buttons: [🔑 المفاتيح] + [🧊 عرض المجسم 3D / 🔙 العودة للشرح والصور]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // [🔑 المفاتيح] Button
                    Surface(
                        color = DeepSlateSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .border(1.dp, EmeraldGlow.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .clickable { onOpenApiSettings() }
                            .testTag("api_settings_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldGlow)
                            )
                            Text(
                                text = "🔑 المفاتيح ($activeKeysCount)",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimaryLight,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // [🧊 عرض المجسم 3D] / [🔙 العودة للشرح والصور] Button (shown when results exist or in 3D mode)
                    if (hasResults || is3DFullScreenMode) {
                        val btnBg = if (is3DFullScreenMode) AmberHighlight.copy(alpha = 0.22f) else SkyCyanGlow.copy(alpha = 0.2f)
                        val btnBorder = if (is3DFullScreenMode) AmberHighlight else SkyCyanGlow
                        val btnText = if (is3DFullScreenMode) "🔙 العودة للشرح والصور" else "🧊 عرض المجسم 3D"
                        val btnTextColor = if (is3DFullScreenMode) AmberHighlight else SkyCyanGlow

                        Surface(
                            color = btnBg,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .border(1.2.dp, btnBorder, RoundedCornerShape(12.dp))
                                .clickable { onToggle3DFullScreen() }
                                .testTag("toggle_3d_fullscreen_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = btnText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = btnTextColor,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // Row 2: Search Input + [اشرح لي ✨] in a single clean line (hidden when in Full-Screen 3D mode)
            AnimatedVisibility(visible = !is3DFullScreenMode) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = {
                                Text(
                                    text = "اسأل كيف يعمل أي شيء أو كيف تصلح أي جهاز...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMutedSlate,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = SkyCyanGlow,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { onSearchQueryChange("") },
                                            modifier = Modifier
                                                .size(30.dp)
                                                .testTag("clear_search_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "مسح البحث",
                                                tint = TextMutedSlate,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = onVoiceSearchClick,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .testTag("voice_search_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "بحث صوتي بالعربية",
                                            tint = EmeraldGlow,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    isSearchFocused = false
                                    focusManager.clearFocus()
                                    onExecuteSearch(null)
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SkyCyanGlow,
                                unfocusedBorderColor = GlassBorderCyan,
                                focusedContainerColor = DeepSlateSurface,
                                unfocusedContainerColor = DeepSlateSurface,
                                focusedTextColor = TextPrimaryLight,
                                unfocusedTextColor = TextPrimaryLight
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    isSearchFocused = focusState.isFocused
                                }
                                .testTag("search_input_field")
                        )

                        Button(
                            onClick = {
                                isSearchFocused = false
                                focusManager.clearFocus()
                                onExecuteSearch(null)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SkyCyanDeep,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("explain_now_button")
                        ) {
                            Text(
                                text = "اشرح لي ✨",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Row 3: Single-Line Horizontal Categories & Suggestions Strip
                    // Automatically hides when search results are displayed unless search input is focused!
                    AnimatedVisibility(visible = !hasResults || isSearchFocused) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            QUICK_CATEGORIES.forEach { cat ->
                                val isSelected = selectedCategory.id == cat.id
                                Surface(
                                    color = if (isSelected) SkyCyanGlow.copy(alpha = 0.22f) else DeepSlateSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) SkyCyanGlow else GlassBorderSubtle,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onSelectCategory(cat) }
                                        .testTag("category_chip_${cat.id}")
                                ) {
                                    Text(
                                        text = cat.titleAr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) SkyCyanGlow else TextSecondarySlate,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Inline quick suggestions in the exact same single horizontal row
                            selectedCategory.suggestedQueries.forEachIndexed { idx, suggestion ->
                                Surface(
                                    color = ObsidianBackground,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .border(1.dp, EmeraldGlow.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            isSearchFocused = false
                                            focusManager.clearFocus()
                                            onExecuteSearch(suggestion)
                                        }
                                        .testTag("quick_suggestion_$idx")
                                ) {
                                    Text(
                                        text = "⚡ $suggestion",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimaryLight,
                                        maxLines = 1,
                                        softWrap = false,
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
}

@Composable
fun MultiApiKeyManagerDialog(
    apiKeyItems: List<ApiKeyItem>,
    isTestingKeys: Boolean,
    onAddKeys: (String) -> Unit,
    onRemoveKey: (String) -> Unit,
    onTestAllKeys: () -> Unit,
    onDismiss: () -> Unit
) {
    var newKeyInput by remember { mutableStateOf("") }
    var isBulkMode by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = DeepSlateSurface,
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 20.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, SkyCyanGlow, RoundedCornerShape(22.dp))
                .testTag("api_settings_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(EmeraldGlow.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = EmeraldGlow,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "إدارة مفاتيح Gemini API المتعددة",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimaryLight,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "السباق المتوازي والتبديل التلقائي (Parallel Race • 8s Timeout)",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldGlow
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = TextSecondarySlate
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBulkMode) "لصق عدة مفاتيح دفعة واحدة (مفتاح في كل سطر):" else "إضافة مفتاح Gemini API جديد:",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondarySlate
                    )
                    Text(
                        text = if (isBulkMode) "إدخال مفتاح مفرد" else "📋 لصق عدة مفاتيح دفعة واحدة",
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyCyanGlow,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { isBulkMode = !isBulkMode }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newKeyInput,
                        onValueChange = { newKeyInput = it },
                        placeholder = {
                            Text(
                                text = if (isBulkMode) "AIzaSy...\nAIzaSy..." else "أدخل مفتاح AIzaSy...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMutedSlate
                            )
                        },
                        singleLine = !isBulkMode,
                        minLines = if (isBulkMode) 3 else 1,
                        maxLines = if (isBulkMode) 4 else 1,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SkyCyanGlow,
                            unfocusedBorderColor = GlassBorderCyan,
                            focusedContainerColor = ObsidianBackground,
                            unfocusedContainerColor = ObsidianBackground,
                            focusedTextColor = TextPrimaryLight,
                            unfocusedTextColor = TextPrimaryLight
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_api_keys_input")
                    )

                    Button(
                        onClick = {
                            if (newKeyInput.isNotBlank()) {
                                onAddKeys(newKeyInput)
                                newKeyInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGlow,
                            contentColor = ObsidianBackground
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("add_api_key_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "إضافة مفتاح",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المفاتيح المحفوظة تلقائياً (${apiKeyItems.size}):",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimaryLight,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedButton(
                        onClick = onTestAllKeys,
                        enabled = !isTestingKeys,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("test_api_keys_button")
                    ) {
                        if (isTestingKeys) {
                            CircularProgressIndicator(
                                color = SkyCyanGlow,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "جارٍ الفحص...",
                                style = MaterialTheme.typography.labelSmall,
                                color = SkyCyanGlow
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = null,
                                tint = SkyCyanGlow,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "فحص المفاتيح",
                                style = MaterialTheme.typography.labelSmall,
                                color = SkyCyanGlow,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 210.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(apiKeyItems, key = { it.key }) { item ->
                        val statusColor = when (item.status) {
                            ApiKeyStatus.ACTIVE -> EmeraldGlow
                            ApiKeyStatus.READY -> SkyCyanGlow
                            ApiKeyStatus.TESTING -> AmberHighlight
                            ApiKeyStatus.INVALID -> CoralMedical
                        }

                        Surface(
                            color = ObsidianBackground,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(statusColor)
                                    )
                                    Column {
                                        Text(
                                            text = item.maskedKey,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextPrimaryLight,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.status.labelAr,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = statusColor
                                            )
                                            item.latencyMs?.let { ms ->
                                                Text(
                                                    text = "• ${ms}ms",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextMutedSlate
                                                )
                                            }
                                        }
                                    }
                                }

                                if (item.isBuiltIn) {
                                    Surface(
                                        color = EmeraldGlow.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = EmeraldGlow,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "احتياطي تلقائي",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldGlow
                                            )
                                        }
                                    }
                                } else {
                                    IconButton(
                                        onClick = { onRemoveKey(item.key) },
                                        modifier = Modifier
                                            .size(30.dp)
                                            .testTag("delete_key_${item.key.take(6)}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "حذف المفتاح",
                                            tint = CoralMedical,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SkyCyanDeep,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_api_keys_button")
                    ) {
                        Text(
                            text = "تم (حفظ تلقائي نشط ✓)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThreeJsCodeDialog(
    topicTitle: String,
    codeSnippet: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = DeepSlateSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, SkyCyanGlow, RoundedCornerShape(20.dp))
                .testTag("threejs_code_dialog")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "كود Three.js المولد: $topicTitle",
                        style = MaterialTheme.typography.titleSmall,
                        color = SkyCyanGlow,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = TextSecondarySlate
                        )
                    }
                }

                Surface(
                    color = ObsidianBackground,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp)
                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = codeSnippet,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = EmeraldGlow,
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("ThreeJS Code", codeSnippet)
                            )
                            copied = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SkyCyanDeep,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (copied) "تم النسخ بنجاح ✓" else "نسخ كود Three.js",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
