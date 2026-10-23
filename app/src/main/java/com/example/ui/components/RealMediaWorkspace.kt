package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.model.ExplainStage
import com.example.model.InteractiveHotspot
import com.example.ui.theme.DeepSlateSurface
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.GlassCardSurface
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SkyCyanGlow
import com.example.ui.theme.TextMutedSlate
import com.example.ui.theme.TextPrimaryLight
import com.example.ui.theme.TextSecondarySlate
import okhttp3.OkHttpClient
import kotlin.math.roundToInt

/**
 * Large Clean Media Viewer Card + Clean Thumbnails Strip (Zero Floating PIP or Cluttered Badges)
 */
@Composable
fun CleanLargeMediaViewerCard(
    currentStage: ExplainStage,
    allStages: List<ExplainStage>,
    currentStageIndex: Int,
    selectedHotspot: InteractiveHotspot?,
    httpClient: OkHttpClient,
    onSelectStage: (Int) -> Unit,
    onSelectHotspot: (InteractiveHotspot?) -> Unit,
    onOpenLightboxZoom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customImageLoader = remember(httpClient) {
        ImageLoader.Builder(context)
            .okHttpClient(httpClient)
            .crossfade(true)
            .build()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "hotspotPulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseRadius"
    )

    Surface(
        color = GlassCardSurface,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorderCyan, RoundedCornerShape(20.dp))
            .testTag("clean_media_viewer_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Main High-Resolution Stage Image Box (aspect-video / h-64 feel)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(245.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF13203B), ObsidianBackground)
                        )
                    )
                    .border(1.dp, GlassBorderSubtle, RoundedCornerShape(14.dp))
            ) {
                val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentStage.mediaUrl)
                        .crossfade(true)
                        .build(),
                    imageLoader = customImageLoader,
                    contentDescription = currentStage.mediaCaptionAr,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onOpenLightboxZoom() }
                        .padding(8.dp),
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = SkyCyanGlow,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentStage.mediaCaptionAr,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextSecondarySlate
                            )
                        }
                    }
                )

                // Interactive Numbered Hotspot Pins on the Image
                currentStage.hotspots.forEachIndexed { idx, hotspot ->
                    val isSelected = selectedHotspot?.id == hotspot.id
                    val pinX = (hotspot.xPercent * boxW).roundToInt()
                    val pinY = (hotspot.yPercent * boxH).roundToInt()

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(pinX - 20, pinY - 20) }
                            .size(40.dp)
                            .clickable { onSelectHotspot(hotspot) }
                            .testTag("hotspot_pin_${idx + 1}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .scale(if (isSelected) 1.3f else pulseRadius)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) EmeraldGlow.copy(alpha = 0.4f)
                                    else SkyCyanGlow.copy(alpha = 0.28f)
                                )
                        )
                        Surface(
                            color = if (isSelected) EmeraldGlow else SkyCyanGlow,
                            shape = CircleShape,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .size(22.dp)
                                .border(1.5.dp, Color.White, CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ObsidianBackground,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Full-Screen Lightbox Zoom Button (Top-Left in RTL)
                Surface(
                    color = ObsidianBackground.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .border(1.dp, SkyCyanGlow.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .clickable { onOpenLightboxZoom() }
                        .testTag("lightbox_zoom_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "تكبير الصورة بملء الشاشة",
                            tint = SkyCyanGlow,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "تكبير الصورة",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimaryLight,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Subtle Image Caption Bar at Bottom of Image
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, ObsidianBackground.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = currentStage.mediaCaptionAr,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondarySlate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Optional Clean Selected Hotspot Detail Box (only shown when user clicks a pin)
            AnimatedVisibility(visible = selectedHotspot != null) {
                selectedHotspot?.let { hs ->
                    Surface(
                        color = ObsidianBackground,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, EmeraldGlow.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .testTag("hotspot_detail_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "📍 ${hs.labelAr}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EmeraldGlow,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = hs.functionAr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimaryLight
                                )
                            }
                            IconButton(
                                onClick = { onSelectHotspot(null) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "إغلاق",
                                    tint = TextMutedSlate,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Clean Thumbnails Carousel directly below the image
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stages_thumbnails_carousel"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(allStages) { idx, stage ->
                    val isSelected = idx == currentStageIndex
                    Surface(
                        color = ObsidianBackground,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .size(width = 86.dp, height = 56.dp)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) SkyCyanGlow else GlassBorderSubtle,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectStage(idx) }
                            .testTag("stage_thumbnail_$idx")
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(stage.mediaUrl)
                                    .crossfade(true)
                                    .build(),
                                imageLoader = customImageLoader,
                                contentDescription = stage.titleAr,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (isSelected) Color.Transparent
                                        else ObsidianBackground.copy(alpha = 0.45f)
                                    )
                            )
                            Surface(
                                color = if (isSelected) SkyCyanGlow else DeepSlateSurface.copy(alpha = 0.88f),
                                shape = RoundedCornerShape(bottomEnd = 8.dp),
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = "م ${stage.stageNumber}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) ObsidianBackground else TextPrimaryLight,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-Screen Lightbox Image Zoom Dialog
 */
@Composable
fun FullScreenLightboxZoomDialog(
    stage: ExplainStage,
    httpClient: OkHttpClient,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val customImageLoader = remember(httpClient) {
        ImageLoader.Builder(context)
            .okHttpClient(httpClient)
            .crossfade(true)
            .build()
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBackground.copy(alpha = 0.97f))
                .testTag("lightbox_zoom_dialog")
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(stage.mediaUrl)
                    .crossfade(true)
                    .build(),
                imageLoader = customImageLoader,
                contentDescription = stage.mediaCaptionAr,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            scale = (scale * zoomChange).coerceIn(0.8f, 4.5f)
                            pan += panChange
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                    }
                    .padding(16.dp)
            )

            // Top Lightbox Controls
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = DeepSlateSurface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, GlassBorderCyan, RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = "المرحلة ${stage.stageNumber}: ${stage.titleAr}",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimaryLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            scale = 1f
                            pan = Offset.Zero
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(DeepSlateSurface)
                            .border(1.dp, GlassBorderCyan, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "إعادة ضبط التكبير",
                            tint = SkyCyanGlow
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(DeepSlateSurface)
                            .border(1.dp, GlassBorderCyan, CircleShape)
                            .testTag("close_lightbox_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق التكبير",
                            tint = TextPrimaryLight
                        )
                    }
                }
            }
        }
    }
}
