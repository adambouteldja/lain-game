package com.example.sandbox

import kotlin.math.*

enum class SandboxBodyKind {
    BALL,
    WOOD_CRATE,
    METAL_CRATE,
    PENDULUM_BOB
}

enum class SandboxPreset {
    FREE_FALL,
    PENDULUM_LAB,
    MAGNET_FIELD,
    COLLISION_ARENA,
    LEVER_BALANCE
}

data class SandboxBody(
    val id: Int,
    val kind: SandboxBodyKind,
    val x: Float,
    val y: Float,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val radius: Float = 24f,
    val mass: Float = 1.5f,
    val restitution: Float = 0.72f,
    val colorArgb: Long = 0xFF64F0FFL,
    // Pendulum constraint fields (when kind == PENDULUM_BOB)
    val anchorX: Float = 0f,
    val anchorY: Float = 0f,
    val ropeLength: Float = 180f,
    val isDragged: Boolean = false
)

data class SandboxMagnet(
    val id: Int,
    val x: Float,
    val y: Float,
    val attract: Boolean = true,
    val strength: Float = 180_000f,
    val range: Float = 360f
)

data class SandboxBeam(
    val pivotX: Float,
    val pivotY: Float,
    val halfLength: Float = 150f,
    val angleDeg: Float = 0f,
    val angularVel: Float = 0f
)

data class SandboxState(
    val worldWidth: Float = 860f,
    val worldHeight: Float = 520f,
    val gravityMs2: Float = 9.81f, // Displayed in m/s² (scaled by 55 px/m)
    val friction: Float = 0.25f,
    val airDrag: Float = 0.04f,
    val timeScale: Float = 1.0f,
    val isPaused: Boolean = false,
    val bodies: List<SandboxBody> = emptyList(),
    val magnets: List<SandboxMagnet> = emptyList(),
    val beam: SandboxBeam? = null,
    val nextId: Int = 1,
    val elapsed: Float = 0f
) {
    fun totalKineticEnergyJ(): Float {
        return bodies.sumOf { b ->
            val vMs = hypot(b.vx, b.vy) / PX_PER_METER
            (0.5 * b.mass * vMs * vMs)
        }.toFloat()
    }

    fun totalPotentialEnergyJ(): Float {
        return bodies.sumOf { b ->
            val hMeters = ((worldHeight - b.radius - b.y).coerceAtLeast(0f)) / PX_PER_METER
            (b.mass * abs(gravityMs2) * hMeters).toDouble()
        }.toFloat()
    }

    companion object {
        const val PX_PER_METER = 55f
    }
}

/**
 * Pure Kotlin real-time 2D Physics Sandbox simulation engine.
 * Supports elastic collisions, gravity/friction/drag/time-scale control,
 * anchored pendulums, magnetic dipoles, tilting lever beam, touch drag/fling,
 * and SI energy telemetry (Kinetic & Potential energy in Joules).
 */
object PhysicsSandboxEngine {

