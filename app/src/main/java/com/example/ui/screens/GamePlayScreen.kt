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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.audio.SoundEffect
import com.example.data.*
import com.example.game.engine.LevelCatalog
import com.example.game.engine.PhysicsEngine
import com.example.game.model.GameEvent
import com.example.game.model.InputState
import com.example.game.model.LainExpression
import com.example.game.render.GameWorldCanvas
import com.example.game.render.LainSpriteRenderer.drawLainPortrait
import com.example.i18n.LocalStrings
import com.example.judo.JudoBalanceScreen
import com.example.ui.theme.*
import kotlin.math.abs

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
    onOpenDesktop: () -> Unit,
    onExitToWorlds: () -> Unit,
    onExitToMenu: () -> Unit
) {
    val strings = LocalStrings.current
    val haptic = LocalHapticFeedback.current
    val levelDef = remember(levelId) { LevelCatalog.levelById(levelId) }
    val activeOutfit = remember(levelId, settings.autoOutfitByLocation, settings.preferredOutfit) {
        if (settings.autoOutfitByLocation) {
            LevelCatalog.defaultOutfitForWorld(levelDef.worldId)
        } else {
            settings.preferredOutfit
        }
    }

    var runtimeState by remember(levelId) {
        mutableStateOf(
            PhysicsEngine.createInitialState(
                def = levelDef,
                alreadyCollectedShards = progress.collectedShardsByLevel[levelId].orEmpty(),
                completedJudoTrials = progress.completedJudoTrials,
                outfit = activeOutfit
            )
        )
    }

    LaunchedEffect(activeOutfit) {
        if (runtimeState.player.outfit != activeOutfit) {
            runtimeState = PhysicsEngine.updatePlayerOutfit(runtimeState, activeOutfit)
        }
    }

    var isPaused by remember(levelId) { mutableStateOf(false) }
    var showStoryCard by remember(levelId) { mutableStateOf(true) }
    var showHintDialog by remember(levelId) { mutableStateOf(false) }
    var inkDrawMode by remember(levelId) { mutableStateOf(false) }
    var levelCompletionReported by remember(levelId) { mutableStateOf(false) }

    // Viewport dimensions from Canvas for camera clamping
    var viewportW by remember { mutableFloatStateOf(800f) }
    var viewportH by remember { mutableFloatStateOf(480f) }

    // Multi-touch & virtual analog joystick input flags
    var moveLeft by remember { mutableStateOf(false) }
    var moveRight by remember { mutableStateOf(false) }
    var analogX by remember { mutableFloatStateOf(0f) }
    var jumpHeld by remember { mutableStateOf(false) }
    var jumpJustPressed by remember { mutableStateOf(false) }
    var interactJustPressed by remember { mutableStateOf(false) }

    // Navigate to Lain Desktop if requested by Navi Desk interaction in Level 1-1
    LaunchedEffect(runtimeState.requestOpenDesktop) {
        if (runtimeState.requestOpenDesktop) {
            runtimeState = PhysicsEngine.clearDesktopRequest(runtimeState)
            onOpenDesktop()
        }
    }

    BackHandler {
        when {
            runtimeState.pendingJudoTrial != null -> {
                runtimeState = PhysicsEngine.dismissPendingJudo(runtimeState)
            }
            runtimeState.activeDialogueNpc != null -> {
                runtimeState = PhysicsEngine.dismissDialogue(runtimeState)
            }
            showHintDialog -> {
                showHintDialog = false
            }
            else -> {
                isPaused = !isPaused
            }
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
    LaunchedEffect(levelId, isPaused, showHintDialog, runtimeState.isCompleted, activeJudoTrial) {
        if (isPaused || showHintDialog || runtimeState.isCompleted || activeJudoTrial != null) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        while (!isPaused && !showHintDialog && !runtimeState.isCompleted && runtimeState.pendingJudoTrial == null) {
            val now = withFrameNanos { it }
            val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
            lastNanos = now

            val input = InputState(
                moveLeft = moveLeft,
                moveRight = moveRight,
                analogX = if (settings.controlMode == ControlMode.JOYSTICK) analogX else 0f,
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

            // Process game events (audio + subtle haptics + persistence)
            next.events.forEach { ev ->
                when (ev) {
                    GameEvent.JUMPED -> {
                        onPlaySfx(SoundEffect.JUMP)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    GameEvent.LANDED -> onPlaySfx(SoundEffect.LAND)
                    GameEvent.SHARD_COLLECTED -> {
                        onPlaySfx(SoundEffect.SHARD)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    GameEvent.SWITCH_TRIGGERED,
                    GameEvent.GRAVITY_FLIPPED,
                    GameEvent.MAGNET_TOGGLED,
                    GameEvent.MIRROR_ROTATED -> {
                        onPlaySfx(SoundEffect.SWITCH)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    GameEvent.CHECKPOINT_SAVED -> onPlaySfx(SoundEffect.CHECKPOINT)
                    GameEvent.DOOR_OPENED -> onPlaySfx(SoundEffect.CHECKPOINT)
                    GameEvent.RESPAWNED -> onPlaySfx(SoundEffect.ERROR)
                    GameEvent.LEVEL_COMPLETED -> onPlaySfx(SoundEffect.PORTAL)
                    GameEvent.INK_PLATFORM_CREATED -> onPlaySfx(SoundEffect.INK_DRAW)
                    GameEvent.NPC_TALKED -> onPlaySfx(SoundEffect.UI_TAP)
                    GameEvent.DESKTOP_OPENED -> onPlaySfx(SoundEffect.DESKTOP_BOOT)
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
    val edgePad = settings.controlHorizontalPaddingDp.coerceIn(4f, 36f).dp
    val bottomLift = settings.controlBottomOffsetDp.coerceIn(0f, 36f).dp

    fun restartCurrentLevel() {
        isPaused = false
        showHintDialog = false
        inkDrawMode = false
        levelCompletionReported = false
        analogX = 0f
        runtimeState = PhysicsEngine.createInitialState(
            def = levelDef,
            alreadyCollectedShards = progress.collectedShardsByLevel[levelId].orEmpty(),
            completedJudoTrials = progress.completedJudoTrials,
            outfit = activeOutfit
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

                GameplayTopHud(
                    levelId = levelId,
                    state = runtimeState,
                    isMuted = settings.isMuted,
                    inkDrawMode = inkDrawMode,
                    onToggleInkMode = { inkDrawMode = !inkDrawMode },
                    onClearInk = { runtimeState = PhysicsEngine.clearInkBridges(runtimeState) },
                    onOpenHint = { showHintDialog = true },
                    onToggleMute = onToggleMute,
                    onRestart = { restartCurrentLevel() },
                    onPause = { isPaused = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )

                StoryWhisperOverlay(
                    visible = showStoryCard && !isPaused && !runtimeState.isCompleted && runtimeState.activeDialogueNpc == null,
                    levelId = levelId,
                    outfit = activeOutfit,
                    elapsedTime = runtimeState.elapsedTime,
                    onDismiss = { showStoryCard = false },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = 60.dp, start = 20.dp, end = 20.dp)
                )

                GameplayTouchControls(
                    buttonSize = buttonSize,
                    controlMode = settings.controlMode,
                    isTranslucent = true,
                    interactHighlighted = runtimeState.nearbyInteractLabel != null,
                    onLeftChange = { moveLeft = it },
                    onRightChange = { moveRight = it },
                    onAnalogXChange = { analogX = it },
                    onJumpChange = { pressed ->
                        if (pressed && !jumpHeld) jumpJustPressed = true
                        jumpHeld = pressed
                    },
                    onInteractPress = { interactJustPressed = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(start = edgePad, end = edgePad, bottom = bottomLift + 8.dp)
                )
            }
        } else {
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
                        onOpenHint = { showHintDialog = true },
                        onToggleMute = onToggleMute,
                        onRestart = { restartCurrentLevel() },
                        onPause = { isPaused = true },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )

                    StoryWhisperOverlay(
                        visible = showStoryCard && !isPaused && !runtimeState.isCompleted && runtimeState.activeDialogueNpc == null,
                        levelId = levelId,
                        outfit = activeOutfit,
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
                            .padding(
                                start = edgePad,
                                end = edgePad,
                                top = 12.dp,
                                bottom = 12.dp + bottomLift
                            ),
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
                            controlMode = settings.controlMode,
                            isTranslucent = false,
                            interactHighlighted = runtimeState.nearbyInteractLabel != null,
                            onLeftChange = { moveLeft = it },
                            onRightChange = { moveRight = it },
                            onAnalogXChange = { analogX = it },
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

        // Story NPC Dialogue Banner
        val dialogueNpc = runtimeState.activeDialogueNpc
        AnimatedVisibility(
            visible = dialogueNpc != null && !isPaused && !runtimeState.isCompleted,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 110.dp)
        ) {
            if (dialogueNpc != null) {
                Surface(
                    color = WiredSurface.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .border(1.5.dp, NaviCyan.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                        .clickable { runtimeState = PhysicsEngine.dismissDialogue(runtimeState) }
                        .testTag("npc_dialogue_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(dialogueNpc.outfitColorArgb))
                                .border(1.5.dp, NaviCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = TextPrimary)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = strings.npcTexts[dialogueNpc.nameKey] ?: dialogueNpc.id,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NaviCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (dialogueNpc.isCanon) strings.canonTag else strings.originalTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DeskAmber
                                )
                            }
                            Text(
                                text = strings.npcTexts[dialogueNpc.dialogueKey] ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = strings.close,
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
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

        // Persistent HUD Hint Overlay
        if (showHintDialog) {
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
                        .widthIn(min = 280.dp, max = 380.dp)
                        .padding(20.dp)
                        .border(1.dp, DeskAmber.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = DeskAmber)
                            Text(
                                text = "${strings.hintTitle} — $levelId",
                                style = MaterialTheme.typography.titleMedium,
                                color = DeskAmber
                            )
                        }
                        Text(
                            text = strings.levelTitles[levelId] ?: levelId,
                            style = MaterialTheme.typography.labelLarge,
                            color = NaviCyan
                        )
                        Text(
                            text = strings.levelHints[levelId] ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Button(
                            onClick = { showHintDialog = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("hint_dialog_close")
                        ) {
                            Text(strings.ok)
                        }
                    }
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
                            text = "$levelId — ${strings.levelTitles[levelId] ?: ""}",
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
    onOpenHint: () -> Unit,
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
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Left: Level badge + Shards (gracefully ellipsizes on narrow portrait screens)
        Surface(
            color = WiredSurface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f, fill = false)
                .border(1.dp, WiredBorder, RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "$levelId • ${strings.levelTitles[levelId] ?: ""}",
                    style = MaterialTheme.typography.labelLarge,
                    color = NaviCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "◆ $collected/$total",
                    style = MaterialTheme.typography.labelLarge,
                    color = DeskAmber,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.weight(0.001f))

        // Right: Ink Bridge controls + Hint + Mute + Restart + Pause
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
                icon = Icons.Default.Lightbulb,
                contentDesc = strings.hudHintButton,
                tint = DeskAmber,
                onClick = onOpenHint,
                tag = "hud_hint_button"
            )
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
    tint: Color = TextPrimary,
    onClick: () -> Unit,
    tag: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(WiredSurface.copy(alpha = 0.82f))
            .border(1.dp, WiredBorder, CircleShape)
            .testTag(tag)
    ) {
        Icon(imageVector = icon, contentDescription = contentDesc, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun StoryWhisperOverlay(
    visible: Boolean,
    levelId: String,
    outfit: LainOutfit,
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
                        timeSec = elapsedTime,
                        outfit = outfit,
                        expression = LainExpression.CURIOUS
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
    controlMode: ControlMode,
    isTranslucent: Boolean,
    interactHighlighted: Boolean,
    onLeftChange: (Boolean) -> Unit,
    onRightChange: (Boolean) -> Unit,
    onAnalogXChange: (Float) -> Unit,
    onJumpChange: (Boolean) -> Unit,
    onInteractPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val baseAlpha = if (isTranslucent) 0.68f else 0.96f

    // Force physical LTR layout so movement is ALWAYS on the left and Jump/Act on the right
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (controlMode == ControlMode.JOYSTICK) {
                VirtualAnalogStick(
                    diameter = buttonSize * 1.95f,
                    baseAlpha = baseAlpha,
                    onAxisXChange = onAnalogXChange,
                    modifier = Modifier.testTag("control_joystick")
                )
            } else {
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
private fun VirtualAnalogStick(
    diameter: Dp,
    baseAlpha: Float,
    onAxisXChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var knobFractionX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .size(diameter, diameter * 0.62f)
            .clip(RoundedCornerShape(48.dp))
            .background(WiredSurface.copy(alpha = baseAlpha))
            .border(1.5.dp, NaviCyan.copy(alpha = 0.72f), RoundedCornerShape(48.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val halfW = (size.width * 0.42f).coerceAtLeast(1f)
                    val centerX = size.width * 0.5f

                    fun update(px: Float) {
                        val raw = ((px - centerX) / halfW).coerceIn(-1f, 1f)
                        val deadzone = if (abs(raw) < 0.12f) 0f else raw
                        knobFractionX = deadzone
                        onAxisXChange(deadzone)
                    }

                    update(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            knobFractionX = 0f
                            onAxisXChange(0f)
                            break
                        }
                        update(change.position.x)
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height * 0.5f
            val cx = size.width * 0.5f
            val maxShift = size.width * 0.30f
            drawLine(
                color = NaviCyan.copy(alpha = 0.25f),
                start = Offset(cx - maxShift, cy),
                end = Offset(cx + maxShift, cy),
                strokeWidth = 6f
            )
            val knobX = cx + knobFractionX * maxShift
            drawCircle(
                color = NaviCyan.copy(alpha = 0.35f),
                radius = size.height * 0.38f,
                center = Offset(knobX, cy)
            )
            drawCircle(
                color = NaviCyan,
                radius = size.height * 0.28f,
                center = Offset(knobX, cy)
            )
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
