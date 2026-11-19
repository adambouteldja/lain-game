package com.example.game.engine

import com.example.data.JudoTrialId
import com.example.data.LainOutfit
import com.example.game.model.*
import kotlin.math.*

/**
 * Pure Kotlin deterministic 2D physics & puzzle engine (Evolution 2.0).
 * Supports gravity (normal/inverted/low-G), proportional analog + digital controls,
 * surface friction, sloped drawn ink bridges, tilting balance beams, pushable &
 * magnetic boxes, light-beam mirrors, interactive Story NPCs, and Navi Desktop access.
 */
object PhysicsEngine {

    private const val GRAVITY_NORMAL = 1480f
    private const val GRAVITY_LOW = 620f
    private const val MOVE_ACCEL_GROUND = 2280f
    private const val MOVE_ACCEL_AIR = 1380f
    private const val MOVE_ACCEL_ICE = 790f
    private const val MAX_RUN_SPEED = 296f
    private const val JUMP_VELOCITY = 615f
    private const val MAX_FALL_SPEED = 860f
    private const val COYOTE_TIME = 0.13f
    private const val JUMP_BUFFER_TIME = 0.13f
    private const val MAX_DRAWN_BRIDGES = 3

    fun createInitialState(
        def: LevelDefinition,
        alreadyCollectedShards: Set<String> = emptySet(),
        completedJudoTrials: Set<JudoTrialId> = emptySet(),
        outfit: LainOutfit = LevelCatalog.defaultOutfitForWorld(def.worldId)
    ): LevelRuntimeState {
        val player = PlayerState(x = def.spawn.x, y = def.spawn.y, outfit = outfit)
        val shards = def.shards.map { s ->
            if (s.id in alreadyCollectedShards) s.copy(collected = true) else s
        }
        val shrines = def.judoShrines.map { sh ->
            if (sh.trialId in completedJudoTrials) sh.copy(completed = true) else sh
        }
        val base = LevelRuntimeState(
            definition = def,
            player = player,
            platforms = def.platforms,
            boxes = def.boxes,
            switches = def.switches,
            magnets = def.magnets,
            mirrors = def.mirrors,
            doors = def.doors,
            shards = shards,
            checkpoints = def.checkpoints,
            judoShrines = shrines,
            spawnPoint = def.spawn,
            cameraX = (def.spawn.x - 240f).coerceAtLeast(0f),
            cameraY = (def.spawn.y - 220f).coerceAtLeast(0f)
        )
        return step(base, InputState(), dt = 1f / 60f, viewportW = 800f, viewportH = 480f)
            .copy(events = emptyList())
    }

    fun addInkBridge(state: LevelRuntimeState, p1: Vec2, p2: Vec2): LevelRuntimeState {
        if (!state.definition.allowInkDrawing) return state
        // Normalize endpoints left-to-right so slope angle stays in [-28, +28] degrees
        val leftPt = if (p1.x <= p2.x) p1 else p2
        val rightPt = if (p1.x <= p2.x) p2 else p1
        val dx = rightPt.x - leftPt.x
        val dy = rightPt.y - leftPt.y
        val dist = hypot(dx, dy)
        if (dist < 42f) return state

        val clampedW = dist.coerceIn(54f, 240f)
        val rawDeg = if (abs(dx) < 8f) 0f else Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        // Treat nearly flat strokes (<4 deg) as crisp flat bridges; otherwise preserve real ramp slope
        val angleDeg = if (abs(rawDeg) < 4f) 0f else rawDeg.coerceIn(-28f, 28f)
        val cx = (leftPt.x + rightPt.x) * 0.5f
        val cy = (leftPt.y + rightPt.y) * 0.5f

        val newPlat = Platform(
            id = "ink_${System.nanoTime()}",
            x = (cx - clampedW * 0.5f).coerceIn(12f, state.definition.worldWidth - clampedW - 12f),
            y = (cy - 9f).coerceIn(28f, state.definition.worldHeight - 40f),
            width = clampedW,
            height = 18f,
            type = PlatformType.DRAWN_INK,
            tiltAngleDeg = angleDeg
        )
        // Avoid spawning directly trapping Lain inside the bridge
        val pBounds = state.player.bounds()
        val adjustedPlat = if (newPlat.bounds().overlaps(pBounds) && abs(angleDeg) < 1f) {
            newPlat.copy(y = pBounds.bottom + 2f)
        } else newPlat

        val updated = (state.drawnPlatforms + adjustedPlat).takeLast(MAX_DRAWN_BRIDGES)
        val burst = createBurst(adjustedPlat.x + adjustedPlat.width * 0.5f, adjustedPlat.y + 9f, 0xFF64F0FFL, 10)
        return state.copy(
            drawnPlatforms = updated,
            particles = (state.particles + burst).takeLast(90),
            events = state.events + GameEvent.INK_PLATFORM_CREATED
        )
    }

    fun clearInkBridges(state: LevelRuntimeState): LevelRuntimeState =
        state.copy(drawnPlatforms = emptyList())