    fun createPreset(preset: SandboxPreset, worldW: Float = 860f, worldH: Float = 520f): SandboxState {
        return when (preset) {
            SandboxPreset.FREE_FALL -> SandboxState(
                worldWidth = worldW,
                worldHeight = worldH,
                bodies = listOf(
                    SandboxBody(1, SandboxBodyKind.BALL, worldW * 0.28f, 110f, vx = 90f, radius = 22f, mass = 1.0f, restitution = 0.85f, colorArgb = 0xFF64F0FFL),
                    SandboxBody(2, SandboxBodyKind.BALL, worldW * 0.52f, 110f, vx = 0f, radius = 30f, mass = 3.0f, restitution = 0.65f, colorArgb = 0xFFF6BD60L),
                    SandboxBody(3, SandboxBodyKind.WOOD_CRATE, worldW * 0.72f, 140f, vx = -45f, radius = 24f, mass = 2.0f, restitution = 0.40f, colorArgb = 0xFF5EEAD4L)
                ),
                nextId = 4
            )
            SandboxPreset.PENDULUM_LAB -> SandboxState(
                worldWidth = worldW,
                worldHeight = worldH,
                airDrag = 0.01f,
                bodies = listOf(
                    SandboxBody(
                        id = 1,
                        kind = SandboxBodyKind.PENDULUM_BOB,
                        x = worldW * 0.35f + 160f,
                        y = 140f,
                        radius = 24f,
                        mass = 2.0f,
                        colorArgb = 0xFF64F0FFL,
                        anchorX = worldW * 0.35f,
                        anchorY = 60f,
                        ropeLength = 210f
                    ),
                    SandboxBody(
                        id = 2,
                        kind = SandboxBodyKind.PENDULUM_BOB,
                        x = worldW * 0.68f - 135f,
                        y = 180f,
                        radius = 20f,
                        mass = 1.2f,
                        colorArgb = 0xFFA78BFAL,
                        anchorX = worldW * 0.68f,
                        anchorY = 60f,
                        ropeLength = 175f
                    )
                ),
                nextId = 3
            )
            SandboxPreset.MAGNET_FIELD -> SandboxState(
                worldWidth = worldW,
                worldHeight = worldH,
                gravityMs2 = 4.5f,
                bodies = listOf(
                    SandboxBody(1, SandboxBodyKind.METAL_CRATE, worldW * 0.20f, worldH * 0.45f, vx = 40f, radius = 24f, mass = 1.6f, restitution = 0.55f, colorArgb = 0xFF64F0FFL),
                    SandboxBody(2, SandboxBodyKind.METAL_CRATE, worldW * 0.80f, worldH * 0.35f, vx = -40f, radius = 24f, mass = 1.6f, restitution = 0.55f, colorArgb = 0xFF5EEAD4L),
                    SandboxBody(3, SandboxBodyKind.BALL, worldW * 0.50f, 120f, radius = 20f, mass = 1.0f, restitution = 0.8f, colorArgb = 0xFFF6BD60L)
                ),
                magnets = listOf(
                    SandboxMagnet(10, worldW * 0.50f, worldH * 0.52f, attract = true)
                ),
                nextId = 11
            )
            SandboxPreset.COLLISION_ARENA -> SandboxState(
                worldWidth = worldW,
                worldHeight = worldH,
                gravityMs2 = 0f,
                friction = 0.02f,
                airDrag = 0.005f,
                bodies = listOf(
                    SandboxBody(1, SandboxBodyKind.BALL, worldW * 0.22f, worldH * 0.50f, vx = 260f, vy = 35f, radius = 26f, mass = 2.0f, restitution = 0.96f, colorArgb = 0xFF64F0FFL),
                    SandboxBody(2, SandboxBodyKind.BALL, worldW * 0.54f, worldH * 0.50f, vx = 0f, vy = 0f, radius = 26f, mass = 2.0f, restitution = 0.96f, colorArgb = 0xFFF26A8DL),
                    SandboxBody(3, SandboxBodyKind.BALL, worldW * 0.72f, worldH * 0.46f, vx = -90f, vy = -45f, radius = 22f, mass = 1.2f, restitution = 0.96f, colorArgb = 0xFF5EEAD4L),
                    SandboxBody(4, SandboxBodyKind.BALL, worldW * 0.72f, worldH * 0.58f, vx = -60f, vy = 55f, radius = 22f, mass = 1.2f, restitution = 0.96f, colorArgb = 0xFFF6BD60L)
                ),
                nextId = 5
            )
            SandboxPreset.LEVER_BALANCE -> SandboxState(
                worldWidth = worldW,
                worldHeight = worldH,
                bodies = listOf(
                    SandboxBody(1, SandboxBodyKind.WOOD_CRATE, worldW * 0.5f - 105f, worldH * 0.55f - 70f, radius = 24f, mass = 2.0f, restitution = 0.35f, colorArgb = 0xFFF6BD60L),
                    SandboxBody(2, SandboxBodyKind.METAL_CRATE, worldW * 0.5f + 95f, worldH * 0.55f - 90f, radius = 24f, mass = 2.2f, restitution = 0.35f, colorArgb = 0xFF64F0FFL)
                ),
                beam = SandboxBeam(pivotX = worldW * 0.5f, pivotY = worldH * 0.68f, halfLength = 175f),
                nextId = 3
            )
        }
    }

