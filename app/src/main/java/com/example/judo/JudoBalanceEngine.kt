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
    /** Sweep wave requires timed Ukemi (breakfall) instead of directional Kuzushi redirect */
    val isSweepWave: Boolean = false,
    val handled: Boolean = false
)

enum class JudoFeedback {
    NONE,
    PERFECT,
    GOOD,
    UKEMI_SAFE,
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
    /** Center of Gravity offset in [-1.0, +1.0]; safe harmony zone is [-0.38, +0.38] */
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
    val ukemiPoseTimer: Float = 0f,
    val currentWindForce: Float = 0f
)

data class JudoInput(
    val leanLeft: Boolean = false,
    val leanRight: Boolean = false,
    val braceCenter: Boolean = false,
    val redirectLeftJustPressed: Boolean = false,
    val redirectRightJustPressed: Boolean = false,
    val ukemiJustPressed: Boolean = false
)

/**
 * Lightweight, physics-based Judo Balance, Kuzushi & Ukemi mini-game engine (Evolution 2.0).
 * Original game interpretation inspired by balance, momentum redirection, and breakfall timing
 * (not real-world martial arts instruction).
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
        var ukemiPoseTimer = (state.ukemiPoseTimer - clampedDt).coerceAtLeast(0f)

        // 1. Ambient directional force currents
        val windStrength = when (state.trialId) {
            JudoTrialId.STILL_CENTER -> 0.85f
            JudoTrialId.KUZUSHI_REDIRECT -> 0.28f
            JudoTrialId.UKEMI_REACTION -> 0.44f
            JudoTrialId.EQUILIBRIUM_FLOW -> 0.62f
        }
        val wind = (sin(elapsed * 1.45f) * 0.65f + sin(elapsed * 2.9f + 1.1f) * 0.35f) * windStrength

        // 2. Player center-of-gravity counter-lean + inverted pendulum instability
        var playerForce = 0f
        if (input.leanLeft) playerForce -= 1.65f
        if (input.leanRight) playerForce += 1.65f

        val gravityTorque = cog * 0.85f
        val damping = if (input.braceCenter) -3.8f * vel else -1.6f * vel
        val accel = wind + playerForce + gravityTorque + damping

        vel = (vel + accel * clampedDt).coerceIn(-2.4f, 2.4f)
        cog = (cog + vel * clampedDt).coerceIn(-1.05f, 1.05f)

        // 3. Wave Spawning (Standard Kuzushi Waves + High-Impulse Ukemi Sweep Waves)
        val waves = state.waves.map { it.copy(distance = it.distance - it.speed * clampedDt) }.toMutableList()
        var nextWaveTimer = state.nextWaveTimer - clampedDt
        var nextWaveId = state.nextWaveId

        if (state.trialId != JudoTrialId.STILL_CENTER && nextWaveTimer <= 0f) {
            val side = if (nextWaveId % 2 == 1) -1 else 1
            val isSweep = when (state.trialId) {
                JudoTrialId.UKEMI_REACTION -> (nextWaveId % 2 == 0)
                JudoTrialId.EQUILIBRIUM_FLOW -> (nextWaveId % 3 == 0)
                else -> false
            }
            val speed = when (state.trialId) {
                JudoTrialId.EQUILIBRIUM_FLOW -> 0.56f
                JudoTrialId.UKEMI_REACTION -> if (isSweep) 0.52f else 0.46f
                else -> 0.48f
            }
            waves.add(ForceWave(id = nextWaveId, fromSide = side, distance = 1.0f, speed = speed, isSweepWave = isSweep))
            nextWaveId++
            nextWaveTimer = if (state.trialId == JudoTrialId.EQUILIBRIUM_FLOW) 2.2f else 2.0f
        }

        // 4. Handle Ukemi (Breakfall) timing reaction for sweep waves
        if (input.ukemiJustPressed) {
            val sweepIdx = waves.indexOfFirst { !it.handled && it.isSweepWave && it.distance in 0.05f..0.46f }
            if (sweepIdx >= 0) {
                val w = waves[sweepIdx]
                waves[sweepIdx] = w.copy(handled = true)
                harmony += 23f
                score += 240
                stability = (stability + 8f).coerceAtMost(100f)
                vel *= 0.15f
                cog *= 0.35f
                ukemiPoseTimer = 0.55f
                feedback = JudoFeedback.UKEMI_SAFE
                feedbackTimer = 0.90f
            } else {
                // Early/unnecessary breakfall slightly slows flow
                vel *= 0.6f
                stability = (stability - 5f).coerceAtLeast(0f)
                feedback = JudoFeedback.MISS
                feedbackTimer = 0.60f
            }
        }

        // 5. Handle Directional Kuzushi Redirect presses (for standard non-sweep waves)
        if (input.redirectLeftJustPressed || input.redirectRightJustPressed) {
            val chosenSide = if (input.redirectLeftJustPressed) -1 else 1
            val targetIdx = waves.indexOfFirst {
                !it.handled && !it.isSweepWave && it.fromSide == chosenSide && it.distance in 0.05f..0.44f
            }
            if (targetIdx >= 0) {
                val w = waves[targetIdx]
                waves[targetIdx] = w.copy(handled = true)
                val isPerfect = w.distance in 0.14f..0.34f
                harmony += if (isPerfect) 24f else 17f
                score += if (isPerfect) 250 else 150
                stability = (stability + 6f).coerceAtMost(100f)
                vel *= 0.25f
                cog *= 0.45f
                feedback = if (isPerfect) JudoFeedback.PERFECT else JudoFeedback.GOOD
                feedbackTimer = 0.85f
            } else {
                vel += chosenSide * 0.45f
                stability = (stability - 6f).coerceAtLeast(0f)
                feedback = JudoFeedback.MISS
                feedbackTimer = 0.65f
            }
        }

        // 6. Resolve waves that reached center unhandled
        val remainingWaves = mutableListOf<ForceWave>()
        for (w in waves) {
            if (w.handled) continue
            if (w.distance <= 0.04f) {
                val impact = if (w.isSweepWave) 1.38f else 1.15f
                val dmg = if (w.isSweepWave) 20f else 16f
                vel += (-w.fromSide) * impact
                stability = (stability - dmg).coerceAtLeast(0f)
                feedback = JudoFeedback.MISS
                feedbackTimer = 0.75f
            } else {
                remainingWaves.add(w)
            }
        }

        // 7. Stability & Harmony scoring from Center-of-Gravity balance
        val inSafeZone = abs(cog) <= SAFE_ZONE
        if (inSafeZone) {
            val gainRate = when (state.trialId) {
                JudoTrialId.STILL_CENTER -> 12.5f
                JudoTrialId.KUZUSHI_REDIRECT -> 2.2f
                JudoTrialId.UKEMI_REACTION -> 3.0f
                JudoTrialId.EQUILIBRIUM_FLOW -> 4.2f
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
            ukemiPoseTimer = ukemiPoseTimer,
            currentWindForce = wind
        )
    }
}
