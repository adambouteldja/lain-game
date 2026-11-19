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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Evolution 2.0 side-scrolling Compose Canvas renderer for all 10 worlds.
 * Includes reference-based architectural backdrops (School, Cyberia, Wired, Lain's Room, Streets, Family Home),
 * directional magnetic chevrons, sloped ink bridges, and interactive Story NPCs / Navi Desk.
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
                val dir = if (isInv) -1f else -0.5f
                for (col in 0..2) {
                    val cx = gz.x + gz.width * (0.22f + col * 0.28f)
                    val offsetY = ((t * 55f * (if (isInv) 1.5f else 0.7f) + col * 65f) % gz.height)
                    val cy = if (dir < 0f) gz.y + gz.height - offsetY else gz.y + offsetY
                    drawLine(zoneColor.copy(alpha = 0.55f), Offset(cx - 10f, cy + 7f), Offset(cx, cy - 5f), 2.2f, StrokeCap.Round)
                    drawLine(zoneColor.copy(alpha = 0.55f), Offset(cx, cy - 5f), Offset(cx + 10f, cy + 7f), 2.2f, StrokeCap.Round)
                }
            }

            // Power Conduit lines
            drawPowerConduits(state)

            // Magnetic Coils with clear animated force-direction chevrons (E2.2 visual hint upgrade)
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

                // Animated directional pull/push chevrons along horizontal axis toward metal boxes
                val targetMetal = state.boxes.firstOrNull { it.isMetal }
                val sideSign = if (targetMetal != null && targetMetal.bounds().centerX < cx) -1f else 1f
                val flowSign = if (isAttract) -sideSign else sideSign
                for (c in 0..2) {
                    val f = (t * 1.4f + c * 0.33f) % 1f
                    val dist = 36f + (if (isAttract) (1f - f) else f) * 105f
                    val chX = cx + sideSign * dist
                    val tipX = chX + flowSign * 8f
                    drawLine(
                        color = magColor.copy(alpha = 0.82f * (1f - abs(f - 0.5f) * 1.4f).coerceIn(0.2f, 1f)),
                        start = Offset(chX - flowSign * 4f, cy - 8f),
                        end = Offset(tipX, cy),
                        strokeWidth = 2.6f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = magColor.copy(alpha = 0.82f * (1f - abs(f - 0.5f) * 1.4f).coerceIn(0.2f, 1f)),
                        start = Offset(tipX, cy),
                        end = Offset(chX - flowSign * 4f, cy + 8f),
                        strokeWidth = 2.6f,
                        cap = StrokeCap.Round
                    )
                }

                drawRoundRect(Color(0xFF1F2B44), Offset(coil.x, coil.y), Size(coil.width, coil.height), CornerRadius(8f, 8f))
                drawRoundRect(magColor, Offset(coil.x, coil.y), Size(coil.width, coil.height), CornerRadius(8f, 8f), style = Stroke(width = 2.6f))
                drawLine(magColor, Offset(cx - 9f, cy), Offset(cx + 9f, cy), 3f, StrokeCap.Round)
                if (isAttract) {
                    drawLine(magColor, Offset(cx, cy - 9f), Offset(cx, cy + 9f), 3f, StrokeCap.Round)
                }
            }

            // Hazards
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

            // Platforms
            (state.platforms + state.drawnPlatforms).forEach { plat ->
                drawPlatform(plat, state.definition.worldId, t)
            }

            // Story NPCs & Navi Desktop Terminal
            state.definition.npcs.forEach { npc ->
                drawStoryNpc(npc, t)
            }

            // Checkpoints
            state.checkpoints.forEach { cp ->
                val cpColor = if (cp.activated) HarmonyJade else TextMuted
                drawLine(cpColor, Offset(cp.x, cp.y), Offset(cp.x, cp.y - 58f), 3.5f, StrokeCap.Round)
                val pulse = if (cp.activated) 10f + sin(t * 5f) * 2.5f else 7f
                drawCircle(cpColor.copy(alpha = if (cp.activated) 0.25f else 0.1f), pulse * 1.8f, Offset(cp.x, cp.y - 58f))
                drawCircle(cpColor, 7f, Offset(cp.x, cp.y - 58f))
            }

            // Switches
            state.switches.forEach { sw -> drawSwitchNode(sw, t) }

            // Light Emitters, Beams & Mirrors
            state.definition.emitters.forEach { em ->
                drawCircle(Color(0xFF283755), 16f, Offset(em.x, em.y))
                drawCircle(DeskAmber, 8f, Offset(em.x, em.y))
                drawCircle(DeskAmber.copy(alpha = 0.35f), 20f + sin(t * 6f) * 3f, Offset(em.x, em.y))
            }
            state.lightBeams.forEach { seg ->
                drawLine(NaviCyan.copy(alpha = 0.24f), Offset(seg.x1, seg.y1), Offset(seg.x2, seg.y2), 14f, StrokeCap.Round)
                drawLine(NaviCyan, Offset(seg.x1, seg.y1), Offset(seg.x2, seg.y2), 4.2f, StrokeCap.Round)
                drawLine(Color.White, Offset(seg.x1, seg.y1), Offset(seg.x2, seg.y2), 1.6f, StrokeCap.Round)
            }
            state.mirrors.forEach { m ->
                val angleRad = m.angleStep * (PI / 4.0)
                val outX = cos(angleRad).toFloat()
                val outY = sin(angleRad).toFloat()
                drawCircle(Color(0xFF1A2640), m.radius, Offset(m.x, m.y))
                drawCircle(DeskAmber.copy(alpha = 0.85f), m.radius, Offset(m.x, m.y), style = Stroke(width = 2.4f))
                drawLine(NaviCyan, Offset(m.x - outX * 10f, m.y - outY * 10f), Offset(m.x + outX * 17f, m.y + outY * 17f), 3.5f, StrokeCap.Round)
                drawCircle(Color.White, 4f, Offset(m.x + outX * 16f, m.y + outY * 16f))
            }

            // Judo Shrines
            state.judoShrines.forEach { shrine ->
                val sColor = if (shrine.completed) HarmonyJade else DeskAmber
                drawLine(sColor, Offset(shrine.x - 24f, shrine.y), Offset(shrine.x - 20f, shrine.y - 68f), 5f, StrokeCap.Round)
                drawLine(sColor, Offset(shrine.x + 24f, shrine.y), Offset(shrine.x + 20f, shrine.y - 68f), 5f, StrokeCap.Round)
                drawLine(sColor, Offset(shrine.x - 34f, shrine.y - 68f), Offset(shrine.x + 34f, shrine.y - 68f), 6f, StrokeCap.Round)
                drawLine(sColor.copy(alpha = 0.7f), Offset(shrine.x - 24f, shrine.y - 54f), Offset(shrine.x + 24f, shrine.y - 54f), 3.5f)
                drawCircle(sColor.copy(alpha = 0.22f), 16f + sin(t * 4f) * 3f, Offset(shrine.x, shrine.y - 30f))
                drawCircle(sColor, 9f, Offset(shrine.x, shrine.y - 30f), style = Stroke(width = 2.2f))
            }

            // Doors
            state.doors.forEach { door ->
                if (!door.isOpen) {
                    drawRoundRect(Color(0xFF2B1D30), Offset(door.x, door.y), Size(door.width, door.height), CornerRadius(4f, 4f))
                    drawRoundRect(SignalCoral, Offset(door.x, door.y), Size(door.width, door.height), CornerRadius(4f, 4f), style = Stroke(width = 2.6f))
                    for (b in 1..3) {
                        val by = door.y + door.height * (b / 4f)
                        drawLine(SignalCoral.copy(alpha = 0.65f), Offset(door.x + 4f, by), Offset(door.x + door.width - 4f, by), 2.2f)
                    }
                } else {
                    drawRoundRect(
                        HarmonyJade.copy(alpha = 0.25f),
                        Offset(door.x + 6f, door.y),
                        Size(door.width - 12f, door.height),
                        CornerRadius(4f, 4f),
                        style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                    )
                }
            }

            // Exit Portal
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
            drawRoundRect(NaviCyan, Offset(exit.x, exit.y), Size(exit.width, exit.height), CornerRadius(24f, 24f), style = Stroke(width = 3.2f))
            val innerPulse = 0.72f + 0.14f * sin(t * 4.5f)
            drawRoundRect(
                Color.White.copy(alpha = 0.85f),
                Offset(ecx - exit.width * 0.5f * innerPulse, ecy - exit.height * 0.5f * innerPulse),
                Size(exit.width * innerPulse, exit.height * innerPulse),
                CornerRadius(18f, 18f),
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
                    drawCircle(DeskAmber.copy(alpha = 0.25f), 18f, Offset(sx, sy))
                    drawPath(diamond, color = DeskAmber)
                    drawPath(diamond, color = Color.White.copy(alpha = 0.85f), style = Stroke(width = 1.8f))
                }
            }

            // Pushable Physics Crates
            state.boxes.forEach { box ->
                val fill = if (box.isMetal) Color(0xFF394D70) else Color(0xFF383044)
                val accent = if (box.isMetal) NaviCyan else DeskAmber
                drawRoundRect(fill, Offset(box.x, box.y), Size(box.width, box.height), CornerRadius(5f, 5f))
                drawRoundRect(accent, Offset(box.x, box.y), Size(box.width, box.height), CornerRadius(5f, 5f), style = Stroke(width = 2.4f))
                drawLine(accent.copy(alpha = 0.45f), Offset(box.x + 6f, box.y + 6f), Offset(box.x + box.width - 6f, box.y + box.height - 6f), 2f)
                drawLine(accent.copy(alpha = 0.45f), Offset(box.x + box.width - 6f, box.y + 6f), Offset(box.x + 6f, box.y + box.height - 6f), 2f)
            }

            // Animated Lain Sprite
            drawLainGameSprite(
                player = state.player,
                screenX = state.player.x,
                screenY = state.player.y,
                scale = 1f
            )

            // Floating Interact Indicator Ring
            if (state.nearbyInteractLabel != null) {
                val ix = state.player.x + state.player.width * 0.5f
                val iy = state.player.y - 18f + sin(t * 7f) * 3f
                drawCircle(NaviCyan.copy(alpha = 0.85f), 9f, Offset(ix, iy), style = Stroke(width = 2.2f))
                drawCircle(NaviCyan, 3.5f, Offset(ix, iy))
            }

            // Particles
            state.particles.forEach { p ->
                val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                drawCircle(Color(p.colorArgb).copy(alpha = alpha), p.size * alpha.coerceAtLeast(0.4f), Offset(p.x, p.y))
            }
        }

        // 3. Live Ink Bridge Drag Preview (screen space)
        val ds = dragStartScreen
        val de = dragCurrentScreen
        if (inkDrawMode && ds != null && de != null) {
            drawLine(NaviCyan.copy(alpha = 0.45f), ds, de, 14f, StrokeCap.Round)
            drawLine(Color.White, ds, de, 3f, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
        }
    }
}

