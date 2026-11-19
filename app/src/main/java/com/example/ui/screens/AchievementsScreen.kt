package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AchievementId
import com.example.data.SaveProgress
import com.example.i18n.LocalStrings
import com.example.ui.theme.*

@Composable
fun AchievementsScreen(
    progress: SaveProgress,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalStrings.current
    val total = AchievementId.entries.size
    val unlockedCount = progress.unlockedAchievements.size

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("achievements_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back,
                            tint = TextPrimary
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = strings.achievementsHeader,
                        style = MaterialTheme.typography.headlineMedium,
                        color = HarmonyJade
                    )
                }

                Surface(
                    color = WiredSurface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.border(1.dp, HarmonyJade.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = "$unlockedCount / $total ${strings.unlockedCount}",
                        style = MaterialTheme.typography.labelLarge,
                        color = HarmonyJade,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                items(AchievementId.entries, key = { it.name }) { id ->
                    val unlocked = id in progress.unlockedAchievements
                    Surface(
                        color = if (unlocked) WiredSurface else WiredAbyss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (unlocked) HarmonyJade.copy(alpha = 0.65f) else WiredBorder.copy(alpha = 0.4f),
                                RoundedCornerShape(14.dp)
                            )
                            .testTag("achievement_${id.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = if (unlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (unlocked) DeskAmber else TextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.achievementTitles[id] ?: id.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (unlocked) TextPrimary else TextMuted,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = strings.achievementDescriptions[id] ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            if (unlocked) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = strings.unlockedCount,
                                    tint = HarmonyJade,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
