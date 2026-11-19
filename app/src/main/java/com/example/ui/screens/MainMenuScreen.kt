package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.SaveProgress
import com.example.game.render.LainsRoomBackdrop
import com.example.i18n.LocalStrings
import com.example.ui.theme.*

@Composable
fun MainMenuScreen(
    progress: SaveProgress,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onSketchbook: () -> Unit,
    onWorlds: () -> Unit,
    onAchievements: () -> Unit,
    onSettings: () -> Unit
) {
    val strings = LocalStrings.current
    var showNewGameDialog by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(WiredVoid)
    ) {
        val isLandscape = maxWidth > maxHeight

        // Animated Lain in her bedroom with her Navi computer
        LainsRoomBackdrop(modifier = Modifier.fillMaxSize())

        // Atmospheric readability gradient scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            WiredVoid.copy(alpha = 0.65f),
                            Color.Transparent,
                            WiredVoid.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side: Title & atmospheric status
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TitleBadge(strings.appSubtitle)
                    Text(
                        text = strings.appTitle,
                        style = MaterialTheme.typography.displayLarge,
                        color = TextPrimary
                    )
                    if (progress.hasStarted) {
                        Text(
                            text = "${strings.shardsLabel}: ${progress.totalShardsCollected()} / 36  •  ${progress.completedLevels.size} / 12",
                            style = MaterialTheme.typography.labelLarge,
                            color = NaviCyan
                        )
                    }
                }

                // Right side: Compact Glass Menu Deck
                Column(
                    modifier = Modifier
                        .widthIn(min = 260.dp, max = 330.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MenuButtonsList(
                        hasStarted = progress.hasStarted,
                        onContinue = onContinue,
                        onNewGameClick = {
                            if (progress.hasStarted) showNewGameDialog = true else onNewGame()
                        },
                        onSketchbook = onSketchbook,
                        onWorlds = onWorlds,
                        onAchievements = onAchievements,
                        onSettings = onSettings
                    )
                }
            }
        } else {
            // Portrait: Upper room scene unobstructed, lower crisp menu deck
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TitleBadge(strings.appSubtitle)
                    Text(
                        text = strings.appTitle,
                        style = MaterialTheme.typography.displayLarge,
                        color = TextPrimary
                    )
                    if (progress.hasStarted) {
                        Text(
                            text = "${strings.shardsLabel}: ${progress.totalShardsCollected()} / 36  •  ${progress.completedLevels.size} / 12",
                            style = MaterialTheme.typography.labelLarge,
                            color = NaviCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp)
                        .align(Alignment.CenterHorizontally)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MenuButtonsList(
                        hasStarted = progress.hasStarted,
                        onContinue = onContinue,
                        onNewGameClick = {
                            if (progress.hasStarted) showNewGameDialog = true else onNewGame()
                        },
                        onSketchbook = onSketchbook,
                        onWorlds = onWorlds,
                        onAchievements = onAchievements,
                        onSettings = onSettings
                    )
                }
            }
        }
    }

    if (showNewGameDialog) {
        AlertDialog(
            onDismissRequest = { showNewGameDialog = false },
            containerColor = WiredSurface,
            title = { Text(strings.newGameConfirmTitle, color = TextPrimary) },
            text = { Text(strings.newGameConfirmBody, color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showNewGameDialog = false
                        onNewGame()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SignalCoral),
                    modifier = Modifier.testTag("confirm_new_game_button")
                ) {
                    Text(strings.confirm, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewGameDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun TitleBadge(subtitle: String) {
    Surface(
        color = WiredSurface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(1.dp, NaviCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
    ) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = NaviCyan,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MenuButtonsList(
    hasStarted: Boolean,
    onContinue: () -> Unit,
    onNewGameClick: () -> Unit,
    onSketchbook: () -> Unit,
    onWorlds: () -> Unit,
    onAchievements: () -> Unit,
    onSettings: () -> Unit
) {
    val strings = LocalStrings.current

    // Primary Action: Continue (or start from 1-1 cleanly if fresh)
    Button(
        onClick = onContinue,
        colors = ButtonDefaults.buttonColors(
            containerColor = NaviCyan,
            contentColor = WiredVoid
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("menu_continue_button")
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (hasStarted) strings.menuContinue else "${strings.menuContinue} (1-1)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }

    // Secondary row: New Game & Worlds
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MenuTileButton(
            label = strings.menuNewGame,
            icon = Icons.Default.Refresh,
            accent = SignalCoral,
            onClick = onNewGameClick,
            modifier = Modifier
                .weight(1f)
                .testTag("menu_new_game_button")
        )
        MenuTileButton(
            label = strings.menuWorlds,
            icon = Icons.Default.Public,
            accent = NaviCyan,
            onClick = onWorlds,
            modifier = Modifier
                .weight(1f)
                .testTag("menu_worlds_button")
        )
    }

    // Sketchbook (Standalone Drawing Studio)
    MenuTileButton(
        label = strings.menuSketchbook,
        icon = Icons.Default.Brush,
        accent = DeskAmber,
        onClick = onSketchbook,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("menu_sketchbook_button")
    )

    // Achievements & Settings
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MenuTileButton(
            label = strings.menuAchievements,
            icon = Icons.Default.EmojiEvents,
            accent = HarmonyJade,
            onClick = onAchievements,
            modifier = Modifier
                .weight(1f)
                .testTag("menu_achievements_button")
        )
        MenuTileButton(
            label = strings.menuSettings,
            icon = Icons.Default.Settings,
            accent = WiredViolet,
            onClick = onSettings,
            modifier = Modifier
                .weight(1f)
                .testTag("menu_settings_button")
        )
    }
}

@Composable
private fun MenuTileButton(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = WiredSurface.copy(alpha = 0.88f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}
