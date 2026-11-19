package com.example.judo

import com.example.data.JudoTrialId
import kotlin.math.abs
import kotlin.math.sin

data class ForceWave(
    val id: Int,
    /** -1 = incoming from left pushing right, +1 = incoming from right pushing left */
    val fromSide: Int,
    /** 1.0 = outer edge, 0.0 = center */
    val distance: Float,
    val speed: Float,
    val handled: Boolean = false
)

enum class JudoFeedback {
    NONE,
    PERFECT,
    GOOD,
    MISS
}

enum class JudoStatus {
    READY,
    ACTIVE,
    WON,
    LOST
}

data class JudoState(
    val trialId: JudoTrialId,
    val status: JudoStatus = JudoStatus.READY,
    /** Center of Gravity offset in [-1.0, +1.0]; safe harmony zone is [-0.36, +0.36] */
    val cogOffset: Float = 0f,
    val cogVelocity: Float = 0f,
    /** 0..100 stability health */
    val stability: Float = 100f,
    /** 0..100 harmony completion goal */
    val harmony: Float = 0f,
    val score: Int = 0,
    val elapsed: Float = 0f,
    val waves: List<ForceWave> = emptyList(),
    val nextWaveTimer: Float = 1.4f,
    val nextWaveId: Int = 1,
    val feedback: JudoFeedback = JudoFeedback.NONE,
    val feedbackTimer: Float = 0f,
    val currentWindForce: Float = 0f
)

data class JudoInput(
    val leanLeft: Boolean = false,
    val leanRight: Boolean = false,
    val braceCenter: Boolean = false,
    val redirectLeftJustPressed: Boolean = false,
    val redirectRightJustPressed: Boolean = false
)

/**
 * Lightweight, physics-based Judo Balance & Kuzushi mini-game engine.
 * Models center-of-gravity torque, external force currents, and timing-based
 * directional force redirection (using incoming momentum rather than opposing it).
 */
object JudoBalanceEngine {

    private const val SAFE_ZONE = 0.38f

    fun createInitial(trialId: JudoTrialId): JudoState = JudoState(trialId = trialId)

    fun start(state: JudoState): JudoState = createInitial(state.trialId).copy(status = JudoStatus.ACTIVE)

