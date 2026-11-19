package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LainPersistenceAndUiRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `progress and settings persist cleanly across storage instances`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val writer = GameStorage(context)

        val expectedProgress = SaveProgress(
            hasStarted = true,
            currentLevelId = "2-1",
            unlockedWorlds = setOf(WorldId.LAINS_ROOM, WorldId.GRAVITY),
            completedLevels = setOf("1-1", "1-2"),
            collectedShardsByLevel = mapOf("1-1" to setOf("s_1_1_a", "s_1_1_b")),
            completedJudoTrials = setOf(JudoTrialId.STILL_CENTER),
            bestJudoScores = mapOf(JudoTrialId.STILL_CENTER to 740),
            unlockedAchievements = setOf(AchievementId.FIRST_SIGNAL),
            totalSketchesSaved = 2
        )
        writer.saveProgress(expectedProgress)

        val expectedSettings = GameSettings(
            languageCode = "ar",
            musicVolume = 0.35f,
            sfxVolume = 0.85f,
            isMuted = true,
            controlSize = ControlSize.LARGE,
            reduceShake = true
        )
        writer.saveSettings(expectedSettings)

        // Create brand new storage reader to simulate app restart
        val reader = GameStorage(context)
        val loadedProgress = reader.loadProgress()
        val loadedSettings = reader.loadSettings()

        assertEquals(expectedProgress, loadedProgress)
        assertEquals(expectedSettings.languageCode, loadedSettings.languageCode)
        assertEquals(expectedSettings.isMuted, loadedSettings.isMuted)
        assertEquals(expectedSettings.controlSize, loadedSettings.controlSize)
        assertEquals(expectedSettings.reduceShake, loadedSettings.reduceShake)
        assertEquals(expectedSettings.musicVolume, loadedSettings.musicVolume, 0.01f)
        assertEquals(expectedSettings.sfxVolume, loadedSettings.sfxVolume, 0.01f)
    }

    @Test
    fun `sketchbook saves reloads and deletes local vector drawings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = GameStorage(context)

        val sketch = SketchDocument(
            id = "test_sketch_01",
            title = "Navi Window",
            updatedAtMillis = 1700000000000L,
            canvasWidth = 800,
            canvasHeight = 600,
            strokes = listOf(
                SketchStroke(
                    points = listOf(SketchPoint(10f, 20f), SketchPoint(120f, 240f)),
                    colorArgb = 0xFF64F0FFL,
                    strokeWidth = 8f,
                    isEraser = false
                )
            )
        )

        assertTrue("Save should succeed", storage.saveSketch(sketch))
        val reloaded = storage.loadSketch("test_sketch_01")
        assertNotNull("Saved sketch should reload", reloaded)
        assertEquals("Navi Window", reloaded!!.title)
        assertEquals(1, reloaded.strokes.size)
        assertEquals(2, reloaded.strokes.first().points.size)
        assertEquals(120f, reloaded.strokes.first().points[1].x, 0.01f)

        assertTrue("Delete should succeed", storage.deleteSketch("test_sketch_01"))
        assertNull("Deleted sketch should no longer exist", storage.loadSketch("test_sketch_01"))
    }

    @Test
    fun `main menu navigation reaches worlds sketchbook achievements settings and gameplay`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(120L)

        // Main menu buttons visible
        composeRule.onNodeWithTag("menu_continue_button").assertIsDisplayed()
        composeRule.onNodeWithTag("menu_sketchbook_button").assertIsDisplayed()
        composeRule.onNodeWithTag("menu_worlds_button").assertIsDisplayed()
        composeRule.onNodeWithTag("menu_achievements_button").assertIsDisplayed()
        composeRule.onNodeWithTag("menu_settings_button").assertIsDisplayed()

        // Navigate to Settings & toggle Arabic RTL then back to English LTR
        composeRule.onNodeWithTag("menu_settings_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("lang_ar_chip").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("lang_en_chip").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("settings_back_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)

        // Navigate to Sketchbook and verify tools
        composeRule.onNodeWithTag("menu_sketchbook_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("sketch_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("sketch_save_button").assertIsDisplayed()
        composeRule.onNodeWithTag("sketch_back_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)

        // Navigate to Achievements
        composeRule.onNodeWithTag("menu_achievements_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("achievements_back_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)

        // Navigate to Worlds and launch Level 1-1
        composeRule.onNodeWithTag("menu_worlds_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("level_card_1-1").performClick()
        composeRule.mainClock.advanceTimeBy(120L)

        // Verify gameplay touch controls & pause
        composeRule.onNodeWithTag("control_left").assertIsDisplayed()
        composeRule.onNodeWithTag("control_right").assertIsDisplayed()
        composeRule.onNodeWithTag("control_jump").assertIsDisplayed()
        composeRule.onNodeWithTag("control_interact").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_pause_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("pause_resume_button").assertIsDisplayed()
    }
}