    fun spawnBody(state: SandboxState, kind: SandboxBodyKind): SandboxState {
        if (state.bodies.size >= 16) return state
        val id = state.nextId
        val jitterX = ((id * 73) % 240) - 120f
        val cx = (state.worldWidth * 0.5f + jitterX).coerceIn(70f, state.worldWidth - 70f)
        val body = when (kind) {
            SandboxBodyKind.BALL -> SandboxBody(id, kind, cx, 95f, vx = (id % 3 - 1) * 70f, radius = 22f, mass = 1.2f, restitution = 0.84f, colorArgb = 0xFF64F0FFL)
            SandboxBodyKind.WOOD_CRATE -> SandboxBody(id, kind, cx, 105f, radius = 24f, mass = 2.0f, restitution = 0.42f, colorArgb = 0xFFF6BD60L)
            SandboxBodyKind.METAL_CRATE -> SandboxBody(id, kind, cx, 105f, radius = 24f, mass = 2.6f, restitution = 0.50f, colorArgb = 0xFF5EEAD4L)
            SandboxBodyKind.PENDULUM_BOB -> SandboxBody(
                id = id,
                kind = kind,
                x = (cx + 125f).coerceAtMost(state.worldWidth - 50f),
                y = 140f,
                radius = 22f,
                mass = 1.8f,
                colorArgb = 0xFFA78BFAL,
                anchorX = cx,
                anchorY = 55f,
                ropeLength = 195f
            )
        }
        return state.copy(bodies = state.bodies + body, nextId = id + 1)
    }

    fun toggleMagnet(state: SandboxState): SandboxState {
        if (state.magnets.isEmpty()) {
            val mag = SandboxMagnet(state.nextId, state.worldWidth * 0.5f, state.worldHeight * 0.48f, attract = true)
            return state.copy(magnets = listOf(mag), nextId = state.nextId + 1)
        }
        return state.copy(magnets = state.magnets.map { it.copy(attract = !it.attract) })
    }

    fun toggleBeam(state: SandboxState): SandboxState {
        return if (state.beam == null) {
            state.copy(beam = SandboxBeam(state.worldWidth * 0.5f, state.worldHeight * 0.68f, 170f))
        } else {
            state.copy(beam = null)
        }
    }

