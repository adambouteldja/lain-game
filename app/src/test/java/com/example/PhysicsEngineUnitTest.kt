package com.example

import com.example.data.JudoTrialId
import com.example.data.WorldId
import com.example.game.engine.LevelCatalog
import com.example.game.engine.PhysicsEngine
import com.example.game.model.*
import com.example.judo.JudoBalanceEngine
import com.example.judo.JudoInput
import com.example.judo.JudoStatus
import com.example.sandbox.PhysicsSandboxEngine
import com.example.sandbox.SandboxBodyKind
import com.example.sandbox.SandboxPreset
import org.junit.Assert.*
import org.junit.Test

class PhysicsEngineUnitTest {

    @Test
    fun `movement and jumping physics work with ground collision`() {
        val def = LevelCatalog.levelById("1-1")
        var state = PhysicsEngine.createInitialState(def)

        // Let Lain settle onto bedroom floor
        repeat(30) {
            state = PhysicsEngine.step(state, InputState(), 1f / 60f)
        }
        assertTrue("Lain should land on floor", state.player.onGround)
        val groundY = state.player.y
        val startX = state.player.x

        // Move right for 0.5s
        repeat(30) {
            state = PhysicsEngine.step(state, InputState(moveRight = true), 1f / 60f)
        }
        assertTrue("Lain should advance right", state.player.x > startX + 60f)
        assertTrue(
            "Lain should animate walking or running",
            state.player.animState == LainAnimState.WALKING || state.player.animState == LainAnimState.RUNNING
        )

        // Jump upward
        state = PhysicsEngine.step(state, InputState(jumpPressed = true, jumpJustPressed = true), 1f / 60f)
        assertTrue("Jump must impart negative vertical velocity", state.player.vy < -400f)
        repeat(10) {
            state = PhysicsEngine.step(state, InputState(jumpPressed = true), 1f / 60f)
        }
        assertTrue("Lain should rise above ground", state.player.y < groundY - 40f)

        // Let gravity pull Lain back down onto floor
        repeat(70) {
            state = PhysicsEngine.step(state, InputState(), 1f / 60f)
        }
        assertTrue("Lain should land back on floor after jump", state.player.onGround)
        assertEquals(groundY, state.player.y, 2f)
    }

    @Test
    fun `analog joystick provides proportional horizontal speed`() {
        val def = LevelCatalog.levelById("1-1")
        var halfState = PhysicsEngine.createInitialState(def)
        var fullState = PhysicsEngine.createInitialState(def)

        repeat(25) {
            halfState = PhysicsEngine.step(halfState, InputState(analogX = 0.45f), 1f / 60f)
            fullState = PhysicsEngine.step(fullState, InputState(analogX = 1.0f), 1f / 60f)
        }

        assertTrue("Half stick should move right", halfState.player.vx > 40f)
        assertTrue("Full stick should move faster than half stick", fullState.player.vx > halfState.player.vx * 1.4f)
    }

    @Test
    fun `ice surface preserves momentum more than solid surface`() {
        val def = LevelCatalog.levelById("2-1")
        var state = PhysicsEngine.createInitialState(def)
        state = state.copy(player = state.player.copy(x = 320f, y = 420f))
        repeat(15) {
            state = PhysicsEngine.step(state, InputState(), 1f / 60f)
        }
        assertEquals(PlatformType.ICE, state.player.currentSurfaceType)

        repeat(20) {
            state = PhysicsEngine.step(state, InputState(moveRight = true), 1f / 60f)
        }
        val speedAfterAccel = state.player.vx
        assertTrue(speedAfterAccel > 100f)

        repeat(15) {
            state = PhysicsEngine.step(state, InputState(), 1f / 60f)
        }
        assertTrue("Ice should preserve >70% horizontal momentum", state.player.vx > speedAfterAccel * 0.70f)
    }

    @Test
    fun `pushing crate onto pressure plate opens linked door`() {
        val def = LevelCatalog.levelById("1-2")
        var state = PhysicsEngine.createInitialState(def)
        assertFalse("Door should start closed", state.doors.first().isOpen)

        val placedBox = state.boxes.first().copy(x = 1315f, y = 415f)
        state = state.copy(boxes = listOf(placedBox))

        repeat(20) {
            state = PhysicsEngine.step(state, InputState(), 1f / 60f)
        }
        assertTrue("Pressure plate should activate under crate weight", state.switches.first().isActive)
        assertTrue("Linked door should open when plate is pressed", state.doors.first().isOpen)
    }

