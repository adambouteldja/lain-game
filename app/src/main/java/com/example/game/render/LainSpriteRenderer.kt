package com.example.game.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.data.LainOutfit
import com.example.game.model.LainAnimState
import com.example.game.model.LainExpression
import com.example.game.model.PlayerState
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.sin

/**
 * Original 2D anime-style Canvas sprite & portrait renderer for Lain Iwakura (Evolution 2.0).
 * Features 5 structurally distinct outfits (4 canon + 1 original Judo Gi),
 * 7 fluid animation states, and 4 facial expressions.
 */
object LainSpriteRenderer {

    fun DrawScope.drawLainGameSprite(
        player: PlayerState,
        screenX: Float,
        screenY: Float,
        scale: Float = 1f
    ) {
        val w = player.width * scale
        val h = player.height * scale
        val cx = screenX + w * 0.5f
        val cy = screenY + h * 0.5f

        val t = player.animTime
        val isBlinking = (t % 3.8f) > 3.66f
        val isRunning = player.animState == LainAnimState.RUNNING
        val isWalking = player.animState == LainAnimState.WALKING
        val isLanding = player.animState == LainAnimState.LANDING
        val moveCycle = when {
            isRunning -> t * 16.5f
            isWalking -> t * 11.2f
            else -> 0f
        }
        val breathBob = if (player.animState == LainAnimState.IDLE) sin(t * 2.8f) * 1.4f * scale else 0f
        val moveBob = when {
            isRunning -> abs(sin(moveCycle)) * 3.2f * scale
            isWalking -> abs(sin(moveCycle)) * 2.2f * scale
            else -> 0f
        }
        val squashScaleX = if (isLanding) 1.12f else if (player.animState == LainAnimState.JUMPING) 0.94f else 1f
        val squashScaleY = if (isLanding) 0.88f else if (player.animState == LainAnimState.JUMPING) 1.07f else 1f
        val runLeanDeg = if (isRunning) 8.5f else if (isWalking) 2.5f else 0f

        val jumpStretch = when (player.animState) {
            LainAnimState.JUMPING -> -2.2f * scale
            LainAnimState.FALLING -> 1.5f * scale
            LainAnimState.LANDING -> 3.0f * scale
            else -> 0f
        }
        val hairLag = when (player.animState) {
            LainAnimState.JUMPING -> 5.8f * scale
            LainAnimState.FALLING -> -5.8f * scale
            LainAnimState.RUNNING -> 5.2f * scale + sin(moveCycle) * 3.0f * scale
            LainAnimState.WALKING -> sin(moveCycle) * 2.8f * scale
            else -> sin(t * 2.1f) * 1.4f * scale
        }

        // Ground shadow
        if (player.onGround) {
            val shadowY = if (player.gravityInverted) screenY + 2f * scale else screenY + h - 1f * scale
            drawOval(
                color = Color.Black.copy(alpha = 0.36f),
                topLeft = Offset(cx - w * 0.44f * squashScaleX, shadowY - 3f * scale),
                size = Size(w * 0.88f * squashScaleX, 6f * scale)
            )
        }

        withTransform({
            translate(left = cx, top = cy)
            scale(
                scaleX = (if (player.facingRight) 1f else -1f) * squashScaleX,
                scaleY = (if (player.gravityInverted) -1f else 1f) * squashScaleY,
                pivot = Offset.Zero
            )
            if (runLeanDeg != 0f) {
                rotate(degrees = runLeanDeg, pivot = Offset(0f, 18f * scale))
            }
        }) {
            val s = scale
            val bodyOffsetY = breathBob - moveBob + jumpStretch

            val legSwing = when (player.animState) {
                LainAnimState.RUNNING -> sin(moveCycle) * 12.5f * s
                LainAnimState.WALKING -> sin(moveCycle) * 8.8f * s
                LainAnimState.JUMPING -> 6.0f * s
                LainAnimState.FALLING -> -3.8f * s
                else -> 0f
            }

            // Outfit palette
            val outfit = player.outfit
            val barefoot = outfit == LainOutfit.WIRED_CAMISOLE || outfit == LainOutfit.JUDO_GI
            val backLegColor = when (outfit) {
                LainOutfit.JUDO_GI -> Color(0xFFE0E5EE)
                LainOutfit.WIRED_CAMISOLE -> LainSkinShadow
                LainOutfit.CYBERIA_JACKET -> Color(0xFF282B36)
                else -> Color(0xFFD5D0C8)
            }
            val frontLegColor = when (outfit) {
                LainOutfit.JUDO_GI -> Color(0xFFF3F6FA)
                LainOutfit.WIRED_CAMISOLE -> LainSkin
                LainOutfit.CYBERIA_JACKET -> Color(0xFF333745)
                else -> Color(0xFFEBE7E0)
            }
            val legStroke = if (outfit == LainOutfit.JUDO_GI) 6.4f * s else 5.0f * s

            // Back leg
            drawLine(
                color = backLegColor,
                start = Offset(-4f * s, 14f * s + bodyOffsetY),
                end = Offset(-4f * s - legSwing, 28f * s),
                strokeWidth = legStroke,
                cap = StrokeCap.Round
            )
            if (!barefoot) {
                drawRoundRect(
                    color = Color(0xFF2B2226),
                    topLeft = Offset(-7.5f * s - legSwing, 26.5f * s),
                    size = Size(8.5f * s, 4.8f * s),
                    cornerRadius = CornerRadius(2.5f * s, 2.5f * s)
                )
            } else {
                drawOval(
                    color = LainSkinShadow,
                    topLeft = Offset(-7f * s - legSwing, 26.8f * s),
                    size = Size(7.2f * s, 4.0f * s)
                )
            }

            // Front leg
            drawLine(
                color = frontLegColor,
                start = Offset(4f * s, 14f * s + bodyOffsetY),
                end = Offset(4f * s + legSwing, 28f * s),
                strokeWidth = legStroke,
                cap = StrokeCap.Round
            )
            if (!barefoot) {
                drawRoundRect(
                    color = Color(0xFF34282E),
                    topLeft = Offset(1f * s + legSwing, 26.5f * s),
                    size = Size(9f * s, 4.8f * s),
                    cornerRadius = CornerRadius(2.5f * s, 2.5f * s)
                )
            } else {
                drawOval(
                    color = LainSkin,
                    topLeft = Offset(1.5f * s + legSwing, 26.8f * s),
                    size = Size(7.4f * s, 4.0f * s)
                )
            }

            // Lower garment (skirt or gi flap)
            val skirtFlare = if (player.animState == LainAnimState.JUMPING || player.animState == LainAnimState.FALLING) 3f * s else 0f
            val skirtPath = Path().apply {
                moveTo(-9.2f * s, 7f * s + bodyOffsetY)
                lineTo(9.2f * s, 7f * s + bodyOffsetY)
                lineTo(13.5f * s + skirtFlare, 18.8f * s + bodyOffsetY)
                lineTo(-13.5f * s - skirtFlare, 18.8f * s + bodyOffsetY)
                close()
            }
            when (outfit) {
                LainOutfit.HOME_SWEATER -> {
                    drawPath(skirtPath, color = LainSkirt)
                    drawLine(Color(0xFF1A1D28), Offset(-3f * s, 9f * s + bodyOffsetY), Offset(-4.5f * s, 18f * s + bodyOffsetY), 1.2f * s)
                    drawLine(Color(0xFF1A1D28), Offset(4f * s, 9f * s + bodyOffsetY), Offset(5.5f * s, 18f * s + bodyOffsetY), 1.2f * s)
                }
                LainOutfit.SCHOOL_UNIFORM -> {
                    // Dark green plaid skirt (Ref Image 5)
                    drawPath(skirtPath, color = Color(0xFF1A3125))
                    drawLine(Color(0xFF2D523E), Offset(-5f * s, 8f * s + bodyOffsetY), Offset(-7f * s, 18.5f * s + bodyOffsetY), 1.4f * s)
                    drawLine(Color(0xFF2D523E), Offset(2f * s, 8f * s + bodyOffsetY), Offset(3.5f * s, 18.5f * s + bodyOffsetY), 1.4f * s)
                    drawLine(Color(0xFF2D523E), Offset(-11f * s, 14f * s + bodyOffsetY), Offset(11f * s, 14f * s + bodyOffsetY), 1.1f * s)
                }
                LainOutfit.CYBERIA_JACKET -> {
                    // Dark skirt with crimson side trim (Ref Image 3)
                    drawPath(skirtPath, color = Color(0xFF1C1C24))
                    drawLine(Color(0xFFAE252F), Offset(9.5f * s, 9f * s + bodyOffsetY), Offset(12.2f * s + skirtFlare, 18.4f * s + bodyOffsetY), 2.4f * s)
                }
                LainOutfit.WIRED_CAMISOLE -> {
                    // Dark burgundy skirt (Ref Image 6)
                    drawPath(skirtPath, color = Color(0xFF542D3C))
                    drawLine(Color(0xFF2A161F), Offset(-9f * s, 8.5f * s + bodyOffsetY), Offset(9f * s, 8.5f * s + bodyOffsetY), 2.2f * s)
                }
                LainOutfit.JUDO_GI -> {
                    // White Judo gi lower jacket skirt + hanging black obi ends
                    drawPath(skirtPath, color = Color(0xFFEFF3F8))
                    drawPath(skirtPath, color = Color(0xFFBDC7D6), style = Stroke(width = 1f * s))
                }
            }

            // Back arm
            val sleeveBackColor = when (outfit) {
                LainOutfit.HOME_SWEATER -> LainSweaterShadow
                LainOutfit.SCHOOL_UNIFORM -> Color(0xFF313640)
                LainOutfit.CYBERIA_JACKET -> Color(0xFFAFA77F)
                LainOutfit.WIRED_CAMISOLE -> LainSkinShadow
                LainOutfit.JUDO_GI -> Color(0xFFD8E0EC)
            }
            val sleeveFrontColor = when (outfit) {
                LainOutfit.HOME_SWEATER -> LainSweater
                LainOutfit.SCHOOL_UNIFORM -> Color(0xFF434955)
                LainOutfit.CYBERIA_JACKET -> Color(0xFFCBC499)
                LainOutfit.WIRED_CAMISOLE -> LainSkin
                LainOutfit.JUDO_GI -> Color(0xFFF4F7FC)
            }
            val backArmSwing = -legSwing * 0.65f
            drawLine(
                color = sleeveBackColor,
                start = Offset(-6f * s, -4f * s + bodyOffsetY),
                end = Offset(-10f * s + backArmSwing, 8f * s + bodyOffsetY),
                strokeWidth = if (outfit == LainOutfit.WIRED_CAMISOLE) 4.2f * s else 5.5f * s,
                cap = StrokeCap.Round
            )

            // Torso by Outfit
            val torsoPath = Path().apply {
                moveTo(-10f * s, -7f * s + bodyOffsetY)
                lineTo(10f * s, -7f * s + bodyOffsetY)
                lineTo(11.5f * s, 9f * s + bodyOffsetY)
                lineTo(-11.5f * s, 9f * s + bodyOffsetY)
                close()
            }
            when (outfit) {
                LainOutfit.HOME_SWEATER -> {
                    drawPath(torsoPath, color = LainSweater)
                    drawPath(
                        Path().apply {
                            moveTo(-10f * s, -6f * s + bodyOffsetY)
                            lineTo(-3f * s, -6f * s + bodyOffsetY)
                            lineTo(-5f * s, 9f * s + bodyOffsetY)
                            lineTo(-11.5f * s, 9f * s + bodyOffsetY)
                            close()
                        },
                        color = LainSweaterShadow.copy(alpha = 0.5f)
                    )
                    val collarPath = Path().apply {
                        moveTo(-8.5f * s, -7.5f * s + bodyOffsetY)
                        lineTo(0f, -2.5f * s + bodyOffsetY)
                        lineTo(8.5f * s, -7.5f * s + bodyOffsetY)
                        lineTo(6.5f * s, -10f * s + bodyOffsetY)
                        lineTo(-6.5f * s, -10f * s + bodyOffsetY)
                        close()
                    }
                    drawPath(collarPath, color = Color(0xFFD5CCC2))
                    drawPath(collarPath, color = LainSweaterShadow, style = Stroke(width = 1f * s))
                }
                LainOutfit.SCHOOL_UNIFORM -> {
                    // Charcoal blazer + white V-shirt + crimson ribbon tie (Ref Image 5)
                    drawPath(torsoPath, color = Color(0xFF424854))
                    val shirtV = Path().apply {
                        moveTo(-4.5f * s, -7.5f * s + bodyOffsetY)
                        lineTo(4.5f * s, -7.5f * s + bodyOffsetY)
                        lineTo(0f, 0.5f * s + bodyOffsetY)
                        close()
                    }
                    drawPath(shirtV, color = Color(0xFFF3F4F6))
                    // Crimson ribbon bow
                    drawCircle(Color(0xFFC2242E), 2.1f * s, Offset(0f, -4.0f * s + bodyOffsetY))
                    drawLine(Color(0xFFC2242E), Offset(0f, -4f * s + bodyOffsetY), Offset(-3.2f * s, 1.5f * s + bodyOffsetY), 1.8f * s, StrokeCap.Round)
                    drawLine(Color(0xFFC2242E), Offset(0f, -4f * s + bodyOffsetY), Offset(3.2f * s, 1.5f * s + bodyOffsetY), 1.8f * s, StrokeCap.Round)
                    // Blazer buttons
                    drawCircle(Color(0xFF232730), 1.1f * s, Offset(3.2f * s, 3.5f * s + bodyOffsetY))
                    drawCircle(Color(0xFF232730), 1.1f * s, Offset(3.2f * s, 6.5f * s + bodyOffsetY))
                }
                LainOutfit.CYBERIA_JACKET -> {
                    // Pale olive-cream jacket with standing collar (Ref Image 3)
                    drawPath(torsoPath, color = Color(0xFFCCC59A))
                    drawLine(Color(0xFF9C946B), Offset(1.5f * s, -7f * s + bodyOffsetY), Offset(1.5f * s, 9f * s + bodyOffsetY), 1.3f * s)
                    drawRoundRect(
                        color = Color(0xFFD9D3AE),
                        topLeft = Offset(-7.5f * s, -9.8f * s + bodyOffsetY),
                        size = Size(15f * s, 3.8f * s),
                        cornerRadius = CornerRadius(2f * s, 2f * s)
                    )
                }
                LainOutfit.WIRED_CAMISOLE -> {
                    // Bare shoulders + lilac-gray thin-strap camisole + tiny bow (Ref Image 6)
                    drawRoundRect(
                        color = LainSkin,
                        topLeft = Offset(-8.5f * s, -8.5f * s + bodyOffsetY),
                        size = Size(17f * s, 5f * s),
                        cornerRadius = CornerRadius(2.5f * s, 2.5f * s)
                    )
                    val camiPath = Path().apply {
                        moveTo(-8.8f * s, -4.5f * s + bodyOffsetY)
                        lineTo(8.8f * s, -4.5f * s + bodyOffsetY)
                        lineTo(10.5f * s, 8.8f * s + bodyOffsetY)
                        lineTo(-10.5f * s, 8.8f * s + bodyOffsetY)
                        close()
                    }
                    drawPath(camiPath, color = Color(0xFF8B8EA8))
                    // Thin straps
                    drawLine(Color(0xFF3E3F52), Offset(-6f * s, -8.2f * s + bodyOffsetY), Offset(-6f * s, -4.2f * s + bodyOffsetY), 1.3f * s)
                    drawLine(Color(0xFF3E3F52), Offset(6f * s, -8.2f * s + bodyOffsetY), Offset(6f * s, -4.2f * s + bodyOffsetY), 1.3f * s)
                    // Chest tiny dark-red ribbon bow
                    drawCircle(Color(0xFF6B2D3C), 1.5f * s, Offset(1.2f * s, -2.0f * s + bodyOffsetY))
                    drawLine(Color(0xFF6B2D3C), Offset(1.2f * s, -2f * s + bodyOffsetY), Offset(-0.5f * s, 1.5f * s + bodyOffsetY), 1.1f * s)
                    drawLine(Color(0xFF6B2D3C), Offset(1.2f * s, -2f * s + bodyOffsetY), Offset(2.8f * s, 1.5f * s + bodyOffsetY), 1.1f * s)
                }
                LainOutfit.JUDO_GI -> {
                    // Crisp white Judo Gi jacket with crossed lapels & black belt (Obi)
                    drawPath(torsoPath, color = Color(0xFFF3F6FB))
                    // Left-over-right lapel crossover
                    drawLine(Color(0xFFCBD4E1), Offset(-6.5f * s, -8f * s + bodyOffsetY), Offset(6.5f * s, 5.5f * s + bodyOffsetY), 2.2f * s)
                    drawLine(Color(0xFFB8C4D4), Offset(6.5f * s, -8f * s + bodyOffsetY), Offset(-4.5f * s, 4.0f * s + bodyOffsetY), 1.8f * s)
                    // Thick black belt (Obi)
                    drawRoundRect(
                        color = Color(0xFF14171F),
                        topLeft = Offset(-11.4f * s, 4.6f * s + bodyOffsetY),
                        size = Size(22.8f * s, 4.0f * s),
                        cornerRadius = CornerRadius(1.5f * s, 1.5f * s)
                    )
                    // Black belt knot & tails
                    drawCircle(Color(0xFF0E1016), 2.5f * s, Offset(2f * s, 6.6f * s + bodyOffsetY))
                    drawLine(Color(0xFF14171F), Offset(1.5f * s, 7.5f * s + bodyOffsetY), Offset(-2.5f * s, 16.5f * s + bodyOffsetY), 2.6f * s, StrokeCap.Round)
                    drawLine(Color(0xFF14171F), Offset(2.5f * s, 7.5f * s + bodyOffsetY), Offset(6.5f * s, 17.0f * s + bodyOffsetY), 2.6f * s, StrokeCap.Round)
                }
            }

            // Front arm (reaches forward with glowing cyan pulse when INTERACTING!)
            if (player.animState == LainAnimState.INTERACTING) {
                drawLine(
                    color = sleeveFrontColor,
                    start = Offset(5f * s, -3f * s + bodyOffsetY),
                    end = Offset(19f * s, -5f * s + bodyOffsetY),
                    strokeWidth = if (outfit == LainOutfit.WIRED_CAMISOLE) 4.2f * s else 5.6f * s,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = LainSkin,
                    radius = 2.6f * s,
                    center = Offset(20.5f * s, -5f * s + bodyOffsetY)
                )
                val pulse = 7f * s + sin(t * 24f) * 2.5f * s
                drawCircle(
                    color = NaviCyan.copy(alpha = 0.78f),
                    radius = pulse,
                    center = Offset(22f * s, -5f * s + bodyOffsetY),
                    style = Stroke(width = 1.8f * s)
                )
                drawCircle(
                    color = NaviCyan.copy(alpha = 0.25f),
                    radius = pulse * 1.45f,
                    center = Offset(22f * s, -5f * s + bodyOffsetY)
                )
            } else {
                val frontArmSwing = legSwing * 0.65f
                drawLine(
                    color = sleeveFrontColor,
                    start = Offset(4f * s, -4f * s + bodyOffsetY),
                    end = Offset(8f * s + frontArmSwing, 8f * s + bodyOffsetY),
                    strokeWidth = if (outfit == LainOutfit.WIRED_CAMISOLE) 4.2f * s else 5.6f * s,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = LainSkin,
                    radius = 2.4f * s,
                    center = Offset(8.5f * s + frontArmSwing, 9.2f * s + bodyOffsetY)
                )
            }

            // Head & Hair
            val headCenterY = -19.5f * s + bodyOffsetY

            val backHair = Path().apply {
                moveTo(-14.5f * s, headCenterY - 2f * s)
                cubicTo(
                    -15f * s, headCenterY - 16f * s,
                    15f * s, headCenterY - 16f * s,
                    14.5f * s, headCenterY - 1f * s
                )
                lineTo(13.5f * s, headCenterY + 12f * s)
                lineTo(-14f * s, headCenterY + 11.5f * s)
                close()
            }
            drawPath(backHair, color = LainHairDark)

            // Face oval
            drawOval(
                color = LainSkin,
                topLeft = Offset(-10.5f * s, headCenterY - 10.5f * s),
                size = Size(21f * s, 22f * s)
            )
            drawArc(
                color = LainSkinShadow.copy(alpha = 0.55f),
                startAngle = 25f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(-9.5f * s, headCenterY - 9f * s),
                size = Size(19f * s, 20f * s),
                style = Stroke(width = 1.6f * s)
            )

            // Expressive warm brown anime eyes with expression variations
            val eyeY = headCenterY + 0.8f * s
            val eye1X = -2.2f * s
            val eye2X = 6.2f * s
            val eyeHScale = when (player.expression) {
                LainExpression.CURIOUS -> 1.14f
                LainExpression.FOCUSED -> 0.84f
                else -> 1f
            }
            if (isBlinking) {
                for (ex in listOf(eye1X, eye2X)) {
                    drawLine(
                        color = LainHairDark,
                        start = Offset(ex - 2.3f * s, eyeY),
                        end = Offset(ex + 2.3f * s, eyeY),
                        strokeWidth = 1.5f * s,
                        cap = StrokeCap.Round
                    )
                }
            } else {
                for (ex in listOf(eye1X, eye2X)) {
                    drawOval(
                        color = Color.White,
                        topLeft = Offset(ex - 2.7f * s, eyeY - 2.4f * s * eyeHScale),
                        size = Size(5.4f * s, 4.8f * s * eyeHScale)
                    )
                    drawCircle(
                        color = LainEyes,
                        radius = 2.0f * s * eyeHScale.coerceAtMost(1.05f),
                        center = Offset(ex + 0.4f * s, eyeY)
                    )
                    drawCircle(
                        color = Color(0xFF23130E),
                        radius = 1.0f * s,
                        center = Offset(ex + 0.5f * s, eyeY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 0.75f * s,
                        center = Offset(ex + 1.1f * s, eyeY - 0.8f * s)
                    )
                    val browTilt = if (player.expression == LainExpression.FOCUSED) 0.5f * s else 0f
                    drawLine(
                        color = Color(0xFF251417),
                        start = Offset(ex - 2.8f * s, eyeY - 2.3f * s * eyeHScale - browTilt),
                        end = Offset(ex + 2.8f * s, eyeY - 2.1f * s * eyeHScale + browTilt),
                        strokeWidth = 1.4f * s,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Expression-driven mouth
            when (player.expression) {
                LainExpression.HAPPY -> {
                    drawArc(
                        color = Color(0xFF8C6259),
                        startAngle = 15f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(0.6f * s, headCenterY + 5.6f * s),
                        size = Size(3.6f * s, 2.4f * s),
                        style = Stroke(width = 1.2f * s, cap = StrokeCap.Round)
                    )
                }
                LainExpression.CURIOUS -> {
                    drawCircle(
                        color = Color(0xFF8C6259),
                        radius = 1.05f * s,
                        center = Offset(2.3f * s, headCenterY + 7.2f * s)
                    )
                }
                else -> {
                    drawLine(
                        color = Color(0xFF8C6259),
                        start = Offset(1.2f * s, headCenterY + 7.2f * s),
                        end = Offset(3.4f * s, headCenterY + 7.2f * s),
                        strokeWidth = 1.1f * s,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Choppy uneven bangs
            val bangsPath = Path().apply {
                moveTo(-13.5f * s, headCenterY - 4f * s)
                cubicTo(
                    -13f * s, headCenterY - 15.5f * s,
                    13.5f * s, headCenterY - 15.5f * s,
                    13.5f * s, headCenterY - 4f * s
                )
                lineTo(10.5f * s, headCenterY - 2.5f * s)
                lineTo(8.5f * s, headCenterY - 6.8f * s)
                lineTo(5.8f * s, headCenterY - 2.2f * s)
                lineTo(2.5f * s, headCenterY - 7.2f * s)
                lineTo(-0.8f * s, headCenterY - 2.0f * s)
                lineTo(-4.4f * s, headCenterY - 6.5f * s)
                lineTo(-7.2f * s, headCenterY - 2.5f * s)
                lineTo(-10.5f * s, headCenterY - 5.5f * s)
                close()
            }
            drawPath(bangsPath, color = LainHairDark)
            drawArc(
                color = LainHairHighlight.copy(alpha = 0.65f),
                startAngle = 210f,
                sweepAngle = 95f,
                useCenter = false,
                topLeft = Offset(-10f * s, headCenterY - 13f * s),
                size = Size(20f * s, 10f * s),
                style = Stroke(width = 1.6f * s, cap = StrokeCap.Round)
            )

            // Iconic long thin hair strand on Lain's left side with sway + silver-ivory X hairpin
            val strandPath = Path().apply {
                moveTo(-11.5f * s, headCenterY - 3f * s)
                lineTo(-7.2f * s, headCenterY - 2f * s)
                quadraticTo(
                    -9.5f * s - hairLag * 0.45f,
                    headCenterY + 13f * s,
                    -10.2f * s - hairLag,
                    headCenterY + 25f * s
                )
                lineTo(-13.2f * s - hairLag, headCenterY + 23f * s)
                quadraticTo(
                    -12.5f * s - hairLag * 0.45f,
                    headCenterY + 11f * s,
                    -11.5f * s,
                    headCenterY - 3f * s
                )
                close()
            }
            drawPath(strandPath, color = LainHairDark)

            val pinX = -10.3f * s
            val pinY = headCenterY + 4.8f * s
            drawLine(
                color = LainHairpinIvory,
                start = Offset(pinX - 3.0f * s, pinY - 3.0f * s),
                end = Offset(pinX + 3.0f * s, pinY + 3.0f * s),
                strokeWidth = 1.75f * s,
                cap = StrokeCap.Round
            )
            drawLine(
                color = LainHairpinIvory,
                start = Offset(pinX + 3.0f * s, pinY - 3.0f * s),
                end = Offset(pinX - 3.0f * s, pinY + 3.0f * s),
                strokeWidth = 1.75f * s,
                cap = StrokeCap.Round
            )
        }
    }

    /**
     * Expressive anime close-up portrait of Lain for dialogue whispers, Desktop, and Wardrobe.
     */
    fun DrawScope.drawLainPortrait(
        center: Offset,
        radius: Float,
        timeSec: Float,
        outfit: LainOutfit = LainOutfit.HOME_SWEATER,
        expression: LainExpression = LainExpression.NEUTRAL
    ) {
        val blink = (timeSec % 4.1f) > 3.96f
        val r = radius

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1F2D49), Color(0xFF0C1220)),
                center = center,
                radius = r
            ),
            radius = r,
            center = center
        )
        drawCircle(
            color = NaviCyan.copy(alpha = 0.65f),
            radius = r,
            center = center,
            style = Stroke(width = 2f)
        )

        withTransform({ translate(center.x, center.y) }) {
            // Back hair
            drawPath(
                Path().apply {
                    moveTo(-r * 0.66f, -r * 0.05f)
                    cubicTo(-r * 0.68f, -r * 0.78f, r * 0.68f, -r * 0.78f, r * 0.66f, -r * 0.05f)
                    lineTo(r * 0.62f, r * 0.76f)
                    lineTo(r * 0.42f, r * 0.76f)
                    lineTo(r * 0.40f, r * 0.38f)
                    lineTo(-r * 0.50f, r * 0.40f)
                    lineTo(-r * 0.60f, r * 0.25f)
                    close()
                },
                color = LainHairDark
            )

            // Outfit shoulders
            val shoulderColor = when (outfit) {
                LainOutfit.HOME_SWEATER -> LainSweater
                LainOutfit.SCHOOL_UNIFORM -> Color(0xFF424854)
                LainOutfit.CYBERIA_JACKET -> Color(0xFFCCC59A)
                LainOutfit.WIRED_CAMISOLE -> Color(0xFF8B8EA8)
                LainOutfit.JUDO_GI -> Color(0xFFF3F6FB)
            }
            drawPath(
                Path().apply {
                    moveTo(-r * 0.48f, r * 0.56f)
                    lineTo(0f, r * 0.72f)
                    lineTo(r * 0.48f, r * 0.56f)
                    lineTo(r * 0.58f, r * 0.88f)
                    lineTo(-r * 0.58f, r * 0.88f)
                    close()
                },
                color = shoulderColor
            )
            if (outfit == LainOutfit.SCHOOL_UNIFORM) {
                drawCircle(Color(0xFFC2242E), r * 0.075f, Offset(0f, r * 0.70f))
            } else if (outfit == LainOutfit.JUDO_GI) {
                drawLine(Color(0xFF1A1E28), Offset(-r * 0.28f, r * 0.58f), Offset(r * 0.22f, r * 0.86f), r * 0.05f)
            }

            // Face
            drawOval(
                color = LainSkin,
                topLeft = Offset(-r * 0.44f, -r * 0.42f),
                size = Size(r * 0.88f, r * 0.94f)
            )

            // Eyes
            val ey = r * 0.03f
            val exOff = r * 0.20f
            if (blink) {
                for (sign in listOf(-1f, 1f)) {
                    drawLine(
                        color = LainHairDark,
                        start = Offset(sign * exOff - r * 0.11f, ey),
                        end = Offset(sign * exOff + r * 0.11f, ey),
                        strokeWidth = r * 0.06f,
                        cap = StrokeCap.Round
                    )
                }
            } else {
                for (sign in listOf(-1f, 1f)) {
                    val ex = sign * exOff
                    drawOval(
                        color = Color.White,
                        topLeft = Offset(ex - r * 0.11f, ey - r * 0.09f),
                        size = Size(r * 0.22f, r * 0.18f)
                    )
                    drawCircle(color = LainEyes, radius = r * 0.075f, center = Offset(ex, ey))
                    drawCircle(color = Color(0xFF22120E), radius = r * 0.036f, center = Offset(ex, ey))
                    drawCircle(
                        color = Color.White,
                        radius = r * 0.026f,
                        center = Offset(ex + r * 0.026f, ey - r * 0.026f)
                    )
                    drawLine(
                        color = Color(0xFF231317),
                        start = Offset(ex - r * 0.11f, ey - r * 0.085f),
                        end = Offset(ex + r * 0.11f, ey - r * 0.085f),
                        strokeWidth = r * 0.045f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Mouth
            if (expression == LainExpression.HAPPY) {
                drawArc(
                    color = Color(0xFF8C6259),
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(-r * 0.08f, r * 0.25f),
                    size = Size(r * 0.16f, r * 0.09f),
                    style = Stroke(width = r * 0.035f, cap = StrokeCap.Round)
                )
            } else {
                drawLine(
                    color = Color(0xFF8C6259),
                    start = Offset(-r * 0.045f, r * 0.30f),
                    end = Offset(r * 0.045f, r * 0.30f),
                    strokeWidth = r * 0.035f,
                    cap = StrokeCap.Round
                )
            }

            // Choppy bangs
            drawPath(
                Path().apply {
                    moveTo(-r * 0.56f, -r * 0.14f)
                    cubicTo(-r * 0.56f, -r * 0.68f, r * 0.56f, -r * 0.68f, r * 0.56f, -r * 0.14f)
                    lineTo(r * 0.36f, -r * 0.12f)
                    lineTo(r * 0.25f, -r * 0.32f)
                    lineTo(r * 0.12f, -r * 0.10f)
                    lineTo(-r * 0.02f, -r * 0.34f)
                    lineTo(-r * 0.16f, -r * 0.10f)
                    lineTo(-r * 0.31f, -r * 0.30f)
                    lineTo(-r * 0.44f, -r * 0.12f)
                    close()
                },
                color = LainHairDark
            )

            // Signature X hairpin on viewer's right (Lain's left lock)
            val px = r * 0.49f
            val py = r * 0.18f
            drawLine(
                color = LainHairpinIvory,
                start = Offset(px - r * 0.10f, py - r * 0.10f),
                end = Offset(px + r * 0.10f, py + r * 0.10f),
                strokeWidth = r * 0.06f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = LainHairpinIvory,
                start = Offset(px + r * 0.10f, py - r * 0.10f),
                end = Offset(px - r * 0.10f, py + r * 0.10f),
                strokeWidth = r * 0.06f,
                cap = StrokeCap.Round
            )
        }
    }
}