    fun step(state: SandboxState, dt: Float, forceStep: Boolean = false): SandboxState {
        if (state.isPaused && !forceStep) return state
        val simDt = (dt.coerceIn(0.001f, 0.033f) * state.timeScale).coerceIn(0.0005f, 0.05f)
        val gravPx = state.gravityMs2 * SandboxState.PX_PER_METER
        val bodies = state.bodies.toMutableList()
        var beam = state.beam

        // 1. Integrate forces & kinematics
        for (i in bodies.indices) {
            var b = bodies[i]
            if (b.isDragged) continue

            var ax = 0f
            var ay = gravPx

            // Magnetic force on metal crates
            if (b.kind == SandboxBodyKind.METAL_CRATE) {
                for (m in state.magnets) {
                    val dx = m.x - b.x
                    val dy = m.y - b.y
                    val dist = hypot(dx, dy).coerceAtLeast(36f)
                    if (dist < m.range) {
                        val sign = if (m.attract) 1f else -1f
                        val magForce = sign * m.strength / (dist * dist * 0.08f + 120f)
                        ax += (dx / dist) * magForce / b.mass
                        ay += (dy / dist) * magForce / b.mass
                    }
                }
            }

            var vx = b.vx + ax * simDt
            var vy = b.vy + ay * simDt

            // Air drag
            val dragFactor = (1f - state.airDrag * simDt * 12f).coerceIn(0.75f, 1f)
            vx *= dragFactor
            vy *= dragFactor

            var nx = b.x + vx * simDt
            var ny = b.y + vy * simDt

            // Pendulum inextensible rope constraint
            if (b.kind == SandboxBodyKind.PENDULUM_BOB) {
                val dx = nx - b.anchorX
                val dy = ny - b.anchorY
                val dist = hypot(dx, dy).coerceAtLeast(1f)
                if (dist > b.ropeLength) {
                    val ux = dx / dist
                    val uy = dy / dist
                    nx = b.anchorX + ux * b.ropeLength
                    ny = b.anchorY + uy * b.ropeLength
                    // Project out outward radial velocity component
                    val radialVel = vx * ux + vy * uy
                    if (radialVel > 0f) {
                        vx -= radialVel * ux
                        vy -= radialVel * uy
                    }
                }
            }

            // World boundaries (floor, ceiling, walls)
            val floorY = state.worldHeight - b.radius
            val ceilY = b.radius
            val leftX = b.radius
            val rightX = state.worldWidth - b.radius

            if (ny > floorY) {
                ny = floorY
                if (vy > 0f) vy = -vy * b.restitution
                if (abs(vy) < 22f) vy = 0f
                vx *= (1f - state.friction * 0.22f).coerceIn(0.6f, 1f)
            } else if (ny < ceilY) {
                ny = ceilY
                if (vy < 0f) vy = -vy * b.restitution
            }
            if (nx < leftX) {
                nx = leftX
                if (vx < 0f) vx = -vx * b.restitution
            } else if (nx > rightX) {
                nx = rightX
                if (vx > 0f) vx = -vx * b.restitution
            }

            bodies[i] = b.copy(x = nx, y = ny, vx = vx, vy = vy)
        }

        // 2. Circle-circle elastic collisions with mass impulse resolution
        for (i in 0 until bodies.size) {
            for (j in i + 1 until bodies.size) {
                val a = bodies[i]
                val b = bodies[j]
                val dx = b.x - a.x
                val dy = b.y - a.y
                val dist = hypot(dx, dy)
                val minDist = a.radius + b.radius
                if (dist < minDist && dist > 0.001f) {
                    val nx = dx / dist
                    val ny = dy / dist
                    val overlap = (minDist - dist) * 0.5f
                    val ax = if (a.isDragged) a.x else a.x - nx * overlap
                    val ay = if (a.isDragged) a.y else a.y - ny * overlap
                    val bx = if (b.isDragged) b.x else b.x + nx * overlap
                    val by = if (b.isDragged) b.y else b.y + ny * overlap

                    val rvx = b.vx - a.vx
                    val rvy = b.vy - a.vy
                    val velAlongNormal = rvx * nx + rvy * ny
                    var avx = a.vx
                    var avy = a.vy
                    var bvx = b.vx
                    var bvy = b.vy

                    if (velAlongNormal < 0f) {
                        val e = min(a.restitution, b.restitution)
                        val invMassA = if (a.isDragged) 0f else 1f / a.mass
                        val invMassB = if (b.isDragged) 0f else 1f / b.mass
                        val denom = (invMassA + invMassB).coerceAtLeast(0.001f)
                        val impulse = -(1f + e) * velAlongNormal / denom
                        avx -= impulse * invMassA * nx
                        avy -= impulse * invMassA * ny
                        bvx += impulse * invMassB * nx
                        bvy += impulse * invMassB * ny
                    }

                    bodies[i] = a.copy(x = ax, y = ay, vx = avx, vy = avy)
                    bodies[j] = b.copy(x = bx, y = by, vx = bvx, vy = bvy)
                }
            }
        }

        // 3. Optional Tilting Lever Beam torque & collision
        beam?.let { bm ->
            var torque = 0f
            val rad = Math.toRadians(bm.angleDeg.toDouble())
            val slope = tan(rad).toFloat()
            for (i in bodies.indices) {
                val b = bodies[i]
                val relX = b.x - bm.pivotX
                if (abs(relX) <= bm.halfLength) {
                    val surfY = bm.pivotY + relX * slope
                    if (b.y + b.radius >= surfY && b.y - b.radius <= surfY + 24f && b.vy >= -10f) {
                        val ny = surfY - b.radius
                        val nvy = if (b.vy > 30f) -b.vy * 0.25f else 0f
                        val nvx = b.vx + sin(rad).toFloat() * gravPx * simDt * 0.55f
                        bodies[i] = b.copy(y = ny, vx = nvx, vy = nvy)
                        torque += (relX / bm.halfLength) * b.mass * 38f
                    }
                }
            }
            val spring = -1.6f * bm.angleDeg
            val damp = -2.6f * bm.angularVel
            val newAngVel = bm.angularVel + (torque + spring + damp) * simDt
            val newAngle = (bm.angleDeg + newAngVel * simDt).coerceIn(-24f, 24f)
            beam = bm.copy(angleDeg = newAngle, angularVel = newAngVel)
        }

        return state.copy(
            bodies = bodies,
            beam = beam,
            elapsed = state.elapsed + simDt
        )
    }
}
