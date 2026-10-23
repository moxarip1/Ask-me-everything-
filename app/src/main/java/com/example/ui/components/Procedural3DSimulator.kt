package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Animation3DType
import com.example.model.Part3DSpec
import com.example.model.Shape3DType
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )
    fun normalized(): Vec3 {
        val len = sqrt(x * x + y * y + z * z)
        return if (len > 1e-5f) Vec3(x / len, y / len, z / len) else Vec3(0f, 1f, 0f)
    }
}

private data class Face3D(
    val vertices: List<Vec3>,
    val normal: Vec3,
    val part: Part3DSpec,
    val baseColor: Color,
    val isActiveStagePart: Boolean,
    val isSelectedPart: Boolean
)

private data class ProjectedPartCenter(
    val part: Part3DSpec,
    val screenX: Float,
    val screenY: Float,
    val depthZ: Float,
    val originScreenX: Float,
    val originScreenY: Float
)

/**
 * Isolated Full-Screen 3D CAD & Anatomy Simulator with Dedicated 3D Controls
 * (X-Ray, Explode View, Orbit/Zoom, and Single-Line Part Inspector)
 */
@Composable
fun Procedural3DSimulator(
    topicTitleAr: String,
    parts3D: List<Part3DSpec>,
    currentStageNumber: Int,
    isXRayEnabled: Boolean,
    isExplodeEnabled: Boolean,
    zoomPercent: Int,
    selectedPart: Part3DSpec?,
    onToggleXRay: () -> Unit,
    onToggleExplode: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onSelectPart: (Part3DSpec?) -> Unit,
    onShowThreeJsCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var yawAngle by remember { mutableFloatStateOf(0.55f) }
    var pitchAngle by remember { mutableFloatStateOf(0.32f) }

    val explodeFactor by animateFloatAsState(
        targetValue = if (isExplodeEnabled) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "explodeFactor"
    )

    val xRayFactor by animateFloatAsState(
        targetValue = if (isXRayEnabled) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "xRayFactor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "sim3dAnim")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "animPhase"
    )

    val projectedCenters = remember { mutableStateListOf<ProjectedPartCenter>() }

    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 26f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF102340),
                        Color(0xFF091224),
                        ObsidianBackground
                    )
                )
            )
            .testTag("procedural_3d_simulator")
    ) {
        // 1. Full-Screen 3D Interactive Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        yawAngle += dragAmount.x * 0.0085f
                        pitchAngle = (pitchAngle + dragAmount.y * 0.0085f)
                            .coerceIn(-1.25f, 1.25f)
                    }
                }
                .pointerInput(parts3D) {
                    detectTapGestures { tapOffset ->
                        val hit = projectedCenters
                            .sortedBy { it.depthZ }
                            .firstOrNull { center ->
                                hypot(
                                    tapOffset.x - center.screenX,
                                    tapOffset.y - center.screenY
                                ) < 68f
                            }
                        if (hit != null) {
                            onSelectPart(hit.part)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.47f
            val baseScale = min(w, h) * 0.22f * (zoomPercent / 100f)

            drawHolographicCadFloor(
                cx = cx,
                cy = cy + baseScale * 1.15f,
                radius = baseScale * 1.65f,
                pitch = pitchAngle
            )

            val allFaces = mutableListOf<Face3D>()
            val newCenters = mutableListOf<ProjectedPartCenter>()

            val cosY = cos(yawAngle)
            val sinY = sin(yawAngle)
            val cosP = cos(pitchAngle)
            val sinP = sin(pitchAngle)

            fun rotateCamera(v: Vec3): Vec3 {
                val rx = v.x * cosY + v.z * sinY
                val rz = -v.x * sinY + v.z * cosY
                val ry = v.y * cosP - rz * sinP
                val rz2 = v.y * sinP + rz * cosP
                return Vec3(rx, ry, rz2)
            }

            fun project(vCam: Vec3): Offset {
                val fovDist = 4.6f
                val perspective = fovDist / (fovDist + vCam.z + 2.8f).coerceAtLeast(0.8f)
                return Offset(
                    x = cx + vCam.x * baseScale * perspective,
                    y = cy - vCam.y * baseScale * perspective
                )
            }

            for (part in parts3D) {
                val isActiveInStage = part.activeInStages.contains(currentStageNumber)
                val isSelected = selectedPart?.id == part.id

                val pulseScale = if (isActiveInStage || part.animationType == Animation3DType.PULSE) {
                    1f + 0.08f * sin(animPhase * 2f)
                } else {
                    1f
                }

                val oscY = if (part.animationType == Animation3DType.OSCILLATE_Y) {
                    0.14f * sin(animPhase)
                } else {
                    0f
                }

                val orbitX = if (part.animationType == Animation3DType.FLOW_ORBIT) {
                    0.15f * cos(animPhase)
                } else {
                    0f
                }
                val orbitZ = if (part.animationType == Animation3DType.FLOW_ORBIT) {
                    0.15f * sin(animPhase)
                } else {
                    0f
                }

                val localRotAngle = when (part.animationType) {
                    Animation3DType.ROTATE_X,
                    Animation3DType.ROTATE_Y,
                    Animation3DType.ROTATE_Z -> animPhase
                    else -> 0f
                }

                val partCenterModel = Vec3(
                    x = part.posX + part.explodeX * explodeFactor + orbitX,
                    y = part.posY + part.explodeY * explodeFactor + oscY,
                    z = part.posZ + part.explodeZ * explodeFactor + orbitZ
                )

                val partOriginModel = Vec3(part.posX, part.posY, part.posZ)

                val camCenter = rotateCamera(partCenterModel)
                val screenCenter = project(camCenter)
                val screenOrigin = project(rotateCamera(partOriginModel))

                newCenters.add(
                    ProjectedPartCenter(
                        part = part,
                        screenX = screenCenter.x,
                        screenY = screenCenter.y,
                        depthZ = camCenter.z,
                        originScreenX = screenOrigin.x,
                        originScreenY = screenOrigin.y
                    )
                )

                val baseColor = parseHexColor(part.colorHex)
                val rawMesh = generateProceduralMesh(
                    shape = part.shapeType,
                    sx = part.scaleX * pulseScale,
                    sy = part.scaleY * pulseScale,
                    sz = part.scaleZ * pulseScale,
                    localAnimAngle = localRotAngle,
                    animType = part.animationType
                )

                for (localFace in rawMesh) {
                    val worldVerts = localFace.map { v -> rotateCamera(v + partCenterModel) }
                    if (worldVerts.size >= 3) {
                        val e1 = worldVerts[1] - worldVerts[0]
                        val e2 = worldVerts[2] - worldVerts[0]
                        val norm = e1.cross(e2).normalized()
                        allFaces.add(
                            Face3D(
                                vertices = worldVerts,
                                normal = norm,
                                part = part,
                                baseColor = baseColor,
                                isActiveStagePart = isActiveInStage,
                                isSelectedPart = isSelected
                            )
                        )
                    }
                }
            }

            projectedCenters.clear()
            projectedCenters.addAll(newCenters)

            // Bloom glow behind active/selected parts
            for (center in newCenters.sortedByDescending { it.depthZ }) {
                val isStageActive = center.part.activeInStages.contains(currentStageNumber)
                val isSelected = selectedPart?.id == center.part.id
                if (isStageActive || isSelected || (isXRayEnabled && center.part.isInternal)) {
                    val glowColor = when {
                        isSelected -> SkyCyanGlow
                        isStageActive -> EmeraldGlow
                        else -> parseHexColor(center.part.colorHex)
                    }
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = if (isSelected) 0.42f else 0.25f),
                                Color.Transparent
                            ),
                            center = Offset(center.screenX, center.screenY),
                            radius = baseScale * 0.65f
                        ),
                        radius = baseScale * 0.65f,
                        center = Offset(center.screenX, center.screenY)
                    )
                }
            }

            val sortedFaces = allFaces.sortedByDescending { face ->
                face.vertices.sumOf { it.z.toDouble() }.toFloat() / face.vertices.size
            }

            val keyLightDir = Vec3(-0.45f, 0.72f, -0.52f).normalized()
            val viewDir = Vec3(0f, 0f, -1f)

            for (face in sortedFaces) {
                val path = Path()
                face.vertices.forEachIndexed { idx, v ->
                    val pt = project(v)
                    if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }
                path.close()

                val n = if (face.normal.z > 0f) face.normal * -1f else face.normal
                val ndotl = max(0f, n.dot(keyLightDir))
                val rim = max(0f, 1f - abs(n.dot(viewDir))).pow(2.2f)
                val halfVec = (keyLightDir + viewDir).normalized()
                val spec = max(0f, n.dot(halfVec)).pow(24f)
                val hemi = 0.35f + 0.2f * (n.y * 0.5f + 0.5f)

                val isShellInXRay = face.part.isShell && xRayFactor > 0.05f
                val alpha = if (isShellInXRay) {
                    (1f - 0.84f * xRayFactor).coerceIn(0.14f, 1f)
                } else {
                    0.96f
                }

                val litColor = applyPbrShading(
                    base = face.baseColor,
                    ambient = hemi,
                    diffuse = ndotl * 0.65f,
                    specular = spec * 0.48f,
                    fresnelRim = rim * (if (isShellInXRay || face.isSelectedPart) 0.65f else 0.22f),
                    highlightTint = when {
                        face.isSelectedPart -> SkyCyanGlow
                        face.isActiveStagePart -> EmeraldGlow
                        else -> null
                    },
                    alpha = alpha
                )

                drawPath(path = path, color = litColor)

                val strokeAlpha = when {
                    face.isSelectedPart -> 0.85f
                    isShellInXRay -> 0.45f
                    face.isActiveStagePart -> 0.5f
                    else -> 0.16f
                }
                val strokeColor = when {
                    face.isSelectedPart -> SkyCyanGlow.copy(alpha = strokeAlpha)
                    isShellInXRay -> SkyCyanGlow.copy(alpha = strokeAlpha)
                    face.isActiveStagePart -> EmeraldGlow.copy(alpha = strokeAlpha)
                    else -> Color.White.copy(alpha = strokeAlpha)
                }
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(width = if (face.isSelectedPart || isShellInXRay) 1.8f else 0.9f)
                )
            }

            if (explodeFactor > 0.1f || isXRayEnabled) {
                for (center in newCenters.sortedBy { it.depthZ }) {
                    val isSelected = selectedPart?.id == center.part.id
                    if (explodeFactor > 0.15f) {
                        drawLine(
                            color = if (isSelected) SkyCyanGlow else EmeraldGlow.copy(alpha = 0.55f),
                            start = Offset(center.originScreenX, center.originScreenY),
                            end = Offset(center.screenX, center.screenY),
                            strokeWidth = 2f
                        )
                    }

                    if (explodeFactor > 0.25f || isSelected) {
                        val badgeColor = when {
                            isSelected -> SkyCyanGlow
                            center.part.isShell -> AmberHighlight
                            else -> EmeraldGlow
                        }
                        drawCircle(
                            color = badgeColor,
                            radius = if (isSelected) 9f else 6f,
                            center = Offset(center.screenX, center.screenY)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            center.part.nameAr,
                            center.screenX,
                            center.screenY - 16f,
                            labelPaint
                        )
                    }
                }
            }
        }

        // 2. Top Dedicated 3D Toolbar (ONLY visible inside Full-Screen 3D Mode)
        Surface(
            color = GlassCardSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .border(1.dp, GlassBorderCyan, RoundedCornerShape(16.dp))
                .testTag("dedicated_3d_toolbar")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // [🩻 X-Ray]
                    Surface(
                        color = if (isXRayEnabled) SkyCyanGlow.copy(alpha = 0.25f) else ObsidianBackground,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (isXRayEnabled) SkyCyanGlow else GlassBorderSubtle,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onToggleXRay() }
                            .testTag("tool_xray_button")
                    ) {
                        Text(
                            text = "🩻 X-Ray (الأشعة السينية)",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isXRayEnabled) SkyCyanGlow else TextSecondarySlate,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // [🧩 تفكيك القطع]
                    Surface(
                        color = if (isExplodeEnabled) EmeraldGlow.copy(alpha = 0.25f) else ObsidianBackground,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (isExplodeEnabled) EmeraldGlow else GlassBorderSubtle,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onToggleExplode() }
                            .testTag("tool_explode_button")
                    ) {
                        Text(
                            text = "🧩 تفكيك القطع (Explode)",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isExplodeEnabled) EmeraldGlow else TextSecondarySlate,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Zoom & Camera Reset Controls
                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianBackground)
                            .border(1.dp, GlassBorderSubtle, RoundedCornerShape(10.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onZoomIn,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("zoom_in_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "تكبير",
                                tint = SkyCyanGlow,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "$zoomPercent%",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimaryLight,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onResetZoom() }
                                .padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = onZoomOut,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("zoom_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "تصغير",
                                tint = SkyCyanGlow,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            yawAngle = 0.55f
                            pitchAngle = 0.32f
                            onResetZoom()
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ObsidianBackground)
                            .border(1.dp, GlassBorderCyan, CircleShape)
                            .testTag("reset_3d_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "إعادة ضبط الكاميرا",
                            tint = SkyCyanGlow,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onShowThreeJsCode,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ObsidianBackground)
                            .border(1.dp, GlassBorderCyan, CircleShape)
                            .testTag("show_threejs_code_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "عرض كود Three.js",
                            tint = EmeraldGlow,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 3. Bottom Clean Single-Line 3D Part Inspector (No stacked overlapping chips)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(visible = selectedPart != null) {
                selectedPart?.let { part ->
                    Surface(
                        color = DeepSlateSurface.copy(alpha = 0.94f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SkyCyanGlow.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (part.isShell) Icons.Default.Layers else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = if (part.isShell) AmberHighlight else EmeraldGlow,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = part.nameAr,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimaryLight,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    color = if (part.isShell) AmberHighlight.copy(alpha = 0.2f) else EmeraldGlow.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (part.isShell) "غلاف خارجي" else "جزء داخلي متحرك",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (part.isShell) AmberHighlight else EmeraldGlow,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = part.roleAr,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondarySlate
                            )
                        }
                    }
                }
            }

            // Single-line horizontal scrollable strip of 3D parts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                parts3D.forEach { part ->
                    val isSelected = selectedPart?.id == part.id
                    val isStageActive = part.activeInStages.contains(currentStageNumber)
                    Surface(
                        color = when {
                            isSelected -> SkyCyanGlow.copy(alpha = 0.25f)
                            isStageActive -> EmeraldGlow.copy(alpha = 0.18f)
                            else -> GlassCardSurface
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = when {
                                    isSelected -> SkyCyanGlow
                                    isStageActive -> EmeraldGlow
                                    else -> GlassBorderSubtle
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectPart(part) }
                            .testTag("part_3d_chip_${part.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(part.colorHex))
                            )
                            Text(
                                text = part.nameAr,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) SkyCyanGlow else TextPrimaryLight,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHolographicCadFloor(
    cx: Float,
    cy: Float,
    radius: Float,
    pitch: Float
) {
    val tilt = (0.32f + 0.25f * sin(pitch)).coerceIn(0.18f, 0.65f)
    for (ring in 1..3) {
        val r = radius * (ring / 3f)
        drawOval(
            color = SkyCyanGlow.copy(alpha = 0.14f / ring),
            topLeft = Offset(cx - r, cy - r * tilt),
            size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f * tilt),
            style = Stroke(width = 1.5f)
        )
    }
}

private fun parseHexColor(hex: String): Color {
    return try {
        val cleaned = hex.trim().removePrefix("#")
        val value = cleaned.toLong(16)
        if (cleaned.length == 6) {
            Color(0xFF000000 or value)
        } else {
            Color(value)
        }
    } catch (e: Exception) {
        SkyCyanGlow
    }
}

private fun applyPbrShading(
    base: Color,
    ambient: Float,
    diffuse: Float,
    specular: Float,
    fresnelRim: Float,
    highlightTint: Color?,
    alpha: Float
): Color {
    val lightFactor = (ambient + diffuse).coerceIn(0.2f, 1.35f)
    var r = base.red * lightFactor + specular + fresnelRim * 0.22f
    var g = base.green * lightFactor + specular + fresnelRim * 0.65f
    var b = base.blue * lightFactor + specular + fresnelRim * 0.95f

    if (highlightTint != null) {
        r = r * 0.78f + highlightTint.red * 0.28f
        g = g * 0.78f + highlightTint.green * 0.28f
        b = b * 0.78f + highlightTint.blue * 0.28f
    }

    return Color(
        red = r.coerceIn(0f, 1f),
        green = g.coerceIn(0f, 1f),
        blue = b.coerceIn(0f, 1f),
        alpha = alpha.coerceIn(0.1f, 1f)
    )
}

private fun generateProceduralMesh(
    shape: Shape3DType,
    sx: Float,
    sy: Float,
    sz: Float,
    localAnimAngle: Float,
    animType: Animation3DType
): List<List<Vec3>> {
    val rawFaces = when (shape) {
        Shape3DType.BOX, Shape3DType.CHAMBER -> createBoxFaces(sx, sy, sz)
        Shape3DType.SPHERE, Shape3DType.CAPSULE, Shape3DType.DOME ->
            createSphereFaces(
                rx = sx * 0.75f,
                ry = sy * (if (shape == Shape3DType.CAPSULE) 0.95f else 0.75f),
                rz = sz * 0.75f,
                isHalfDome = (shape == Shape3DType.DOME)
            )
        Shape3DType.TORUS, Shape3DType.HELIX_RING, Shape3DType.TUBE_ARC ->
            createTorusFaces(
                majorR = max(sx, sz) * 0.7f,
                minorR = min(sx, sy) * 0.25f,
                arcFraction = if (shape == Shape3DType.TUBE_ARC) 0.65f else 1f
            )
        Shape3DType.CONE ->
            createCylinderOrConeFaces(rx = sx * 0.7f, topScale = 0.08f, hy = sy * 0.8f, rz = sz * 0.7f)
        Shape3DType.GEAR ->
            createGearFaces(radius = max(sx, sz) * 0.75f, thickness = sy * 0.35f)
        Shape3DType.CYLINDER, Shape3DType.PISTON ->
            createCylinderOrConeFaces(rx = sx * 0.65f, topScale = 1f, hy = sy * 0.75f, rz = sz * 0.65f)
    }

    if (localAnimAngle == 0f) return rawFaces

    val cosA = cos(localAnimAngle)
    val sinA = sin(localAnimAngle)
    return rawFaces.map { face ->
        face.map { v ->
            when (animType) {
                Animation3DType.ROTATE_X -> Vec3(v.x, v.y * cosA - v.z * sinA, v.y * sinA + v.z * cosA)
                Animation3DType.ROTATE_Y -> Vec3(v.x * cosA + v.z * sinA, v.y, -v.x * sinA + v.z * cosA)
                Animation3DType.ROTATE_Z -> Vec3(v.x * cosA - v.y * sinA, v.x * sinA + v.y * cosA, v.z)
                else -> v
            }
        }
    }
}

private fun createBoxFaces(sx: Float, sy: Float, sz: Float): List<List<Vec3>> {
    val hx = sx * 0.6f
    val hy = sy * 0.6f
    val hz = sz * 0.6f
    val v000 = Vec3(-hx, -hy, -hz)
    val v100 = Vec3(hx, -hy, -hz)
    val v110 = Vec3(hx, hy, -hz)
    val v010 = Vec3(-hx, hy, -hz)
    val v001 = Vec3(-hx, -hy, hz)
    val v101 = Vec3(hx, -hy, hz)
    val v111 = Vec3(hx, hy, hz)
    val v011 = Vec3(-hx, hy, hz)
    return listOf(
        listOf(v001, v101, v111, v011),
        listOf(v100, v000, v010, v110),
        listOf(v011, v111, v110, v010),
        listOf(v000, v100, v101, v001),
        listOf(v101, v100, v110, v111),
        listOf(v000, v001, v011, v010)
    )
}

private fun createSphereFaces(
    rx: Float,
    ry: Float,
    rz: Float,
    isHalfDome: Boolean
): List<List<Vec3>> {
    val stacks = 7
    val slices = 12
    val faces = mutableListOf<List<Vec3>>()
    val maxPhi = if (isHalfDome) (PI * 0.55) else PI
    for (i in 0 until stacks) {
        val phi1 = maxPhi * i / stacks
        val phi2 = maxPhi * (i + 1) / stacks
        for (j in 0 until slices) {
            val theta1 = 2.0 * PI * j / slices
            val theta2 = 2.0 * PI * (j + 1) / slices
            fun pt(phi: Double, theta: Double) = Vec3(
                (rx * sin(phi) * cos(theta)).toFloat(),
                (ry * cos(phi)).toFloat(),
                (rz * sin(phi) * sin(theta)).toFloat()
            )
            faces.add(listOf(pt(phi1, theta1), pt(phi2, theta1), pt(phi2, theta2), pt(phi1, theta2)))
        }
    }
    return faces
}

private fun createCylinderOrConeFaces(
    rx: Float,
    topScale: Float,
    hy: Float,
    rz: Float
): List<List<Vec3>> {
    val slices = 12
    val faces = mutableListOf<List<Vec3>>()
    for (j in 0 until slices) {
        val t1 = 2.0 * PI * j / slices
        val t2 = 2.0 * PI * (j + 1) / slices
        val b1 = Vec3((rx * cos(t1)).toFloat(), -hy * 0.5f, (rz * sin(t1)).toFloat())
        val b2 = Vec3((rx * cos(t2)).toFloat(), -hy * 0.5f, (rz * sin(t2)).toFloat())
        val u1 = Vec3((rx * topScale * cos(t1)).toFloat(), hy * 0.5f, (rz * topScale * sin(t1)).toFloat())
        val u2 = Vec3((rx * topScale * cos(t2)).toFloat(), hy * 0.5f, (rz * topScale * sin(t2)).toFloat())
        faces.add(listOf(b1, b2, u2, u1))
        faces.add(listOf(Vec3(0f, hy * 0.5f, 0f), u1, u2))
        faces.add(listOf(Vec3(0f, -hy * 0.5f, 0f), b2, b1))
    }
    return faces
}

private fun createTorusFaces(
    majorR: Float,
    minorR: Float,
    arcFraction: Float
): List<List<Vec3>> {
    val majorSteps = 12
    val minorSteps = 6
    val faces = mutableListOf<List<Vec3>>()
    for (i in 0 until majorSteps) {
        val u1 = 2.0 * PI * arcFraction * i / majorSteps
        val u2 = 2.0 * PI * arcFraction * (i + 1) / majorSteps
        for (j in 0 until minorSteps) {
            val v1 = 2.0 * PI * j / minorSteps
            val v2 = 2.0 * PI * (j + 1) / minorSteps
            fun pt(u: Double, v: Double): Vec3 {
                val r = majorR + minorR * cos(v)
                return Vec3(
                    (r * cos(u)).toFloat(),
                    (minorR * sin(v)).toFloat(),
                    (r * sin(u)).toFloat()
                )
            }
            faces.add(listOf(pt(u1, v1), pt(u2, v1), pt(u2, v2), pt(u1, v2)))
        }
    }
    return faces
}

private fun createGearFaces(radius: Float, thickness: Float): List<List<Vec3>> {
    val teeth = 14
    val faces = mutableListOf<List<Vec3>>()
    for (i in 0 until teeth) {
        val t1 = 2.0 * PI * i / teeth
        val t2 = 2.0 * PI * (i + 1) / teeth
        val r1 = if (i % 2 == 0) radius else radius * 0.78f
        val r2 = if ((i + 1) % 2 == 0) radius else radius * 0.78f
        val b1 = Vec3((r1 * cos(t1)).toFloat(), -thickness, (r1 * sin(t1)).toFloat())
        val b2 = Vec3((r2 * cos(t2)).toFloat(), -thickness, (r2 * sin(t2)).toFloat())
        val u1 = Vec3((r1 * cos(t1)).toFloat(), thickness, (r1 * sin(t1)).toFloat())
        val u2 = Vec3((r2 * cos(t2)).toFloat(), thickness, (r2 * sin(t2)).toFloat())
        faces.add(listOf(b1, b2, u2, u1))
        faces.add(listOf(Vec3(0f, thickness, 0f), u1, u2))
    }
    return faces
}
