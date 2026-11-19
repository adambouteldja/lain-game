package com.example.data

enum class WorldId(val index: Int) {
    LAINS_ROOM(0),
    GRAVITY(1),
    CREATION(2),
    ENERGY(3),
    BALANCE(4),
    THE_WIRED(5)
}

enum class ControlSize {
    COMPACT,
    STANDARD,
    LARGE
}

enum class AchievementId {
    FIRST_SIGNAL,
    GRAVITY_DEFIER,
    DIGITAL_ARTIST,
    BEAM_ARCHITECT,
    MAGNETIC_MIND,
    KUZUSHI_MASTER,
    MEMORY_SEEKER,
    INK_WEAVER,
    BEYOND_REALITY,
    COMPLETE_ARCHIVE
}

enum class JudoTrialId(val index: Int) {
    STILL_CENTER(0),
    KUZUSHI_REDIRECT(1),
    EQUILIBRIUM_FLOW(2)
}

data class GameSettings(
    val languageCode: String = "en", // "en" or "ar"
    val musicVolume: Float = 0.55f,
    val sfxVolume: Float = 0.75f,
    val isMuted: Boolean = false,
    val controlSize: ControlSize = ControlSize.STANDARD,
    val reduceShake: Boolean = false
)

data class SaveProgress(
    val hasStarted: Boolean = false,
    val currentLevelId: String = "1-1",
    val unlockedWorlds: Set<WorldId> = setOf(WorldId.LAINS_ROOM),
    val completedLevels: Set<String> = emptySet(),
    val collectedShardsByLevel: Map<String, Set<String>> = emptyMap(),
    val completedJudoTrials: Set<JudoTrialId> = emptySet(),
    val bestJudoScores: Map<JudoTrialId, Int> = emptyMap(),
    val unlockedAchievements: Set<AchievementId> = emptySet(),
    val totalSketchesSaved: Int = 0
) {
    fun totalShardsCollected(): Int = collectedShardsByLevel.values.sumOf { it.size }
}

data class SketchPoint(
    val x: Float,
    val y: Float
)

data class SketchStroke(
    val points: List<SketchPoint>,
    val colorArgb: Long,
    val strokeWidth: Float,
    val isEraser: Boolean
)

data class SketchDocument(
    val id: String,
    val title: String,
    val updatedAtMillis: Long,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val strokes: List<SketchStroke>
)
