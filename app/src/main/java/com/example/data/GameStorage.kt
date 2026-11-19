package com.example.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class GameStorage(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val sketchesDir: File
        get() = File(context.filesDir, "sketches").apply { if (!exists()) mkdirs() }

    fun loadProgress(): SaveProgress {
        val raw = prefs.getString(KEY_PROGRESS, null) ?: return SaveProgress()
        return try {
            val json = JSONObject(raw)
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

            val shardsMap = mutableMapOf<String, Set<String>>()
            val shardsObj = json.optJSONObject("collectedShardsByLevel")
            if (shardsObj != null) {
                val keys = shardsObj.keys()
                while (keys.hasNext()) {
                    val levelKey = keys.next()
                    val arr = shardsObj.optJSONArray(levelKey) ?: continue
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) {
                        set.add(arr.getString(i))
                    }
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

            SaveProgress(
                hasStarted = json.optBoolean("hasStarted", false),
                currentLevelId = json.optString("currentLevelId", "1-1"),
                unlockedWorlds = unlockedWorlds,
                completedLevels = completedLevels,
                collectedShardsByLevel = shardsMap,
                completedJudoTrials = judoSet,
                bestJudoScores = judoScores,
                unlockedAchievements = achSet,
                totalSketchesSaved = json.optInt("totalSketchesSaved", 0)
            )
        } catch (_: Exception) {
            SaveProgress()
        }
    }

    fun saveProgress(progress: SaveProgress) {
        try {
            val json = JSONObject().apply {
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
            }
            prefs.edit().putString(KEY_PROGRESS, json.toString()).commit()
        } catch (_: Exception) {
            // Gracefully absorb serialization failures
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
                reduceShake = json.optBoolean("reduceShake", false)
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
                put("reduceShake", settings.reduceShake)
            }
            prefs.edit().putString(KEY_SETTINGS, json.toString()).commit()
        } catch (_: Exception) {
            // Gracefully absorb
        }
    }

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
            file.writeText(json.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadAllSketches(): List<SketchDocument> {
        return try {
            val files = sketchesDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
                ?: return emptyList()
            files.mapNotNull { file -> parseSketchFile(file) }
                .sortedByDescending { it.updatedAtMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadSketch(id: String): SketchDocument? {
        val file = File(sketchesDir, "$id.json")
        if (!file.exists()) return null
        return parseSketchFile(file)
    }

    fun deleteSketch(id: String): Boolean {
        return try {
            val file = File(sketchesDir, "$id.json")
            !file.exists() || file.delete()
        } catch (_: Exception) {
            false
        }
    }

    private fun parseSketchFile(file: File): SketchDocument? {
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
        private const val KEY_PROGRESS = "progress_v1"
        private const val KEY_SETTINGS = "settings_v1"
    }
}
