package com.example.data

import android.content.Context
import com.example.sketch.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Resilient local persistence with v1 -> v2 backward-compatible save migration,
 * backup recovery from corrupted files, and non-flattened multi-layer studio projects.
 */
class GameStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val sketchesDir: File
        get() = File(context.filesDir, "sketches").apply { if (!exists()) mkdirs() }
    private val studioDir: File
        get() = File(context.filesDir, "studio_projects").apply { if (!exists()) mkdirs() }
    private val autosaveFile: File
        get() = File(studioDir, "_autosave_draft.json")

    fun loadProgress(): SaveProgress {
        val raw = prefs.getString(KEY_PROGRESS_V2, null)
            ?: prefs.getString(KEY_PROGRESS_V1, null)
            ?: prefs.getString(KEY_PROGRESS_BACKUP, null)
            ?: return SaveProgress()

        return parseProgressJson(raw) ?: run {
            val backup = prefs.getString(KEY_PROGRESS_BACKUP, null)
            if (backup != null) parseProgressJson(backup) ?: SaveProgress() else SaveProgress()
        }
    }

    private fun parseProgressJson(raw: String): SaveProgress? {
        return try {
            val json = JSONObject(raw)
            val version = json.optInt("schemaVersion", 1)
            val unlockedWorlds = mutableSetOf(WorldId.LAINS_ROOM)
            val worldsArr = json.optJSONArray("unlockedWorlds")
            if (worldsArr != null) {
                for (i in 0 until worldsArr.length()) {
                    runCatching { WorldId.valueOf(worldsArr.getString(i)) }.getOrNull()?.let {
                        unlockedWorlds.add(it)
                    }
                }
            }

            val completedLevels = mutableSetOf<String>()
            val compArr = json.optJSONArray("completedLevels")
            if (compArr != null) {
                for (i in 0 until compArr.length()) {
                    completedLevels.add(compArr.getString(i))
                }
            }

            // Backward-compatible v1 -> v2 progression migration:
            // Preserve all cleared v1 worlds/levels and unlock new narrative locations if 1-2 or higher was cleared
            if (version < SaveProgress.CURRENT_SCHEMA_VERSION) {
                if ("1-1" in completedLevels || "1-2" in completedLevels) {
                    unlockedWorlds.add(WorldId.FAMILY_HOME)
                }
                if ("1-2" in completedLevels || WorldId.GRAVITY in unlockedWorlds) {
                    unlockedWorlds.add(WorldId.SCHOOL)
                    unlockedWorlds.add(WorldId.STREETS)
                    unlockedWorlds.add(WorldId.CYBERIA)
                }
            }

            val shardsMap = mutableMapOf<String, Set<String>>()
            val shardsObj = json.optJSONObject("collectedShardsByLevel")
            if (shardsObj != null) {
                val keys = shardsObj.keys()
                while (keys.hasNext()) {
                    val levelKey = keys.next()
                    val arr = shardsObj.optJSONArray(levelKey) ?: continue
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    shardsMap[levelKey] = set
                }
            }

            val judoSet = mutableSetOf<JudoTrialId>()
            val judoArr = json.optJSONArray("completedJudoTrials")
            if (judoArr != null) {
                for (i in 0 until judoArr.length()) {
                    runCatching { JudoTrialId.valueOf(judoArr.getString(i)) }.getOrNull()?.let {
                        judoSet.add(it)
                    }
                }
            }

            val judoScores = mutableMapOf<JudoTrialId, Int>()
            val scoresObj = json.optJSONObject("bestJudoScores")
            if (scoresObj != null) {
                val keys = scoresObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    runCatching { JudoTrialId.valueOf(key) }.getOrNull()?.let { id ->
                        judoScores[id] = scoresObj.optInt(key, 0)
                    }
                }
            }

            val achSet = mutableSetOf<AchievementId>()
            val achArr = json.optJSONArray("unlockedAchievements")
            if (achArr != null) {
                for (i in 0 until achArr.length()) {
                    runCatching { AchievementId.valueOf(achArr.getString(i)) }.getOrNull()?.let {
                        achSet.add(it)
                    }
                }
            }

            val readMsgs = mutableSetOf<String>()
            json.optJSONArray("readMessageIds")?.let { arr ->
                for (i in 0 until arr.length()) readMsgs.add(arr.getString(i))
            }

            val labQuizzes = mutableSetOf<String>()
            json.optJSONArray("completedLabQuizzes")?.let { arr ->
                for (i in 0 until arr.length()) labQuizzes.add(arr.getString(i))
            }

            val migrated = SaveProgress(
                schemaVersion = SaveProgress.CURRENT_SCHEMA_VERSION,
                hasStarted = json.optBoolean("hasStarted", false),
                currentLevelId = json.optString("currentLevelId", "1-1"),
                unlockedWorlds = unlockedWorlds,
                completedLevels = completedLevels,
                collectedShardsByLevel = shardsMap,
                completedJudoTrials = judoSet,
                bestJudoScores = judoScores,
                unlockedAchievements = achSet,
                totalSketchesSaved = json.optInt("totalSketchesSaved", 0),
                readMessageIds = readMsgs,
                completedLabQuizzes = labQuizzes,
                openedSandboxCount = json.optInt("openedSandboxCount", 0)
            )
            if (version < SaveProgress.CURRENT_SCHEMA_VERSION) {
                saveProgress(migrated)
            }
            migrated
        } catch (_: Exception) {
            null
        }
    }

    fun saveProgress(progress: SaveProgress) {
        try {
            val json = JSONObject().apply {
                put("schemaVersion", SaveProgress.CURRENT_SCHEMA_VERSION)
                put("hasStarted", progress.hasStarted)
                put("currentLevelId", progress.currentLevelId)
                put("unlockedWorlds", JSONArray(progress.unlockedWorlds.map { it.name }))
                put("completedLevels", JSONArray(progress.completedLevels.toList()))
                put("collectedShardsByLevel", JSONObject().apply {
                    progress.collectedShardsByLevel.forEach { (levelId, shards) ->
                        put(levelId, JSONArray(shards.toList()))
                    }
                })
                put("completedJudoTrials", JSONArray(progress.completedJudoTrials.map { it.name }))
                put("bestJudoScores", JSONObject().apply {
                    progress.bestJudoScores.forEach { (id, score) ->
                        put(id.name, score)
                    }
                })
                put("unlockedAchievements", JSONArray(progress.unlockedAchievements.map { it.name }))
                put("totalSketchesSaved", progress.totalSketchesSaved)
                put("readMessageIds", JSONArray(progress.readMessageIds.toList()))
                put("completedLabQuizzes", JSONArray(progress.completedLabQuizzes.toList()))
                put("openedSandboxCount", progress.openedSandboxCount)
            }
            val serialized = json.toString()
            val prev = prefs.getString(KEY_PROGRESS_V2, null)
            prefs.edit().apply {
                if (prev != null) putString(KEY_PROGRESS_BACKUP, prev)
                putString(KEY_PROGRESS_V2, serialized)
            }.commit()
        } catch (_: Exception) {
            // Safely absorb serialization failures
        }
    }

    fun loadSettings(): GameSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return GameSettings()
        return try {
            val json = JSONObject(raw)
            GameSettings(
                languageCode = json.optString("languageCode", "en"),
                musicVolume = json.optDouble("musicVolume", 0.55).toFloat().coerceIn(0f, 1f),
                sfxVolume = json.optDouble("sfxVolume", 0.75).toFloat().coerceIn(0f, 1f),
                isMuted = json.optBoolean("isMuted", false),
                controlSize = runCatching {
                    ControlSize.valueOf(json.optString("controlSize", ControlSize.STANDARD.name))
                }.getOrDefault(ControlSize.STANDARD),
                controlMode = runCatching {
                    ControlMode.valueOf(json.optString("controlMode", ControlMode.BUTTONS.name))
                }.getOrDefault(ControlMode.BUTTONS),
                controlHorizontalPaddingDp = json.optDouble("controlHorizontalPaddingDp", 14.0).toFloat().coerceIn(4f, 36f),
                controlBottomOffsetDp = json.optDouble("controlBottomOffsetDp", 10.0).toFloat().coerceIn(0f, 36f),
                reduceShake = json.optBoolean("reduceShake", false),
                autoOutfitByLocation = json.optBoolean("autoOutfitByLocation", true),
                preferredOutfit = runCatching {
                    LainOutfit.valueOf(json.optString("preferredOutfit", LainOutfit.HOME_SWEATER.name))
                }.getOrDefault(LainOutfit.HOME_SWEATER),
                desktopTheme = runCatching {
                    DesktopThemeTint.valueOf(json.optString("desktopTheme", DesktopThemeTint.NAVI_CYAN.name))
                }.getOrDefault(DesktopThemeTint.NAVI_CYAN),
                desktopScanlines = json.optBoolean("desktopScanlines", true)
            )
        } catch (_: Exception) {
            GameSettings()
        }
    }

    fun saveSettings(settings: GameSettings) {
        try {
            val json = JSONObject().apply {
                put("languageCode", settings.languageCode)
                put("musicVolume", settings.musicVolume.toDouble())
                put("sfxVolume", settings.sfxVolume.toDouble())
                put("isMuted", settings.isMuted)
                put("controlSize", settings.controlSize.name)
                put("controlMode", settings.controlMode.name)
                put("controlHorizontalPaddingDp", settings.controlHorizontalPaddingDp.toDouble())
                put("controlBottomOffsetDp", settings.controlBottomOffsetDp.toDouble())
                put("reduceShake", settings.reduceShake)
                put("autoOutfitByLocation", settings.autoOutfitByLocation)
                put("preferredOutfit", settings.preferredOutfit.name)
                put("desktopTheme", settings.desktopTheme.name)
                put("desktopScanlines", settings.desktopScanlines)
            }
            prefs.edit().putString(KEY_SETTINGS, json.toString()).commit()
        } catch (_: Exception) {
            // Gracefully absorb
        }
    }

    // =========================================================================
    // MULTI-LAYER STUDIO PROJECTS + AUTOMATIC v1 SKETCH MIGRATION + AUTOSAVE
    // =========================================================================

    fun migrateLegacySketchesIfNeeded() {
        val legacyFiles = sketchesDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return
        for (file in legacyFiles) {
            val doc = parseLegacySketchFile(file) ?: continue
            val target = File(studioDir, "${doc.id}.json")
            if (!target.exists()) {
                val layerId = "layer_migrated_${doc.id}"
                val project = StudioProject(
                    id = doc.id,
                    title = doc.title,
                    createdAtMillis = doc.updatedAtMillis,
                    updatedAtMillis = doc.updatedAtMillis,
                    canvasWidth = doc.canvasWidth.coerceAtLeast(400),
                    canvasHeight = doc.canvasHeight.coerceAtLeast(400),
                    layers = listOf(
                        DrawingLayer(
                            id = layerId,
                            name = "Layer 1",
                            strokes = doc.strokes.mapIndexed { idx, s ->
                                StudioStroke(
                                    id = "s_${idx}_${doc.id}",
                                    points = s.points.map { StudioPoint(it.x, it.y, 1f) },
                                    brushType = if (s.isEraser) BrushType.ERASER else BrushType.PEN,
                                    colorArgb = s.colorArgb,
                                    size = s.strokeWidth,
                                    opacity = 1f
                                )
                            }
                        )
                    ),
                    activeLayerId = layerId
                )
                saveStudioProject(project)
            }
        }
    }

    fun saveStudioProject(project: StudioProject): Boolean {
        return try {
            val target = File(studioDir, "${project.id}.json")
            writeJsonAtomically(target, serializeStudioProject(project))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun saveStudioAutosave(project: StudioProject) {
        try {
            writeJsonAtomically(autosaveFile, serializeStudioProject(project))
        } catch (_: Exception) {
            // ignore
        }
    }

    fun loadStudioAutosave(): StudioProject? {
        if (!autosaveFile.exists()) return null
        return parseStudioProjectFile(autosaveFile)
    }

    fun clearStudioAutosave() {
        runCatching { if (autosaveFile.exists()) autosaveFile.delete() }
    }

    fun loadAllStudioProjects(): List<StudioProject> {
        migrateLegacySketchesIfNeeded()
        return try {
            val files = studioDir.listFiles { f ->
                f.isFile && f.name.endsWith(".json") && !f.name.startsWith("_autosave")
            } ?: return emptyList()
            files.mapNotNull { parseStudioProjectFile(it) }
                .sortedByDescending { it.updatedAtMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadStudioProject(id: String): StudioProject? {
        val file = File(studioDir, "$id.json")
        if (!file.exists()) return null
        return parseStudioProjectFile(file)
    }

    fun deleteStudioProject(id: String): Boolean {
        return try {
            val file = File(studioDir, "$id.json")
            val legacy = File(sketchesDir, "$id.json")
            if (legacy.exists()) legacy.delete()
            !file.exists() || file.delete()
        } catch (_: Exception) {
            false
        }
    }

    fun duplicateStudioProject(source: StudioProject, newTitle: String): StudioProject? {
        val now = System.currentTimeMillis()
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            title = newTitle,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        return if (saveStudioProject(copy)) copy else null
    }

    // Legacy v1 Sketch API kept intact for test compatibility
    fun saveSketch(doc: SketchDocument): Boolean {
        return try {
            val json = JSONObject().apply {
                put("id", doc.id)
                put("title", doc.title)
                put("updatedAtMillis", doc.updatedAtMillis)
                put("canvasWidth", doc.canvasWidth)
                put("canvasHeight", doc.canvasHeight)
                put("strokes", JSONArray().apply {
                    doc.strokes.forEach { stroke ->
                        put(JSONObject().apply {
                            put("colorArgb", stroke.colorArgb)
                            put("strokeWidth", stroke.strokeWidth.toDouble())
                            put("isEraser", stroke.isEraser)
                            put("points", JSONArray().apply {
                                stroke.points.forEach { pt ->
                                    put(JSONObject().apply {
                                        put("x", pt.x.toDouble())
                                        put("y", pt.y.toDouble())
                                    })
                                }
                            })
                        })
                    }
                })
            }
            val file = File(sketchesDir, "${doc.id}.json")
            writeJsonAtomically(file, json.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadSketch(id: String): SketchDocument? {
        val file = File(sketchesDir, "$id.json")
        if (!file.exists()) return null
        return parseLegacySketchFile(file)
    }

    fun deleteSketch(id: String): Boolean {
        return try {
            val file = File(sketchesDir, "$id.json")
            !file.exists() || file.delete()
        } catch (_: Exception) {
            false
        }
    }

    private fun writeJsonAtomically(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (target.exists()) {
            val bak = File(target.parentFile, "${target.name}.bak")
            runCatching { target.copyTo(bak, overwrite = true) }
        }
        if (!tmp.renameTo(target)) {
            target.writeText(content)
            tmp.delete()
        }
    }

    private fun serializeStudioProject(p: StudioProject): String {
        val json = JSONObject().apply {
            put("id", p.id)
            put("title", p.title)
            put("createdAtMillis", p.createdAtMillis)
            put("updatedAtMillis", p.updatedAtMillis)
            put("canvasWidth", p.canvasWidth)
            put("canvasHeight", p.canvasHeight)
            put("backgroundColorArgb", p.backgroundColorArgb)
            put("activeLayerId", p.activeLayerId)
            put("crop", JSONObject().apply {
                put("l", p.cropRect.leftFrac.toDouble())
                put("t", p.cropRect.topFrac.toDouble())
                put("r", p.cropRect.rightFrac.toDouble())
                put("b", p.cropRect.bottomFrac.toDouble())
            })
            put("layers", JSONArray().apply {
                p.layers.forEach { layer ->
                    put(JSONObject().apply {
                        put("id", layer.id)
                        put("name", layer.name)
                        put("visible", layer.visible)
                        put("opacity", layer.opacity.toDouble())
                        if (layer.fillColorArgb != null) put("fillColorArgb", layer.fillColorArgb)
                        put("offsetX", layer.offsetX.toDouble())
                        put("offsetY", layer.offsetY.toDouble())
                        put("rotationDeg", layer.rotationDeg.toDouble())
                        put("flipH", layer.flipH)
                        put("flipV", layer.flipV)
                        put("strokes", JSONArray().apply {
                            layer.strokes.forEach { st ->
                                put(JSONObject().apply {
                                    put("id", st.id)
                                    put("brushType", st.brushType.name)
                                    put("colorArgb", st.colorArgb)
                                    put("size", st.size.toDouble())
                                    put("opacity", st.opacity.toDouble())
                                    put("hardness", st.hardness.toDouble())
                                    put("points", JSONArray().apply {
                                        st.points.forEach { pt ->
                                            put(JSONObject().apply {
                                                put("x", pt.x.toDouble())
                                                put("y", pt.y.toDouble())
                                                put("p", pt.pressure.toDouble())
                                            })
                                        }
                                    })
                                })
                            }
                        })
                    })
                }
            })
        }
        return json.toString()
    }

    private fun parseStudioProjectFile(file: File): StudioProject? {
        return parseStudioProjectJson(runCatching { file.readText() }.getOrNull() ?: return null, file.nameWithoutExtension)
            ?: run {
                val bak = File(file.parentFile, "${file.name}.bak")
                if (bak.exists()) parseStudioProjectJson(runCatching { bak.readText() }.getOrNull() ?: return null, file.nameWithoutExtension)
                else null
            }
    }

    private fun parseStudioProjectJson(raw: String, fallbackId: String): StudioProject? {
        return try {
            val json = JSONObject(raw)
            val layersArr = json.optJSONArray("layers") ?: JSONArray()
            val layers = mutableListOf<DrawingLayer>()
            for (i in 0 until layersArr.length()) {
                val lObj = layersArr.getJSONObject(i)
                val strokesArr = lObj.optJSONArray("strokes") ?: JSONArray()
                val strokes = mutableListOf<StudioStroke>()
                for (j in 0 until strokesArr.length()) {
                    val sObj = strokesArr.getJSONObject(j)
                    val ptsArr = sObj.optJSONArray("points") ?: JSONArray()
                    val pts = mutableListOf<StudioPoint>()
                    for (k in 0 until ptsArr.length()) {
                        val pObj = ptsArr.getJSONObject(k)
                        pts.add(
                            StudioPoint(
                                x = pObj.optDouble("x", 0.0).toFloat(),
                                y = pObj.optDouble("y", 0.0).toFloat(),
                                pressure = pObj.optDouble("p", 1.0).toFloat().coerceIn(0.15f, 1.5f)
                            )
                        )
                    }
                    strokes.add(
                        StudioStroke(
                            id = sObj.optString("id", "st_$j"),
                            points = pts,
                            brushType = runCatching {
                                BrushType.valueOf(sObj.optString("brushType", BrushType.PEN.name))
                            }.getOrDefault(BrushType.PEN),
                            colorArgb = sObj.optLong("colorArgb", 0xFF64F0FFL),
                            size = sObj.optDouble("size", 8.0).toFloat(),
                            opacity = sObj.optDouble("opacity", 1.0).toFloat().coerceIn(0.05f, 1f),
                            hardness = sObj.optDouble("hardness", 0.85).toFloat().coerceIn(0.1f, 1f)
                        )
                    )
                }
                layers.add(
                    DrawingLayer(
                        id = lObj.optString("id", "layer_$i"),
                        name = lObj.optString("name", "Layer ${i + 1}"),
                        visible = lObj.optBoolean("visible", true),
                        opacity = lObj.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f),
                        fillColorArgb = if (lObj.has("fillColorArgb")) lObj.optLong("fillColorArgb") else null,
                        strokes = strokes,
                        offsetX = lObj.optDouble("offsetX", 0.0).toFloat(),
                        offsetY = lObj.optDouble("offsetY", 0.0).toFloat(),
                        rotationDeg = lObj.optDouble("rotationDeg", 0.0).toFloat(),
                        flipH = lObj.optBoolean("flipH", false),
                        flipV = lObj.optBoolean("flipV", false)
                    )
                )
            }
            val safeLayers = if (layers.isEmpty()) listOf(DrawingLayer(id = "layer_1", name = "Layer 1")) else layers
            val cropObj = json.optJSONObject("crop")
            val crop = if (cropObj != null) {
                StudioCropRect(
                    leftFrac = cropObj.optDouble("l", 0.0).toFloat().coerceIn(0f, 0.8f),
                    topFrac = cropObj.optDouble("t", 0.0).toFloat().coerceIn(0f, 0.8f),
                    rightFrac = cropObj.optDouble("r", 1.0).toFloat().coerceIn(0.2f, 1f),
                    bottomFrac = cropObj.optDouble("b", 1.0).toFloat().coerceIn(0.2f, 1f)
                )
            } else StudioCropRect()

            StudioProject(
                id = json.optString("id", fallbackId),
                title = json.optString("title", "Untitled"),
                createdAtMillis = json.optLong("createdAtMillis", System.currentTimeMillis()),
                updatedAtMillis = json.optLong("updatedAtMillis", System.currentTimeMillis()),
                canvasWidth = json.optInt("canvasWidth", 1080),
                canvasHeight = json.optInt("canvasHeight", 1080),
                backgroundColorArgb = json.optLong("backgroundColorArgb", 0xFF0D1322L),
                layers = safeLayers,
                activeLayerId = json.optString("activeLayerId", safeLayers.last().id),
                cropRect = crop
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLegacySketchFile(file: File): SketchDocument? {
        return try {
            val json = JSONObject(file.readText())
            val strokesArr = json.optJSONArray("strokes") ?: JSONArray()
            val strokes = mutableListOf<SketchStroke>()
            for (i in 0 until strokesArr.length()) {
                val sObj = strokesArr.getJSONObject(i)
                val ptsArr = sObj.optJSONArray("points") ?: JSONArray()
                val points = mutableListOf<SketchPoint>()
                for (j in 0 until ptsArr.length()) {
                    val pObj = ptsArr.getJSONObject(j)
                    points.add(
                        SketchPoint(
                            x = pObj.optDouble("x", 0.0).toFloat(),
                            y = pObj.optDouble("y", 0.0).toFloat()
                        )
                    )
                }
                strokes.add(
                    SketchStroke(
                        points = points,
                        colorArgb = sObj.optLong("colorArgb", 0xFF64F0FFL),
                        strokeWidth = sObj.optDouble("strokeWidth", 6.0).toFloat(),
                        isEraser = sObj.optBoolean("isEraser", false)
                    )
                )
            }
            SketchDocument(
                id = json.optString("id", file.nameWithoutExtension),
                title = json.optString("title", "Untitled"),
                updatedAtMillis = json.optLong("updatedAtMillis", file.lastModified()),
                canvasWidth = json.optInt("canvasWidth", 1000),
                canvasHeight = json.optInt("canvasHeight", 700),
                strokes = strokes
            )
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "lain_beyond_reality_save"
        private const val KEY_PROGRESS_V1 = "progress_v1"
        private const val KEY_PROGRESS_V2 = "progress_v2"
        private const val KEY_PROGRESS_BACKUP = "progress_backup"
        private const val KEY_SETTINGS = "settings_v1"
    }
}
