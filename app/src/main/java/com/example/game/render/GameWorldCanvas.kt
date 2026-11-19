package com.example.game.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.WorldId
import com.example.game.model.*
import com.example.game.render.LainSpriteRenderer.drawLainGameSprite
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 2D side-scrolling Compose Canvas renderer for all 6 worlds of LAIN: Beyond Reality.
 * Supports smooth side-view camera scaling, parallax world atmospheres, real-time light beams,
 * magnetic field rings, tilting balance beams, interactive ink bridge drawing, and animated Lain.
 */
@Composable
fun GameWorldCanvas(
    state: LevelRuntimeState,
    inkDrawMode: Boolean,
    reduceShake: Boolean,
    onViewportChanged: (Float, Float) -> Unit,
    onInkBridgeDrawn: (Vec2, Vec2) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragStartScreen by remember { mutableStateOf<Offset?>(null) }
    var dragCurrentScreen by remember { mutableStateOf<Offset?>(null) }
    var lastScale by remember { mutableFloatStateOf(1f) }
    var lastCamX by remember { mutableFloatStateOf(0f) }
    var lastCamY by remember { mutableFloatStateOf(0f) }

    val drawModifier = if (inkDrawMode && state.definition.allowInkDrawing) {
        modifier.pointerInput(inkDrawMode, state.definition.id) {
            detectDragGestures(
                onDragStart = { offset ->
                    dragStartScreen = offset
                    dragCurrentScreen = offset
                },
                onDrag = { change, _ ->
                    dragCurrentScreen = change.position
                },
                onDragEnd = {
                    val s = dragStartScreen
                    val e = dragCurrentScreen
                    if (s != null && e != null && lastScale > 0.01f) {
                        val w1 = Vec2(s.x / lastScale + lastCamX, s.y / lastScale + lastCamY)
                        val w2 = Vec2(e.x / lastScale + lastCamX, e.y / lastScale + lastCamY)
                        onInkBridgeDrawn(w1, w2)
                    }
                    dragStartScreen = null
                    dragCurrentScreen = null
                },
                onDragCancel = {
                    dragStartScreen = null
                    dragCurrentScreen = null
                }
            )
        }
    } else modifier

    Canvas(modifier = drawModifier.fillMaxSize()) {
        val viewScale = (size.height / 540f).coerceIn(0.65f, 1.85f)
        val logicalW = size.width / viewScale
        val logicalH = size.height / viewScale
        lastScale = viewScale
        lastCamX = state.cameraX
        lastCamY = state.cameraY
        onViewportChanged(logicalW, logicalH)

        val shakeAmp = if (reduceShake) 0f else state.screenShake * 7f
        val shakeX = if (shakeAmp > 0.1f) sin(state.elapsedTime * 58f) * shakeAmp else 0f
        val shakeY = if (shakeAmp > 0.1f) cos(state.elapsedTime * 49f) * shakeAmp else 0f

        // 1. World-themed parallax background
        drawWorldBackdrop(state.definition.worldId, state.cameraX, state.elapsedTime)

        // 2. World-space camera transform
        withTransform({
            translate(left = shakeX, top = shakeY)
            scale(scaleX = viewScale, scaleY = viewScale, pivot = Offset.Zero)
            translate(left = -state.cameraX, top = -state.cameraY)
        }) {
            val t = state.elapsedTime

            // Gravity Zones
            state.definition.gravityZones.forEach { gz ->
                val isInv = gz.mode == GravityZoneMode.INVERTED
                val zoneColor = if (isInv) WiredViolet else NaviCyan
                drawRoundRect(
                    color = zoneColor.copy(alpha = 0.11f),
                    topLeft = Offset(gz.x, gz.y),
                    size = Size(gz.width, gz.height),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                drawRoundRect(
                    color = zoneColor.copy(alpha = 0.42f),
                    topLeft = Offset(gz.x, gz.y),
                    size = Size(gz.width, gz.height),
                    cornerRadius = CornerRadius(8f, 8f),
                    style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                )
                // Animated floating chevrons indicating gravity direction
                val dir = if (isInv) -1f else -0.5f
                for (col in 0..2) {
                    val cx = gz.x + gz.width * (0.22f + col * 0.28f)
                    val offsetY = ((t * 55f * (if (isInv) 1.5f else 0.7f) + col * 65f) % gz.height)
                    val cy = if (dir < 0f) gz.y + gz.height - offsetY else gz.y + offsetY
                    drawLine(
                        color = zoneColor.copy(alpha = 0.55f),
                        start = Offset(cx - 10f, cy + 7f),
                        end = Offset(cx, cy - 5f),
                        strokeWidth = 2.2f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = zoneColor.copy(alpha = 0.55f),
                        start = Offset(cx, cy - 5f),
                        end = Offset(cx + 10f, cy + 7f),
                        strokeWidth = 2.2f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Power Conduit lines connecting active/inactive switches to doors & hard-light bridges
            drawPowerConduits(state)

            // Magnetic Coil field rings
            state.magnets.forEach { coil ->
                val cx = coil.x + coil.width * 0.5f
                val cy = coil.y + coil.height * 0.5f
                val isAttract = coil.mode == MagnetMode.ATTRACT
                val magColor = if (isAttract) NaviCyan else SignalCoral
                for (ring in 0..2) {
                    val phase = (t * 0.85f + ring * 0.33f) % 1f
                    val rad = if (isAttract) coil.range * (1f - phase) else coil.range * phase
                    drawCircle(
                        color = magColor.copy(alpha = (1f - phase) * 0.24f),
                        radius = rad.coerceAtLeast(18f),
                        center = Offset(cx, cy),
                        style = Stroke(width = 2f)
                    )
                }
                // Coil housing
                drawRoundRect(
                    color = Color(0xFF1F2B44),
                    topLeft = Offset(coil.x, coil.y),
                    size = Size(coil.width, coil.height),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                drawRoundRect(
                    color = magColor,
                    topLeft = Offset(coil.x, coil.y),
                    size = Size(coil.width, coil.height),
                    cornerRadius = CornerRadius(8f, 8f),
                    style = Stroke(width = 2.6f)
                )
                // Polarity symbol (+ / -)
                drawLine(
                    color = magColor,
                    start = Offset(cx - 9f, cy),
                    end = Offset(cx + 9f, cy),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                if (isAttract) {
                    drawLine(
                        color = magColor,
                        start = Offset(cx, cy - 9f),
                        end = Offset(cx, cy + 9f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Hazards (crimson digital static pit)
            state.definition.hazards.forEach { hz ->
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(SignalCoral.copy(alpha = 0.08f), SignalCoral.copy(alpha = 0.65f)),
                        startY = hz.y - 12f,
                        endY = hz.y + hz.height
                    ),
                    topLeft = Offset(hz.x, hz.y - 12f),
                    size = Size(hz.width, hz.height + 12f)
                )
                val glitchShift = sin(t * 22f) * 6f
                drawLine(
                    color = SignalCoral,
                    start = Offset(hz.x, hz.y),
                    end = Offset(hz.x + hz.width, hz.y),
                    strokeWidth = 2.6f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), phase = glitchShift)
                )
            }

            // Platforms (static, ice, sticky, moving, hard-light, tilting beams, drawn ink)
            (state.platforms + state.drawnPlatforms).forEach { plat ->
                drawPlatform(plat, state.definition.worldId, t)
            }

            // Checkpoints
            state.checkpoints.forEach { cp ->
                val cpColor = if (cp.activated) HarmonyJade else TextMuted
                drawLine(
                    color = cpColor,
                    start = Offset(cp.x, cp.y),
                    end = Offset(cp.x, cp.y - 58f),
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round
                )
                val pulse = if (cp.activated) 10f + sin(t * 5f) * 2.5f else 7f
                drawCircle(
                    color = cpColor.copy(alpha = if (cp.activated) 0.25f else 0.1f),
                    radius = pulse * 1.8f,
                    center = Offset(cp.x, cp.y - 58f)
                )
                drawCircle(
                    color = cpColor,
                    radius = 7f,
                    center = Offset(cp.x, cp.y - 58f)
                )
            }

            // Switches (Pressure plates, Terminals, Gravity switches, Magnet switches, Photo-sensors)
            state.switches.forEach { sw ->
                drawSwitchNode(sw, t)
            }

            // Light Emitters & Mirrors & Live Light Beams
            state.definition.emitters.forEach { em ->
                drawCircle(color = Color(0xFF283755), radius = 16f, center = Offset(em.x, em.y))
                drawCircle(color = DeskAmber, radius = 8f, center = Offset(em.x, em.y))
                drawCircle(
                    color = DeskAmber.copy(alpha = 0.35f),
                    radius = 20f + sin(t * 6f) * 3f,
                    center = Offset(em.x, em.y)
                )
            }
            state.lightBeams.forEach { seg ->
                drawLine(
                    color = NaviCyan.copy(alpha = 0.24f),
                    start = Offset(seg.x1, seg.y1),
                    end = Offset(seg.x2, seg.y2),
                    strokeWidth = 14f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = NaviCyan,
                    start = Offset(seg.x1, seg.y1),
                    end = Offset(seg.x2, seg.y2),
                    strokeWidth = 4.2f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(seg.x1, seg.y1),
                    end = Offset(seg.x2, seg.y2),
                    strokeWidth = 1.6f,
                    cap = StrokeCap.Round
                )
            }
            state.mirrors.forEach { m ->
                val angleRad = m.angleStep * (PI / 4.0)
                val outX = cos(angleRad).toFloat()
                val outY = sin(angleRad).toFloat()
                drawCircle(
                    color = Color(0xFF1A2640),
                    radius = m.radius,
                    center = Offset(m.x, m.y)
                )
                drawCircle(
                    color = DeskAmber.copy(alpha = 0.85f),
                    radius = m.radius,
                    center = Offset(m.x, m.y),
                    style = Stroke(width = 2.4f)
                )
                // Outgoing aim arrow inside prism
                drawLine(
                    color = NaviCyan,
                    start = Offset(m.x - outX * 10f, m.y - outY * 10f),
                    end = Offset(m.x + outX * 17f, m.y + outY * 17f),
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = Offset(m.x + outX * 16f, m.y + outY * 16f)
                )
            }

            // Judo Shrines (Tatami Torii Gate)
            state.judoShrines.forEach { shrine ->
                val sColor = if (shrine.completed) HarmonyJade else DeskAmber
                // Torii pillars
                drawLine(
                    color = sColor,
                    start = Offset(shrine.x - 24f, shrine.y),
                    end = Offset(shrine.x - 20f, shrine.y - 68f),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = sColor,
                    start = Offset(shrine.x + 24f, shrine.y),
                    end = Offset(shrine.x + 20f, shrine.y - 68f),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
                // Top lintel
                drawLine(
                    color = sColor,
                    start = Offset(shrine.x - 34f, shrine.y - 68f),
                    end = Offset(shrine.x + 34f, shrine.y - 68f),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = sColor.copy(alpha = 0.7f),
                    start = Offset(shrine.x - 24f, shrine.y - 54f),
                    end = Offset(shrine.x + 24f, shrine.y - 54f),
                    strokeWidth = 3.5f
                )
                // Center balance emblem
                drawCircle(
                    color = sColor.copy(alpha = 0.22f),
                    radius = 16f + sin(t * 4f) * 3f,
                    center = Offset(shrine.x, shrine.y - 30f)
                )
                drawCircle(
                    color = sColor,
                    radius = 9f,
                    center = Offset(shrine.x, shrine.y - 30f),
                    style = Stroke(width = 2.2f)
                )
            }

            // Doors
            state.doors.forEach { door ->
                if (!door.isOpen) {
                    drawRoundRect(
                        color = Color(0xFF2B1D30),
                        topLeft = Offset(door.x, door.y),
                        size = Size(door.width, door.height),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    drawRoundRect(
                        color = SignalCoral,
                        topLeft = Offset(door.x, door.y),
                        size = Size(door.width, door.height),
                        cornerRadius = CornerRadius(4f, 4f),
                        style = Stroke(width = 2.6f)
                    )
                    // Lock bars
                    for (b in 1..3) {
                        val by = door.y + door.height * (b / 4f)
                        drawLine(
                            color = SignalCoral.copy(alpha = 0.65f),
                            start = Offset(door.x + 4f, by),
                            end = Offset(door.x + door.width - 4f, by),
                            strokeWidth = 2.2f
                        )
                    }
                } else {
                    // Open door hologram ghost frame
                    drawRoundRect(
                        color = HarmonyJade.copy(alpha = 0.25f),
                        topLeft = Offset(door.x + 6f, door.y),
                        size = Size(door.width - 12f, door.height),
                        cornerRadius = CornerRadius(4f, 4f),
                        style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                    )
                }
            }

            // Exit Portal (Wired Gate)
            val exit = state.definition.exit
            val ecx = exit.x + exit.width * 0.5f
            val ecy = exit.y + exit.height * 0.5f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(NaviCyan.copy(alpha = 0.72f), WiredViolet.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(ecx, ecy),
                    radius = exit.height * 0.72f
                ),
                topLeft = Offset(exit.x - 12f, exit.y - 10f),
                size = Size(exit.width + 24f, exit.height + 20f)
            )
            drawRoundRect(
                color = NaviCyan,
                topLeft = Offset(exit.x, exit.y),
                size = Size(exit.width, exit.height),
                cornerRadius = CornerRadius(24f, 24f),
                style = Stroke(width = 3.2f)
            )
            val innerPulse = 0.72f + 0.14f * sin(t * 4.5f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(ecx - exit.width * 0.5f * innerPulse, ecy - exit.height * 0.5f * innerPulse),
                size = Size(exit.width * innerPulse, exit.height * innerPulse),
                cornerRadius = CornerRadius(18f, 18f),
                style = Stroke(width = 1.6f)
            )

            // Collectible Memory Shards
            state.shards.forEach { shard ->
                if (!shard.collected) {
                    val bob = sin(t * 3.8f + shard.x * 0.03f) * 6f
                    val sx = shard.x
                    val sy = shard.y + bob
                    val diamond = Path().apply {
                        moveTo(sx, sy - 14f)
                        lineTo(sx + 10f, sy)
                        lineTo(sx, sy + 14f)
                        lineTo(sx - 10f, sy)
                        close()
                    }
                    drawCircle(
                        color = DeskAmber.copy(alpha = 0.25f),
                        radius = 18f,
                        center = Offset(sx, sy)
                    )
                    drawPath(diamond, color = DeskAmber)
                    drawPath(diamond, color = Color.White.copy(alpha = 0.85f), style = Stroke(width = 1.8f))
                }
            }

            // Pushable Physics Crates
            state.boxes.forEach { box ->
                val fill = if (box.isMetal) Color(0xFF394D70) else Color(0xFF383044)
                val accent = if (box.isMetal) NaviCyan else DeskAmber
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(box.x, box.y),
                    size = Size(box.width, box.height),
                    cornerRadius = CornerRadius(5f, 5f)
                )
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(box.x, box.y),
                    size = Size(box.width, box.height),
                    cornerRadius = CornerRadius(5f, 5f),
                    style = Stroke(width = 2.4f)
                )
                // Cross brace
                drawLine(
                    color = accent.copy(alpha = 0.45f),
                    start = Offset(box.x + 6f, box.y + 6f),
                    end = Offset(box.x + box.width - 6f, box.y + box.height - 6f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = accent.copy(alpha = 0.45f),
                    start = Offset(box.x + box.width - 6f, box.y + 6f),
                    end = Offset(box.x + 6f, box.y + box.height - 6f),
                    strokeWidth = 2f
                )
            }

            // Animated Lain Character Sprite!
            drawLainGameSprite(
                player = state.player,
                screenX = state.player.x,
                screenY = state.player.y,
                scale = 1f
            )

            // Floating Interact Indicator Ring above Lain when near an interactive terminal/mirror/shrine
            if (state.nearbyInteractLabel != null) {
                val ix = state.player.x + state.player.width * 0.5f
                val iy = state.player.y - 18f + sin(t * 7f) * 3f
                drawCircle(
                    color = NaviCyan.copy(alpha = 0.85f),
                    radius = 9f,
                    center = Offset(ix, iy),
                    style = Stroke(width = 2.2f)
                )
                drawCircle(
                    color = NaviCyan,
                    radius = 3.5f,
                    center = Offset(ix, iy)
                )
            }

            // Particles
            state.particles.forEach { p ->
                val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                drawCircle(
                    color = Color(p.colorArgb).copy(alpha = alpha),
                    radius = p.size * alpha.coerceAtLeast(0.4f),
                    center = Offset(p.x, p.y)
                )
            }
        }

        // 3. Live Ink Bridge Drag Preview (screen space)
        val ds = dragStartScreen
        val de = dragCurrentScreen
        if (inkDrawMode && ds != null && de != null) {
            drawLine(
                color = NaviCyan.copy(alpha = 0.45f),
                start = ds,
                end = de,
                strokeWidth = 14f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = ds,
                end = de,
                strokeWidth = 3f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
            )
        }
    }
}

private fun DrawScope.drawWorldBackdrop(worldId: WorldId, cameraX: Float, t: Float) {
    val (topColor, botColor, accent) = when (worldId) {
        WorldId.LAINS_ROOM -> Triple(Color(0xFF0A0E19), Color(0xFF161E31), DeskAmber)
        WorldId.GRAVITY -> Triple(Color(0xFF090E22), Color(0xFF1A173B), WiredViolet)
        WorldId.CREATION -> Triple(Color(0xFF0C1220), Color(0xFF162436), NaviCyan)
        WorldId.ENERGY -> Triple(Color(0xFF08121D), Color(0xFF13293D), DeskAmber)
        WorldId.BALANCE -> Triple(Color(0xFF0E141B), Color(0xFF1D282C), HarmonyJade)
        WorldId.THE_WIRED -> Triple(Color(0xFF050812), Color(0xFF14102E), SignalCoral)
    }

    drawRect(brush = Brush.verticalGradient(colors = listOf(topColor, botColor)))

    // Parallax subtle architectural grid / powerline motif
    val gridSpacing = 76f
    val parallaxX = -(cameraX * 0.28f) % gridSpacing
    var gx = parallaxX
    while (gx < size.width) {
        drawLine(
            color = accent.copy(alpha = 0.06f),
            start = Offset(gx, 0f),
            end = Offset(gx, size.height),
            strokeWidth = 1f
        )
        gx += gridSpacing
    }
    var gy = 48f
    while (gy < size.height) {
        drawLine(
            color = accent.copy(alpha = 0.05f),
            start = Offset(0f, gy),
            end = Offset(size.width, gy),
            strokeWidth = 1f
        )
        gy += gridSpacing
    }

    // Parallax distant utility towers / Wired monolith silhouettes
    val pillarGap = 240f
    val pOffset = -(cameraX * 0.18f) % pillarGap
    var px = pOffset - pillarGap
    while (px < size.width + pillarGap) {
        val h = size.height * 0.58f
        drawLine(
            color = accent.copy(alpha = 0.12f),
            start = Offset(px, size.height),
            end = Offset(px, size.height - h),
            strokeWidth = 3f
        )
        drawLine(
            color = accent.copy(alpha = 0.10f),
            start = Offset(px - 28f, size.height - h * 0.78f),
            end = Offset(px + 28f, size.height - h * 0.78f),
            strokeWidth = 2f
        )
        // Overhead cable sag
        val wire = Path().apply {
            moveTo(px, size.height - h * 0.78f)
            quadraticTo(px + pillarGap * 0.5f, size.height - h * 0.65f, px + pillarGap, size.height - h * 0.78f)
        }
        drawPath(wire, color = accent.copy(alpha = 0.09f), style = Stroke(width = 1.4f))
        px += pillarGap
    }

    // Drifting atmosphere motes
    for (i in 0..11) {
        val mx = ((i * 131f - cameraX * 0.12f + t * 14f) % size.width + size.width) % size.width
        val my = ((i * 89f + sin(t + i) * 20f) % size.height + size.height) % size.height
        drawCircle(color = accent.copy(alpha = 0.16f), radius = 2.2f, center = Offset(mx, my))
    }
}

private fun DrawScope.drawPlatform(plat: Platform, worldId: WorldId, t: Float) {
    when (plat.type) {
        PlatformType.TILTING_BEAM -> {
            val pivotX = plat.x + plat.width * 0.5f
            val pivotY = plat.y + plat.height * 0.5f
            // Fulcrum triangle under pivot
            val fulcrum = Path().apply {
                moveTo(pivotX, pivotY + 6f)
                lineTo(pivotX - 18f, pivotY + 34f)
                lineTo(pivotX + 18f, pivotY + 34f)
                close()
            }
            drawPath(fulcrum, color = Color(0xFF283A42))
            drawPath(fulcrum, color = HarmonyJade.copy(alpha = 0.8f), style = Stroke(width = 2f))

            withTransform({
                rotate(degrees = plat.tiltAngleDeg, pivot = Offset(pivotX, pivotY))
            }) {
                drawRoundRect(
                    color = Color(0xFF253740),
                    topLeft = Offset(plat.x, plat.y),
                    size = Size(plat.width, plat.height),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = HarmonyJade,
                    topLeft = Offset(plat.x, plat.y),
                    size = Size(plat.width, plat.height),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = 2.4f)
                )
                // Center balance dot
                drawCircle(color = DeskAmber, radius = 4.5f, center = Offset(pivotX, pivotY))
            }
        }
        PlatformType.HARD_LIGHT -> {
            if (plat.isSolidActive) {
                drawRoundRect(
                    color = NaviCyan.copy(alpha = 0.28f),
                    topLeft = Offset(plat.x, plat.y),
                    size = Size(plat.width, plat.height),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = NaviCyan,
                    topLeft = Offset(plat.x, plat.y),
                    size = Size(plat.width, plat.height),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = 2.4f)
                )
            } else {
                drawRoundRect(
                    color = NaviCyan.copy(alpha = 0.22f),
                    topLeft = Offset(plat.x, plat.y),
                    size = Size(plat.width, plat.height),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = 1.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                )
            }
        }
        PlatformType.DRAWN_INK -> {
            val glow = 0.75f + 0.2f * sin(t * 5f)
            drawRoundRect(
                color = NaviCyan.copy(alpha = 0.25f * glow),
                topLeft = Offset(plat.x - 4f, plat.y - 4f),
                size = Size(plat.width + 8f, plat.height + 8f),
                cornerRadius = CornerRadius(10f, 10f)
            )
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(NaviCyan, HarmonyJade)),
                topLeft = Offset(plat.x, plat.y),
                size = Size(plat.width, plat.height),
                cornerRadius = CornerRadius(9f, 9f)
            )
        }
        PlatformType.ICE -> {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF69D8F5), Color(0xFF236685)),
                    startY = plat.y,
                    endY = plat.y + plat.height
                ),
                topLeft = Offset(plat.x, plat.y),
                size = Size(plat.width, plat.height),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = Offset(plat.x + 4f, plat.y + 2f),
                end = Offset(plat.x + plat.width - 4f, plat.y + 2f),
                strokeWidth = 2.4f
            )
        }
        PlatformType.STICKY -> {
            drawRoundRect(
                color = Color(0xFF422C3D),
                topLeft = Offset(plat.x, plat.y),
                size = Size(plat.width, plat.height),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawLine(
                color = DeskAmber,
                start = Offset(plat.x, plat.y + 2f),
                end = Offset(plat.x + plat.width, plat.y + 2f),
                strokeWidth = 4f
            )
        }
        PlatformType.MOVING -> {
            drawRoundRect(
                color = Color(0xFF233252),
                topLeft = Offset(plat.x, plat.y),
                size = Size(plat.width, plat.height),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = NaviCyan.copy(alpha = 0.9f),
                topLeft = Offset(plat.x, plat.y),
                size = Size(plat.width, plat.height),
                cornerRadius = CornerRadius(6f, 6f),
                style = Stroke(width = 2.2f)
            )
            // Thruster dots
            drawCircle(color = DeskAmber, radius = 3.5f, center = Offset(plat.x + 14f, plat.y + plat.height * 0.5f))
            drawCircle(
                color = DeskAmber,
                radius = 3.5f,
                center = Offset(plat.x + plat.width - 14f, plat.y + plat.height * 0.5f)
            )
        }
        PlatformType.SOLID -> {
            val topEdge = when (worldId) {
                WorldId.LAINS_ROOM -> Color(0xFF566B91)
                WorldId.GRAVITY -> WiredViolet
                WorldId.CREATION -> NaviCyan
                WorldId.ENERGY -> DeskAmber
                WorldId.BALANCE -> HarmonyJade
                WorldId.THE_WIRED -> SignalCoral
            }
            drawRoundRect(
                color = Color(0xFF172236),
                topLeft = Offset(plat.x, plat.y),
                size = Size(plat.width, plat.height),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawLine(
                color = topEdge.copy(alpha = 0.85f),
                start = Offset(plat.x + 2f, plat.y + 2f),
                end = Offset(plat.x + plat.width - 2f, plat.y + 2f),
                strokeWidth = 3f
            )
            drawRoundRect(
                color = WiredBorder.copy(alpha = 0.55f),
                topLeft = Offset(plat.x, plat.y),
                size = Size(plat.width, plat.height),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 1.4f)
            )
        }
    }
}