    @Test
    fun `rotating prism mirrors routes light beam into photo sensor and activates hard-light bridge`() {
        val def = LevelCatalog.levelById("4-1")
        var state = PhysicsEngine.createInitialState(def)
        assertFalse("Hard-light bridge starts inactive", state.platforms.first { it.id == "hl_4_1" }.isSolidActive)
        assertTrue("Standing platform under second mirror must exist", def.platforms.any { it.id == "m_plat2" })

        val alignedMirrors = state.mirrors.map { m ->
            when (m.id) {
                "mir_4_1_a" -> m.copy(angleStep = 0)
                "mir_4_1_b" -> m.copy(angleStep = 6)
                else -> m
            }
        }
        state = state.copy(mirrors = alignedMirrors)
        state = PhysicsEngine.step(state, InputState(), 1f / 60f)

        assertTrue("Photo-sensor should be energized by reflected beam", state.switches.first().isActive)
        assertTrue("Hard-light bridge should materialize", state.platforms.first { it.id == "hl_4_1" }.isSolidActive)
        assertTrue("Exit door should open", state.doors.first().isOpen)
    }

    @Test
    fun `level 4-2 metallic crate stays on upper track even after delayed arrival then solves door`() {
        val def = LevelCatalog.levelById("4-2")
        var state = PhysicsEngine.createInitialState(def)

        // Simulate player taking 4 seconds (240 frames) before reaching the Magnet Switch!
        repeat(240) {
            state = PhysicsEngine.step(state, InputState(), 1f / 60f)
        }
        val crateAfterWait = state.boxes.first()
        assertTrue("Left track stopper must prevent crate from falling off upper track", crateAfterWait.y < 320f)
        assertTrue("Crate must remain on the upper track", crateAfterWait.x >= 480f)

        // Now flip magnets to ATTRACT (same as pressing ACT at the Magnet Terminal)
        val flippedMagnets = state.magnets.map { coil ->
            coil.copy(mode = if (coil.mode == MagnetMode.ATTRACT) MagnetMode.REPEL else MagnetMode.ATTRACT)
        }
        state = state.copy(magnets = flippedMagnets)

        repeat(200) {
            state = PhysicsEngine.step(state, InputState(), 1f / 60f)
        }
        assertTrue("Door should unlock once crate reaches pressure plate", state.doors.first().isOpen)
    }

    @Test
    fun `level 6-1 metallic crate remains on upper magnetic track after delay and activates plate`() {
        val def = LevelCatalog.levelById("6-1")
        var state = PhysicsEngine.createInitialState(def)

        repeat(240) {
            state = PhysicsEngine.step(state, InputState(), 1f / 60f)
        }
        val crateAfterWait = state.boxes.first { it.isMetal }
        assertTrue("6-1 left stopper must keep metallic crate on upper track", crateAfterWait.y < 340f)

        val flippedMagnets = state.magnets.map { coil ->
            coil.copy(mode = if (coil.mode == MagnetMode.ATTRACT) MagnetMode.REPEL else MagnetMode.ATTRACT)
        }
        state = state.copy(magnets = flippedMagnets)

        repeat(200) {
            state = PhysicsEngine.step(state, InputState(), 1f / 60f)
        }
        val plateActive = state.switches.first { it.kind == SwitchKind.PRESSURE_PLATE }.isActive
        assertTrue("6-1 pressure plate must activate after magnetic pull", plateActive)
    }

    @Test
    fun `ink bridge creation supports sloped platforms in Creation world`() {
        val def = LevelCatalog.levelById("3-1")
        var state = PhysicsEngine.createInitialState(def)
        assertTrue(state.drawnPlatforms.isEmpty())

        state = PhysicsEngine.addInkBridge(state, Vec2(420f, 490f), Vec2(620f, 430f))
        assertEquals(1, state.drawnPlatforms.size)
        val bridge = state.drawnPlatforms.first()
        assertEquals(PlatformType.DRAWN_INK, bridge.type)
        assertTrue("Sloped ink draw should produce sloped surface", bridge.isSlopedSurface())
    }

