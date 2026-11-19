package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.audio.SoundEffect
import com.example.data.ControlSize
import com.example.data.GameSettings
import com.example.data.JudoTrialId
import com.example.data.SaveProgress
import com.example.game.engine.LevelCatalog
import com.example.game.engine.PhysicsEngine
import com.example.game.model.GameEvent
import com.example.game.model.InputState
import com.example.game.render.GameWorldCanvas
import com.example.game.render.LainSpriteRenderer.drawLainPortrait
import com.example.i18n.LocalStrings
import com.example.judo.JudoBalanceScreen
import com.example.ui.theme.*

@Composable
fun GamePlayScreen(
    levelId: String,
    progress: SaveProgress,
    settings: GameSettings,
    onPlaySfx: (SoundEffect) -> Unit,
    onToggleMute: () -> Unit,
    onShardCollected: (levelId: String, shardId: String) -> Unit,
    onJudoTrialCompleted: (JudoTrialId, Int) -> Unit,
    onLevelCompleted: (levelId: String, collectedShardIds: Set<String>) -> Unit,
    onSelectNextLevel: (String) -> Unit,
    onExitToWorlds: () -> Unit,
    onExitToMenu: () -> Unit
) {
    val strings = LocalStrings.current
    val levelDef = remember(levelId) { LevelCatalog.levelById(levelId) }

    var runtimeState by remember(levelId) {
        mutableStateOf(
            PhysicsEngine.createInitialState(
                def = levelDef,
                alreadyCollectedShards = progress.collectedShardsByLevel[levelId].orEmpty(),
                completedJudoTrials = progress.completedJudoTrials
            )
        )
    }

    var isPaused by remember(levelId) { mutableStateOf(false) }
    var showStoryCard by remember(levelId) { mutableStateOf(true) }
    var inkDrawMode by remember(levelId) { mutableStateOf(false) }
    var levelCompletionReported by remember(levelId) { mutableStateOf(false) }

    // Viewport dimensions from Canvas for camera clamping
    var viewportW by remember { mutableFloatStateOf(800f) }
    var viewportH by remember { mutableFloatStateOf(480f) }

    // Multi-touch input flags
    var moveLeft by remember { mutableStateOf(false) }
    var moveRight by remember { mutableStateOf(false) }
    var jumpHeld by remember { mutableStateOf(false) }
    var jumpJustPressed by remember { mutableStateOf(false) }
    var interactJustPressed by remember { mutableStateOf(false) }

    BackHandler {
        if (runtimeState.pendingJudoTrial != null) {
            runtimeState = PhysicsEngine.dismissPendingJudo(runtimeState)
        } else {
            isPaused = !isPaused
        }
    }

    // Inline Judo Trial when triggered from a Tatami Shrine inside World 5 or 6-2
    val activeJudoTrial = runtimeState.pendingJudoTrial
    if (activeJudoTrial != null) {
        JudoBalanceScreen(
            trialId = activeJudoTrial,
            bestScore = progress.bestJudoScores[activeJudoTrial] ?: 0,
            onPlaySfx = onPlaySfx,
            onVictory = { score ->
                onJudoTrialCompleted(activeJudoTrial, score)
                runtimeState = PhysicsEngine.markJudoTrialCompleted(runtimeState, activeJudoTrial)
            },
            onExit = {
                runtimeState = PhysicsEngine.dismissPendingJudo(runtimeState)
            }
        )
        return
    }

    // 60 FPS Fixed-clamped Game Loop
    LaunchedEffect(levelId, isPaused, runtimeState.isCompleted, activeJudoTrial) {
        if (isPaused || runtimeState.isCompleted || activeJudoTrial != null) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        while (!isPaused && !runtimeState.isCompleted && runtimeState.pendingJudoTrial == null) {
            val now = withFrameNanos { it }
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
            lastNanos = now

            val input = InputState(
                moveLeft = moveLeft,
                moveRight = moveRight,
                jumpPressed = jumpHeld,
                jumpJustPressed = jumpJustPressed,
                interactJustPressed = interactJustPressed
            )
            jumpJustPressed = false
            interactJustPressed = false

            val prevShards = runtimeState.shards.filter { it.collected }.map { it.id }.toSet()
            val next = PhysicsEngine.step(
                state = runtimeState,
                input = input,
                dt = dt,
                viewportW = viewportW,
                viewportH = viewportH
            )

            // Process game events (audio + persistence)
            next.events.forEach { ev ->
                when (ev) {
                    GameEvent.JUMPED -> onPlaySfx(SoundEffect.JUMP)
                    GameEvent.LANDED -> onPlaySfx(SoundEffect.LAND)
                    GameEvent.SHARD_COLLECTED -> onPlaySfx(SoundEffect.SHARD)
                    GameEvent.SWITCH_TRIGGERED,
                    GameEvent.GRAVITY_FLIPPED,
                    GameEvent.MAGNET_TOGGLED,
                    GameEvent.MIRROR_ROTATED -> onPlaySfx(SoundEffect.SWITCH)
                    GameEvent.CHECKPOINT_SAVED -> onPlaySfx(SoundEffect.CHECKPOINT)
                    GameEvent.DOOR_OPENED -> onPlaySfx(SoundEffect.CHECKPOINT)
                    GameEvent.RESPAWNED -> onPlaySfx(SoundEffect.ERROR)
                    GameEvent.LEVEL_COMPLETED -> onPlaySfx(SoundEffect.PORTAL)
                    GameEvent.INK_PLATFORM_CREATED -> onPlaySfx(SoundEffect.INK_DRAW)
                }
            }

            // Persist newly collected shards immediately
            val nowShards = next.shards.filter { it.collected }.map { it.id }.toSet()
            (nowShards - prevShards).forEach { shardId ->
                onShardCollected(levelId, shardId)
            }

            if (next.isCompleted && !levelCompletionReported) {
                levelCompletionReported = true
                onLevelCompleted(levelId, nowShards)
            }

            runtimeState = next
        }
    }

    val buttonSize: Dp = when (settings.controlSize) {
        ControlSize.COMPACT -> 56.dp
        ControlSize.STANDARD -> 66.dp
        ControlSize.LARGE -> 76.dp
    }

    fun restartCurrentLevel() {
        isPaused = false
        inkDrawMode = false
        levelCompletionReported = false
        runtimeState = PhysicsEngine.createInitialState(
            def = levelDef,
            alreadyCollectedShards = progress.collectedShardsByLevel[levelId].orEmpty(),
            completedJudoTrials = progress.completedJudoTrials
        )
        onPlaySfx(SoundEffect.UI_TAP)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(WiredVoid)
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            // LANDSCAPE: Full-bleed wide viewport + translucent controls in corners
            Box(modifier = Modifier.fillMaxSize()) {
                GameWorldCanvas(
                    state = runtimeState,
                    inkDrawMode = inkDrawMode,
                    reduceShake = settings.reduceShake,
                    onViewportChanged = { w, h ->
                        viewportW = w
                        viewportH = h
                    },
                    onInkBridgeDrawn = { p1, p2 ->
                        runtimeState = PhysicsEngine.addInkBridge(runtimeState, p1, p2)
                        onPlaySfx(SoundEffect.INK_DRAW)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Top HUD
                GameplayTopHud(
                    levelId = levelId,
                    state = runtimeState,
                    isMuted = settings.isMuted,
                    inkDrawMode = inkDrawMode,
                    onToggleInkMode = { inkDrawMode = !inkDrawMode },
                    onClearInk = { runtimeState = PhysicsEngine.clearInkBridges(runtimeState) },
                    onToggleMute = onToggleMute,
                    onRestart = { restartCurrentLevel() },
                    onPause = { isPaused = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )

                // Story Whisper Banner
                StoryWhisperOverlay(
                    visible = showStoryCard && !isPaused && !runtimeState.isCompleted,
                    levelId = levelId,
                    elapsedTime = runtimeState.elapsedTime,
                    onDismiss = { showStoryCard = false },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = 60.dp, start = 20.dp, end = 20.dp)
                )

                // Translucent Bottom-Corner Controls
                GameplayTouchControls(
                    buttonSize = buttonSize,
                    isTranslucent = true,
                    interactHighlighted = runtimeState.nearbyInteractLabel != null,
                    onLeftChange = { moveLeft = it },
                    onRightChange = { moveRight = it },
                    onJumpChange = { pressed ->
                        if (pressed && !jumpHeld) jumpJustPressed = true
                        jumpHeld = pressed
                    },
                    onInteractPress = { interactJustPressed = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        } else {
            // PORTRAIT: Top crisp viewport + zero-occlusion bottom ergonomic thumb deck
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    GameWorldCanvas(
                        state = runtimeState,
                        inkDrawMode = inkDrawMode,
                        reduceShake = settings.reduceShake,
                        onViewportChanged = { w, h ->
                            viewportW = w
                            viewportH = h
                        },
                        onInkBridgeDrawn = { p1, p2 ->
                            runtimeState = PhysicsEngine.addInkBridge(runtimeState, p1, p2)
                            onPlaySfx(SoundEffect.INK_DRAW)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    GameplayTopHud(
                        levelId = levelId,
                        state = runtimeState,
                        isMuted = settings.isMuted,
                        inkDrawMode = inkDrawMode,
                        onToggleInkMode = { inkDrawMode = !inkDrawMode },
                        onClearInk = { runtimeState = PhysicsEngine.clearInkBridges(runtimeState) },
                        onToggleMute = onToggleMute,
                        onRestart = { restartCurrentLevel() },
                        onPause = { isPaused = true },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )

                    StoryWhisperOverlay(
                        visible = showStoryCard && !isPaused && !runtimeState.isCompleted,
                        levelId = levelId,
                        elapsedTime = runtimeState.elapsedTime,
                        onDismiss = { showStoryCard = false },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                // Dedicated Portrait Ergonomic Controller Deck
                Surface(
                    color = WiredAbyss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, WiredBorder.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (inkDrawMode && runtimeState.definition.allowInkDrawing) {
                            Text(
                                text = strings.drawBridgeHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = NaviCyan,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        GameplayTouchControls(
                            buttonSize = buttonSize,
                            isTranslucent = false,
                            interactHighlighted = runtimeState.nearbyInteractLabel != null,
                            onLeftChange = { moveLeft = it },
                            onRightChange = { moveRight = it },
                            onJumpChange = { pressed ->
                                if (pressed && !jumpHeld) jumpJustPressed = true
                                jumpHeld = pressed
                            },
                            onInteractPress = { interactJustPressed = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Status Feedback Toast (Checkpoint / Gravity Flipped / Door Unlocked)
        val bannerKey = runtimeState.statusBannerKey
        AnimatedVisibility(
            visible = bannerKey != null && !isPaused && !runtimeState.isCompleted,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 64.dp)
        ) {
            val statusText = when (bannerKey) {
                "checkpoint_reached" -> strings.checkpointReached
                "gravity_flipped" -> strings.gravityFlipped
                "magnet_toggled" -> strings.magnetToggled
                "mirror_rotated" -> strings.mirrorRotated
                "door_unlocked" -> strings.doorUnlocked
                else -> ""
            }
            if (statusText.isNotEmpty()) {
                Surface(
                    color = WiredSurface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.border(1.dp, HarmonyJade, RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        color = HarmonyJade,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Pause Overlay Dialog
        if (isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WiredVoid.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = WiredSurface,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .widthIn(min = 280.dp, max = 360.dp)
                        .padding(20.dp)
                        .border(1.dp, NaviCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(strings.paused, style = MaterialTheme.typography.headlineMedium, color = NaviCyan)
                        Text(
                            text = "${levelId} — ${strings.levelTitles[levelId] ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Button(
                            onClick = { isPaused = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("pause_resume_button")
                        ) { Text(strings.resume) }
                        OutlinedButton(
                            onClick = { restartCurrentLevel() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("pause_restart_button")
                        ) { Text(strings.restart) }
                        OutlinedButton(
                            onClick = onExitToWorlds,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) { Text(strings.exitToWorlds) }
                        TextButton(
                            onClick = onExitToMenu,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(strings.exitToMenu, color = SignalCoral) }
                    }
                }
            }
        }

        // Level Complete / Story Finale Overlay
        if (runtimeState.isCompleted) {
            val nextId = LevelCatalog.nextLevelId(levelId)
            val isFinale = nextId == null
            val collectedNow = runtimeState.shards.count { it.collected }
            val totalShards = runtimeState.shards.size

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WiredVoid.copy(alpha = 0.84f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = WiredSurface,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .widthIn(min = 300.dp, max = 400.dp)
                        .padding(20.dp)
                        .border(1.5.dp, HarmonyJade, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isFinale) Icons.Default.AutoAwesome else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isFinale) DeskAmber else HarmonyJade,
                            modifier = Modifier.size(46.dp)
                        )
                        Text(
                            text = if (isFinale) strings.storyFinaleTitle else strings.levelComplete,
                            style = MaterialTheme.typography.headlineMedium,
                            color = HarmonyJade,
                            textAlign = TextAlign.Center
                        )
                        if (isFinale) {
                            Text(
                                text = strings.storyFinaleBody,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            text = "${strings.shardsLabel}: ◆ $collectedNow / $totalShards",
                            style = MaterialTheme.typography.titleMedium,
                            color = DeskAmber
                        )
                        if (nextId != null) {
                            Button(
                                onClick = { onSelectNextLevel(nextId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("next_level_button")
                            ) {
                                Text(strings.nextLevel)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { restartCurrentLevel() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text(strings.replayLevel)
                            }
                            OutlinedButton(
                                onClick = onExitToWorlds,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text(strings.exitToWorlds)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameplayTopHud(
    levelId: String,
    state: com.example.game.model.LevelRuntimeState,
    isMuted: Boolean,
    inkDrawMode: Boolean,
    onToggleInkMode: () -> Unit,
    onClearInk: () -> Unit,
    onToggleMute: () -> Unit,
    onRestart: () -> Unit,
    onPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val collected = state.shards.count { it.collected }
    val total = state.shards.size

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Level badge + Shards
        Surface(
            color = WiredSurface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.border(1.dp, WiredBorder, RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "$levelId • ${strings.levelTitles[levelId] ?: ""}",
                    style = MaterialTheme.typography.labelLarge,
                    color = NaviCyan
                )
                Text(
                    text = "◆ $collected/$total",
                    style = MaterialTheme.typography.labelLarge,
                    color = DeskAmber
                )
            }
        }

        // Right: Ink Bridge controls (when allowed) + Mute + Restart + Pause
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (state.definition.allowInkDrawing) {
                FilterChip(
                    selected = inkDrawMode,
                    onClick = onToggleInkMode,
                    label = { Text(strings.drawBridgeMode, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("hud_ink_mode_chip")
                )
                if (state.drawnPlatforms.isNotEmpty()) {
                    IconButton(
                        onClick = onClearInk,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(WiredSurface.copy(alpha = 0.8f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = strings.clearBridges,
                            tint = SignalCoral,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            HudIconButton(
                icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDesc = strings.muteAll,
                onClick = onToggleMute,
                tag = "hud_mute_button"
            )
            HudIconButton(
                icon = Icons.Default.Refresh,
                contentDesc = strings.restart,
                onClick = onRestart,
                tag = "hud_restart_button"
            )
            HudIconButton(
                icon = Icons.Default.Pause,
                contentDesc = strings.paused,
                onClick = onPause,
                tag = "hud_pause_button"
            )
        }
    }
}

@Composable
private fun HudIconButton(
    icon: ImageVector,
    contentDesc: String,
    onClick: () -> Unit,
    tag: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(WiredSurface.copy(alpha = 0.82f))
            .border(1.dp, WiredBorder, CircleShape)
            .testTag(tag)
    ) {
        Icon(imageVector = icon, contentDescription = contentDesc, tint = TextPrimary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StoryWhisperOverlay(
    visible: Boolean,
    levelId: String,
    elapsedTime: Float,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = WiredSurface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .border(1.dp, NaviCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .clickable { onDismiss() }
                .testTag("story_whisper_card")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Canvas(modifier = Modifier.size(52.dp)) {
                    drawLainPortrait(
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                        radius = size.minDimension * 0.48f,
                        timeSec = elapsedTime
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = strings.lainSpeaker,
                        style = MaterialTheme.typography.labelSmall,
                        color = NaviCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.levelIntros[levelId] ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = strings.levelHints[levelId] ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = DeskAmber
                    )
                }
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = strings.cancel,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun GameplayTouchControls(
    buttonSize: Dp,
    isTranslucent: Boolean,
    interactHighlighted: Boolean,
    onLeftChange: (Boolean) -> Unit,
    onRightChange: (Boolean) -> Unit,
    onJumpChange: (Boolean) -> Unit,
    onInteractPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val baseAlpha = if (isTranslucent) 0.68f else 0.96f

    // Force physical LTR layout so Left button is ALWAYS on the physical left side
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left & Right Movement Pad
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MultiTouchControlButton(
                    size = buttonSize,
                    accent = NaviCyan,
                    baseAlpha = baseAlpha,
                    onHoldChange = onLeftChange,
                    modifier = Modifier.testTag("control_left")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = strings.btnLeftDesc,
                        tint = TextPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
                MultiTouchControlButton(
                    size = buttonSize,
                    accent = NaviCyan,
                    baseAlpha = baseAlpha,
                    onHoldChange = onRightChange,
                    modifier = Modifier.testTag("control_right")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = strings.btnRightDesc,
                        tint = TextPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            // Interact & Jump Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MultiTouchControlButton(
                    size = buttonSize,
                    accent = if (interactHighlighted) HarmonyJade else DeskAmber,
                    baseAlpha = if (interactHighlighted) 0.92f else baseAlpha,
                    highlighted = interactHighlighted,
                    onHoldChange = { pressed -> if (pressed) onInteractPress() },
                    modifier = Modifier.testTag("control_interact")
                ) {
                    Text(
                        text = strings.btnAct,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (interactHighlighted) WiredVoid else DeskAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
                MultiTouchControlButton(
                    size = buttonSize,
                    accent = SignalCoral,
                    baseAlpha = baseAlpha,
                    onHoldChange = onJumpChange,
                    modifier = Modifier.testTag("control_jump")
                ) {
                    Text(
                        text = strings.btnJump,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiTouchControlButton(
    size: Dp,
    accent: Color,
    baseAlpha: Float,
    highlighted: Boolean = false,
    onHoldChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val bgColor = when {
        pressed -> accent.copy(alpha = 0.55f)
        highlighted -> accent.copy(alpha = 0.88f)
        else -> WiredSurface.copy(alpha = baseAlpha)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = if (pressed || highlighted) 2.5.dp else 1.5.dp,
                color = accent.copy(alpha = if (pressed || highlighted) 1f else 0.7f),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onHoldChange(true)
                    waitForUpOrCancellation()
                    pressed = false
                    onHoldChange(false)
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}
