package com.example.game.model

import com.example.data.JudoTrialId
import com.example.data.LainOutfit
import com.example.data.WorldId
import kotlin.math.abs
import kotlin.math.tan

data class Vec2(val x: Float = 0f, val y: Float = 0f) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
}

data class RectBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    val left: Float get() = x
    val top: Float get() = y
    val right: Float get() = x + width
    val bottom: Float get() = y + height
    val centerX: Float get() = x + width * 0.5f
    val centerY: Float get() = y + height * 0.5f

    fun overlaps(other: RectBounds): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

enum class PlatformType {
    SOLID,
    ICE,
    STICKY,
    MOVING,
    HARD_LIGHT,
    DRAWN_INK,
    TILTING_BEAM
}

data class Platform(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val type: PlatformType = PlatformType.SOLID,
    // Moving platform fields
    val startX: Float = x,
    val startY: Float = y,
    val endX: Float = x,
    val endY: Float = y,
    val moveSpeed: Float = 0f,
    val movePhase: Float = 0f,
    val deltaX: Float = 0f,
    val deltaY: Float = 0f,
    // Hard-light conditional bridge
    val linkedSignalId: String? = null,
    val isSolidActive: Boolean = true,
    // Tilting balance beam & sloped drawn ink state
    val tiltAngleDeg: Float = 0f,
    val tiltAngularVel: Float = 0f
) {
    val left: Float get() = x
    val top: Float get() = y
    val right: Float get() = x + width
    val bottom: Float get() = y + height

    fun bounds(): RectBounds = RectBounds(x, y, width, height)

    fun isSlopedSurface(): Boolean =
        type == PlatformType.TILTING_BEAM || (type == PlatformType.DRAWN_INK && abs(tiltAngleDeg) > 0.5f)

    fun surfaceYAt(worldX: Float): Float {
        if (!isSlopedSurface()) return y
        val pivotX = x + width * 0.5f
        val rad = Math.toRadians(tiltAngleDeg.toDouble())
        val dx = (worldX - pivotX).coerceIn(-width * 0.5f, width * 0.5f)
        return y + (dx * tan(rad)).toFloat()
    }
}

data class PhysicsBox(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float = 44f,
    val height: Float = 44f,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val isMetal: Boolean = false,
    val mass: Float = 1.0f,
    val onGround: Boolean = false
) {
    fun bounds(): RectBounds = RectBounds(x, y, width, height)
}

enum class SwitchKind {
    PRESSURE_PLATE,
    TERMINAL,
    PHOTO_SENSOR,
    GRAVITY_SWITCH,
    MAGNET_SWITCH
}

data class SwitchNode(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float = 54f,
    val height: Float = 16f,
    val kind: SwitchKind,
    val signalId: String,
    val isActive: Boolean = false,
    val requiresWeight: Boolean = false
) {
    fun bounds(): RectBounds = RectBounds(x, y, width, height)
}

enum class MagnetMode {
    ATTRACT,
    REPEL
}

data class MagnetCoil(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float = 44f,
    val height: Float = 44f,
    val mode: MagnetMode = MagnetMode.ATTRACT,
    val range: Float = 360f,
    val strength: Float = 1050f,
    val linkedSignalId: String? = null,
    val isPowered: Boolean = true
) {
    fun bounds(): RectBounds = RectBounds(x, y, width, height)
}

data class LightEmitter(
    val id: String,
    val x: Float,
    val y: Float,
    val dirX: Float,
    val dirY: Float
)

data class MirrorNode(
    val id: String,
    val x: Float,
    val y: Float,
    val radius: Float = 26f,
    /** Angle index 0..7 in 45-degree increments for mirror reflection direction */
    val angleStep: Int = 1
) {
    fun bounds(): RectBounds = RectBounds(x - radius, y - radius, radius * 2f, radius * 2f)
}

data class LightBeamSegment(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float
)

enum class GravityZoneMode {
    LOW_GRAVITY,
    INVERTED
}

data class GravityZone(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val mode: GravityZoneMode
) {
    fun bounds(): RectBounds = RectBounds(x, y, width, height)
}

data class Door(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float = 28f,
    val height: Float = 104f,
    val linkedSignalId: String,
    val isOpen: Boolean = false
) {
    fun bounds(): RectBounds = RectBounds(x, y, width, height)
}

data class CollectibleShard(
    val id: String,
    val x: Float,
    val y: Float,
    val collected: Boolean = false
) {
    fun bounds(): RectBounds = RectBounds(x - 16f, y - 16f, 32f, 32f)
}

data class Checkpoint(
    val id: String,
    val x: Float,
    val y: Float,
    val activated: Boolean = false
) {
    fun bounds(): RectBounds = RectBounds(x - 20f, y - 64f, 40f, 64f)
}

data class HazardZone(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    fun bounds(): RectBounds = RectBounds(x, y, width, height)
}

data class JudoShrine(
    val id: String,
    val x: Float,
    val y: Float,
    val trialId: JudoTrialId,
    val signalId: String,
    val completed: Boolean = false
) {
    fun bounds(): RectBounds = RectBounds(x - 32f, y - 74f, 64f, 74f)
}

