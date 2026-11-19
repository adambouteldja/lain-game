package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.desktop.LainDesktopScreen
import com.example.i18n.LocalStrings
import com.example.i18n.stringsForLanguage
import com.example.judo.JudoBalanceScreen
import com.example.physicslab.PhysicsLabScreen
import com.example.sandbox.PhysicsSandboxScreen
import com.example.sketch.SketchbookScreen
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.viewmodel.AppScreen
import com.example.viewmodel.LainAppViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LainAppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            LainBeyondRealityApp(viewModel = viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppPause()
    }
}

@Composable
fun LainBeyondRealityApp(viewModel: LainAppViewModel) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val studioProjects by viewModel.studioProjects.collectAsStateWithLifecycle()
    val studioAutosave by viewModel.studioAutosaveDraft.collectAsStateWithLifecycle()
    val recentAchievement by viewModel.recentAchievement.collectAsStateWithLifecycle()

    val strings = stringsForLanguage(settings.languageCode)
    val layoutDirection = if (strings.isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalStrings provides strings,
        LocalLayoutDirection provides layoutDirection
    ) {
        LainTheme(isArabic = strings.isArabic) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val current = screen) {
                    is AppScreen.MainMenu -> MainMenuScreen(
                        progress = progress,
                        settings = settings,
                        onContinue = { viewModel.continueGame() },
                        onNewGame = { viewModel.startNewGame() },
                        onSketchbook = { viewModel.navigateTo(AppScreen.Sketchbook) },
                        onWorlds = { viewModel.navigateTo(AppScreen.Worlds) },
                        onDesktop = { viewModel.navigateTo(AppScreen.LainDesktop) },
                        onSandbox = { viewModel.navigateTo(AppScreen.PhysicsSandbox()) },
                        onPhysicsLab = { viewModel.navigateTo(AppScreen.PhysicsLab) },
                        onAchievements = { viewModel.navigateTo(AppScreen.Achievements) },
                        onSettings = { viewModel.navigateTo(AppScreen.Settings) }
                    )

                    is AppScreen.Worlds -> WorldsScreen(
                        progress = progress,
                        onSelectLevel = { levelId -> viewModel.enterLevel(levelId) },
                        onOpenSketchbook = { viewModel.navigateTo(AppScreen.Sketchbook) },
                        onOpenSandbox = { viewModel.navigateTo(AppScreen.PhysicsSandbox()) },
                        onOpenJudoTrial = { trial -> viewModel.navigateTo(AppScreen.JudoStandalone(trial)) },
                        onBack = { viewModel.navigateTo(AppScreen.MainMenu) }
                    )

                    is AppScreen.Gameplay -> GamePlayScreen(
                        levelId = current.levelId,
                        progress = progress,
                        settings = settings,
                        onPlaySfx = { viewModel.playSfx(it) },
                        onToggleMute = { viewModel.toggleMute() },
                        onShardCollected = { lvl, shard -> viewModel.onShardCollected(lvl, shard) },
                        onJudoTrialCompleted = { trial, score -> viewModel.onJudoTrialCompleted(trial, score) },
                        onLevelCompleted = { lvl, shards -> viewModel.onLevelCompleted(lvl, shards) },
                        onSelectNextLevel = { nextId -> viewModel.enterLevel(nextId) },
                        onOpenDesktop = { viewModel.navigateTo(AppScreen.LainDesktop) },
                        onExitToWorlds = { viewModel.navigateTo(AppScreen.Worlds) },
                        onExitToMenu = { viewModel.navigateTo(AppScreen.MainMenu) }
                    )

                    is AppScreen.Sketchbook -> SketchbookScreen(
                        strings = strings,
                        savedProjects = studioProjects,
                        autosaveDraft = studioAutosave,
                        onSaveProject = { project -> viewModel.saveStudioProject(project) },
                        onAutosaveProject = { draft -> viewModel.autosaveStudioProject(draft) },
                        onDuplicateProject = { src -> viewModel.duplicateStudioProject(src) },
                        onDeleteProject = { id -> viewModel.deleteStudioProject(id) },
                        onBack = { viewModel.navigateTo(AppScreen.MainMenu) }
                    )

                    is AppScreen.JudoStandalone -> JudoBalanceScreen(
                        trialId = current.trialId,
                        bestScore = progress.bestJudoScores[current.trialId] ?: 0,
                        onPlaySfx = { viewModel.playSfx(it) },
                        onVictory = { score ->
                            viewModel.onJudoTrialCompleted(current.trialId, score)
                            viewModel.navigateTo(AppScreen.Worlds)
                        },
                        onExit = { viewModel.navigateTo(AppScreen.Worlds) }
                    )

                    is AppScreen.LainDesktop -> LainDesktopScreen(
                        settings = settings,
                        readMessageIds = progress.readMessageIds,
                        onMarkMessageRead = { msgId -> viewModel.markMessageRead(msgId) },
                        onUpdateSettings = { updated -> viewModel.updateSettings(updated) },
                        onOpenStudio = { viewModel.navigateTo(AppScreen.Sketchbook) },
                        onOpenSandbox = { viewModel.navigateTo(AppScreen.PhysicsSandbox()) },
                        onOpenPhysicsLab = { viewModel.navigateTo(AppScreen.PhysicsLab) },
                        onBack = { viewModel.navigateTo(AppScreen.MainMenu) }
                    )

                    is AppScreen.PhysicsSandbox -> PhysicsSandboxScreen(
                        initialPreset = current.preset,
                        onBack = { viewModel.navigateTo(AppScreen.MainMenu) }
                    )

                    is AppScreen.PhysicsLab -> PhysicsLabScreen(
                        isArabic = strings.isArabic,
                        completedQuizIds = progress.completedLabQuizzes,
                        onQuizCompleted = { quizId -> viewModel.onLabQuizCompleted(quizId) },
                        onLaunchSandboxPreset = { preset ->
                            viewModel.navigateTo(AppScreen.PhysicsSandbox(preset))
                        },
                        onBack = { viewModel.navigateTo(AppScreen.MainMenu) }
                    )

                    is AppScreen.Achievements -> AchievementsScreen(
                        progress = progress,
                        onBack = { viewModel.navigateTo(AppScreen.MainMenu) }
                    )

                    is AppScreen.Settings -> SettingsScreen(
                        settings = settings,
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        onBack = { viewModel.navigateTo(AppScreen.MainMenu) }
                    )
                }

                // Live Achievement Unlocked Toast Banner
                AnimatedVisibility(
                    visible = recentAchievement != null,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                ) {
                    val achId = recentAchievement
                    if (achId != null) {
                        Surface(
                            color = WiredSurface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.border(1.5.dp, DeskAmber, RoundedCornerShape(14.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = DeskAmber,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = strings.achievementUnlockedToast,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DeskAmber
                                    )
                                    Text(
                                        text = strings.achievementTitles[achId] ?: achId.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
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
