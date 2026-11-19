package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AmbienceMood
import com.example.audio.SoundEffect
import com.example.audio.SoundEngine
import com.example.data.*
import com.example.game.engine.LevelCatalog
import com.example.sandbox.SandboxPreset
import com.example.sketch.StudioProject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppScreen {
    data object MainMenu : AppScreen
    data object Worlds : AppScreen
    data class Gameplay(val levelId: String) : AppScreen
    data object Sketchbook : AppScreen
    data class JudoStandalone(val trialId: JudoTrialId) : AppScreen
    data object LainDesktop : AppScreen
    data class PhysicsSandbox(val preset: SandboxPreset = SandboxPreset.FREE_FALL) : AppScreen
    data object PhysicsLab : AppScreen
    data object Achievements : AppScreen
    data object Settings : AppScreen
}

class LainAppViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = GameStorage(application.applicationContext)
    private val soundEngine = SoundEngine()

    private val _screen = MutableStateFlow<AppScreen>(AppScreen.MainMenu)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _progress = MutableStateFlow(storage.loadProgress())
    val progress: StateFlow<SaveProgress> = _progress.asStateFlow()

    private val _settings = MutableStateFlow(storage.loadSettings())
    val settings: StateFlow<GameSettings> = _settings.asStateFlow()

    private val _studioProjects = MutableStateFlow<List<StudioProject>>(emptyList())
    val studioProjects: StateFlow<List<StudioProject>> = _studioProjects.asStateFlow()

    private val _studioAutosaveDraft = MutableStateFlow<StudioProject?>(null)
    val studioAutosaveDraft: StateFlow<StudioProject?> = _studioAutosaveDraft.asStateFlow()

    private val _recentAchievement = MutableStateFlow<AchievementId?>(null)
    val recentAchievement: StateFlow<AchievementId?> = _recentAchievement.asStateFlow()

    init {
        storage.migrateLegacySketchesIfNeeded()
        _studioProjects.value = storage.loadAllStudioProjects()
        _studioAutosaveDraft.value = storage.loadStudioAutosave()
        soundEngine.updateSettings(_settings.value)
        soundEngine.setAmbienceMood(AmbienceMood.ROOM)
    }

    fun onAppResume() {
        soundEngine.onResume()
    }

    fun onAppPause() {
        soundEngine.onPause()
    }

    override fun onCleared() {
        super.onCleared()
        soundEngine.release()
    }

    fun playSfx(effect: SoundEffect) {
        soundEngine.playSfx(effect)
    }

    fun navigateTo(target: AppScreen) {
        when (target) {
            is AppScreen.LainDesktop -> {
                playSfx(SoundEffect.DESKTOP_BOOT)
                soundEngine.setAmbienceMood(AmbienceMood.WIRED)
            }
            is AppScreen.PhysicsSandbox -> {
                playSfx(SoundEffect.UI_TAP)
                soundEngine.setAmbienceMood(AmbienceMood.WIRED)
                onSandboxOpened()
            }
            is AppScreen.PhysicsLab -> {
                playSfx(SoundEffect.UI_TAP)
                soundEngine.setAmbienceMood(AmbienceMood.SCHOOL)
            }
            is AppScreen.JudoStandalone -> {
                playSfx(SoundEffect.UI_TAP)
                soundEngine.setAmbienceMood(AmbienceMood.DOJO)
            }
            is AppScreen.Gameplay -> {
                playSfx(SoundEffect.UI_TAP)
                soundEngine.setAmbienceForWorld(LevelCatalog.worldForLevel(target.levelId))
            }
            else -> {
                playSfx(SoundEffect.UI_TAP)
                soundEngine.setAmbienceMood(AmbienceMood.ROOM)
            }
        }
        _screen.value = target
    }

    fun continueGame() {
        val current = _progress.value
        val startId = current.currentLevelId.ifBlank { "1-1" }
        val updated = current.copy(hasStarted = true, currentLevelId = startId)
        persistProgress(updated)
        navigateTo(AppScreen.Gameplay(startId))
    }

    fun startNewGame() {
        val cur = _progress.value
        val preservedArtist = if (AchievementId.DIGITAL_ARTIST in cur.unlockedAchievements) {
            setOf(AchievementId.DIGITAL_ARTIST)
        } else emptySet()
        val fresh = SaveProgress(
            schemaVersion = SaveProgress.CURRENT_SCHEMA_VERSION,
            hasStarted = true,
            currentLevelId = "1-1",
            unlockedWorlds = setOf(WorldId.LAINS_ROOM),
            unlockedAchievements = preservedArtist,
            totalSketchesSaved = cur.totalSketchesSaved,
            completedLabQuizzes = cur.completedLabQuizzes
        )
        persistProgress(fresh)
        navigateTo(AppScreen.Gameplay("1-1"))
    }

    fun enterLevel(levelId: String) {
        val updated = _progress.value.copy(hasStarted = true, currentLevelId = levelId)
        persistProgress(updated)
        navigateTo(AppScreen.Gameplay(levelId))
    }

    fun onShardCollected(levelId: String, shardId: String) {
        val cur = _progress.value
        val existing = cur.collectedShardsByLevel[levelId].orEmpty()
        if (shardId in existing) return
        val newMap = cur.collectedShardsByLevel.toMutableMap()
        newMap[levelId] = existing + shardId
        val updated = evaluateAchievements(cur.copy(collectedShardsByLevel = newMap))
        persistProgress(updated)
    }

    fun onLevelCompleted(levelId: String, collectedShards: Set<String>) {
        val cur = _progress.value
        val newCompleted = cur.completedLevels + levelId
        val newMap = cur.collectedShardsByLevel.toMutableMap()
        newMap[levelId] = (newMap[levelId].orEmpty() + collectedShards)

        val nextId = LevelCatalog.nextLevelId(levelId)
        val newWorlds = cur.unlockedWorlds.toMutableSet()
        if (nextId != null) {
            newWorlds.add(LevelCatalog.worldForLevel(nextId))
        }
        // Keep progression welcoming: finishing 1-1 unlocks Family Home; finishing 1-2 unlocks Gravity too
        if (levelId == "1-1") newWorlds.add(WorldId.FAMILY_HOME)
        if (levelId == "1-2") newWorlds.add(WorldId.GRAVITY)

        val updated = evaluateAchievements(
            cur.copy(
                hasStarted = true,
                currentLevelId = nextId ?: levelId,
                unlockedWorlds = newWorlds,
                completedLevels = newCompleted,
                collectedShardsByLevel = newMap
            )
        )
        persistProgress(updated)
    }

    fun onJudoTrialCompleted(trialId: JudoTrialId, score: Int) {
        val cur = _progress.value
        val bestMap = cur.bestJudoScores.toMutableMap()
        bestMap[trialId] = maxOf(bestMap[trialId] ?: 0, score)
        val updated = evaluateAchievements(
            cur.copy(
                completedJudoTrials = cur.completedJudoTrials + trialId,
                bestJudoScores = bestMap
            )
        )
        persistProgress(updated)
    }

    fun saveStudioProject(project: StudioProject) {
        if (storage.saveStudioProject(project)) {
            storage.clearStudioAutosave()
            _studioAutosaveDraft.value = null
            _studioProjects.value = storage.loadAllStudioProjects()
            val cur = _progress.value
            val updated = evaluateAchievements(
                cur.copy(totalSketchesSaved = cur.totalSketchesSaved + 1)
            )
            persistProgress(updated)
            playSfx(SoundEffect.CHECKPOINT)
        }
    }

    fun autosaveStudioProject(project: StudioProject) {
        storage.saveStudioAutosave(project)
        _studioAutosaveDraft.value = project
    }

    fun duplicateStudioProject(source: StudioProject) {
        val copy = storage.duplicateStudioProject(source, "${source.title} (Copy)")
        if (copy != null) {
            _studioProjects.value = storage.loadAllStudioProjects()
            playSfx(SoundEffect.UI_TAP)
        }
    }

    fun deleteStudioProject(id: String) {
        if (storage.deleteStudioProject(id)) {
            _studioProjects.value = storage.loadAllStudioProjects()
            playSfx(SoundEffect.UI_TAP)
        }
    }

    fun markMessageRead(messageId: String) {
        val cur = _progress.value
        if (messageId in cur.readMessageIds) return
        val updated = evaluateAchievements(
            cur.copy(readMessageIds = cur.readMessageIds + messageId)
        )
        persistProgress(updated)
    }

    fun onLabQuizCompleted(quizId: String) {
        val cur = _progress.value
        if (quizId in cur.completedLabQuizzes) return
        val updated = cur.copy(completedLabQuizzes = cur.completedLabQuizzes + quizId)
        persistProgress(updated)
        playSfx(SoundEffect.CHECKPOINT)
    }

    fun onSandboxOpened() {
        val cur = _progress.value
        val updated = evaluateAchievements(
            cur.copy(openedSandboxCount = cur.openedSandboxCount + 1)
        )
        persistProgress(updated)
    }

    fun updateSettings(newSettings: GameSettings) {
        _settings.value = newSettings
        storage.saveSettings(newSettings)
        soundEngine.updateSettings(newSettings)
    }

    fun toggleMute() {
        val cur = _settings.value
        updateSettings(cur.copy(isMuted = !cur.isMuted))
    }

    private fun evaluateAchievements(p: SaveProgress): SaveProgress {
        val newlyUnlocked = mutableListOf<AchievementId>()
        val unlocked = p.unlockedAchievements.toMutableSet()

        fun grant(id: AchievementId, condition: Boolean) {
            if (condition && id !in unlocked) {
                unlocked.add(id)
                newlyUnlocked.add(id)
            }
        }

        grant(AchievementId.FIRST_SIGNAL, "1-1" in p.completedLevels)
        grant(AchievementId.GRAVITY_DEFIER, "2-1" in p.completedLevels && "2-2" in p.completedLevels)
        grant(AchievementId.DIGITAL_ARTIST, p.totalSketchesSaved >= 1)
        grant(AchievementId.INK_WEAVER, "3-1" in p.completedLevels && "3-2" in p.completedLevels)
        grant(AchievementId.BEAM_ARCHITECT, "4-1" in p.completedLevels)
        grant(AchievementId.MAGNETIC_MIND, "4-2" in p.completedLevels)
        grant(AchievementId.KUZUSHI_MASTER, p.completedJudoTrials.size >= JudoTrialId.entries.size)
        grant(AchievementId.MEMORY_SEEKER, p.totalShardsCollected() >= 15)
        grant(AchievementId.BEYOND_REALITY, "6-2" in p.completedLevels)
        grant(AchievementId.COMPLETE_ARCHIVE, p.completedLevels.size >= LevelCatalog.allLevels.size)
        grant(
            AchievementId.CITY_WANDERER,
            setOf("H-1", "S-1", "C-1", "Y-1").all { it in p.completedLevels }
        )
        grant(AchievementId.NAVI_OPERATOR, p.readMessageIds.isNotEmpty())
        grant(AchievementId.SANDBOX_SCIENTIST, p.openedSandboxCount >= 1)
        grant(AchievementId.UKEMI_ADEPT, JudoTrialId.UKEMI_REACTION in p.completedJudoTrials)

        if (newlyUnlocked.isNotEmpty()) {
            showAchievementToast(newlyUnlocked.first())
        }
        return p.copy(unlockedAchievements = unlocked)
    }

    private fun showAchievementToast(id: AchievementId) {
        viewModelScope.launch {
            _recentAchievement.value = id
            delay(3200L)
            if (_recentAchievement.value == id) {
                _recentAchievement.value = null
            }
        }
    }

    private fun persistProgress(updated: SaveProgress) {
        _progress.value = updated
        storage.saveProgress(updated)
    }
}
