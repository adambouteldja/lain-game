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
import com.example.data.LainOutfit
import com.example.game.model.LainAnimState
import com.example.game.model.PlayerState
import com.example.game.render.LainSpriteRenderer.drawLainGameSprite
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.sin

/**
 * Reference-reconstructed Main Menu Canvas of Lain's Upgraded Bedroom at night
 * (based on verified Series Reference Images 1 & 4):
 * - Deep cobalt nocturnal shadows & slatted closet doors
 * - Ribbed overhead Navi cooling hoses descending from ceiling
 * - Left dome-vented Navi cooling tower with exposed green PCB cards & glowing CRT
 * - Central bright white-cyan Navi workstation & keyboard
 * - Right 3-tier stacked hardware rig & angled CRT
 * - Far-right pale cyan night window with silhouette teddy bear on the windowsill
 * - Tangled black cables & open hardware manuals across the bedroom floor
 */
@Composable
fun LainsRoomBackdrop(
    outfit: LainOutfit = LainOutfit.HOME_SWEATER,
    modifier: Modifier = Modifier
) {
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
        val floorY = h * 0.72f

        // 1. Deep cobalt nocturnal walls gradient (matching Ref Images 1 & 4)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF0A1021), Color(0xFF172340), Color(0xFF0E1528))
            )
        )

        // 2. Slatted closet doors behind central Navi desk (Ref Image 4)
        val closetX = w * 0.24f
        val closetW = w * 0.36f
        drawRect(
            color = Color(0xFF131D34),
            topLeft = Offset(closetX, h * 0.04f),
            size = Size(closetW, floorY - h * 0.04f)
        )
        var slatY = h * 0.08f
        while (slatY < floorY - 24f) {
            drawLine(
                color = Color(0xFF0B1120),
                start = Offset(closetX + 10f, slatY),
                end = Offset(closetX + closetW - 10f, slatY),
                strokeWidth = 3f
            )
            slatY += 14f
        }
        drawLine(
            color = Color(0xFF090E1A),
            start = Offset(closetX + closetW * 0.5f, h * 0.04f),
            end = Offset(closetX + closetW * 0.5f, floorY),
            strokeWidth = 3f
        )

        // 3. Far-Right Pale Cyan Night Window with Powerlines & Teddy Bear on Windowsill (Ref Image 4!)
        val winW = (w * 0.24f).coerceIn(96f, 220f)
        val winH = (h * 0.30f).coerceIn(110f, 220f)
        val winX = w - winW - w * 0.04f
        val winY = h * 0.08f

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF4284A3), Color(0xFF285978), Color(0xFF193952)),
                startY = winY,
                endY = winY + winH
            ),
            topLeft = Offset(winX, winY),
            size = Size(winW, winH),
            cornerRadius = CornerRadius(4f, 4f)
        )
        // Distant powerlines outside night window
        for (lineIdx in 0..1) {
            val ly = winY + winH * (0.28f + lineIdx * 0.16f)
            val wirePath = Path().apply {
                moveTo(winX, ly - 5f)
                quadraticTo(winX + winW * 0.5f, ly + 12f, winX + winW, ly - 3f)
            }
            drawPath(wirePath, color = Color(0xFF0E1B2B), style = Stroke(width = 1.6f))
        }
        // Silhouette Teddy Bear sitting on the right windowsill!
        val bearX = winX + winW * 0.72f
        val bearY = winY + winH - 18f
        drawOval(Color(0xFF122338), Offset(bearX - 18f, bearY - 12f), Size(36f, 30f)) // body
        drawCircle(Color(0xFF122338), 15f, Offset(bearX, bearY - 22f)) // head
        drawCircle(Color(0xFF122338), 6f, Offset(bearX - 11f, bearY - 34f)) // left ear
        drawCircle(Color(0xFF122338), 6f, Offset(bearX + 11f, bearY - 34f)) // right ear
        drawOval(Color(0xFF122338), Offset(bearX - 24f, bearY + 4f), Size(14f, 10f)) // foot
        // Window frame & sill
        drawRoundRect(
            color = Color(0xFF152036),
            topLeft = Offset(winX, winY),
            size = Size(winW, winH),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = 5f)
        )
        drawLine(Color(0xFF152036), Offset(winX + winW * 0.45f, winY), Offset(winX + winW * 0.45f, winY + winH), 3.5f)
        drawRect(Color(0xFF1D2B46), Offset(winX - 6f, winY + winH), Size(winW + 12f, 8f))

        // 4. Curved Ribbed Overhead Navi Cooling Hoses from Ceiling (Ref Image 4)
        val hosePath = Path().apply {
            moveTo(w * 0.14f, 0f)
            cubicTo(w * 0.22f, h * 0.34f, w * 0.38f, h * 0.34f, w * 0.46f, floorY - 130f)
        }
        drawPath(hosePath, color = Color(0xFF28354F), style = Stroke(width = 16f, cap = StrokeCap.Round))
        drawPath(
            hosePath,
            color = Color(0xFF0E1526),
            style = Stroke(
                width = 14f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
            )
        )

        // 5. Floor plane
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF141E34), Color(0xFF0B111F)),
                startY = floorY,
                endY = h
            ),
            topLeft = Offset(0f, floorY),
            size = Size(w, h - floorY)
        )
        drawLine(WiredBorder.copy(alpha = 0.55f), Offset(0f, floorY), Offset(w, floorY), 2f)

        // 6. Left Dome-Vented Navi Cooling Tower with Exposed Green PCB Cards + CRT (Ref Images 1 & 4)
        val towerW = (w * 0.15f).coerceIn(66f, 118f)
        val towerH = (h * 0.24f).coerceIn(95f, 150f)
        val towerX = w * 0.04f
        val towerY = floorY - towerH + 6f
        drawRoundRect(Color(0xFF424D64), Offset(towerX, towerY), Size(towerW, towerH), CornerRadius(5f, 5f))
        // Exposed green PCB cards on side
        for (p in 0..2) {
            drawRect(
                Color(0xFF204839),
                Offset(towerX + 6f + p * 10f, towerY + 14f),
                Size(6f, towerH * 0.42f)
            )
        }
        // Signature rounded intake dome vent
        drawArc(
            color = Color(0xFF697691),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(towerX + towerW * 0.35f, towerY + towerH * 0.38f),
            size = Size(towerW * 0.58f, towerH * 0.42f)
        )
        drawArc(
            color = Color(0xFF162033),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(towerX + towerW * 0.43f, towerY + towerH * 0.48f),
            size = Size(towerW * 0.42f, towerH * 0.26f)
        )
        // Ice-blue CRT sitting atop left cooling tower
        val leftMonW = towerW * 0.78f
        val leftMonH = leftMonW * 0.72f
        val leftMonX = towerX + towerW * 0.10f
        val leftMonY = towerY - leftMonH - 4f
        drawRoundRect(Color(0xFF596784), Offset(leftMonX, leftMonY), Size(leftMonW, leftMonH), CornerRadius(6f, 6f))
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF6CE2FF), Color(0xFF3992BA))),
            topLeft = Offset(leftMonX + 6f, leftMonY + 6f),
            size = Size(leftMonW - 12f, leftMonH - 12f),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // 7. Central Desk & Bright White-Cyan Navi Workstation (Ref Image 4)
        val deskW = (w * 0.36f).coerceIn(155f, 310f)
        val deskH = 14f
        val deskX = w * 0.46f
        val deskY = floorY - 76f

        // Intense white-cyan Navi CRT glow halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF9DF2FF).copy(alpha = 0.28f), NaviCyan.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(deskX + deskW * 0.48f, deskY - 48f),
                radius = deskW * 0.95f
            ),
            radius = deskW * 0.95f,
            center = Offset(deskX + deskW * 0.48f, deskY - 48f)
        )

        drawRect(Color(0xFF1B2438), Offset(deskX + 12f, deskY + deskH), Size(10f, floorY - (deskY + deskH)))
        drawRect(Color(0xFF1B2438), Offset(deskX + deskW - 22f, deskY + deskH), Size(10f, floorY - (deskY + deskH)))
        drawRoundRect(Color(0xFF28344E), Offset(deskX, deskY), Size(deskW, deskH), CornerRadius(4f, 4f))

        val monW = (deskW * 0.54f).coerceIn(88f, 144f)
        val monH = monW * 0.78f
        val monX = deskX + deskW * 0.22f
        val monY = deskY - monH - 10f
        drawRect(Color(0xFF25324E), Offset(monX + monW * 0.30f, deskY - 10f), Size(monW * 0.40f, 10f))
        drawRoundRect(Color(0xFF4C5975), Offset(monX, monY), Size(monW, monH), CornerRadius(9f, 9f))

        val scrPad = 8f
        val scrX = monX + scrPad
        val scrY = monY + scrPad
        val scrW = monW - scrPad * 2f
        val scrH = monH - scrPad * 2.1f
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF12415E), Color(0xFF5ADDF7), Color(0xFFD6FAFF)),
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
                    sin(frac * 4f * PI.toFloat() + t * 4.2f) * (scrH * 0.18f) * sin(frac * PI.toFloat())
                if (i == 0) moveTo(wx, wy) else lineTo(wx, wy)
            }
        }
        drawPath(wavePath, color = Color(0xFF0A2438), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
        drawCircle(HarmonyJade, 2.5f, Offset(monX + monW - 14f, monY + monH - 5f))

        // 8. Right 3-Tier Stacked Hardware Rig & Small CRT (Ref Images 1 & 4)
        val stackW = (w * 0.13f).coerceIn(58f, 106f)
        val stackX = w - stackW - w * 0.04f
        val tierH = 18f
        for (tier in 0..2) {
            val ty = floorY - (tier + 1) * (tierH + 3f)
            drawRoundRect(Color(0xFF47546E), Offset(stackX, ty), Size(stackW, tierH), CornerRadius(3f, 3f))
            drawLine(Color(0xFF232C42), Offset(stackX + 8f, ty + 9f), Offset(stackX + stackW * 0.55f, ty + 9f), 2f)
            drawCircle(if (tier == 1) HarmonyJade else DeskAmber, 2f, Offset(stackX + stackW - 10f, ty + 9f))
        }

        // 9. Thick Black Floor Cables & Open Hardware Manual (Ref Image 1)
        for (c in 0..4) {
            val cablePath = Path().apply {
                val startX = deskX + 18f + c * 28f
                moveTo(startX, deskY + deskH)
                cubicTo(
                    startX - 50f, floorY - 8f,
                    startX - 130f - c * 24f, floorY + 28f + c * 9f,
                    w * 0.02f, floorY + 34f + c * 15f
                )
            }
            drawPath(
                cablePath,
                color = if (c == 1) NaviCyanDim.copy(alpha = 0.45f) else Color(0xFF0A0F1A),
                style = Stroke(width = if (c % 2 == 0) 3.8f else 2.4f, cap = StrokeCap.Round)
            )
        }
        // Open technical manual + green PCB on floor
        val bookX = w * 0.25f
        val bookY = floorY + 26f
        drawRoundRect(Color(0xFF9EAAC4), Offset(bookX, bookY), Size(32f, 18f), CornerRadius(2f, 2f))
        drawLine(Color(0xFF283247), Offset(bookX + 16f, bookY), Offset(bookX + 16f, bookY + 18f), 1.6f)
        drawRect(Color(0xFF2D6B51), Offset(bookX - 24f, bookY + 8f), Size(16f, 10f))

        // 10. Animated Lain standing beside her Navi workstation
        val spriteScale = (h / 520f).coerceIn(1.2f, 1.95f)
        val lainW = 36f * spriteScale
        val lainH = 66f * spriteScale
        val lainX = (deskX - lainW * 1.12f).coerceIn(w * 0.20f, w * 0.45f)
        val lainY = floorY - lainH + 2f
        val menuLain = PlayerState(
            x = lainX,
            y = lainY,
            facingRight = true,
            onGround = true,
            animTime = t,
            animState = LainAnimState.IDLE,
            outfit = outfit
        )
        drawLainGameSprite(menuLain, screenX = lainX, screenY = lainY, scale = spriteScale)

        // 11. Ambient floating dust & digital signal motes
        for (i in 0..14) {
            val fx = ((i * 97f + t * (8f + (i % 4) * 3f)) % w)
            val fy = ((i * 61f + sin(t * 0.7f + i) * 26f) % (floorY - 20f)).coerceAtLeast(18f)
            val alpha = 0.16f + 0.24f * ((sin(t * 1.5f + i * 0.8f) + 1f) * 0.5f)
            drawCircle(
                color = if (i % 3 == 0) NaviCyan.copy(alpha = alpha) else TextSecondary.copy(alpha = alpha * 0.7f),
                radius = 1.6f + (i % 3) * 0.7f,
                center = Offset(fx, fy)
            )
        }
    }
}