    @Test
    fun `level catalog exposes 10 worlds 16 levels and interactive Navi Desk in 1-1`() {
        assertEquals(10, WorldId.entries.size)
        assertEquals(16, LevelCatalog.allLevels.size)

        val l11 = LevelCatalog.levelById("1-1")
        assertTrue("1-1 must include interactive Navi Desk NPC", l11.npcs.any { it.opensDesktop })

        val l22 = LevelCatalog.levelById("2-2")
        val gravSwitch = l22.switches.first { it.kind == SwitchKind.GRAVITY_SWITCH }
        assertTrue("2-2 gravity switch must be grounded & reachable", gravSwitch.y >= 460f)
    }

    @Test
    fun `judo balance engine wins Still Center and Ukemi Breakfall trials`() {
        var state = JudoBalanceEngine.start(JudoBalanceEngine.createInitial(JudoTrialId.STILL_CENTER))
        assertEquals(JudoStatus.ACTIVE, state.status)

        var ticks = 0
        while (state.status == JudoStatus.ACTIVE && ticks < 900) {
            val leanLeft = state.cogOffset > 0.05f || state.cogVelocity > 0.12f
            val leanRight = state.cogOffset < -0.05f || state.cogVelocity < -0.12f
            state = JudoBalanceEngine.step(
                state,
                JudoInput(leanLeft = leanLeft, leanRight = leanRight, braceCenter = !leanLeft && !leanRight),
                1f / 60f
            )
            ticks++
        }
        assertEquals("Maintaining center of gravity should win Still Center trial", JudoStatus.WON, state.status)
        assertTrue("Score should be positive", state.score > 0)

        // Trial III: UKEMI_REACTION breakfall
        var ukemiState = JudoBalanceEngine.start(JudoBalanceEngine.createInitial(JudoTrialId.UKEMI_REACTION))
        var ukemiTicks = 0
        while (ukemiState.status == JudoStatus.ACTIVE && ukemiTicks < 1800) {
            val sweepInWindow = ukemiState.waves.any { w -> w.isSweepWave && !w.handled && w.distance in 0.08f..0.28f }
            val redirectLeft = ukemiState.waves.any { w -> !w.isSweepWave && !w.handled && w.fromSide == -1 && w.distance in 0.08f..0.28f }
            val redirectRight = ukemiState.waves.any { w -> !w.isSweepWave && !w.handled && w.fromSide == 1 && w.distance in 0.08f..0.28f }
            val leanLeft = ukemiState.cogOffset > 0.05f || ukemiState.cogVelocity > 0.12f
            val leanRight = ukemiState.cogOffset < -0.05f || ukemiState.cogVelocity < -0.12f
            ukemiState = JudoBalanceEngine.step(
                ukemiState,
                JudoInput(
                    leanLeft = leanLeft,
                    leanRight = leanRight,
                    braceCenter = !leanLeft && !leanRight,
                    redirectLeftJustPressed = redirectLeft,
                    redirectRightJustPressed = redirectRight,
                    ukemiJustPressed = sweepInWindow
                ),
                1f / 60f
            )
            ukemiTicks++
        }
        assertEquals("Timed Ukemi breakfalls should win Trial III", JudoStatus.WON, ukemiState.status)
    }

    @Test
    fun `physics sandbox simulates free fall pendulum and SI energy telemetry`() {
        var fall = PhysicsSandboxEngine.createPreset(SandboxPreset.FREE_FALL)
        val initialY = fall.bodies.first().y
        val initialPe = fall.totalPotentialEnergyJ()
        assertTrue("Initial potential energy must be positive", initialPe > 0f)

        repeat(40) {
            fall = PhysicsSandboxEngine.step(fall, 1f / 60f)
        }
        assertTrue("Body must fall downward under gravity", fall.bodies.first().y > initialY + 30f)
        assertTrue("Falling bodies must gain kinetic energy", fall.totalKineticEnergyJ() > 0f)

        val spawned = PhysicsSandboxEngine.spawnBody(fall, SandboxBodyKind.METAL_CRATE)
        assertEquals(fall.bodies.size + 1, spawned.bodies.size)

        var pend = PhysicsSandboxEngine.createPreset(SandboxPreset.PENDULUM_LAB)
        val startX = pend.bodies.first().x
        repeat(60) {
            pend = PhysicsSandboxEngine.step(pend, 1f / 60f)
        }
        assertNotEquals("Pendulum bob should oscillate", startX, pend.bodies.first().x, 5f)
    }
}
