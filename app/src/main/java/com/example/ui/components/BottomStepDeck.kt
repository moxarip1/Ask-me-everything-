package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.ExplainStage
import com.example.model.VerifiedSource
import com.example.ui.theme.AmberHighlight
import com.example.ui.theme.DeepSlateSurface
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.GlassCardSurface
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SkyCyanGlow
import com.example.ui.theme.TextPrimaryLight
import com.example.ui.theme.TextSecondarySlate

/**
 * Clean Step-by-Step Explanation Card:
 * 1. Header: Stage X of Y badge + Verified Source badge (Wikipedia/Wikimedia/iFixit)
 * 2. Bold Stage Title + Full Stage Explanation Text (untruncated)
 * 3. Structured Bullet Points for key parts/functions/tools
 * (Audio and Stage controls are now pinned in [FixedBottomAudioStepDock] at the bottom of the screen)
 */
@Composable
fun CleanStepByStepExplanationCard(
    stages: List<ExplainStage>,
    currentStageIndex: Int,
    verifiedSource: VerifiedSource,
    modifier: Modifier = Modifier
) {
    if (stages.isEmpty()) return
    val currentStage = stages.getOrElse(currentStageIndex) { stages.first() }
    val totalStages = stages.size
    val progress = (currentStageIndex + 1).toFloat() / totalStages.coerceAtLeast(1)
    val context = LocalContext.current

    Surface(
        color = GlassCardSurface,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorderCyan, RoundedCornerShape(20.dp))
            .testTag("bottom_step_deck")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Card Header: Stage X of Y Badge + Source Badge (Wikipedia / iFixit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = SkyCyanGlow.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.border(1.dp, SkyCyanGlow, RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = "المرحلة ${currentStage.stageNumber} من $totalStages",
                        style = MaterialTheme.typography.labelLarge,
                        color = SkyCyanGlow,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                val badgeColor = if (verifiedSource.isIfixit) AmberHighlight else EmeraldGlow
                Surface(
                    color = badgeColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .border(1.dp, badgeColor.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(verifiedSource.sourceUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        }
                        .testTag("verified_source_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (verifiedSource.isIfixit) "دليل iFixit الموثق" else "موسوعة ويكيبيديا / Wikimedia",
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "فتح المصدر",
                            tint = badgeColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Bold Stage Title
            Text(
                text = currentStage.titleAr,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimaryLight,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("current_stage_title")
            )

            // 2. Full Untruncated Explanation Text
            Text(
                text = currentStage.explanationAr,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondarySlate,
                modifier = Modifier.testTag("current_stage_explanation")
            )

            // Structured Bullet Points for Key Parts / Functions / Tools in this stage
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
            }.take(5)

            if (bulletItems.isNotEmpty()) {
                Surface(
                    color = DeepSlateSurface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GlassBorderSubtle, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (verifiedSource.isIfixit) "🔧 النقاط الأساسية والأدوات في هذه المرحلة:" else "📌 أهم الأجزاء والوظائف الحيوية في هذه المرحلة:",
                            style = MaterialTheme.typography.titleSmall,
                            color = EmeraldGlow,
                            fontWeight = FontWeight.Bold
                        )

                        bulletItems.forEach { point ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(SkyCyanGlow)
                                )
                                Text(
                                    text = point,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimaryLight,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = GlassBorderSubtle)

            // Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                color = SkyCyanGlow,
                trackColor = ObsidianBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
            )
        }
    }
}

/**
 * Fixed Bottom Audio & Step Dock (Mini Audio Player + Step Controls):
 * Always pinned at the bottom of the screen (`bg-slate-900/95 backdrop-blur-xl border-t border-slate-700/60 px-4 py-3 shadow-2xl`).
 * Single coordinated row containing:
 *  - Fixed-size Audio Play/Stop button `[🔊 استماع / ⏹️ إيقاف]` with animated Equalizer wave during narration.
 *  - Fixed navigation controls: `[⏮️ السابق]`, stage indicator `[ 2 / 6 ]`, and `[⏭️ التالي]`.
 */
@Composable
fun FixedBottomAudioStepDock(
    currentStageIndex: Int,
    totalStages: Int,
    isTtsSpeaking: Boolean,
    onToggleTtsSpeak: () -> Unit,
    onPrevStage: () -> Unit,
    onNextStage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeTotal = totalStages.coerceAtLeast(1)
    val displayStageNum = (currentStageIndex + 1).coerceIn(1, safeTotal)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 24.dp)
            .background(Color(0xF20F172A)) // bg-slate-900/95
            .testTag("fixed_bottom_audio_step_dock")
    ) {
        // Top border line (border-t border-slate-700/60)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x99334155))
                .align(Alignment.TopCenter)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Fixed-Size Audio Narration Button [🔊 استماع / ⏹️ إيقاف] + Animated Equalizer
            val audioBg = if (isTtsSpeaking) {
                EmeraldGlow.copy(alpha = 0.24f)
            } else {
                EmeraldGlow.copy(alpha = 0.14f)
            }
            val audioBorder = if (isTtsSpeaking) EmeraldGlow else EmeraldGlow.copy(alpha = 0.75f)

            Surface(
                color = audioBg,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .width(148.dp)
                    .height(44.dp)
                    .border(1.2.dp, audioBorder, RoundedCornerShape(14.dp))
                    .clickable { onToggleTtsSpeak() }
                    .testTag("btn_arabic_tts")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isTtsSpeaking) "⏹️ إيقاف" else "🔊 استماع",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldGlow,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )

                    if (isTtsSpeaking) {
                        Box(modifier = Modifier.padding(start = 8.dp)) {
                            EqualizerWaveAnimation(barColor = EmeraldGlow)
                        }
                    }
                }
            }

            // 2. Fixed Stage Navigation Controls: [⏮️ السابق]  [ 2 / 6 ]  [⏭️ التالي]
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // [⏮️ السابق]
                DockStepButton(
                    text = "⏮️ السابق",
                    backgroundColor = ObsidianBackground,
                    borderColor = GlassBorderSubtle,
                    textColor = TextPrimaryLight,
                    onClick = onPrevStage,
                    testTag = "btn_prev_stage"
                )

                // Stage Counter Indicator [ 2 / 6 ]
                Surface(
                    color = DeepSlateSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .border(1.dp, GlassBorderCyan, RoundedCornerShape(12.dp))
                        .testTag("dock_stage_indicator")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\u200E[ $displayStageNum / $safeTotal ]",
                            style = MaterialTheme.typography.labelLarge,
                            color = SkyCyanGlow,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // [⏭️ التالي]
                DockStepButton(
                    text = "⏭️ التالي",
                    backgroundColor = SkyCyanGlow.copy(alpha = 0.2f),
                    borderColor = SkyCyanGlow,
                    textColor = SkyCyanGlow,
                    onClick = onNextStage,
                    testTag = "btn_next_stage"
                )
            }
        }
    }
}

@Composable
private fun DockStepButton(
    text: String,
    backgroundColor: Color,
    borderColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .height(44.dp)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Animated 4-bar Equalizer wave indicator rendered inside the fixed-size audio button
 * while speech narration is active.
 */
@Composable
private fun EqualizerWaveAnimation(
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer_wave")
    val barDurations = listOf(340, 480, 390, 530)
    val barDelays = listOf(0, 110, 220, 70)

    Row(
        modifier = modifier
            .height(18.dp)
            .testTag("audio_equalizer_animation"),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        barDurations.indices.forEach { index ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = barDurations[index],
                        delayMillis = barDelays[index],
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "eq_bar_$index"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((16f * scale).dp)
                    .clip(CircleShape)
                    .background(barColor)
            )
        }
    }
}