private fun DrawScope.drawSwitchNode(sw: SwitchNode, t: Float) {
    val activeColor = when (sw.kind) {
        SwitchKind.GRAVITY_SWITCH -> WiredViolet
        SwitchKind.MAGNET_SWITCH -> SignalCoral
        else -> HarmonyJade
    }
    val col = if (sw.isActive) activeColor else TextMuted

    when (sw.kind) {
        SwitchKind.PRESSURE_PLATE -> {
            val pressOffset = if (sw.isActive) 6f else 0f
            drawRoundRect(
                color = Color(0xFF24324D),
                topLeft = Offset(sw.x, sw.y + 8f),
                size = Size(sw.width, sw.height - 8f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawRoundRect(
                color = col,
                topLeft = Offset(sw.x + 4f, sw.y + pressOffset),
                size = Size(sw.width - 8f, 9f),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
        SwitchKind.PHOTO_SENSOR -> {
            val cx = sw.x + sw.width * 0.5f
            val cy = sw.y + sw.height * 0.5f
            drawCircle(color = Color(0xFF1A2740), radius = 20f, center = Offset(cx, cy))
            drawCircle(color = col, radius = 20f, center = Offset(cx, cy), style = Stroke(width = 2.8f))
            drawCircle(
                color = if (sw.isActive) NaviCyan else Color(0xFF2D3C5C),
                radius = 10f + (if (sw.isActive) sin(t * 8f) * 2f else 0f),
                center = Offset(cx, cy)
            )
        }
        else -> {
            // Standing Navi Terminal / Gravity / Magnet Switch
            drawRoundRect(
                color = Color(0xFF1D2942),
                topLeft = Offset(sw.x, sw.y),
                size = Size(sw.width, sw.height),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = col,
                topLeft = Offset(sw.x, sw.y),
                size = Size(sw.width, sw.height),
                cornerRadius = CornerRadius(6f, 6f),
                style = Stroke(width = 2.4f)
            )
            // Mini glowing terminal display
            drawRoundRect(
                color = col.copy(alpha = if (sw.isActive) 0.8f else 0.3f),
                topLeft = Offset(sw.x + 7f, sw.y + 8f),
                size = Size(sw.width - 14f, 18f),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
    }
}

private fun DrawScope.drawPowerConduits(state: LevelRuntimeState) {
    state.switches.forEach { sw ->
        val start = Offset(sw.bounds().centerX, sw.bounds().centerY)
        val lineColor = if (sw.isActive) HarmonyJade.copy(alpha = 0.55f) else WiredBorder.copy(alpha = 0.25f)
        state.doors.filter { it.linkedSignalId == sw.signalId }.forEach { door ->
            val end = Offset(door.bounds().centerX, door.bounds().centerY)
            drawLine(
                color = lineColor,
                start = start,
                end = end,
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
        }
        state.platforms.filter { it.linkedSignalId == sw.signalId }.forEach { plat ->
            val end = Offset(plat.x + plat.width * 0.5f, plat.y + plat.height * 0.5f)
            drawLine(
                color = lineColor,
                start = start,
                end = end,
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
        }
        state.magnets.filter { it.linkedSignalId == sw.signalId }.forEach { mag ->
            val end = Offset(mag.x + mag.width * 0.5f, mag.y + mag.height * 0.5f)
            drawLine(
                color = SignalCoral.copy(alpha = 0.35f),
                start = start,
                end = end,
                strokeWidth = 1.8f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
        }
    }
}
