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
import com.example.game.model.LainAnimState
import com.example.game.model.PlayerState
import com.example.game.render.LainSpriteRenderer.drawLainGameSprite
import com.example.i18n.LocalStrings
import com.example.ui.theme.*
import kotlin.math.PI
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
    var victoryReported by remember(trialId) { mutableStateOf(false) }

    // 60 FPS frame loop while active
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
                    redirectRightJustPressed = redirectRightQueued
                ),
                dt = dt
            )
            redirectLeftQueued = false
            redirectRightQueued = false

            if (next.feedback != prevFeedback && next.feedback != JudoFeedback.NONE) {
                when (next.feedback) {
                    JudoFeedback.PERFECT, JudoFeedback.GOOD -> onPlaySfx(SoundEffect.JUDO_REDIRECT)
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
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.back,
                        tint = TextPrimary
                    )
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
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = strings.restart,
                        tint = TextPrimary
                    )
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

            // Dojo Balance Arena Canvas
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

                    // Subtle tatami grid lines
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFF0A111A), Color(0xFF152228)))
                    )
                    drawLine(
                        color = HarmonyJade.copy(alpha = 0.14f),
                        start = Offset(0f, cy + arcR * 0.42f),
                        end = Offset(size.width, cy + arcR * 0.42f),
                        strokeWidth = 2f
                    )

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

                    // Safe Harmony Zone highlight on top of arc
                    drawArc(
                        color = HarmonyJade.copy(alpha = 0.72f),
                        startAngle = 244f,
                        sweepAngle = 52f,
                        useCenter = false,
                        topLeft = Offset(cx - arcR, cy - arcR),
                        size = Size(arcR * 2f, arcR * 2f),
                        style = Stroke(width = 16f, cap = StrokeCap.Round)
                    )

                    // Timing redirect rings (for Trial II & III)
                    if (trialId != JudoTrialId.STILL_CENTER) {
                        val ringOuter = arcR * 0.44f
                        val ringInner = arcR * 0.18f
                        drawCircle(
                            color = DeskAmber.copy(alpha = 0.28f),
                            radius = ringOuter,
                            center = Offset(cx, cy),
                            style = Stroke(width = 3f)
                        )
                        drawCircle(
                            color = HarmonyJade.copy(alpha = 0.4f),
                            radius = ringInner,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2f)
                        )
                    }

                    // Incoming directional force waves
                    val maxWaveSpan = size.width * 0.46f
                    state.waves.forEach { w ->
                        val wx = cx + w.fromSide * (w.distance * maxWaveSpan)
                        val inWindow = w.distance in 0.12f..0.42f
                        val waveColor = if (inWindow) DeskAmber else SignalCoral
                        drawCircle(
                            color = waveColor.copy(alpha = 0.25f),
                            radius = if (inWindow) 28f else 20f,
                            center = Offset(wx, cy)
                        )
                        drawCircle(
                            color = waveColor,
                            radius = if (inWindow) 14f else 11f,
                            center = Offset(wx, cy)
                        )
                        // Direction chevron pointing inward
                        val dir = -w.fromSide.toFloat()
                        drawLine(
                            color = Color.White,
                            start = Offset(wx - dir * 5f, cy - 6f),
                            end = Offset(wx + dir * 5f, cy),
                            strokeWidth = 2.6f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(wx - dir * 5f, cy + 6f),
                            end = Offset(wx + dir * 5f, cy),
                            strokeWidth = 2.6f,
                            cap = StrokeCap.Round
                        )
                    }

                    // Ambient wind direction indicator arrow
                    val windLen = (state.currentWindForce * 65f).coerceIn(-68f, 68f)
                    if (abs(windLen) > 6f) {
                        val arrowY = cy + arcR * 0.56f
                        drawLine(
                            color = NaviCyan.copy(alpha = 0.65f),
                            start = Offset(cx, arrowY),
                            end = Offset(cx + windLen, arrowY),
                            strokeWidth = 4f,
                            cap = StrokeCap.Round
                        )
                    }

                    // Center-of-Gravity Pendulum needle & Lain leaning on balance beam
                    val tiltDeg = state.cogOffset * 58f
                    val angleRad = Math.toRadians((tiltDeg - 90f).toDouble())
                    val bobX = cx + cos(angleRad).toFloat() * arcR
                    val bobY = cy + sin(angleRad).toFloat() * arcR
                    val isBalanced = abs(state.cogOffset) <= 0.38f

                    drawLine(
                        color = if (isBalanced) HarmonyJade else SignalCoral,
                        start = Offset(cx, cy),
                        end = Offset(bobX, bobY),
                        strokeWidth = 3.5f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = if (isBalanced) HarmonyJade else SignalCoral,
                        radius = 12f,
                        center = Offset(bobX, bobY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4.5f,
                        center = Offset(bobX, bobY)
                    )

                    // Lain sprite balancing at center pivot, gently leaning with CoG!
                    val spriteScale = (size.height / 240f).coerceIn(0.95f, 1.55f)
                    withTransform({
                        rotate(degrees = state.cogOffset * 26f, pivot = Offset(cx, cy + 28f * spriteScale))
                    }) {
                        val lainState = PlayerState(
                            x = cx - 18f * spriteScale,
                            y = cy - 34f * spriteScale,
                            facingRight = state.cogOffset >= -0.05f,
                            onGround = true,
                            animTime = state.elapsed,
                            animState = if (state.feedback == JudoFeedback.PERFECT || state.feedback == JudoFeedback.GOOD) {
                                LainAnimState.INTERACTING
                            } else LainAnimState.IDLE
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
                        JudoFeedback.MISS -> strings.judoMiss to SignalCoral
                        JudoFeedback.NONE -> "" to Color.Transparent
                    }
                    Surface(
                        color = WiredVoid.copy(alpha = 0.82f),
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
                            .background(WiredVoid.copy(alpha = 0.80f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .widthIn(max = 360.dp)
                                .padding(20.dp)
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
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = strings.judoVictory,
                                        tint = HarmonyJade,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = strings.judoVictory,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = HarmonyJade
                                    )
                                    Text(
                                        text = "${state.score}",
                                        style = MaterialTheme.typography.displayLarge,
                                        color = NaviCyan
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                victoryReported = false
                                                state = JudoBalanceEngine.start(state)
                                            },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text(strings.replayLevel)
                                        }
                                        Button(
                                            onClick = { onVictory(state.score) },
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("judo_continue_button")
                                        ) {
                                            Text(strings.judoContinue)
                                        }
                                    }
                                }
                                JudoStatus.LOST -> {
                                    Text(
                                        text = strings.judoLostBalance,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = SignalCoral
                                    )
                                    Text(
                                        text = strings.judoTrialGuides[trialId] ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = {
                                            victoryReported = false
                                            state = JudoBalanceEngine.start(state)
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("judo_retry_button")
                                    ) {
                                        Text(strings.judoRetry)
                                    }
                                }
                                JudoStatus.ACTIVE -> Unit
                            }
                        }
                    }
                }
            }

            // Physical Left-to-Right controls (always LTR so Lean Left is physically left!)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Balance Shift Controls
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
                                .height(54.dp)
                                .testTag("judo_lean_left")
                        )
                        HoldActionPad(
                            label = strings.judoBrace,
                            accent = HarmonyJade,
                            onPressedChanged = { braceCenter = it },
                            modifier = Modifier
                                .weight(0.8f)
                                .height(54.dp)
                                .testTag("judo_brace")
                        )
                        HoldActionPad(
                            label = strings.judoShiftRight,
                            accent = NaviCyan,
                            onPressedChanged = { leanRight = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("judo_lean_right")
                        )
                    }

                    // Kuzushi Redirect Buttons (shown on Trial II & III)
                    if (trialId != JudoTrialId.STILL_CENTER) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                                    .height(54.dp)
                                    .border(1.5.dp, DeskAmber.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                    .testTag("judo_redirect_left")
                            ) {
                                Text(strings.judoRedirectLeft, fontWeight = FontWeight.Bold)
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
                                    .height(54.dp)
                                    .border(1.5.dp, DeskAmber.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                    .testTag("judo_redirect_right")
                            ) {
                                Text(strings.judoRedirectRight, fontWeight = FontWeight.Bold)
                            }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
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
