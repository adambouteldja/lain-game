package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.JudoTrialId
import com.example.data.SaveProgress
import com.example.data.WorldId
import com.example.game.engine.LevelCatalog
import com.example.i18n.LocalStrings
import com.example.ui.theme.*

@Composable
fun WorldsScreen(
    progress: SaveProgress,
    onSelectLevel: (String) -> Unit,
    onOpenSketchbook: () -> Unit,
    onOpenJudoTrial: (JudoTrialId) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalStrings.current

    Scaffold(
        containerColor = WiredVoid,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("worlds_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.back,
                        tint = TextPrimary
                    )
                }
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        text = strings.worldsHeader,
                        style = MaterialTheme.typography.headlineMedium,
                        color = NaviCyan
                    )
                    Text(
                        text = strings.worldsSubheader,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                items(WorldId.entries, key = { it.name }) { worldId ->
                    val unlocked = worldId in progress.unlockedWorlds
                    val levels = LevelCatalog.levelsForWorld(worldId)
                    val accent = worldAccent(worldId)

                    Surface(
                        color = WiredSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (unlocked) accent.copy(alpha = 0.55f) else WiredBorder.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = worldIcon(worldId),
                                        contentDescription = null,
                                        tint = if (unlocked) accent else TextMuted,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = strings.worldNames[worldId] ?: worldId.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (unlocked) TextPrimary else TextMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (!unlocked) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = strings.locked,
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = strings.locked,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }

                            Text(
                                text = strings.worldDescriptions[worldId] ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )

                            // Levels in this world
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                levels.forEachIndexed { idx, level ->
                                    val prevLevelCompleted = idx == 0 || levels[idx - 1].id in progress.completedLevels
                                    val canPlay = unlocked && prevLevelCompleted
                                    val isDone = level.id in progress.completedLevels
                                    val shardsGot = progress.collectedShardsByLevel[level.id]?.size ?: 0
                                    val totalShards = level.shards.size

                                    Surface(
                                        onClick = { if (canPlay) onSelectLevel(level.id) },
                                        enabled = canPlay,
                                        color = if (canPlay) WiredAbyss else WiredVoid.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(
                                                1.dp,
                                                when {
                                                    isDone -> HarmonyJade.copy(alpha = 0.65f)
                                                    canPlay -> accent.copy(alpha = 0.45f)
                                                    else -> WiredBorder.copy(alpha = 0.3f)
                                                },
                                                RoundedCornerShape(12.dp)
                                            )
                                            .testTag("level_card_${level.id}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = level.id,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (canPlay) accent else TextMuted
                                                )
                                                if (isDone) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = strings.completed,
                                                        tint = HarmonyJade,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                } else if (!canPlay) {
                                                    Icon(
                                                        imageVector = Icons.Default.Lock,
                                                        contentDescription = strings.locked,
                                                        tint = TextMuted,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = strings.levelTitles[level.id] ?: level.id,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (canPlay) TextPrimary else TextMuted,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "◆ $shardsGot / $totalShards",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (shardsGot == totalShards) DeskAmber else TextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            // Creation World independent studio shortcut
                            if (worldId == WorldId.CREATION) {
                                OutlinedButton(
                                    onClick = onOpenSketchbook,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("worlds_open_sketchbook")
                                ) {
                                    Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.openStudio)
                                }
                            }

                            // Balance World standalone Dojo Trials access
                            if (worldId == WorldId.BALANCE) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = strings.judoTrialsTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = HarmonyJade
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        JudoTrialId.entries.forEach { trial ->
                                            val done = trial in progress.completedJudoTrials
                                            OutlinedButton(
                                                onClick = { onOpenJudoTrial(trial) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 48.dp)
                                                    .testTag("judo_trial_${trial.index}")
                                            ) {
                                                Text(
                                                    text = if (done) "✓ #${trial.index + 1}" else "#${trial.index + 1}",
                                                    color = if (done) HarmonyJade else TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun worldAccent(worldId: WorldId): Color = when (worldId) {
    WorldId.LAINS_ROOM -> DeskAmber
    WorldId.GRAVITY -> WiredViolet
    WorldId.CREATION -> NaviCyan
    WorldId.ENERGY -> DeskAmber
    WorldId.BALANCE -> HarmonyJade
    WorldId.THE_WIRED -> SignalCoral
}

private fun worldIcon(worldId: WorldId) = when (worldId) {
    WorldId.LAINS_ROOM -> Icons.Default.Computer
    WorldId.GRAVITY -> Icons.Default.SwapVert
    WorldId.CREATION -> Icons.Default.Brush
    WorldId.ENERGY -> Icons.Default.Bolt
    WorldId.BALANCE -> Icons.Default.Balance
    WorldId.THE_WIRED -> Icons.Default.Hub
}