    fun markJudoTrialCompleted(state: LevelRuntimeState, trialId: JudoTrialId): LevelRuntimeState {
        val updatedShrines = state.judoShrines.map {
            if (it.trialId == trialId) it.copy(completed = true) else it
        }
        return state.copy(
            judoShrines = updatedShrines,
            pendingJudoTrial = null,
            statusBannerKey = "door_unlocked",
            statusBannerTimer = 2.4f,
            events = state.events + GameEvent.DOOR_OPENED
        )
    }

    fun dismissPendingJudo(state: LevelRuntimeState): LevelRuntimeState =
        state.copy(pendingJudoTrial = null)

    fun dismissDialogue(state: LevelRuntimeState): LevelRuntimeState =
        state.copy(activeDialogueNpc = null)

    fun clearDesktopRequest(state: LevelRuntimeState): LevelRuntimeState =
        state.copy(requestOpenDesktop = false)

    fun updatePlayerOutfit(state: LevelRuntimeState, outfit: LainOutfit): LevelRuntimeState =
        state.copy(player = state.player.copy(outfit = outfit))

    fun step(
        state: LevelRuntimeState,
        input: InputState,
        dt: Float,
        viewportW: Float = 820f,
        viewportH: Float = 480f
    ): LevelRuntimeState {
        if (state.isCompleted || state.pendingJudoTrial != null ||
            state.activeDialogueNpc != null || state.requestOpenDesktop
        ) {
            return state.copy(events = emptyList())
        }

        val clampedDt = dt.coerceIn(0.001f, 0.033f)
        val events = mutableListOf<GameEvent>()
        val particles = state.particles.mapNotNull { p ->
            val nl = p.life - clampedDt
            if (nl <= 0f) null
            else p.copy(
                x = p.x + p.vx * clampedDt,
                y = p.y + p.vy * clampedDt,
                vy = p.vy + 180f * clampedDt,
                life = nl
            )
        }.toMutableList()

        val newElapsed = state.elapsedTime + clampedDt

        // 1. Update Moving Platforms & Tilting Beams
        val updatedPlatforms = state.platforms.map { plat ->
            when (plat.type) {
                PlatformType.MOVING -> {
                    val phase = plat.movePhase + plat.moveSpeed * clampedDt
                    val t = (sin(phase) + 1f) * 0.5f
                    val nx = plat.startX + (plat.endX - plat.startX) * t
                    val ny = plat.startY + (plat.endY - plat.startY) * t
                    plat.copy(
                        x = nx,
                        y = ny,
                        movePhase = phase,
                        deltaX = nx - plat.x,
                        deltaY = ny - plat.y
                    )
                }
                PlatformType.TILTING_BEAM -> {
                    val pivotX = plat.x + plat.width * 0.5f
                    var torque = 0f
                    val pb = state.player.bounds()
                    if (pb.right > plat.left && pb.left < plat.right &&
                        abs(pb.bottom - plat.surfaceYAt(pb.centerX)) < 18f
                    ) {
                        torque += ((pb.centerX - pivotX) / (plat.width * 0.5f)) * 58f
                    }
                    state.boxes.forEach { box ->
                        val bb = box.bounds()
                        if (bb.right > plat.left && bb.left < plat.right &&
                            abs(bb.bottom - plat.surfaceYAt(bb.centerX)) < 18f
                        ) {
                            torque += ((bb.centerX - pivotX) / (plat.width * 0.5f)) * 68f * box.mass
                        }
                    }
                    val springK = -2.2f * plat.tiltAngleDeg
                    val damping = -2.8f * plat.tiltAngularVel
                    val newVel = plat.tiltAngularVel + (torque + springK + damping) * clampedDt
                    val newAngle = (plat.tiltAngleDeg + newVel * clampedDt).coerceIn(-20f, 20f)
                    plat.copy(tiltAngleDeg = newAngle, tiltAngularVel = newVel)
                }
                else -> plat.copy(deltaX = 0f, deltaY = 0f)
            }
        }

        // Gather currently solid platforms + closed doors
        val allPlatforms = updatedPlatforms + state.drawnPlatforms
        val solidObstacles = buildList {
            allPlatforms.forEach { if (it.isSolidActive) add(it) }
            state.doors.forEach { d ->
                if (!d.isOpen) {
                    add(Platform(d.id, d.x, d.y, d.width, d.height, PlatformType.SOLID))
                }
            }
        }

        // 2. Player Gravity Zone check
        val player = state.player
        val pBoundsNow = player.bounds()
        var inLowG = false
        var inInvertedZone = false
        state.definition.gravityZones.forEach { gz ->
            if (pBoundsNow.overlaps(gz.bounds())) {
                when (gz.mode) {
                    GravityZoneMode.LOW_GRAVITY -> inLowG = true
                    GravityZoneMode.INVERTED -> inInvertedZone = true
                }
            }
        }
        val effectiveInverted = state.globalGravityInverted xor inInvertedZone
        val gravSign = if (effectiveInverted) -1f else 1f
        val gravMag = if (inLowG) GRAVITY_LOW else GRAVITY_NORMAL

        // 3. Player horizontal input (supports both virtual analog joystick and digital buttons)
        val accel = when {
            !player.onGround -> MOVE_ACCEL_AIR
            player.currentSurfaceType == PlatformType.ICE -> MOVE_ACCEL_ICE
            else -> MOVE_ACCEL_GROUND
        }
        val rawAxis = when {
            abs(input.analogX) > 0.08f -> input.analogX.coerceIn(-1f, 1f)
            input.moveLeft && !input.moveRight -> -1f
            input.moveRight && !input.moveLeft -> 1f
            else -> 0f
        }

        var vx = player.vx
        var facingRight = player.facingRight
        val maxSpeed = if (player.currentSurfaceType == PlatformType.STICKY && player.onGround) {
            MAX_RUN_SPEED * 0.68f
        } else MAX_RUN_SPEED

        if (abs(rawAxis) > 0.08f) {
            val targetVx = rawAxis * maxSpeed
            val deltaV = accel * clampedDt
            vx = when {
                vx < targetVx -> min(targetVx, vx + deltaV)
                vx > targetVx -> max(targetVx, vx - deltaV)
                else -> targetVx
            }
            facingRight = rawAxis > 0f
        } else {
            val friction = when {
                !player.onGround -> 0.94f
                player.currentSurfaceType == PlatformType.ICE -> 0.985f
                player.currentSurfaceType == PlatformType.STICKY -> 0.62f
                else -> 0.78f
            }
            vx *= friction.pow(clampedDt * 60f)
            if (abs(vx) < 4f) vx = 0f
        }
        vx = vx.coerceIn(-maxSpeed, maxSpeed)

        // 4. Jump buffer & Coyote time
        val coyote = if (player.onGround) COYOTE_TIME else (player.coyoteTimer - clampedDt).coerceAtLeast(0f)
        var jumpBuf = if (input.jumpJustPressed) JUMP_BUFFER_TIME else (player.jumpBufferTimer - clampedDt).coerceAtLeast(0f)
        var vy = player.vy + gravMag * gravSign * clampedDt
        vy = vy.coerceIn(-MAX_FALL_SPEED, MAX_FALL_SPEED)

        if (jumpBuf > 0f && coyote > 0f) {
            vy = -JUMP_VELOCITY * gravSign
            jumpBuf = 0f
            events.add(GameEvent.JUMPED)
            particles.addAll(
                createBurst(
                    player.x + player.width * 0.5f,
                    if (effectiveInverted) player.y else player.y + player.height,
                    0xFFA8B7D1L,
                    5
                )
            )
        }
        // Variable jump height release cut
        if (!input.jumpPressed && ((gravSign > 0 && vy < -240f) || (gravSign < 0 && vy > 240f))) {
            vy *= 0.86f
        }

        // 5. Move Player X & Resolve Platforms + Push Boxes
        var px = player.x + vx * clampedDt
        var py = player.y
        px = px.coerceIn(0f, state.definition.worldWidth - player.width)

        val mutableBoxes = state.boxes.toMutableList()

        // Pushable crates horizontal interaction
        for (i in mutableBoxes.indices) {
            val box = mutableBoxes[i]
            val pRect = RectBounds(px, py, player.width, player.height)
            val bRect = box.bounds()
            if (pRect.overlaps(bRect)) {
                val verticalOverlap = min(pRect.bottom, bRect.bottom) - max(pRect.top, bRect.top)
                if (verticalOverlap > 12f) {
                    if (vx > 0f && pRect.centerX < bRect.centerX) {
                        val pushVx = (vx * 0.82f / box.mass).coerceAtLeast(85f)
                        mutableBoxes[i] = box.copy(vx = pushVx)
                        px = bRect.left - player.width
                        vx = pushVx
                    } else if (vx < 0f && pRect.centerX > bRect.centerX) {
                        val pushVx = (vx * 0.82f / box.mass).coerceAtMost(-85f)
                        mutableBoxes[i] = box.copy(vx = pushVx)
                        px = bRect.right
                        vx = pushVx
                    }
                }
            }
        }

        // Resolve Player X against solid obstacles (skipping sloped surfaces so player climbs them smoothly)
        for (plat in solidObstacles) {
            if (plat.isSlopedSurface()) continue
            val pRect = RectBounds(px, py, player.width, player.height)
            val obs = plat.bounds()
            if (pRect.overlaps(obs)) {
                if (vx > 0f) {
                    px = obs.left - player.width
                    vx = 0f
                } else if (vx < 0f) {
                    px = obs.right
                    vx = 0f
                }
            }
        }

        // 6. Move Player Y & Resolve Platforms + Boxes
        val wasOnGround = player.onGround
        val preImpactVy = vy
        py += vy * clampedDt
        var onGround = false
        var surfaceType = PlatformType.SOLID

        for (plat in solidObstacles) {
            if (plat.isSlopedSurface()) {
                val pCenterX = px + player.width * 0.5f
                if (pCenterX >= plat.left - 8f && pCenterX <= plat.right + 8f) {
                    val surfY = plat.surfaceYAt(pCenterX)
                    if (!effectiveInverted && vy >= 0f &&
                        player.y + player.height <= surfY + 24f &&
                        py + player.height >= surfY - 6f
                    ) {
                        py = surfY - player.height
                        vy = 0f
                        onGround = true
                        surfaceType = plat.type
                        if (plat.type == PlatformType.TILTING_BEAM) {
                            vx += sin(Math.toRadians(plat.tiltAngleDeg.toDouble())).toFloat() * 340f * clampedDt
                        }
                    }
                }
                continue
            }

            val pRect = RectBounds(px, py, player.width, player.height)
            val obs = plat.bounds()
            if (pRect.overlaps(obs)) {
                if (vy > 0f) {
                    py = obs.top - player.height
                    vy = 0f
                    if (!effectiveInverted) {
                        onGround = true
                        surfaceType = plat.type
                        px = (px + plat.deltaX).coerceIn(0f, state.definition.worldWidth - player.width)
                        py += plat.deltaY
                    }
                } else if (vy < 0f) {
                    py = obs.bottom
                    vy = 0f
                    if (effectiveInverted) {
                        onGround = true
                        surfaceType = plat.type
                        px = (px + plat.deltaX).coerceIn(0f, state.definition.worldWidth - player.width)
                        py += plat.deltaY
                    }
                }
            }
        }

        // Player landing on top of boxes
        for (box in mutableBoxes) {
            val pRect = RectBounds(px, py, player.width, player.height)
            val bRect = box.bounds()
            if (pRect.overlaps(bRect)) {
                if (!effectiveInverted && vy >= 0f && player.y + player.height <= bRect.top + 16f) {
                    py = bRect.top - player.height
                    vy = 0f
                    onGround = true
                    surfaceType = PlatformType.SOLID
                } else if (effectiveInverted && vy <= 0f && player.y >= bRect.bottom - 16f) {
                    py = bRect.bottom
                    vy = 0f
                    onGround = true
                    surfaceType = PlatformType.SOLID
                }
            }
        }

        var landingTimer = (player.landingTimer - clampedDt).coerceAtLeast(0f)
        if (!wasOnGround && onGround) {
            events.add(GameEvent.LANDED)
            if (abs(preImpactVy) > 160f) {
                landingTimer = 0.11f
            }
            particles.addAll(
                createBurst(
                    px + player.width * 0.5f,
                    if (effectiveInverted) py else py + player.height,
                    0xFF64F0FFL,
                    4
                )
            )
        }

        // 7. Update Physics Boxes (Gravity, Magnetism, Friction, Collisions, Sloped Ramps)
        for (i in mutableBoxes.indices) {
            val box = mutableBoxes[i]
            var bvx = box.vx
            var bvy = box.vy

            var boxLowG = false
            var boxInvZone = false
            state.definition.gravityZones.forEach { gz ->
                if (box.bounds().overlaps(gz.bounds())) {
                    if (gz.mode == GravityZoneMode.LOW_GRAVITY) boxLowG = true
                    if (gz.mode == GravityZoneMode.INVERTED) boxInvZone = true
                }
            }
            val boxInv = state.globalGravityInverted xor boxInvZone
            val bGravSign = if (boxInv) -1f else 1f
            val bGravMag = if (boxLowG) GRAVITY_LOW else GRAVITY_NORMAL

            if (box.isMetal) {
                val bcx = box.x + box.width * 0.5f
                val bcy = box.y + box.height * 0.5f
                for (coil in state.magnets) {
                    if (!coil.isPowered) continue
                    val mcx = coil.x + coil.width * 0.5f
                    val mcy = coil.y + coil.height * 0.5f
                    val dx = mcx - bcx
                    val dy = mcy - bcy
                    val dist = hypot(dx, dy).coerceAtLeast(32f)
                    if (dist < coil.range) {
                        val falloff = (1f - dist / coil.range).coerceIn(0.15f, 1f)
                        val dir = if (coil.mode == MagnetMode.ATTRACT) 1f else -1f
                        val force = coil.strength * falloff * dir
                        bvx += (dx / dist) * force * clampedDt
                        bvy += (dy / dist) * force * 0.45f * clampedDt
                    }
                }
            }

            bvy = (bvy + bGravMag * bGravSign * clampedDt).coerceIn(-MAX_FALL_SPEED, MAX_FALL_SPEED)

            var boxSurf = PlatformType.SOLID
            var bx = (box.x + bvx * clampedDt).coerceIn(0f, state.definition.worldWidth - box.width)
            var by = box.y

            for (plat in solidObstacles) {
                if (plat.isSlopedSurface()) continue
                val bRect = RectBounds(bx, by, box.width, box.height)
                val obs = plat.bounds()
                if (bRect.overlaps(obs)) {
                    if (bvx > 0f) {
                        bx = obs.left - box.width
                        bvx = 0f
                    } else if (bvx < 0f) {
                        bx = obs.right
                        bvx = 0f
                    }
                }
            }

            by += bvy * clampedDt
            var boxGrounded = false
            for (plat in solidObstacles) {
                if (plat.isSlopedSurface()) {
                    val bCenterX = bx + box.width * 0.5f
                    if (bCenterX >= plat.left && bCenterX <= plat.right) {
                        val surfY = plat.surfaceYAt(bCenterX)
                        if (!boxInv && bvy >= 0f && box.y + box.height <= surfY + 22f && by + box.height >= surfY - 5f) {
                            by = surfY - box.height
                            bvy = 0f
                            boxGrounded = true
                            boxSurf = plat.type
                            bvx += sin(Math.toRadians(plat.tiltAngleDeg.toDouble())).toFloat() * 340f * clampedDt
                        }
                    }
                    continue
                }
                val bRect = RectBounds(bx, by, box.width, box.height)
                val obs = plat.bounds()
                if (bRect.overlaps(obs)) {
                    if (bvy > 0f) {
                        by = obs.top - box.height
                        bvy = 0f
                        if (!boxInv) {
                            boxGrounded = true
                            boxSurf = plat.type
                            bx = (bx + plat.deltaX).coerceIn(0f, state.definition.worldWidth - box.width)
                            by += plat.deltaY
                        }
                    } else if (bvy < 0f) {
                        by = obs.bottom
                        bvy = 0f
                        if (boxInv) {
                            boxGrounded = true
                            boxSurf = plat.type
                            bx = (bx + plat.deltaX).coerceIn(0f, state.definition.worldWidth - box.width)
                            by += plat.deltaY
                        }
                    }
                }
            }

            for (j in mutableBoxes.indices) {
                if (i == j) continue
                val other = mutableBoxes[j]
                val bRect = RectBounds(bx, by, box.width, box.height)
                val oRect = other.bounds()
                if (bRect.overlaps(oRect)) {
                    if (!boxInv && bvy >= 0f && box.y + box.height <= oRect.top + 14f) {
                        by = oRect.top - box.height
                        bvy = 0f
                        boxGrounded = true
                    } else if (bRect.centerX < oRect.centerX) {
                        bx = oRect.left - box.width
                        bvx = 0f
                    } else {
                        bx = oRect.right
                        bvx = 0f
                    }
                }
            }

            val boxFriction = when {
                !boxGrounded -> 0.96f
                boxSurf == PlatformType.ICE -> 0.992f
                boxSurf == PlatformType.DRAWN_INK && abs(bvx) > 5f -> 0.94f
                else -> 0.80f
            }
            bvx *= boxFriction.pow(clampedDt * 60f)
            if (abs(bvx) < 3f) bvx = 0f

            if (by > state.definition.worldHeight + 180f || by < -240f) {
                val orig = state.definition.boxes.find { it.id == box.id } ?: box
                mutableBoxes[i] = orig
            } else {
                mutableBoxes[i] = box.copy(x = bx, y = by, vx = bvx, vy = bvy, onGround = boxGrounded)
            }
        }

        // 8. Interact Button Processing (Terminals, Mirrors, Switches, Judo Shrines, Story NPCs / Navi Desk)
        var interactTimer = (player.interactTimer - clampedDt).coerceAtLeast(0f)
        var statusBannerKey = if (state.statusBannerTimer - clampedDt > 0f) state.statusBannerKey else null
        var statusBannerTimer = (state.statusBannerTimer - clampedDt).coerceAtLeast(0f)
        var globalGravityInverted = state.globalGravityInverted
        val mutableSwitches = state.switches.toMutableList()
        val mutableMirrors = state.mirrors.toMutableList()
        val mutableMagnets = state.magnets.toMutableList()
        var pendingJudo: JudoTrialId? = null
        var activeDialogueNpc: StoryNpc? = null
        var requestOpenDesktop = false
        var nearbyPrompt: String? = null

        val pCenter = Vec2(px + player.width * 0.5f, py + player.height * 0.5f)

        for (i in mutableSwitches.indices) {
            val sw = mutableSwitches[i]
            if (sw.kind == SwitchKind.TERMINAL || sw.kind == SwitchKind.GRAVITY_SWITCH || sw.kind == SwitchKind.MAGNET_SWITCH) {
                val dist = hypot(pCenter.x - sw.bounds().centerX, pCenter.y - sw.bounds().centerY)
                if (dist < 76f) {
                    nearbyPrompt = sw.kind.name
                    if (input.interactJustPressed) {
                        interactTimer = 0.28f
                        when (sw.kind) {
                            SwitchKind.TERMINAL -> {
                                mutableSwitches[i] = sw.copy(isActive = !sw.isActive)
                                events.add(GameEvent.SWITCH_TRIGGERED)
                                statusBannerKey = "door_unlocked"
                                statusBannerTimer = 2.0f
                                particles.addAll(createBurst(sw.bounds().centerX, sw.bounds().centerY, 0xFF64F0FFL, 12))
                            }
                            SwitchKind.GRAVITY_SWITCH -> {
                                globalGravityInverted = !globalGravityInverted
                                mutableSwitches[i] = sw.copy(isActive = globalGravityInverted)
                                events.add(GameEvent.GRAVITY_FLIPPED)
                                statusBannerKey = "gravity_flipped"
                                statusBannerTimer = 2.0f
                                particles.addAll(createBurst(sw.bounds().centerX, sw.bounds().centerY, 0xFFA78BFAL, 14))
                            }
                            SwitchKind.MAGNET_SWITCH -> {
                                val nextActive = !sw.isActive
                                mutableSwitches[i] = sw.copy(isActive = nextActive)
                                for (mIdx in mutableMagnets.indices) {
                                    val coil = mutableMagnets[mIdx]
                                    if (coil.linkedSignalId == sw.signalId) {
                                        val nextMode = if (coil.mode == MagnetMode.ATTRACT) MagnetMode.REPEL else MagnetMode.ATTRACT
                                        mutableMagnets[mIdx] = coil.copy(mode = nextMode)
                                    }
                                }
                                events.add(GameEvent.MAGNET_TOGGLED)
                                statusBannerKey = "magnet_toggled"
                                statusBannerTimer = 2.0f
                                particles.addAll(createBurst(sw.bounds().centerX, sw.bounds().centerY, 0xFFF26A8DL, 12))
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }

        // Mirrors rotation via Interact
        for (i in mutableMirrors.indices) {
            val m = mutableMirrors[i]
            val dist = hypot(pCenter.x - m.x, pCenter.y - m.y)
            if (dist < 82f) {
                nearbyPrompt = "MIRROR"
                if (input.interactJustPressed) {
                    interactTimer = 0.25f
                    mutableMirrors[i] = m.copy(angleStep = (m.angleStep + 1) % 8)
                    events.add(GameEvent.MIRROR_ROTATED)
                    statusBannerKey = "mirror_rotated"
                    statusBannerTimer = 1.8f
                    particles.addAll(createBurst(m.x, m.y, 0xFFF6BD60L, 10))
                }
            }
        }

        // Judo Shrines via Interact
        for (shrine in state.judoShrines) {
            val dist = hypot(pCenter.x - shrine.x, pCenter.y - (shrine.y - 34f))
            if (dist < 80f) {
                nearbyPrompt = "JUDO"
                if (input.interactJustPressed) {
                    interactTimer = 0.28f
                    pendingJudo = shrine.trialId
                }
            }
        }

        // Story NPCs & Navi Desk via Interact
        for (npc in state.definition.npcs) {
            val dist = hypot(pCenter.x - npc.x, pCenter.y - (npc.y - 34f))
            if (dist < 80f) {
                nearbyPrompt = if (npc.opensDesktop) "DESKTOP" else "NPC"
                if (input.interactJustPressed) {
                    interactTimer = 0.28f
                    if (npc.opensDesktop) {
                        requestOpenDesktop = true
                        events.add(GameEvent.DESKTOP_OPENED)
                    } else {
                        activeDialogueNpc = npc
                        events.add(GameEvent.NPC_TALKED)
                    }
                }
            }
        }

        // 9. Raycast Light Beams & Evaluate Photo-Sensors
        val beamResult = computeLightBeams(
            emitters = state.definition.emitters,
            mirrors = mutableMirrors,
            switches = mutableSwitches,
            obstacles = solidObstacles,
            worldW = state.definition.worldWidth,
            worldH = state.definition.worldHeight
        )
        val hitSensorIds = beamResult.second

        // 10. Evaluate Pressure Plates & Photo-Sensors
        val activeSignals = mutableSetOf<String>()
        val finalPlayerBounds = RectBounds(px, py, player.width, player.height)
        for (i in mutableSwitches.indices) {
            val sw = mutableSwitches[i]
            when (sw.kind) {
                SwitchKind.PRESSURE_PLATE -> {
                    val triggerRect = RectBounds(sw.x - 2f, sw.y - 10f, sw.width + 4f, sw.height + 12f)
                    val boxOnPlate = mutableBoxes.any { it.bounds().overlaps(triggerRect) }
                    val playerOnPlate = !sw.requiresWeight && finalPlayerBounds.overlaps(triggerRect)
                    val pressed = boxOnPlate || playerOnPlate
                    if (pressed && !sw.isActive) {
                        events.add(GameEvent.SWITCH_TRIGGERED)
                        particles.addAll(createBurst(sw.bounds().centerX, sw.y, 0xFF5EEAD4L, 10))
                    }
                    mutableSwitches[i] = sw.copy(isActive = pressed)
                    if (pressed) activeSignals.add(sw.signalId)
                }
                SwitchKind.PHOTO_SENSOR -> {
                    val lit = sw.id in hitSensorIds
                    if (lit && !sw.isActive) {
                        events.add(GameEvent.SWITCH_TRIGGERED)
                        statusBannerKey = "door_unlocked"
                        statusBannerTimer = 2.0f
                        particles.addAll(createBurst(sw.bounds().centerX, sw.bounds().centerY, 0xFF64F0FFL, 14))
                    }
                    mutableSwitches[i] = sw.copy(isActive = lit)
                    if (lit) activeSignals.add(sw.signalId)
                }
                SwitchKind.TERMINAL -> {
                    if (sw.isActive) activeSignals.add(sw.signalId)
                }
                else -> Unit
            }
        }
        state.judoShrines.forEach { if (it.completed) activeSignals.add(it.signalId) }

        val updatedDoors = state.doors.map { d ->
            val shouldOpen = d.linkedSignalId in activeSignals
            if (shouldOpen && !d.isOpen) {
                events.add(GameEvent.DOOR_OPENED)
                particles.addAll(createBurst(d.x + d.width * 0.5f, d.y + d.height * 0.5f, 0xFF64F0FFL, 14))
            }
            d.copy(isOpen = shouldOpen)
        }
        val finalPlatforms = updatedPlatforms.map { plat ->
            if (plat.type == PlatformType.HARD_LIGHT && plat.linkedSignalId != null) {
                plat.copy(isSolidActive = plat.linkedSignalId in activeSignals)
            } else plat
        }

        // 11. Collectibles & Checkpoints
        var collectedShardNow = false
        val updatedShards = state.shards.map { s ->
            if (!s.collected && finalPlayerBounds.overlaps(s.bounds())) {
                collectedShardNow = true
                events.add(GameEvent.SHARD_COLLECTED)
                particles.addAll(createBurst(s.x, s.y, 0xFFF6BD60L, 16))
                s.copy(collected = true)
            } else s
        }

        var spawnPoint = state.spawnPoint
        val updatedCheckpoints = state.checkpoints.map { cp ->
            if (!cp.activated && finalPlayerBounds.overlaps(cp.bounds())) {
                spawnPoint = Vec2(cp.x - player.width * 0.5f, cp.y - player.height - 4f)
                events.add(GameEvent.CHECKPOINT_SAVED)
                statusBannerKey = "checkpoint_reached"
                statusBannerTimer = 2.0f
                particles.addAll(createBurst(cp.x, cp.y - 32f, 0xFF5EEAD4L, 16))
                cp.copy(activated = true)
            } else cp
        }

        // 12. Hazards & Out-of-bounds respawn
        var screenShake = (state.screenShake - clampedDt * 2.5f).coerceAtLeast(0f)
        val hitHazard = state.definition.hazards.any { finalPlayerBounds.overlaps(it.bounds()) }
        val outOfBounds = py > state.definition.worldHeight + 120f || py < -220f
        if (hitHazard || outOfBounds) {
            px = spawnPoint.x
            py = spawnPoint.y
            vx = 0f
            vy = 0f
            globalGravityInverted = false
            screenShake = 1.0f
            events.add(GameEvent.RESPAWNED)
            particles.addAll(createBurst(px + player.width * 0.5f, py + player.height * 0.5f, 0xFFF26A8DL, 16))
        }

        // 13. Exit Portal Check
        val completed = finalPlayerBounds.overlaps(state.definition.exit.bounds())
        if (completed) {
            events.add(GameEvent.LEVEL_COMPLETED)
            particles.addAll(createBurst(state.definition.exit.bounds().centerX, state.definition.exit.bounds().centerY, 0xFF64F0FFL, 24))
        }

        // 14. Rich 7-state Animation & Expression State
        val animState = when {
            interactTimer > 0f -> LainAnimState.INTERACTING
            !onGround && ((vy < 0f && !effectiveInverted) || (vy > 0f && effectiveInverted)) -> LainAnimState.JUMPING
            !onGround -> LainAnimState.FALLING
            landingTimer > 0f && abs(vx) < 90f -> LainAnimState.LANDING
            abs(vx) > 215f -> LainAnimState.RUNNING
            abs(vx) > 16f -> LainAnimState.WALKING
            else -> LainAnimState.IDLE
        }

        val expression = when {
            completed || collectedShardNow -> LainExpression.HAPPY
            interactTimer > 0f || !onGround || abs(vx) > 220f -> LainExpression.FOCUSED
            nearbyPrompt != null -> LainExpression.CURIOUS
            else -> LainExpression.NEUTRAL
        }

        val updatedPlayer = player.copy(
            x = px,
            y = py,
            vx = vx,
            vy = vy,
            facingRight = facingRight,
            onGround = onGround,
            gravityInverted = effectiveInverted,
            coyoteTimer = coyote,
            jumpBufferTimer = jumpBuf,
            interactTimer = interactTimer,
            landingTimer = landingTimer,
            animTime = player.animTime + clampedDt,
            animState = animState,
            expression = expression,
            currentSurfaceType = surfaceType
        )

        // 15. Smooth Side-View Camera with Look-Ahead
        val lookAhead = if (facingRight) 68f else -68f
        val targetCamX = (px + player.width * 0.5f + lookAhead - viewportW * 0.5f)
            .coerceIn(0f, (state.definition.worldWidth - viewportW).coerceAtLeast(0f))
        val targetCamY = (py + player.height * 0.5f - viewportH * 0.52f)
            .coerceIn(0f, (state.definition.worldHeight - viewportH).coerceAtLeast(0f))
        val camSmooth = (1f - exp(-7.5f * clampedDt))
        val newCamX = state.cameraX + (targetCamX - state.cameraX) * camSmooth
        val newCamY = state.cameraY + (targetCamY - state.cameraY) * camSmooth

        return state.copy(
            player = updatedPlayer,
            platforms = finalPlatforms,
            boxes = mutableBoxes,
            switches = mutableSwitches,
            magnets = mutableMagnets,
            mirrors = mutableMirrors,
            doors = updatedDoors,
            shards = updatedShards,
            checkpoints = updatedCheckpoints,
            lightBeams = beamResult.first,
            particles = particles.takeLast(110),
            spawnPoint = spawnPoint,
            globalGravityInverted = globalGravityInverted,
            cameraX = newCamX,
            cameraY = newCamY,
            screenShake = screenShake,
            elapsedTime = newElapsed,
            isCompleted = completed,
            nearbyInteractLabel = nearbyPrompt,
            pendingJudoTrial = pendingJudo,
            activeDialogueNpc = activeDialogueNpc,
            requestOpenDesktop = requestOpenDesktop,
            statusBannerKey = statusBannerKey,
            statusBannerTimer = statusBannerTimer,
            events = events
        )
    }

    private fun computeLightBeams(
        emitters: List<LightEmitter>,
        mirrors: List<MirrorNode>,
        switches: List<SwitchNode>,
        obstacles: List<Platform>,
        worldW: Float,
        worldH: Float
    ): Pair<List<LightBeamSegment>, Set<String>> {
        if (emitters.isEmpty()) return Pair(emptyList(), emptySet())
        val segments = mutableListOf<LightBeamSegment>()
        val hitSensors = mutableSetOf<String>()
        val photoSensors = switches.filter { it.kind == SwitchKind.PHOTO_SENSOR }

        for (emitter in emitters) {
            var ox = emitter.x
            var oy = emitter.y
            val len = hypot(emitter.dirX, emitter.dirY).coerceAtLeast(0.001f)
            var dx = emitter.dirX / len
            var dy = emitter.dirY / len
            var lastMirrorId: String? = null

            for (bounce in 0 until 5) {
                var bestDist = 1400f
                var hitMirror: MirrorNode? = null
                var hitSensorId: String? = null

                val stepSize = 8f
                var dist = stepSize
                while (dist < bestDist) {
                    val rx = ox + dx * dist
                    val ry = oy + dy * dist
                    if (rx < 0f || rx > worldW || ry < 0f || ry > worldH) {
                        bestDist = dist
                        break
                    }
                    val sensor = photoSensors.firstOrNull { s ->
                        val b = s.bounds()
                        rx >= b.left - 10f && rx <= b.right + 10f && ry >= b.top - 10f && ry <= b.bottom + 10f
                    }
                    if (sensor != null) {
                        bestDist = dist
                        hitSensorId = sensor.id
                        break
                    }
                    val mirror = mirrors.firstOrNull { m ->
                        m.id != lastMirrorId && hypot(rx - m.x, ry - m.y) <= m.radius
                    }
                    if (mirror != null) {
                        bestDist = dist
                        hitMirror = mirror
                        break
                    }
                    val blocked = obstacles.any { obs ->
                        obs.type != PlatformType.HARD_LIGHT &&
                            rx >= obs.left && rx <= obs.right && ry >= obs.top && ry <= obs.bottom
                    }
                    if (blocked) {
                        bestDist = dist
                        break
                    }
                    dist += stepSize
                }

                val ex = ox + dx * bestDist
                val ey = oy + dy * bestDist
                segments.add(LightBeamSegment(ox, oy, ex, ey))

                if (hitSensorId != null) {
                    hitSensors.add(hitSensorId)
                    break
                }
                if (hitMirror != null) {
                    val outAngle = hitMirror.angleStep * (PI / 4.0)
                    ox = hitMirror.x
                    oy = hitMirror.y
                    dx = cos(outAngle).toFloat()
                    dy = sin(outAngle).toFloat()
                    lastMirrorId = hitMirror.id
                } else {
                    break
                }
            }
        }
        return Pair(segments, hitSensors)
    }

    private fun createBurst(x: Float, y: Float, colorArgb: Long, count: Int): List<Particle> {
        return List(count) { idx ->
            val angle = (idx.toFloat() / count) * (2f * PI.toFloat())
            val speed = 65f + (idx % 3) * 45f
            Particle(
                x = x,
                y = y,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 40f,
                life = 0.35f + (idx % 3) * 0.12f,
                maxLife = 0.55f,
                colorArgb = colorArgb,
                size = 3.5f + (idx % 3) * 1.8f
            )
        }
    }
}
