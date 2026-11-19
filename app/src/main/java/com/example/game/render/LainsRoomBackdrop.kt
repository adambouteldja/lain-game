package com.example.game.render

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.game.model.LainAnimState
import com.example.game.model.PlayerState
import com.example.game.render.LainSpriteRenderer.drawLainGameSprite
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.sin

/**
 * Animated Main Menu atmospheric Canvas depicting Lain's bedroom at night:
 * retro Navi CRT computer glowing with digital waveforms, tangled cables,
 * desk lamp warmth, humming powerlines outside the window, floating dust motes,
 * and animated Lain standing beside her computer.
 */
@Composable
fun LainsRoomBackdrop(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "room_anim")
    val animPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 100_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "room_time"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = animPhase * 1.4f

        // 1. Deep nocturnal bedroom walls gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF070B14), Color(0xFF10182A), Color(0xFF0A0F1A))
            )
        )

        // 2. Bedroom Window with nighttime powerline silhouettes (top-left)
        val winX = w * 0.08f
        val winY = h * 0.09f
        val winW = (w * 0.34f).coerceIn(110f, 280f)
        val winH = (h * 0.28f).coerceIn(110f, 230f)

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF14213D), Color(0xFF213152), Color(0xFF162038)),
                startY = winY,
                endY = winY + winH
            ),
            topLeft = Offset(winX, winY),
            size = Size(winW, winH),
            cornerRadius = CornerRadius(6f, 6f)
        )
        // Utility pole silhouette outside window
        val poleX = winX + winW * 0.65f
        drawLine(
            color = Color(0xFF0B101D),
            start = Offset(poleX, winY + winH * 0.12f),
            end = Offset(poleX, winY + winH),
            strokeWidth = 5f
        )
        drawLine(
            color = Color(0xFF0B101D),
            start = Offset(poleX - 24f, winY + winH * 0.28f),
            end = Offset(poleX + 24f, winY + winH * 0.28f),
            strokeWidth = 3.5f
        )
        // Sagging powerlines across window
        for (lineIdx in 0..2) {
            val ly = winY + winH * (0.28f + lineIdx * 0.14f)
            val wirePath = Path().apply {
                moveTo(winX, ly - 8f)
                quadraticTo(winX + winW * 0.45f, ly + 18f, winX + winW, ly - 4f)
            }
            drawPath(wirePath, color = Color(0xFF0B101D), style = Stroke(width = 1.6f))
        }
        // Subtle red warning beacon blinking on distant tower
        val beaconAlpha = 0.35f + 0.55f * ((sin(t * 2.4f) + 1f) * 0.5f)
        drawCircle(
            color = SignalCoral.copy(alpha = beaconAlpha),
            radius = 3.5f,
            center = Offset(winX + winW * 0.22f, winY + winH * 0.46f)
        )
        // Window frame & mullions
        drawRoundRect(
            color = Color(0xFF1C2740),
            topLeft = Offset(winX, winY),
            size = Size(winW, winH),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = 5f)
        )
        drawLine(
            color = Color(0xFF1C2740),
            start = Offset(winX + winW * 0.5f, winY),
            end = Offset(winX + winW * 0.5f, winY + winH),
            strokeWidth = 3.5f
        )

        // 3. Floor plane
        val floorY = h * 0.72f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF131B2D), Color(0xFF0B101B)),
                startY = floorY,
                endY = h
            ),
            topLeft = Offset(0f, floorY),
            size = Size(w, h - floorY)
        )
        drawLine(
            color = WiredBorder.copy(alpha = 0.6f),
            start = Offset(0f, floorY),
            end = Offset(w, floorY),
            strokeWidth = 2f
        )

        // 4. Desk & Retro Navi CRT Computer (positioned cleanly right-center)
        val deskW = (w * 0.42f).coerceIn(170f, 340f)
        val deskH = 14f
        val deskX = w * 0.52f
        val deskY = floorY - 76f

        // Warm CRT + Desk Lamp glow halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(NaviCyan.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(deskX + deskW * 0.48f, deskY - 48f),
                radius = deskW * 0.95f
            ),
            radius = deskW * 0.95f,
            center = Offset(deskX + deskW * 0.48f, deskY - 48f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(DeskAmber.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(deskX + deskW * 0.85f, deskY - 38f),
                radius = deskW * 0.55f
            ),
            radius = deskW * 0.55f,
            center = Offset(deskX + deskW * 0.85f, deskY - 38f)
        )

        // Desk legs
        drawRect(
            color = Color(0xFF1B2438),
            topLeft = Offset(deskX + 12f, deskY + deskH),
            size = Size(10f, floorY - (deskY + deskH))
        )
        drawRect(
            color = Color(0xFF1B2438),
            topLeft = Offset(deskX + deskW - 22f, deskY + deskH),
            size = Size(10f, floorY - (deskY + deskH))
        )
        // Desk tabletop
        drawRoundRect(
            color = Color(0xFF28344E),
            topLeft = Offset(deskX, deskY),
            size = Size(deskW, deskH),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // Retro Navi Computer Chassis & CRT Monitor
        val monW = (deskW * 0.52f).coerceIn(92f, 150f)
        val monH = monW * 0.78f
        val monX = deskX + deskW * 0.22f
        val monY = deskY - monH - 12f

        // Monitor stand
        drawRect(
            color = Color(0xFF232E47),
            topLeft = Offset(monX + monW * 0.30f, deskY - 12f),
            size = Size(monW * 0.40f, 12f)
        )
        // Outer CRT shell
        drawRoundRect(
            color = Color(0xFF2F3B57),
            topLeft = Offset(monX, monY),
            size = Size(monW, monH),
            cornerRadius = CornerRadius(10f, 10f)
        )
        // Glowing CRT screen
        val scrPad = 9f
        val scrX = monX + scrPad
        val scrY = monY + scrPad
        val scrW = monW - scrPad * 2f
        val scrH = monH - scrPad * 2.2f
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF0B2538), Color(0xFF114059)),
                start = Offset(scrX, scrY),
                end = Offset(scrX + scrW, scrY + scrH)
            ),
            topLeft = Offset(scrX, scrY),
            size = Size(scrW, scrH),
            cornerRadius = CornerRadius(5f, 5f)
        )
        // Animated digital waveform inside Navi CRT
        val wavePath = Path().apply {
            val steps = 28
            for (i in 0..steps) {
                val frac = i.toFloat() / steps
                val wx = scrX + 4f + frac * (scrW - 8f)
                val wy = scrY + scrH * 0.52f +
                    sin(frac * 4f * PI.toFloat() + t * 4.2f) * (scrH * 0.18f) *
                    sin(frac * PI.toFloat())
                if (i == 0) moveTo(wx, wy) else lineTo(wx, wy)
            }
        }
        drawPath(wavePath, color = NaviCyan, style = Stroke(width = 2.2f, cap = StrokeCap.Round))
        // Subtle CRT scanlines
        var scanY = scrY + 4f
        while (scanY < scrY + scrH) {
            drawLine(
                color = Color.Black.copy(alpha = 0.16f),
                start = Offset(scrX, scanY),
                end = Offset(scrX + scrW, scanY),
                strokeWidth = 1f
            )
            scanY += 4.5f
        }
        // Power LED on monitor
        drawCircle(
            color = HarmonyJade,
            radius = 2.5f,
            center = Offset(monX + monW - 14f, monY + monH - 6f)
        )

        // Small warm desk lamp
        val lampBaseX = deskX + deskW * 0.85f
        drawLine(
            color = Color(0xFF475577),
            start = Offset(lampBaseX, deskY),
            end = Offset(lampBaseX + 6f, deskY - 34f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF475577),
            start = Offset(lampBaseX + 6f, deskY - 34f),
            end = Offset(lampBaseX - 8f, deskY - 46f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = DeskAmber.copy(alpha = 0.9f),
            radius = 5.5f,
            center = Offset(lampBaseX - 10f, deskY - 44f)
        )

        // 5. Tangled Navi cables snaking across the bedroom floor
        for (c in 0..3) {
            val cablePath = Path().apply {
                val startX = deskX + 24f + c * 32f
                moveTo(startX, deskY + deskH)
                cubicTo(
                    startX - 45f, floorY - 12f,
                    startX - 110f - c * 28f, floorY + 26f + c * 10f,
                    w * 0.04f, floorY + 40f + c * 16f
                )
            }
            drawPath(
                cablePath,
                color = if (c == 1) NaviCyanDim.copy(alpha = 0.45f) else Color(0xFF1A2236),
                style = Stroke(width = if (c == 0) 3.2f else 2.2f, cap = StrokeCap.Round)
            )
        }

        // 6. Animated Lain standing beside her Navi computer!
        val spriteScale = (h / 520f).coerceIn(1.2f, 2.0f)
        val lainW = 36f * spriteScale
        val lainH = 66f * spriteScale
        val lainX = (deskX - lainW * 1.15f).coerceIn(w * 0.14f, w * 0.46f)
        val lainY = floorY - lainH + 2f
        val menuLain = PlayerState(
            x = lainX,
            y = lainY,
            facingRight = true,
            onGround = true,
            animTime = t,
            animState = LainAnimState.IDLE
        )
        drawLainGameSprite(menuLain, screenX = lainX, screenY = lainY, scale = spriteScale)

        // 7. Ambient floating dust & digital signal motes
        for (i in 0..15) {
            val fx = ((i * 97f + t * (8f + (i % 4) * 3f)) % w)
            val fy = ((i * 61f + sin(t * 0.7f + i) * 26f) % (floorY - 20f)).coerceAtLeast(18f)
            val alpha = 0.15f + 0.25f * ((sin(t * 1.5f + i * 0.8f) + 1f) * 0.5f)
            drawCircle(
                color = if (i % 3 == 0) NaviCyan.copy(alpha = alpha) else TextSecondary.copy(alpha = alpha * 0.7f),
                radius = 1.6f + (i % 3) * 0.7f,
                center = Offset(fx, fy)
            )
        }
    }
}
