package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundEffect
import com.example.audio.SoundEngine
import com.example.data.*
import com.example.game.engine.LevelCatalog
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

    private val _sketches = MutableStateFlow(storage.loadAllSketches())
    val sketches: StateFlow<List<SketchDocument>> = _sketches.asStateFlow()

    private val _recentAchievement = MutableStateFlow<AchievementId?>(null)
    val recentAchievement: StateFlow<AchievementId?> = _recentAchievement.asStateFlow()

    init {
        soundEngine.updateSettings(_settings.value)
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
        playSfx(SoundEffect.UI_TAP)
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
        val preservedSketchesCount = _progress.value.totalSketchesSaved
        val preservedArtist = if (AchievementId.DIGITAL_ARTIST in _progress.value.unlockedAchievements) {
            setOf(AchievementId.DIGITAL_ARTIST)
        } else emptySet()
        val fresh = SaveProgress(
            hasStarted = true,
            currentLevelId = "1-1",
            unlockedWorlds = setOf(WorldId.LAINS_ROOM),
            unlockedAchievements = preservedArtist,
            totalSketchesSaved = preservedSketchesCount
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

    fun saveSketch(doc: SketchDocument) {
        val ok = storage.saveSketch(doc)
        if (ok) {
            _sketches.value = storage.loadAllSketches()
            val cur = _progress.value
            val updated = evaluateAchievements(
                cur.copy(totalSketchesSaved = cur.totalSketchesSaved + 1)
            )
            persistProgress(updated)
        }
    }

    fun deleteSketch(id: String) {
        if (storage.deleteSketch(id)) {
            _sketches.value = storage.loadAllSketches()
        }
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