private fun DrawScope.drawStoryNpc(npc: StoryNpc, t: Float) {
    if (npc.opensDesktop) {
        // Glowing Navi Desktop workstation on Lain's desk
        val nx = npc.x
        val ny = npc.y
        drawCircle(NaviCyan.copy(alpha = 0.24f + 0.08f * sin(t * 4f)), 34f, Offset(nx, ny - 24f))
        drawRoundRect(Color(0xFF475674), Offset(nx - 24f, ny - 44f), Size(48f, 36f), CornerRadius(6f, 6f))
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF288EB0), Color(0xFF9DF5FF))),
            topLeft = Offset(nx - 19f, ny - 39f),
            size = Size(38f, 25f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRect(Color(0xFF2A364F), Offset(nx - 10f, ny - 8f), Size(20f, 8f))
        return
    }

    val bob = sin(t * 2.4f + npc.x * 0.02f) * 1.4f
    val x = npc.x
    val y = npc.y + bob
    // Subtle story beacon ring
    drawCircle(DeskAmber.copy(alpha = 0.16f), 26f + sin(t * 3f) * 3f, Offset(x, y - 34f))
    // Legs
    drawLine(Color(0xFF232836), Offset(x - 5f, y - 16f), Offset(x - 6f, y), 4.5f, StrokeCap.Round)
    drawLine(Color(0xFF232836), Offset(x + 5f, y - 16f), Offset(x + 6f, y), 4.5f, StrokeCap.Round)
    // Outfit torso
    drawRoundRect(Color(npc.outfitColorArgb), Offset(x - 11f, y - 42f), Size(22f, 28f), CornerRadius(6f, 6f))
    // Head
    drawCircle(LainSkin, 10.5f, Offset(x, y - 53f))
    // Hair cap
    drawArc(
        color = Color(npc.hairColorArgb),
        startAngle = 175f,
        sweepAngle = 190f,
        useCenter = true,
        topLeft = Offset(x - 12f, y - 65f),
        size = Size(24f, 21f)
    )
    // Speech indicator dot
    drawCircle(NaviCyan, 3.5f, Offset(x, y - 74f + sin(t * 5f) * 2.5f))
}

