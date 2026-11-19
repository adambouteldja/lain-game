package com.example.sandbox

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.i18n.LocalStrings
import com.example.ui.theme.*
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Standalone interactive 2D Physics Sandbox Screen (Evolution 2.0).
 * Touch-drag & fling objects, spawn balls/crates/metal/pendulums/magnets/lever beams,
 * tune real-time gravity, friction, drag & time-scale, and inspect live SI kinetic/potential energy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicsSandboxScreen(
    initialPreset: SandboxPreset = SandboxPreset.FREE_FALL,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalStrings.current

    var state by remember(initialPreset) { mutableStateOf(PhysicsSandboxEngine.createPreset(initialPreset)) }
    var canvasPx by remember { mutableStateOf(IntSize(860, 520)) }
    var showSliders by remember { mutableStateOf(false) }

    // 60 FPS simulation loop
    LaunchedEffect(Unit) {
        var lastNanos = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
            lastNanos = now
            state = PhysicsSandboxEngine.step(state, dt)
        }
    }

    val ke = state.totalKineticEnergyJ()
    val pe = state.totalPotentialEnergyJ()

    Scaffold(
        containerColor = VoidBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VoidSurface),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("sandbox_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = TextPrimary)
                    }
                },
                title = {
                    Column {
                        Text(strings.sandboxTitle, style = MaterialTheme.typography.titleMedium, color = NaviCyan)
                        Text(
                            "Ek: ${ke.roundToInt()} J  •  Ep: ${pe.roundToInt()} J  •  Em: ${(ke + pe).roundToInt()} J",
                            style = MaterialTheme.typography.labelSmall,
                            color = HarmonyJade
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { state = state.copy(isPaused = !state.isPaused) },
                        modifier = Modifier.testTag("sandbox_play_pause")
                    ) {
                        Icon(
                            if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = strings.pause,
                            tint = if (state.isPaused) DeskAmber else NaviCyan
                        )
                    }
                    IconButton(
                        onClick = { showSliders = !showSliders },
                        modifier = Modifier.testTag("sandbox_params_button")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = strings.sandboxParameters, tint = if (showSliders) NaviCyan else TextPrimary)
                    }
                    IconButton(
                        onClick = { state = state.copy(bodies = emptyList(), magnets = emptyList(), beam = null) },
                        modifier = Modifier.testTag("sandbox_clear_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = strings.clear, tint = SignalCoral)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Preset Selector Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VoidSurfaceElevated)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SandboxPreset.entries.forEach { preset ->
                    AssistChip(
                        onClick = { state = PhysicsSandboxEngine.createPreset(preset, state.worldWidth, state.worldHeight) },
                        label = { Text(strings.sandboxPresetName(preset), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("sandbox_preset_${preset.name.lowercase()}")
                    )
                }
            }

            // Optional Live Physics Sliders Panel
            if (showSliders) {
                Surface(
                    color = VoidSurface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${strings.sandboxGravity}: ${String.format("%.1f", state.gravityMs2)} m/s²", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Slider(value = state.gravityMs2, onValueChange = { state = state.copy(gravityMs2 = it) }, valueRange = -15f..22f)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${strings.sandboxTimeScale}: ${String.format("%.2f", state.timeScale)}x", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Slider(value = state.timeScale, onValueChange = { state = state.copy(timeScale = it) }, valueRange = 0.1f..2.0f)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${strings.sandboxFriction}: ${(state.friction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Slider(value = state.friction, onValueChange = { state = state.copy(friction = it) }, valueRange = 0f..0.95f)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${strings.sandboxAirDrag}: ${(state.airDrag * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Slider(value = state.airDrag, onValueChange = { state = state.copy(airDrag = it) }, valueRange = 0f..0.25f)
                            }
                        }
                    }
                }
            }

            // Interactive Physics Viewport (Touch Drag & Fling)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(WiredAbyss)
                    .border(1.5.dp, WiredBorder, RoundedCornerShape(16.dp))
                    .onSizeChanged { canvasPx = it }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val sx = state.worldWidth / canvasPx.width.coerceAtLeast(1).toFloat()
                                val sy = state.worldHeight / canvasPx.height.coerceAtLeast(1).toFloat()
                                val wx = down.position.x * sx
                                val wy = down.position.y * sy

                                val grabbed = state.bodies.minByOrNull { hypot(it.x - wx, it.y - wy) }
                                    ?.takeIf { hypot(it.x - wx, it.y - wy) <= it.radius + 32f }
                                    ?: return@awaitEachGesture

                                state = state.copy(bodies = state.bodies.map {
                                    if (it.id == grabbed.id) it.copy(isDragged = true, vx = 0f, vy = 0f) else it
                                })

                                var lastX = wx
                                var lastY = wy
                                var flingVx = 0f
                                var flingVy = 0f
                                down.consume()

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    val curX = (change.position.x * sx).coerceIn(28f, state.worldWidth - 28f)
                                    val curY = (change.position.y * sy).coerceIn(28f, state.worldHeight - 28f)
                                    flingVx = ((curX - lastX) * 42f).coerceIn(-680f, 680f)
                                    flingVy = ((curY - lastY) * 42f).coerceIn(-680f, 680f)
                                    lastX = curX
                                    lastY = curY
                                    state = state.copy(bodies = state.bodies.map {
                                        if (it.id == grabbed.id) it.copy(x = curX, y = curY, vx = 0f, vy = 0f) else it
                                    })
                                    change.consume()
                                }

                                state = state.copy(bodies = state.bodies.map {
                                    if (it.id == grabbed.id) it.copy(isDragged = false, vx = flingVx, vy = flingVy) else it
                                })
                            }
                        }
                        .testTag("sandbox_canvas")
                ) {
                    val scaleX = size.width / state.worldWidth
                    val scaleY = size.height / state.worldHeight

                    // Lab blueprint background grid
                    drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF0A101E), Color(0xFF121C32))))
                    val gridStep = 48f
                    var gx = gridStep
                    while (gx < size.width) {
                        drawLine(NaviCyan.copy(alpha = 0.06f), Offset(gx, 0f), Offset(gx, size.height), 1f)
                        gx += gridStep
                    }
                    var gy = gridStep
                    while (gy < size.height) {
                        drawLine(NaviCyan.copy(alpha = 0.06f), Offset(0f, gy), Offset(size.width, gy), 1f)
                        gy += gridStep
                    }

                    withTransform({ scale(scaleX, scaleY, pivot = Offset.Zero) }) {
                        // Magnets
                        state.magnets.forEach { m ->
                            val col = if (m.attract) NaviCyan else SignalCoral
                            drawCircle(col.copy(alpha = 0.12f), m.range * 0.45f, Offset(m.x, m.y))
                            drawCircle(col.copy(alpha = 0.35f), m.range * 0.25f, Offset(m.x, m.y), style = Stroke(2f))
                            drawRoundRect(Color(0xFF1F2B44), Offset(m.x - 22f, m.y - 22f), Size(44f, 44f), CornerRadius(8f, 8f))
                            drawRoundRect(col, Offset(m.x - 22f, m.y - 22f), Size(44f, 44f), CornerRadius(8f, 8f), style = Stroke(2.6f))
                            drawLine(col, Offset(m.x - 9f, m.y), Offset(m.x + 9f, m.y), 3f, StrokeCap.Round)
                            if (m.attract) {
                                drawLine(col, Offset(m.x, m.y - 9f), Offset(m.x, m.y + 9f), 3f, StrokeCap.Round)
                            }
                        }

                        // Optional Lever Beam
                        state.beam?.let { bm ->
                            val fulcrum = Path().apply {
                                moveTo(bm.pivotX, bm.pivotY + 6f)
                                lineTo(bm.pivotX - 20f, bm.pivotY + 40f)
                                lineTo(bm.pivotX + 20f, bm.pivotY + 40f)
                                close()
                            }
                            drawPath(fulcrum, color = Color(0xFF283A44))
                            drawPath(fulcrum, color = HarmonyJade, style = Stroke(2f))
                            withTransform({ rotate(bm.angleDeg, Offset(bm.pivotX, bm.pivotY)) }) {
                                drawRoundRect(Color(0xFF223344), Offset(bm.pivotX - bm.halfLength, bm.pivotY - 9f), Size(bm.halfLength * 2f, 18f), CornerRadius(6f, 6f))
                                drawRoundRect(HarmonyJade, Offset(bm.pivotX - bm.halfLength, bm.pivotY - 9f), Size(bm.halfLength * 2f, 18f), CornerRadius(6f, 6f), style = Stroke(2.4f))
                                drawCircle(DeskAmber, 5f, Offset(bm.pivotX, bm.pivotY))
                            }
                        }

                        // Physics Bodies
                        state.bodies.forEach { b ->
                            val col = Color(b.colorArgb)
                            if (b.kind == SandboxBodyKind.PENDULUM_BOB) {
                                drawCircle(TextSecondary, 5f, Offset(b.anchorX, b.anchorY))
                                drawLine(TextSecondary.copy(alpha = 0.7f), Offset(b.anchorX, b.anchorY), Offset(b.x, b.y), 2.4f)
                            }
                            when (b.kind) {
                                SandboxBodyKind.BALL, SandboxBodyKind.PENDULUM_BOB -> {
                                    drawCircle(col.copy(alpha = 0.28f), b.radius * 1.18f, Offset(b.x, b.y))
                                    drawCircle(col, b.radius, Offset(b.x, b.y))
                                    drawCircle(Color.White.copy(alpha = 0.75f), b.radius, Offset(b.x, b.y), style = Stroke(2f))
                                }
                                SandboxBodyKind.WOOD_CRATE, SandboxBodyKind.METAL_CRATE -> {
                                    val fill = if (b.kind == SandboxBodyKind.METAL_CRATE) Color(0xFF33496B) else Color(0xFF453642)
                                    drawRoundRect(fill, Offset(b.x - b.radius, b.y - b.radius), Size(b.radius * 2f, b.radius * 2f), CornerRadius(6f, 6f))
                                    drawRoundRect(col, Offset(b.x - b.radius, b.y - b.radius), Size(b.radius * 2f, b.radius * 2f), CornerRadius(6f, 6f), style = Stroke(2.6f))
                                    drawLine(col.copy(alpha = 0.5f), Offset(b.x - b.radius + 6f, b.y - b.radius + 6f), Offset(b.x + b.radius - 6f, b.y + b.radius - 6f), 2f)
                                    drawLine(col.copy(alpha = 0.5f), Offset(b.x + b.radius - 6f, b.y - b.radius + 6f), Offset(b.x - b.radius + 6f, b.y + b.radius - 6f), 2f)
                                }
                            }
                            // Velocity vector indicator
                            val speed = hypot(b.vx, b.vy)
                            if (speed > 25f) {
                                val vecScale = 0.14f
                                drawLine(
                                    color = Color.White.copy(alpha = 0.7f),
                                    start = Offset(b.x, b.y),
                                    end = Offset(b.x + b.vx * vecScale, b.y + b.vy * vecScale),
                                    strokeWidth = 2.2f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }

                // Helpful drag hint overlay
                Text(
                    text = strings.sandboxDragHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                )
            }

            // Bottom Spawn Controls Dock (48dp touch targets)
            Surface(color = VoidSurface, tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { state = PhysicsSandboxEngine.spawnBody(state, SandboxBodyKind.BALL) },
                        modifier = Modifier.height(48.dp).testTag("sandbox_spawn_ball")
                    ) { Text("+ ${strings.sandboxBall}") }

                    FilledTonalButton(
                        onClick = { state = PhysicsSandboxEngine.spawnBody(state, SandboxBodyKind.WOOD_CRATE) },
                        modifier = Modifier.height(48.dp).testTag("sandbox_spawn_crate")
                    ) { Text("+ ${strings.sandboxCrate}") }

                    FilledTonalButton(
                        onClick = { state = PhysicsSandboxEngine.spawnBody(state, SandboxBodyKind.METAL_CRATE) },
                        modifier = Modifier.height(48.dp).testTag("sandbox_spawn_metal")
                    ) { Text("+ ${strings.sandboxMetal}") }

                    FilledTonalButton(
                        onClick = { state = PhysicsSandboxEngine.spawnBody(state, SandboxBodyKind.PENDULUM_BOB) },
                        modifier = Modifier.height(48.dp).testTag("sandbox_spawn_pendulum")
                    ) { Text("+ ${strings.sandboxPendulum}") }

                    OutlinedButton(
                        onClick = { state = PhysicsSandboxEngine.toggleMagnet(state) },
                        modifier = Modifier.height(48.dp).testTag("sandbox_toggle_magnet")
                    ) { Text(strings.sandboxMagnetToggle) }

                    OutlinedButton(
                        onClick = { state = PhysicsSandboxEngine.toggleBeam(state) },
                        modifier = Modifier.height(48.dp).testTag("sandbox_toggle_beam")
                    ) { Text(strings.sandboxLeverToggle) }
                }
            }
        }
    }
}