data class StoryNpc(
    val id: String,
    val x: Float,
    val y: Float,
    val nameKey: String,
    val dialogueKey: String,
    val isCanon: Boolean = true,
    val outfitColorArgb: Long = 0xFF424752L,
    val hairColorArgb: Long = 0xFF1F2429L,
    val opensDesktop: Boolean = false
) {
    fun bounds(): RectBounds = RectBounds(x - 24f, y - 68f, 48f, 68f)
}

data class PortalExit(
    val x: Float,
    val y: Float,
    val width: Float = 56f,
    val height: Float = 86f
) {
    fun bounds(): RectBounds = RectBounds(x, y, width, height)
}

enum class LainAnimState {
    IDLE,
    WALKING,
    RUNNING,
    JUMPING,
    FALLING,
    LANDING,
    INTERACTING
}

enum class LainExpression {
    NEUTRAL,
    CURIOUS,
    FOCUSED,
    HAPPY
}

data class PlayerState(
    val x: Float,
    val y: Float,
    val width: Float = 36f,
    val height: Float = 66f,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val facingRight: Boolean = true,
    val onGround: Boolean = false,
    val gravityInverted: Boolean = false,
    val coyoteTimer: Float = 0f,
    val jumpBufferTimer: Float = 0f,
    val interactTimer: Float = 0f,
    val landingTimer: Float = 0f,
    val animTime: Float = 0f,
    val animState: LainAnimState = LainAnimState.IDLE,
    val expression: LainExpression = LainExpression.NEUTRAL,
    val outfit: LainOutfit = LainOutfit.HOME_SWEATER,
    val currentSurfaceType: PlatformType = PlatformType.SOLID
) {
    fun bounds(): RectBounds = RectBounds(x, y, width, height)
}

data class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val life: Float,
    val maxLife: Float,
    val colorArgb: Long,
    val size: Float
)

data class InputState(
    val moveLeft: Boolean = false,
    val moveRight: Boolean = false,
    val analogX: Float = 0f, // Smooth virtual joystick axis (-1f..1f)
    val jumpPressed: Boolean = false,
    val jumpJustPressed: Boolean = false,
    val interactJustPressed: Boolean = false
)

enum class GameEvent {
    JUMPED,
    LANDED,
    SHARD_COLLECTED,
    SWITCH_TRIGGERED,
    CHECKPOINT_SAVED,
    GRAVITY_FLIPPED,
    MAGNET_TOGGLED,
    MIRROR_ROTATED,
    DOOR_OPENED,
    RESPAWNED,
    LEVEL_COMPLETED,
    INK_PLATFORM_CREATED,
    NPC_TALKED,
    DESKTOP_OPENED
}

data class LevelDefinition(
    val id: String,
    val worldId: WorldId,
    val orderIndex: Int,
    val worldWidth: Float,
    val worldHeight: Float,
    val spawn: Vec2,
    val allowInkDrawing: Boolean = false,
    val platforms: List<Platform>,
    val boxes: List<PhysicsBox> = emptyList(),
    val switches: List<SwitchNode> = emptyList(),
    val magnets: List<MagnetCoil> = emptyList(),
    val emitters: List<LightEmitter> = emptyList(),
    val mirrors: List<MirrorNode> = emptyList(),
    val gravityZones: List<GravityZone> = emptyList(),
    val doors: List<Door> = emptyList(),
    val shards: List<CollectibleShard> = emptyList(),
    val checkpoints: List<Checkpoint> = emptyList(),
    val hazards: List<HazardZone> = emptyList(),
    val judoShrines: List<JudoShrine> = emptyList(),
    val npcs: List<StoryNpc> = emptyList(),
    val exit: PortalExit
)

data class LevelRuntimeState(
    val definition: LevelDefinition,
    val player: PlayerState,
    val platforms: List<Platform>,
    val boxes: List<PhysicsBox>,
    val switches: List<SwitchNode>,
    val magnets: List<MagnetCoil>,
    val mirrors: List<MirrorNode>,
    val doors: List<Door>,
    val shards: List<CollectibleShard>,
    val checkpoints: List<Checkpoint>,
    val judoShrines: List<JudoShrine>,
    val drawnPlatforms: List<Platform> = emptyList(),
    val lightBeams: List<LightBeamSegment> = emptyList(),
    val particles: List<Particle> = emptyList(),
    val spawnPoint: Vec2,
    val globalGravityInverted: Boolean = false,
    val cameraX: Float = 0f,
    val cameraY: Float = 0f,
    val screenShake: Float = 0f,
    val elapsedTime: Float = 0f,
    val isCompleted: Boolean = false,
    val nearbyInteractLabel: String? = null,
    val pendingJudoTrial: JudoTrialId? = null,
    val activeDialogueNpc: StoryNpc? = null,
    val requestOpenDesktop: Boolean = false,
    val statusBannerKey: String? = null,
    val statusBannerTimer: Float = 0f,
    val events: List<GameEvent> = emptyList()
)