private fun DrawScope.drawWorldBackdrop(worldId: WorldId, cameraX: Float, t: Float) {
    when (worldId) {
        WorldId.SCHOOL -> {
            // Reference Image 5: Bright frosted-glass windows + sage-green sliding doors + cream walls
            drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF263847), Color(0xFF1A2835))))
            val panelW = 220f
            val off = -(cameraX * 0.22f) % panelW
            var x = off - panelW
            var idx = 0
            while (x < size.width + panelW) {
                if (idx % 3 == 0) {
                    // Sage-green sliding classroom door
                    drawRect(Color(0xFF476350).copy(alpha = 0.72f), Offset(x + 12f, size.height * 0.14f), Size(124f, size.height * 0.74f))
                    drawRect(Color(0xFF83B4C7).copy(alpha = 0.45f), Offset(x + 28f, size.height * 0.22f), Size(92f, size.height * 0.30f))
                } else {
                    // Frosted cyan-white classroom window
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFBCE6F5).copy(alpha = 0.34f), Color(0xFF6DA8C4).copy(alpha = 0.22f)),
                            startY = size.height * 0.14f,
                            endY = size.height * 0.62f
                        ),
                        topLeft = Offset(x + 8f, size.height * 0.14f),
                        size = Size(192f, size.height * 0.48f)
                    )
                    drawRect(Color(0xFF627788).copy(alpha = 0.5f), Offset(x + 8f, size.height * 0.14f), Size(192f, size.height * 0.48f), style = Stroke(width = 3f))
                }
                x += panelW
                idx++
            }
            return
        }
        WorldId.CYBERIA -> {
            // Reference Images 2 & 3: Textured magenta-purple club walls + glowing ice-blue CYBERIA neon cloud
            drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF2D0F38), Color(0xFF591B62), Color(0xFF190A24))))
            val signX = ((size.width * 0.55f - cameraX * 0.18f) % (size.width + 260f) + (size.width + 260f)) % (size.width + 260f) - 130f
            val signY = size.height * 0.24f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF8CF2FF).copy(alpha = 0.26f), Color.Transparent),
                    center = Offset(signX, signY),
                    radius = 150f
                ),
                topLeft = Offset(signX - 150f, signY - 80f),
                size = Size(300f, 160f)
            )
            // Neon cloud outline ("CYBERIA")
            drawOval(
                color = Color(0xFF9DF6FF).copy(alpha = 0.72f + 0.12f * sin(t * 7f)),
                topLeft = Offset(signX - 96f, signY - 34f),
                size = Size(192f, 68f),
                style = Stroke(width = 3.5f)
            )
            // Overhead club powerlines & dark bar stools silhouette
            for (wIdx in 0..2) {
                val wy = signY - 18f + wIdx * 18f
                drawLine(Color(0xFF100716), Offset(0f, wy), Offset(size.width, wy + 16f), 2f)
            }
            val stoolGap = 150f
            val sOff = -(cameraX * 0.30f) % stoolGap
            var sx = sOff
            while (sx < size.width + stoolGap) {
                drawLine(Color(0xFF140B1D), Offset(sx - 12f, size.height), Offset(sx - 12f, size.height * 0.72f), 3f)
                drawLine(Color(0xFF140B1D), Offset(sx + 12f, size.height), Offset(sx + 12f, size.height * 0.72f), 3f)
                drawOval(Color(0xFF1F122B), Offset(sx - 18f, size.height * 0.70f), Size(36f, 10f))
                sx += stoolGap
            }
            return
        }
        WorldId.THE_WIRED -> {
            // Reference Images 6 & 7: Pitch-black void + orbiting holographic Wired monitors + wireframe arcs
            drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF03050C), Color(0xFF0A0D1E), Color(0xFF04060E))))
            // Translucent white-blue wireframe arcs (Ref Image 7)
            drawArc(
                color = Color(0xFF9FD6FF).copy(alpha = 0.22f),
                startAngle = -60f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(-size.width * 0.25f, size.height * 0.05f),
                size = Size(size.width * 0.55f, size.height * 0.90f),
                style = Stroke(width = 3f)
            )
            drawArc(
                color = Color(0xFF9FD6FF).copy(alpha = 0.18f),
                startAngle = 120f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(size.width * 0.70f, size.height * 0.05f),
                size = Size(size.width * 0.55f, size.height * 0.90f),
                style = Stroke(width = 3f)
            )
            // Floating orbiting holographic Wired screens (Ref Image 6)
            for (m in 0..7) {
                val mx = ((m * 165f - cameraX * 0.20f + t * 18f) % (size.width + 180f) + (size.width + 180f)) % (size.width + 180f) - 90f
                val my = size.height * (0.18f + (m % 3) * 0.18f) + sin(t * 1.4f + m) * 14f
                val monColor = if (m % 2 == 0) NaviCyan else WiredViolet
                drawRoundRect(monColor.copy(alpha = 0.16f), Offset(mx, my), Size(64f, 44f), CornerRadius(6f, 6f))
                drawRoundRect(monColor.copy(alpha = 0.55f), Offset(mx, my), Size(64f, 44f), CornerRadius(6f, 6f), style = Stroke(width = 1.6f))
                drawCircle(monColor.copy(alpha = 0.35f), 11f, Offset(mx + 32f, my + 22f))
            }
            return
        }
        else -> Unit
    }

    val (topColor, botColor, accent) = when (worldId) {
        WorldId.LAINS_ROOM -> Triple(Color(0xFF0A1022), Color(0xFF192746), NaviCyan)
        WorldId.FAMILY_HOME -> Triple(Color(0xFF1A1618), Color(0xFF2B2324), DeskAmber)
        WorldId.STREETS -> Triple(Color(0xFF131A2C), Color(0xFF282E42), SignalCoral)
        WorldId.GRAVITY -> Triple(Color(0xFF090E22), Color(0xFF1A173B), WiredViolet)
        WorldId.CREATION -> Triple(Color(0xFF0C1220), Color(0xFF162436), NaviCyan)
        WorldId.ENERGY -> Triple(Color(0xFF08121D), Color(0xFF13293D), DeskAmber)
        WorldId.BALANCE -> Triple(Color(0xFF11191D), Color(0xFF22302F), HarmonyJade)
        else -> Triple(Color(0xFF0A0E19), Color(0xFF161E31), NaviCyan)
    }

    drawRect(brush = Brush.verticalGradient(colors = listOf(topColor, botColor)))

    val gridSpacing = 76f
    val parallaxX = -(cameraX * 0.28f) % gridSpacing
    var gx = parallaxX
    while (gx < size.width) {
        drawLine(accent.copy(alpha = 0.06f), Offset(gx, 0f), Offset(gx, size.height), 1f)
        gx += gridSpacing
    }
    var gy = 48f
    while (gy < size.height) {
        drawLine(accent.copy(alpha = 0.05f), Offset(0f, gy), Offset(size.width, gy), 1f)
        gy += gridSpacing
    }

    val pillarGap = 240f
    val pOffset = -(cameraX * 0.18f) % pillarGap
    var px = pOffset - pillarGap
    while (px < size.width + pillarGap) {
        val h = size.height * 0.62f
        drawLine(accent.copy(alpha = 0.14f), Offset(px, size.height), Offset(px, size.height - h), 3.4f)
        drawLine(accent.copy(alpha = 0.12f), Offset(px - 28f, size.height - h * 0.78f), Offset(px + 28f, size.height - h * 0.78f), 2.2f)
        val wire = Path().apply {
            moveTo(px, size.height - h * 0.78f)
            quadraticTo(px + pillarGap * 0.5f, size.height - h * 0.65f, px + pillarGap, size.height - h * 0.78f)
        }
        drawPath(wire, color = accent.copy(alpha = 0.11f), style = Stroke(width = 1.5f))
        px += pillarGap
    }

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
                drawRoundRect(Color(0xFF253740), Offset(plat.x, plat.y), Size(plat.width, plat.height), CornerRadius(6f, 6f))
                drawRoundRect(HarmonyJade, Offset(plat.x, plat.y), Size(plat.width, plat.height), CornerRadius(6f, 6f), style = Stroke(width = 2.4f))
                drawCircle(DeskAmber, 4.5f, Offset(pivotX, pivotY))
            }
        }
        PlatformType.HARD_LIGHT -> {
            if (plat.isSolidActive) {
                drawRoundRect(NaviCyan.copy(alpha = 0.28f), Offset(plat.x, plat.y), Size(plat.width, plat.height), CornerRadius(6f, 6f))
                drawRoundRect(NaviCyan, Offset(plat.x, plat.y), Size(plat.width, plat.height), CornerRadius(6f, 6f), style = Stroke(width = 2.4f))
            } else {
                drawRoundRect(
                    NaviCyan.copy(alpha = 0.22f),
                    Offset(plat.x, plat.y),
                    Size(plat.width, plat.height),
                    CornerRadius(6f, 6f),
                    style = Stroke(width = 1.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                )
            }
        }
        PlatformType.DRAWN_INK -> {
            val glow = 0.75f + 0.2f * sin(t * 5f)
            val pivot = Offset(plat.x + plat.width * 0.5f, plat.y + plat.height * 0.5f)
            withTransform({
                if (abs(plat.tiltAngleDeg) > 0.5f) {
                    rotate(degrees = plat.tiltAngleDeg, pivot = pivot)
                }
            }) {
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
            drawLine(Color.White.copy(alpha = 0.85f), Offset(plat.x + 4f, plat.y + 2f), Offset(plat.x + plat.width - 4f, plat.y + 2f), 2.4f)
        }
        PlatformType.STICKY -> {
            drawRoundRect(Color(0xFF422C3D), Offset(plat.x, plat.y), Size(plat.width, plat.height), CornerRadius(4f, 4f))
            drawLine(DeskAmber, Offset(plat.x, plat.y + 2f), Offset(plat.x + plat.width, plat.y + 2f), 4f)
        }
        PlatformType.MOVING -> {
            drawRoundRect(Color(0xFF233252), Offset(plat.x, plat.y), Size(plat.width, plat.height), CornerRadius(6f, 6f))
            drawRoundRect(NaviCyan.copy(alpha = 0.9f), Offset(plat.x, plat.y), Size(plat.width, plat.height), CornerRadius(6f, 6f), style = Stroke(width = 2.2f))
            drawCircle(DeskAmber, 3.5f, Offset(plat.x + 14f, plat.y + plat.height * 0.5f))
            drawCircle(DeskAmber, 3.5f, Offset(plat.x + plat.width - 14f, plat.y + plat.height * 0.5f))
        }
        PlatformType.SOLID -> {
            val topEdge = when (worldId) {
                WorldId.LAINS_ROOM -> Color(0xFF64A6D8)
                WorldId.FAMILY_HOME -> Color(0xFFC59B6D)
                WorldId.SCHOOL -> Color(0xFF81A88D)
                WorldId.STREETS -> Color(0xFF7D8FA8)
                WorldId.CYBERIA -> Color(0xFFD964E8)
                WorldId.GRAVITY -> WiredViolet
                WorldId.CREATION -> NaviCyan
                WorldId.ENERGY -> DeskAmber
                WorldId.BALANCE -> HarmonyJade
                WorldId.THE_WIRED -> SignalCoral
            }
            val fill = when (worldId) {
                WorldId.FAMILY_HOME -> Color(0xFF2A2120)
                WorldId.SCHOOL -> Color(0xFF28353B)
                WorldId.CYBERIA -> Color(0xFF24132D)
                else -> Color(0xFF172236)
            }
            drawRoundRect(fill, Offset(plat.x, plat.y), Size(plat.width, plat.height), CornerRadius(4f, 4f))
            drawLine(topEdge.copy(alpha = 0.85f), Offset(plat.x + 2f, plat.y + 2f), Offset(plat.x + plat.width - 2f, plat.y + 2f), 3f)
            drawRoundRect(WiredBorder.copy(alpha = 0.55f), Offset(plat.x, plat.y), Size(plat.width, plat.height), CornerRadius(4f, 4f), style = Stroke(width = 1.4f))
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
            drawRoundRect(Color(0xFF24324D), Offset(sw.x, sw.y + 8f), Size(sw.width, sw.height - 8f), CornerRadius(3f, 3f))
            drawRoundRect(col, Offset(sw.x + 4f, sw.y + pressOffset), Size(sw.width - 8f, 9f), CornerRadius(3f, 3f))
        }
        SwitchKind.PHOTO_SENSOR -> {
            val cx = sw.x + sw.width * 0.5f
            val cy = sw.y + sw.height * 0.5f
            drawCircle(Color(0xFF1A2740), 20f, Offset(cx, cy))
            drawCircle(col, 20f, Offset(cx, cy), style = Stroke(width = 2.8f))
            drawCircle(if (sw.isActive) NaviCyan else Color(0xFF2D3C5C), 10f + (if (sw.isActive) sin(t * 8f) * 2f else 0f), Offset(cx, cy))
        }
        else -> {
            drawRoundRect(Color(0xFF1D2942), Offset(sw.x, sw.y), Size(sw.width, sw.height), CornerRadius(6f, 6f))
            drawRoundRect(col, Offset(sw.x, sw.y), Size(sw.width, sw.height), CornerRadius(6f, 6f), style = Stroke(width = 2.4f))
            drawRoundRect(col.copy(alpha = if (sw.isActive) 0.8f else 0.3f), Offset(sw.x + 7f, sw.y + 8f), Size(sw.width - 14f, 18f), CornerRadius(3f, 3f))
        }
    }
}

private fun DrawScope.drawPowerConduits(state: LevelRuntimeState) {
    state.switches.forEach { sw ->
        val start = Offset(sw.bounds().centerX, sw.bounds().centerY)
        val lineColor = if (sw.isActive) HarmonyJade.copy(alpha = 0.55f) else WiredBorder.copy(alpha = 0.25f)
        state.doors.filter { it.linkedSignalId == sw.signalId }.forEach { door ->
            drawLine(lineColor, start, Offset(door.bounds().centerX, door.bounds().centerY), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        state.platforms.filter { it.linkedSignalId == sw.signalId }.forEach { plat ->
            drawLine(lineColor, start, Offset(plat.x + plat.width * 0.5f, plat.y + plat.height * 0.5f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        state.magnets.filter { it.linkedSignalId == sw.signalId }.forEach { mag ->
            drawLine(SignalCoral.copy(alpha = 0.35f), start, Offset(mag.x + mag.width * 0.5f, mag.y + mag.height * 0.5f), 1.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
    }
}
