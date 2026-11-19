package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.physicslab.PhysicsLabCatalog
import com.example.sketch.*
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
            schemaVersion = SaveProgress.CURRENT_SCHEMA_VERSION,
            hasStarted = true,
            currentLevelId = "2-1",
            unlockedWorlds = setOf(WorldId.LAINS_ROOM, WorldId.FAMILY_HOME, WorldId.GRAVITY),
            completedLevels = setOf("1-1", "1-2"),
            collectedShardsByLevel = mapOf("1-1" to setOf("s_1_1_a", "s_1_1_b")),
            completedJudoTrials = setOf(JudoTrialId.STILL_CENTER, JudoTrialId.UKEMI_REACTION),
            bestJudoScores = mapOf(JudoTrialId.STILL_CENTER to 740, JudoTrialId.UKEMI_REACTION to 920),
            unlockedAchievements = setOf(AchievementId.FIRST_SIGNAL, AchievementId.UKEMI_ADEPT),
            totalSketchesSaved = 2,
            readMessageIds = setOf("msg_chisa"),
            completedLabQuizzes = setOf("q_m1"),
            openedSandboxCount = 3
        )
        writer.saveProgress(expectedProgress)

        val expectedSettings = GameSettings(
            languageCode = "ar",
            musicVolume = 0.35f,
            sfxVolume = 0.85f,
            isMuted = true,
            controlSize = ControlSize.LARGE,
            controlMode = ControlMode.JOYSTICK,
            controlHorizontalPaddingDp = 24f,
            controlBottomOffsetDp = 18f,
            reduceShake = true,
            autoOutfitByLocation = false,
            preferredOutfit = LainOutfit.CYBERIA_JACKET,
            desktopTheme = DesktopThemeTint.CYBERIA_MAGENTA,
            desktopScanlines = false
        )
        writer.saveSettings(expectedSettings)

        val reader = GameStorage(context)
        val loadedProgress = reader.loadProgress()
        val loadedSettings = reader.loadSettings()

        assertEquals(expectedProgress, loadedProgress)
        assertEquals(expectedSettings.languageCode, loadedSettings.languageCode)
        assertEquals(expectedSettings.isMuted, loadedSettings.isMuted)
        assertEquals(expectedSettings.controlSize, loadedSettings.controlSize)
        assertEquals(expectedSettings.controlMode, loadedSettings.controlMode)
        assertEquals(expectedSettings.preferredOutfit, loadedSettings.preferredOutfit)
        assertEquals(expectedSettings.desktopTheme, loadedSettings.desktopTheme)
        assertEquals(expectedSettings.desktopScanlines, loadedSettings.desktopScanlines)
        assertEquals(expectedSettings.controlHorizontalPaddingDp, loadedSettings.controlHorizontalPaddingDp, 0.01f)
        assertEquals(expectedSettings.controlBottomOffsetDp, loadedSettings.controlBottomOffsetDp, 0.01f)
    }

    @Test
    fun `legacy v1 save progress migrates safely to v2 without losing data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("lain_beyond_reality_save", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val v1Json = """
            {
              "hasStarted": true,
              "currentLevelId": "2-1",
              "unlockedWorlds": ["LAINS_ROOM", "GRAVITY"],
              "completedLevels": ["1-1", "1-2"],
              "collectedShardsByLevel": {"1-1": ["s_1_1_a"]},
              "completedJudoTrials": ["STILL_CENTER"],
              "bestJudoScores": {"STILL_CENTER": 610},
              "unlockedAchievements": ["FIRST_SIGNAL"],
              "totalSketchesSaved": 1
            }
        """.trimIndent()
        prefs.edit().putString("progress_v1", v1Json).commit()

        val storage = GameStorage(context)
        val migrated = storage.loadProgress()

        assertEquals(SaveProgress.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
        assertTrue("1-1" in migrated.completedLevels)
        assertTrue("1-2" in migrated.completedLevels)
        assertTrue(WorldId.GRAVITY in migrated.unlockedWorlds)
        assertTrue("Migration should unlock Family Home", WorldId.FAMILY_HOME in migrated.unlockedWorlds)
        assertTrue("Migration should unlock School", WorldId.SCHOOL in migrated.unlockedWorlds)
        assertEquals(610, migrated.bestJudoScores[JudoTrialId.STILL_CENTER])
    }

    @Test
    fun `studio saves multi-layer projects migrates v1 sketches and supports autosave recovery`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = GameStorage(context)

        // Legacy v1 sketch -> studio project migration
        val v1Sketch = SketchDocument(
            id = "legacy_sketch_01",
            title = "Legacy Navi",
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
        assertTrue(storage.saveSketch(v1Sketch))
        storage.migrateLegacySketchesIfNeeded()
        val migratedProject = storage.loadStudioProject("legacy_sketch_01")
        assertNotNull("v1 sketch should migrate to StudioProject", migratedProject)
        assertEquals("Legacy Navi", migratedProject!!.title)
        assertEquals(1, migratedProject.layers.size)
        assertEquals(1, migratedProject.layers.first().strokes.size)

        // Multi-layer StudioProject save, duplicate, autosave, delete
        val project = StudioProject(
            id = "studio_proj_01",
            title = "Cyberia Portrait",
            createdAtMillis = 1700000001000L,
            updatedAtMillis = 1700000002000L,
            layers = listOf(
                DrawingLayer(id = "l1", name = "Lineart", opacity = 1f, strokes = listOf(
                    StudioStroke(
                        id = "stroke_1",
                        points = listOf(StudioPoint(0.1f, 0.2f, 0.8f), StudioPoint(0.5f, 0.6f, 0.9f)),
                        brushType = BrushType.PEN,
                        colorArgb = 0xFF64F0FFL,
                        size = 6f
                    )
                )),
                DrawingLayer(id = "l2", name = "Neon Glow", opacity = 0.7f, visible = true)
            ),
            activeLayerId = "l2"
        )
        assertTrue(storage.saveStudioProject(project))
        val reloaded = storage.loadStudioProject("studio_proj_01")
        assertNotNull(reloaded)
        assertEquals(2, reloaded!!.layers.size)
        assertEquals("Neon Glow", reloaded.layers[1].name)

        storage.saveStudioAutosave(project.copy(title = "Autosaved Draft"))
        assertEquals("Autosaved Draft", storage.loadStudioAutosave()?.title)
        storage.clearStudioAutosave()
        assertNull(storage.loadStudioAutosave())

        val copy = storage.duplicateStudioProject(project, "Cyberia Copy")
        assertNotNull(copy)
        assertEquals("Cyberia Copy", copy!!.title)

        assertTrue(storage.deleteStudioProject("studio_proj_01"))
        assertTrue(storage.deleteStudioProject(copy.id))
        assertTrue(storage.deleteStudioProject("legacy_sketch_01"))
    }

    @Test
    fun `physics lab parses valid imported official course JSON and rejects invalid JSON`() {
        val valid = PhysicsLabCatalog.parseImportedCourseJson(
            """{"title":"L1 SM Cinématique Officielle","summary":"Cours PDF officiel importé."}"""
        )
        assertNotNull(valid)
        assertEquals("L1 SM Cinématique Officielle", valid!!.titleEn)

        val invalid = PhysicsLabCatalog.parseImportedCourseJson("not valid json")
        assertNull(invalid)
    }

    @Test
    fun `main menu navigation reaches desktop sandbox physics lab studio settings worlds and gameplay`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(120L)

        // Main menu Evolution 2.0 destinations visible
        composeRule.onNodeWithTag("menu_continue_button").assertIsDisplayed()
        composeRule.onNodeWithTag("menu_desktop_button").assertIsDisplayed()
        composeRule.onNodeWithTag("menu_sketchbook_button").assertIsDisplayed()
        composeRule.onNodeWithTag("menu_sandbox_button").assertIsDisplayed()
        composeRule.onNodeWithTag("menu_physics_lab_button").assertIsDisplayed()
        composeRule.onNodeWithTag("menu_worlds_button").assertIsDisplayed()
        composeRule.onNodeWithTag("menu_achievements_button").assertIsDisplayed()
        composeRule.onNodeWithTag("menu_settings_button").assertIsDisplayed()

        // Navigate to Lain Desktop
        composeRule.onNodeWithTag("menu_desktop_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("desktop_app_messages").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop_app_files").assertIsDisplayed()
        composeRule.onNodeWithTag("desktop_back_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)

        // Navigate to Physics Sandbox
        composeRule.onNodeWithTag("menu_sandbox_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("sandbox_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("sandbox_spawn_ball").performClick()
        composeRule.mainClock.advanceTimeBy(80L)
        composeRule.onNodeWithTag("sandbox_back_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)

        // Navigate to University SM Physics Lab
        composeRule.onNodeWithTag("menu_physics_lab_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("lab_year1_chip").assertIsDisplayed()
        composeRule.onNodeWithTag("lab_year2_chip").performClick()
        composeRule.mainClock.advanceTimeBy(80L)
        composeRule.onNodeWithTag("lab_back_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)

        // Navigate to Drawing Studio
        composeRule.onNodeWithTag("menu_sketchbook_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("studio_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("studio_save_button").assertIsDisplayed()
        composeRule.onNodeWithTag("studio_back_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)

        // Navigate to Settings & toggle Arabic RTL then back to English LTR
        composeRule.onNodeWithTag("menu_settings_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("lang_ar_chip").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("lang_en_chip").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("settings_back_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)

        // Navigate to Worlds and launch Level 1-1
        composeRule.onNodeWithTag("menu_worlds_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("level_card_1-1").performClick()
        composeRule.mainClock.advanceTimeBy(120L)

        // Verify gameplay touch controls, persistent HUD hint button, & pause
        composeRule.onNodeWithTag("control_left").assertIsDisplayed()
        composeRule.onNodeWithTag("control_right").assertIsDisplayed()
        composeRule.onNodeWithTag("control_jump").assertIsDisplayed()
        composeRule.onNodeWithTag("control_interact").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_hint_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("hint_dialog_close").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("hud_pause_button").performClick()
        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.onNodeWithTag("pause_resume_button").assertIsDisplayed()
    }
}
