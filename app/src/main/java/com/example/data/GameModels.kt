package com.example.data

enum class WorldId(val index: Int, val isCanonLocation: Boolean) {
    LAINS_ROOM(0, true),
    FAMILY_HOME(1, true),
    SCHOOL(2, true),
    STREETS(3, true),
    CYBERIA(4, true),
    GRAVITY(5, false),
    CREATION(6, false),
    ENERGY(7, false),
    BALANCE(8, false),
    THE_WIRED(9, true)
}

enum class LainOutfit(val isCanon: Boolean) {
    HOME_SWEATER(true),
    SCHOOL_UNIFORM(true),
    CYBERIA_JACKET(true),
    WIRED_CAMISOLE(true),
    JUDO_GI(false) // Original game element, not anime canon
}

enum class ControlSize {
    COMPACT,
    STANDARD,
    LARGE
}

enum class ControlMode {
    BUTTONS,
    JOYSTICK
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
    COMPLETE_ARCHIVE,
    CITY_WANDERER,
    NAVI_OPERATOR,
    SANDBOX_SCIENTIST,
    UKEMI_ADEPT
}

enum class JudoTrialId(val index: Int) {
    STILL_CENTER(0),
    KUZUSHI_REDIRECT(1),
    UKEMI_REACTION(2),
    EQUILIBRIUM_FLOW(3)
}

enum class DesktopThemeTint {
    NAVI_CYAN,
    CYBERIA_MAGENTA,
    AMBER_CRT
}

data class GameSettings(
    val languageCode: String = "en", // "en" or "ar"
    val musicVolume: Float = 0.55f,
    val sfxVolume: Float = 0.75f,
    val isMuted: Boolean = false,
    val controlSize: ControlSize = ControlSize.STANDARD,
    val controlMode: ControlMode = ControlMode.BUTTONS,
    val controlHorizontalPaddingDp: Float = 14f, // 4..36dp customizable button inset
    val controlBottomOffsetDp: Float = 10f, // 0..36dp customizable vertical lift
    val reduceShake: Boolean = false,
    val autoOutfitByLocation: Boolean = true,
    val preferredOutfit: LainOutfit = LainOutfit.HOME_SWEATER,
    val desktopTheme: DesktopThemeTint = DesktopThemeTint.NAVI_CYAN,
    val desktopScanlines: Boolean = true
)

data class SaveProgress(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val hasStarted: Boolean = false,
    val currentLevelId: String = "1-1",
    val unlockedWorlds: Set<WorldId> = setOf(WorldId.LAINS_ROOM),
    val completedLevels: Set<String> = emptySet(),
    val collectedShardsByLevel: Map<String, Set<String>> = emptyMap(),
    val completedJudoTrials: Set<JudoTrialId> = emptySet(),
    val bestJudoScores: Map<JudoTrialId, Int> = emptyMap(),
    val unlockedAchievements: Set<AchievementId> = emptySet(),
    val totalSketchesSaved: Int = 0,
    val readMessageIds: Set<String> = emptySet(),
    val completedLabQuizzes: Set<String> = emptySet(),
    val openedSandboxCount: Int = 0
) {
    fun totalShardsCollected(): Int = collectedShardsByLevel.values.sumOf { it.size }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

// Legacy v1 Sketch models retained for backward-compatible migration
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
