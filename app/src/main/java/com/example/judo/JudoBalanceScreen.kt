package com.example.judo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.audio.SoundEffect
import com.example.data.JudoTrialId
import com.example.data.LainOutfit
import com.example.game.model.LainAnimState
import com.example.game.model.LainExpression
import com.example.game.model.PlayerState
import com.example.game.render.LainSpriteRenderer.drawLainGameSprite
import com.example.i18n.LocalStrings
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun JudoBalanceScreen(
    trialId: JudoTrialId,
    bestScore: Int,
    onPlaySfx: (SoundEffect) -> Unit,
    onVictory: (score: Int) -> Unit,
    onExit: () -> Unit
) {
    BackHandler { onExit() }

    val strings = LocalStrings.current
    var state by remember(trialId) { mutableStateOf(JudoBalanceEngine.createInitial(trialId)) }
    var leanLeft by remember { mutableStateOf(false) }
    var leanRight by remember { mutableStateOf(false) }
    var braceCenter by remember { mutableStateOf(false) }
    var redirectLeftQueued by remember { mutableStateOf(false) }
    var redirectRightQueued by remember { mutableStateOf(false) }
    var ukemiQueued by remember { mutableStateOf(false) }
    var victoryReported by remember(trialId) { mutableStateOf(false) }

    val supportsUkemi = trialId == JudoTrialId.UKEMI_REACTION || trialId == JudoTrialId.EQUILIBRIUM_FLOW

    LaunchedEffect(state.status, trialId) {
        if (state.status != JudoStatus.ACTIVE) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        while (state.status == JudoStatus.ACTIVE) {
            val now = withFrameNanos { it }
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
            lastNanos = now

            val prevFeedback = state.feedback
            val next = JudoBalanceEngine.step(
                state = state,
                input = JudoInput(
                    leanLeft = leanLeft,
                    leanRight = leanRight,
                    braceCenter = braceCenter,
                    redirectLeftJustPressed = redirectLeftQueued,
                    redirectRightJustPressed = redirectRightQueued,
                    ukemiJustPressed = ukemiQueued
                ),
                dt = dt
            )
            redirectLeftQueued = false
            redirectRightQueued = false
            ukemiQueued = false

            if (next.feedback != prevFeedback && next.feedback != JudoFeedback.NONE) {
                when (next.feedback) {
                    JudoFeedback.PERFECT, JudoFeedback.GOOD -> onPlaySfx(SoundEffect.JUDO_REDIRECT)
                    JudoFeedback.UKEMI_SAFE -> onPlaySfx(SoundEffect.JUDO_UKEMI)
                    JudoFeedback.MISS -> onPlaySfx(SoundEffect.ERROR)
                    JudoFeedback.NONE -> Unit
                }
            }
            if (next.status == JudoStatus.WON && !victoryReported) {
                victoryReported = true
                onPlaySfx(SoundEffect.PORTAL)
            } else if (next.status == JudoStatus.LOST) {
                onPlaySfx(SoundEffect.ERROR)
            }
            state = next
        }
    }

    Scaffold(
        containerColor = WiredVoid,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onExit,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("judo_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = strings.judoTrialNames[trialId] ?: strings.judoHeader,
                        style = MaterialTheme.typography.titleMedium,
                        color = HarmonyJade
                    )
                    Text(
                        text = "${strings.judoBestScore}: $bestScore",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                IconButton(
                    onClick = {
                        victoryReported = false
                        state = JudoBalanceEngine.start(state)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("judo_restart_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = strings.restart, tint = TextPrimary)
                }
            }

            // Stability & Harmony Meters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                JudoGaugeBar(
                    label = strings.judoStability,
                    progress = state.stability / 100f,
                    color = if (state.stability > 35f) NaviCyan else SignalCoral,
                    modifier = Modifier.weight(1f)
                )
                JudoGaugeBar(
                    label = strings.judoHarmony,
                    progress = state.harmony / 100f,
                    color = HarmonyJade,
                    modifier = Modifier.weight(1f)
                )
            }

            // Tatami Dojo Balance Arena Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(WiredAbyss)
                    .border(1.dp, WiredBorder, RoundedCornerShape(18.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width * 0.5f
                    val cy = size.height * 0.56f
                    val arcR = minOf(size.width * 0.40f, size.height * 0.44f)

                    // Dojo Shoji upper wall + woven Tatami mat floor
                    val matY = cy + arcR * 0.34f
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFF151E22), Color(0xFF1E2B2A))),
                        size = Size(size.width, matY)
                    )
                    // Shoji lattice lines
                    val shojiGap = 54f
                    var sx = shojiGap
                    while (sx < size.width) {
                        drawLine(Color(0xFF3A4C46).copy(alpha = 0.22f), Offset(sx, 0f), Offset(sx, matY), 1.5f)
                        sx += shojiGap
                    }
                    // Tatami mat surface (warm sage green with dark cloth borders)
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFF284237), Color(0xFF1C3028)), startY = matY, endY = size.height),
                        topLeft = Offset(0f, matY),
                        size = Size(size.width, size.height - matY)
                    )
                    drawLine(Color(0xFF111D18), Offset(0f, matY), Offset(size.width, matY), 4f)
                    // Vertical tatami tile seams
                    for (tIdx in 1..3) {
                        val tx = size.width * (tIdx / 4f)
                        drawLine(Color(0xFF12211B), Offset(tx, matY), Offset(tx, size.height), 3f)
                    }

                    // Outer balance track arc
                    drawArc(
                        color = WiredBorder.copy(alpha = 0.65f),
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(cx - arcR, cy - arcR),
                        size = Size(arcR * 2f, arcR * 2f),
                        style = Stroke(width = 14f, cap = StrokeCap.Round)
                    )

                    // Safe Harmony Zone highlight
                    drawArc(
                        color = HarmonyJade.copy(alpha = 0.72f),
                        startAngle = 244f,
                        sweepAngle = 52f,
                        useCenter = false,
                        topLeft = Offset(cx - arcR, cy - arcR),
                        size = Size(arcR * 2f, arcR * 2f),
                        style = Stroke(width = 16f, cap = StrokeCap.Round)
                    )

                    if (trialId != JudoTrialId.STILL_CENTER) {
                        drawCircle(DeskAmber.copy(alpha = 0.28f), arcR * 0.44f, Offset(cx, cy), style = Stroke(width = 3f))
                        drawCircle(HarmonyJade.copy(alpha = 0.4f), arcR * 0.18f, Offset(cx, cy), style = Stroke(width = 2f))
                    }

                    // Incoming directional force waves (Amber/Coral for Redirect, Violet-Pulse for Ukemi Sweep!)
                    val maxWaveSpan = size.width * 0.46f
                    state.waves.forEach { w ->
                        val wx = cx + w.fromSide * (w.distance * maxWaveSpan)
                        val inWindow = w.distance in 0.12f..0.44f
                        val waveColor = when {
                            w.isSweepWave -> WiredViolet
                            inWindow -> DeskAmber
                            else -> SignalCoral
                        }
                        drawCircle(waveColor.copy(alpha = 0.28f), if (inWindow) 30f else 22f, Offset(wx, cy))
                        drawCircle(waveColor, if (inWindow) 15f else 11f, Offset(wx, cy))
                        if (w.isSweepWave) {
                            // Double ring indicating Ukemi breakfall sweep!
                            drawCircle(Color.White.copy(alpha = 0.85f), 20f, Offset(wx, cy), style = Stroke(width = 2f))
                        } else {
                            val dir = -w.fromSide.toFloat()
                            drawLine(Color.White, Offset(wx - dir * 5f, cy - 6f), Offset(wx + dir * 5f, cy), 2.6f, StrokeCap.Round)
                            drawLine(Color.White, Offset(wx - dir * 5f, cy + 6f), Offset(wx + dir * 5f, cy), 2.6f, StrokeCap.Round)
                        }
                    }

                    // Ambient wind direction indicator
                    val windLen = (state.currentWindForce * 65f).coerceIn(-68f, 68f)
                    if (abs(windLen) > 6f) {
                        val arrowY = cy + arcR * 0.56f
                        drawLine(NaviCyan.copy(alpha = 0.65f), Offset(cx, arrowY), Offset(cx + windLen, arrowY), 4f, StrokeCap.Round)
                    }

                    // Center-of-Gravity Pendulum needle
                    val tiltDeg = state.cogOffset * 58f
                    val angleRad = Math.toRadians((tiltDeg - 90f).toDouble())
                    val bobX = cx + cos(angleRad).toFloat() * arcR
                    val bobY = cy + sin(angleRad).toFloat() * arcR
                    val isBalanced = abs(state.cogOffset) <= 0.38f

                    drawLine(if (isBalanced) HarmonyJade else SignalCoral, Offset(cx, cy), Offset(bobX, bobY), 3.5f, StrokeCap.Round)
                    drawCircle(if (isBalanced) HarmonyJade else SignalCoral, 12f, Offset(bobX, bobY))
                    drawCircle(Color.White, 4.5f, Offset(bobX, bobY))

                    // Lain in Black-Belt Judo Gi (Original game interpretation)
                    val spriteScale = (size.height / 235f).coerceIn(0.95f, 1.55f)
                    val ukemiTilt = if (state.ukemiPoseTimer > 0f) 24f else 0f
                    withTransform({
                        rotate(degrees = state.cogOffset * 26f + ukemiTilt, pivot = Offset(cx, cy + 28f * spriteScale))
                    }) {
                        val lainState = PlayerState(
                            x = cx - 18f * spriteScale,
                            y = cy - 34f * spriteScale,
                            facingRight = state.cogOffset >= -0.05f,
                            onGround = true,
                            animTime = state.elapsed,
                            animState = when {
                                state.ukemiPoseTimer > 0f -> LainAnimState.LANDING
                                state.feedback == JudoFeedback.PERFECT || state.feedback == JudoFeedback.GOOD -> LainAnimState.INTERACTING
                                else -> LainAnimState.IDLE
                            },
                            expression = when (state.feedback) {
                                JudoFeedback.PERFECT, JudoFeedback.UKEMI_SAFE -> LainExpression.HAPPY
                                JudoFeedback.MISS -> LainExpression.FOCUSED
                                else -> LainExpression.FOCUSED
                            },
                            outfit = LainOutfit.JUDO_GI
                        )
                        drawLainGameSprite(
                            player = lainState,
                            screenX = cx - 18f * spriteScale,
                            screenY = cy - 34f * spriteScale,
                            scale = spriteScale
                        )
                    }
                }

                // Live Feedback Banner
                if (state.feedback != JudoFeedback.NONE) {
                    val (fbText, fbColor) = when (state.feedback) {
                        JudoFeedback.PERFECT -> strings.judoPerfect to HarmonyJade
                        JudoFeedback.GOOD -> strings.judoGood to NaviCyan
                        JudoFeedback.UKEMI_SAFE -> strings.judoUkemiSuccess to WiredViolet
                        JudoFeedback.MISS -> strings.judoMiss to SignalCoral
                        JudoFeedback.NONE -> "" to Color.Transparent
                    }
                    Surface(
                        color = WiredVoid.copy(alpha = 0.84f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                    ) {
                        Text(
                            text = fbText,
                            color = fbColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                // Ready / Victory / Lost Overlay
                if (state.status != JudoStatus.ACTIVE) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(WiredVoid.copy(alpha = 0.82f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .widthIn(max = 380.dp)
                                .padding(18.dp)
                        ) {
                            when (state.status) {
                                JudoStatus.READY -> {
                                    Text(
                                        text = strings.judoTrialNames[trialId] ?: "",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = HarmonyJade,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = strings.judoTrialGuides[trialId] ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                    Surface(
                                        color = WiredSurface.copy(alpha = 0.9f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, WiredBorder)
                                    ) {
                                        Text(
                                            text = strings.judoOriginalNote,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            onPlaySfx(SoundEffect.UI_TAP)
                                            state = JudoBalanceEngine.start(state)
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("judo_start_button")
                                    ) {
                                        Text(strings.judoStart)
                                    }
                                }
                                JudoStatus.WON -> {
                                    Icon(Icons.Default.CheckCircle, contentDescription = strings.judoVictory, tint = HarmonyJade, modifier = Modifier.size(44.dp))
                                    Text(strings.judoVictory, style = MaterialTheme.typography.headlineMedium, color = HarmonyJade)
                                    Text("${state.score}", style = MaterialTheme.typography.displayLarge, color = NaviCyan)
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                victoryReported = false
                                                state = JudoBalanceEngine.start(state)
                                            },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) { Text(strings.replayLevel) }
                                        Button(
                                            onClick = { onVictory(state.score) },
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("judo_continue_button")
                                        ) { Text(strings.judoContinue) }
                                    }
                                }
                                JudoStatus.LOST -> {
                                    Text(strings.judoLostBalance, style = MaterialTheme.typography.headlineMedium, color = SignalCoral)
                                    Text(strings.judoTrialGuides[trialId] ?: "", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
                                    Button(
                                        onClick = {
                                            victoryReported = false
                                            state = JudoBalanceEngine.start(state)
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("judo_retry_button")
                                    ) { Text(strings.judoRetry) }
                                }
                                JudoStatus.ACTIVE -> Unit
                            }
                        }
                    }
                }
            }

            // Physical Left-to-Right controls (always LTR)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HoldActionPad(
                            label = strings.judoShiftLeft,
                            accent = NaviCyan,
                            onPressedChanged = { leanLeft = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("judo_lean_left")
                        )
                        HoldActionPad(
                            label = strings.judoBrace,
                            accent = HarmonyJade,
                            onPressedChanged = { braceCenter = it },
                            modifier = Modifier
                                .weight(0.8f)
                                .height(52.dp)
                                .testTag("judo_brace")
                        )
                        HoldActionPad(
                            label = strings.judoShiftRight,
                            accent = NaviCyan,
                            onPressedChanged = { leanRight = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("judo_lean_right")
                        )
                    }

                    if (trialId != JudoTrialId.STILL_CENTER) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { redirectLeftQueued = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DeskAmber.copy(alpha = 0.22f),
                                    contentColor = DeskAmber
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .border(1.5.dp, DeskAmber.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                    .testTag("judo_redirect_left")
                            ) { Text(strings.judoRedirectLeft, fontWeight = FontWeight.Bold) }

                            if (supportsUkemi) {
                                Button(
                                    onClick = { ukemiQueued = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = WiredViolet.copy(alpha = 0.26f),
                                        contentColor = WiredViolet
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .border(1.5.dp, WiredViolet.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                        .testTag("judo_ukemi_button")
                                ) { Text(strings.judoUkemiAction, fontWeight = FontWeight.Bold) }
                            }

                            Button(
                                onClick = { redirectRightQueued = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DeskAmber.copy(alpha = 0.22f),
                                    contentColor = DeskAmber
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .border(1.5.dp, DeskAmber.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                    .testTag("judo_redirect_right")
                            ) { Text(strings.judoRedirectRight, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JudoGaugeBar(
    label: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = color)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = color,
            trackColor = WiredSurfaceHigh,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun HoldActionPad(
    label: String,
    accent: Color,
    onPressedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) accent.copy(alpha = 0.32f) else WiredSurface)
            .border(1.5.dp, if (pressed) accent else WiredBorder, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onPressedChanged(true)
                        tryAwaitRelease()
                        pressed = false
                        onPressedChanged(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (pressed) Color.White else accent,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
