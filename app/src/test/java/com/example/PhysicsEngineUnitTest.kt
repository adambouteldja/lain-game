package com.example

import com.example.data.JudoTrialId
import com.example.game.engine.LevelCatalog
import com.example.game.engine.PhysicsEngine
import com.example.game.model.*
import com.example.judo.JudoBalanceEngine
import com.example.judo.JudoInput
import com.example.judo.JudoStatus
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
        assertEquals(LainAnimState.WALKING, state.player.animState)

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
    fun `ice surface preserves momentum more than solid surface`() {
        val def = LevelCatalog.levelById("2-1")
        var state = PhysicsEngine.createInitialState(def)
        // Place Lain on the ice runway (x=320, y=420)
        state = state.copy(player = state.player.copy(x = 320f, y = 420f))
        repeat(15) {
            state = PhysicsEngine.step(state, InputState(), 1f / 60f)
        }
        assertEquals(PlatformType.ICE, state.player.currentSurfaceType)

        // Accelerate right briefly then release
        repeat(20) {
            state = PhysicsEngine.step(state, InputState(moveRight = true), 1f / 60f)
        }
        val speedAfterAccel = state.player.vx
        assertTrue(speedAfterAccel > 100f)

        // Coast for 0.25s with no input — should still be sliding rapidly on ice
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

        // Place crate right onto pressure plate at x=1315, y=410
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

        // Aim mirror A to Right (0) and mirror B to Up (6) -> hits sensor at (810, 175)
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
    fun `magnetic coil pulls metallic crate onto pressure plate when polarity switches`() {
        val def = LevelCatalog.levelById("4-2")
        var state = PhysicsEngine.createInitialState(def)

        // Flip magnets to push right and attract toward right coil (like toggling the Magnet Switch)
        val flippedMagnets = state.magnets.map { coil ->
            coil.copy(mode = if (coil.mode == MagnetMode.ATTRACT) MagnetMode.REPEL else MagnetMode.ATTRACT)
        }
        state = state.copy(magnets = flippedMagnets)

        val initialBoxX = state.boxes.first().x
        repeat(140) {
            state = PhysicsEngine.step(state, InputState(), 1f / 60f)
        }
        val finalBoxX = state.boxes.first().x
        assertTrue("Magnetic force should pull metallic crate significantly right", finalBoxX > initialBoxX + 350f)
        assertTrue("Door should unlock once crate reaches pressure plate", state.doors.first().isOpen)
    }

    @Test
    fun `ink bridge creation adds solid platform in Creation world`() {
        val def = LevelCatalog.levelById("3-1")
        var state = PhysicsEngine.createInitialState(def)
        assertTrue(state.drawnPlatforms.isEmpty())

        state = PhysicsEngine.addInkBridge(state, Vec2(420f, 470f), Vec2(620f, 470f))
        assertEquals(1, state.drawnPlatforms.size)
        assertEquals(PlatformType.DRAWN_INK, state.drawnPlatforms.first().type)
    }

    @Test
    fun `judo balance engine fills harmony when balanced and redirects incoming waves`() {
        var state = JudoBalanceEngine.start(JudoBalanceEngine.createInitial(JudoTrialId.STILL_CENTER))
        assertEquals(JudoStatus.ACTIVE, state.status)

        // Counteract wind gently for 10 seconds to achieve equilibrium victory
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
    }
}