    fun step(state: JudoState, input: JudoInput, dt: Float): JudoState {
        if (state.status != JudoStatus.ACTIVE) return state

        val clampedDt = dt.coerceIn(0.001f, 0.033f)
        val elapsed = state.elapsed + clampedDt
        var cog = state.cogOffset
        var vel = state.cogVelocity
        var stability = state.stability
        var harmony = state.harmony
        var score = state.score
        var feedback = if (state.feedbackTimer - clampedDt > 0f) state.feedback else JudoFeedback.NONE
        var feedbackTimer = (state.feedbackTimer - clampedDt).coerceAtLeast(0f)

        // 1. Ambient directional force currents (stronger on Trial I & III)
        val windStrength = when (state.trialId) {
            JudoTrialId.STILL_CENTER -> 0.85f
            JudoTrialId.KUZUSHI_REDIRECT -> 0.28f
            JudoTrialId.EQUILIBRIUM_FLOW -> 0.62f
        }
        val wind = (sin(elapsed * 1.45f) * 0.65f + sin(elapsed * 2.9f + 1.1f) * 0.35f) * windStrength

        // 2. Player center-of-gravity counter-lean + inverted pendulum instability
        var playerForce = 0f
        if (input.leanLeft) playerForce -= 1.65f
        if (input.leanRight) playerForce += 1.65f

        val gravityTorque = cog * 0.85f // Leaning farther increases pull outward
        val damping = if (input.braceCenter) -3.8f * vel else -1.6f * vel
        val accel = wind + playerForce + gravityTorque + damping

        vel = (vel + accel * clampedDt).coerceIn(-2.4f, 2.4f)
        cog = (cog + vel * clampedDt).coerceIn(-1.05f, 1.05f)

        // 3. Wave Spawning & Kuzushi Redirection (Trial II & III)
        var waves = state.waves.map { it.copy(distance = it.distance - it.speed * clampedDt) }.toMutableList()
        var nextWaveTimer = state.nextWaveTimer - clampedDt
        var nextWaveId = state.nextWaveId

        if (state.trialId != JudoTrialId.STILL_CENTER && nextWaveTimer <= 0f) {
            val side = if (nextWaveId % 2 == 1) -1 else 1
            val speed = if (state.trialId == JudoTrialId.EQUILIBRIUM_FLOW) 0.56f else 0.48f
            waves.add(ForceWave(id = nextWaveId, fromSide = side, distance = 1.0f, speed = speed))
            nextWaveId++
            nextWaveTimer = if (state.trialId == JudoTrialId.EQUILIBRIUM_FLOW) 2.25f else 2.0f
        }

        // Handle Redirect button presses (Redirect Left catches waves coming from left / pushing right, etc.)
        if (input.redirectLeftJustPressed || input.redirectRightJustPressed) {
            val chosenSide = if (input.redirectLeftJustPressed) -1 else 1
            val targetIdx = waves.indexOfFirst { !it.handled && it.fromSide == chosenSide && it.distance in 0.05f..0.44f }
            if (targetIdx >= 0) {
                val w = waves[targetIdx]
                waves[targetIdx] = w.copy(handled = true)
                val isPerfect = w.distance in 0.14f..0.34f
                harmony += if (isPerfect) 24f else 17f
                score += if (isPerfect) 250 else 150
                stability = (stability + 6f).coerceAtMost(100f)
                // Smoothly recenter CoG via Kuzushi flow
                vel *= 0.25f
                cog *= 0.45f
                feedback = if (isPerfect) JudoFeedback.PERFECT else JudoFeedback.GOOD
                feedbackTimer = 0.85f
            } else {
                // Mistimed redirect slightly nudges balance
                vel += chosenSide * 0.45f
                stability = (stability - 6f).coerceAtLeast(0f)
                feedback = JudoFeedback.MISS
                feedbackTimer = 0.65f
            }
        }

        // Resolve waves that reached center unhandled
        val remainingWaves = mutableListOf<ForceWave>()
        for (w in waves) {
            if (w.handled) continue
            if (w.distance <= 0.04f) {
                // Force impact pushes CoG opposite to where it came from
                vel += (-w.fromSide) * 1.15f
                stability = (stability - 16f).coerceAtLeast(0f)
                feedback = JudoFeedback.MISS
                feedbackTimer = 0.75f
            } else {
                remainingWaves.add(w)
            }
        }

        // 4. Stability & Harmony scoring from Center-of-Gravity balance
        val inSafeZone = abs(cog) <= SAFE_ZONE
        if (inSafeZone) {
            val gainRate = when (state.trialId) {
                JudoTrialId.STILL_CENTER -> 12.5f // ~8s of calm balance wins Trial I
                JudoTrialId.KUZUSHI_REDIRECT -> 2.2f // Redirects are primary driver
                JudoTrialId.EQUILIBRIUM_FLOW -> 4.2f // Balance + redirects combined
            }
            harmony += gainRate * clampedDt
            score += (18f * clampedDt).toInt()
        } else {
            val drain = (abs(cog) - SAFE_ZONE) * 46f
            stability = (stability - drain * clampedDt).coerceAtLeast(0f)
        }
        if (abs(cog) >= 0.98f) {
            stability = (stability - 34f * clampedDt).coerceAtLeast(0f)
        }

        val finalHarmony = harmony.coerceIn(0f, 100f)
        val finalStability = stability.coerceIn(0f, 100f)
        val finalStatus = when {
            finalHarmony >= 100f -> JudoStatus.WON
            finalStability <= 0f -> JudoStatus.LOST
            else -> JudoStatus.ACTIVE
        }

        return state.copy(
            status = finalStatus,
            cogOffset = cog,
            cogVelocity = vel,
            stability = finalStability,
            harmony = finalHarmony,
            score = score + if (finalStatus == JudoStatus.WON) (finalStability * 5).toInt() else 0,
            elapsed = elapsed,
            waves = remainingWaves,
            nextWaveTimer = nextWaveTimer,
            nextWaveId = nextWaveId,
            feedback = feedback,
            feedbackTimer = feedbackTimer,
            currentWindForce = wind
        )
    }
}
