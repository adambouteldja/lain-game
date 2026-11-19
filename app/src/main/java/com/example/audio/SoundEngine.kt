package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.data.GameSettings
import com.example.data.WorldId
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class SoundEffect {
    JUMP,
    LAND,
    SHARD,
    SWITCH,
    CHECKPOINT,
    PORTAL,
    ERROR,
    JUDO_REDIRECT,
    JUDO_UKEMI,
    INK_DRAW,
    UI_TAP,
    DESKTOP_BOOT
}

enum class AmbienceMood {
    ROOM,
    SCHOOL,
    STREETS,
    CYBERIA,
    WIRED,
    DOJO
}

private class ActiveSfxVoice(
    val samples: FloatArray,
    var cursor: Int = 0
)

/**
 * Evolution 2.0 Single-Stream Mixed Procedural Audio Engine.
 * Eliminates per-SFX AudioTrack allocation churn by mixing short procedural SFX voices
 * directly into the continuous low-latency ambient stream, with 6 distinct location soundscapes.
 */
class SoundEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var streamJob: Job? = null
    private val activeVoices = CopyOnWriteArrayList<ActiveSfxVoice>()

    @Volatile private var musicVolume: Float = 0.55f
    @Volatile private var sfxVolume: Float = 0.75f
    @Volatile private var isMuted: Boolean = false
    @Volatile private var isAppForeground: Boolean = true
    @Volatile private var mood: AmbienceMood = AmbienceMood.ROOM

    private val sampleRate = 22050

    fun updateSettings(settings: GameSettings) {
        musicVolume = settings.musicVolume.coerceIn(0f, 1f)
        sfxVolume = settings.sfxVolume.coerceIn(0f, 1f)
        isMuted = settings.isMuted
    }

    fun setAmbienceForWorld(worldId: WorldId) {
        mood = when (worldId) {
            WorldId.LAINS_ROOM, WorldId.FAMILY_HOME -> AmbienceMood.ROOM
            WorldId.SCHOOL -> AmbienceMood.SCHOOL
            WorldId.STREETS -> AmbienceMood.STREETS
            WorldId.CYBERIA -> AmbienceMood.CYBERIA
            WorldId.BALANCE -> AmbienceMood.DOJO
            else -> AmbienceMood.WIRED
        }
    }

    fun setAmbienceMood(newMood: AmbienceMood) {
        mood = newMood
    }

    fun onResume() {
        isAppForeground = true
        startAudioMixer()
    }

    fun onPause() {
        isAppForeground = false
        stopAudioMixer()
    }

    fun release() {
        stopAudioMixer()
        activeVoices.clear()
        scope.cancel()
    }

    private fun startAudioMixer() {
        if (streamJob?.isActive == true) return
        streamJob = scope.launch {
            val chunkSamples = 1102 // ~50ms chunks for responsive SFX mixing
            val buffer = ShortArray(chunkSamples)
            var track: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf <= 0) return@launch
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuf, chunkSamples * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()

                var sampleIndex = 0L
                val roomChimes = doubleArrayOf(293.66, 349.23, 440.0, 523.25, 659.25)
                val schoolChimes = doubleArrayOf(392.0, 440.0, 523.25, 587.33, 783.99)
                val dojoChimes = doubleArrayOf(261.63, 293.66, 349.23, 392.0, 523.25)

                while (isActive && isAppForeground) {
                    val mVol = if (isMuted) 0f else musicVolume * 0.15f
                    val sVol = if (isMuted) 0f else sfxVolume * 0.42f
                    val curMood = mood

                    for (i in 0 until chunkSamples) {
                        val t = (sampleIndex + i).toDouble() / sampleRate
                        var mix = 0.0

                        if (mVol > 0.001f) {
                            mix += when (curMood) {
                                AmbienceMood.ROOM -> {
                                    // Quiet CRT 60Hz-harmonic hum + sleepy pentatonic chimes
                                    val hum = sin(2.0 * PI * 110.0 * t) * 0.30 + sin(2.0 * PI * 164.81 * t) * 0.22
                                    val cycle = t % 3.4
                                    val note = roomChimes[((t / 3.4).toInt() * 3) % roomChimes.size]
                                    val chime = sin(2.0 * PI * note * t) * 0.28 * exp(-cycle * 2.1)
                                    (hum + chime) * mVol
                                }
                                AmbienceMood.SCHOOL -> {
                                    // Airy daylight bell pad
                                    val pad = sin(2.0 * PI * 261.63 * t) * 0.20 + sin(2.0 * PI * 329.63 * t) * 0.18
                                    val cycle = t % 4.0
                                    val note = schoolChimes[((t / 4.0).toInt() * 2) % schoolChimes.size]
                                    val bell = sin(2.0 * PI * note * t) * 0.24 * exp(-cycle * 1.8)
                                    (pad + bell) * mVol
                                }
                                AmbienceMood.STREETS -> {
                                    // Subtle electric powerline 120Hz buzz + distant wind swell
                                    val swell = 0.55 + 0.45 * sin(2.0 * PI * 0.12 * t)
                                    val wireHum = (sin(2.0 * PI * 120.0 * t) * 0.34 + sin(2.0 * PI * 240.0 * t) * 0.14) * swell
                                    val sub = sin(2.0 * PI * 82.41 * t) * 0.25
                                    (wireHum + sub) * mVol
                                }
                                AmbienceMood.CYBERIA -> {
                                    // Deep pulsing minimal club sub-bass (subtle ambient)
                                    val beatPos = t % 0.5
                                    val kick = sin(2.0 * PI * (68.0 - 26.0 * beatPos) * beatPos) * exp(-beatPos * 10.0) * 0.48
                                    val synth = sin(2.0 * PI * 146.83 * t) * 0.18 + sin(2.0 * PI * 220.0 * t) * 0.14
                                    (kick + synth) * mVol
                                }
                                AmbienceMood.DOJO -> {
                                    // Calm wooden temple drone + zen harmonic chime
                                    val drone = sin(2.0 * PI * 130.81 * t) * 0.26 + sin(2.0 * PI * 196.0 * t) * 0.20
                                    val cycle = t % 4.2
                                    val note = dojoChimes[((t / 4.2).toInt()) % dojoChimes.size]
                                    val gong = sin(2.0 * PI * note * t) * 0.28 * exp(-cycle * 1.6)
                                    (drone + gong) * mVol
                                }
                                AmbienceMood.WIRED -> {
                                    // Ethereal detuned digital shimmer
                                    val d1 = sin(2.0 * PI * 110.0 * t) * 0.26
                                    val d2 = sin(2.0 * PI * 110.7 * t) * 0.20
                                    val shimmer = sin(2.0 * PI * (440.0 + 18.0 * sin(2.0 * PI * 0.35 * t)) * t) * 0.16
                                    (d1 + d2 + shimmer) * mVol
                                }
                            }
                        }

                        // Mix active SFX voices cleanly
                        if (activeVoices.isNotEmpty()) {
                            for (voice in activeVoices) {
                                val idx = voice.cursor
                                if (idx < voice.samples.size) {
                                    mix += voice.samples[idx] * sVol
                                    voice.cursor = idx + 1
                                }
                            }
                        }

                        buffer[i] = (mix.coerceIn(-0.98, 0.98) * Short.MAX_VALUE).toInt().toShort()
                    }
                    // Prune finished voices
                    activeVoices.removeIf { it.cursor >= it.samples.size }
                    sampleIndex += chunkSamples
                    track.write(buffer, 0, chunkSamples)
                }
            } catch (_: Throwable) {
                // Safe fallback in headless JVM tests
            } finally {
                runCatching {
                    track?.stop()
                    track?.release()
                }
            }
        }
    }

    private fun stopAudioMixer() {
        streamJob?.cancel()
        streamJob = null
    }

    fun playSfx(effect: SoundEffect) {
        if (isMuted || sfxVolume <= 0.01f) return
        if (activeVoices.size > 8) activeVoices.removeAt(0)
        val durationSec = when (effect) {
            SoundEffect.JUMP -> 0.12
            SoundEffect.LAND -> 0.06
            SoundEffect.SHARD -> 0.22
            SoundEffect.SWITCH -> 0.11
            SoundEffect.CHECKPOINT -> 0.26
            SoundEffect.PORTAL -> 0.38
            SoundEffect.ERROR -> 0.18
            SoundEffect.JUDO_REDIRECT -> 0.20
            SoundEffect.JUDO_UKEMI -> 0.18
            SoundEffect.INK_DRAW -> 0.08
            SoundEffect.UI_TAP -> 0.05
            SoundEffect.DESKTOP_BOOT -> 0.34
        }
        val total = (sampleRate * durationSec).toInt()
        val samples = FloatArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sampleRate
            val p = i.toDouble() / total
            val env = (1.0 - p) * (if (p < 0.05) p / 0.05 else 1.0)
            val raw = when (effect) {
                SoundEffect.JUMP -> sin(2.0 * PI * (270.0 + 340.0 * p) * t)
                SoundEffect.LAND -> sin(2.0 * PI * (125.0 - 55.0 * p) * t) * exp(-p * 5.0)
                SoundEffect.SHARD -> (sin(2.0 * PI * 659.25 * t) * 0.55 + sin(2.0 * PI * 987.77 * t) * 0.45) * exp(-p * 2.8)
                SoundEffect.SWITCH -> sin(2.0 * PI * (if (p < 0.45) 480.0 else 720.0) * t)
                SoundEffect.CHECKPOINT -> {
                    val f = if (p < 0.33) 440.0 else if (p < 0.66) 587.33 else 880.0
                    sin(2.0 * PI * f * t)
                }
                SoundEffect.PORTAL -> {
                    val f = when {
                        p < 0.25 -> 392.0
                        p < 0.50 -> 523.25
                        p < 0.75 -> 659.25
                        else -> 783.99
                    }
                    sin(2.0 * PI * f * t) * 0.75
                }
                SoundEffect.ERROR -> sin(2.0 * PI * (175.0 - 65.0 * p) * t) * 0.8
                SoundEffect.JUDO_REDIRECT -> sin(2.0 * PI * (330.0 + 240.0 * (1.0 - exp(-p * 5.0))) * t)
                SoundEffect.JUDO_UKEMI -> (sin(2.0 * PI * (210.0 + 180.0 * p) * t) * 0.7 + sin(2.0 * PI * 520.0 * t) * 0.3)
                SoundEffect.INK_DRAW -> sin(2.0 * PI * (520.0 + 140.0 * p) * t) * 0.55
                SoundEffect.UI_TAP -> sin(2.0 * PI * 640.0 * t) * 0.45
                SoundEffect.DESKTOP_BOOT -> {
                    val f = if (p < 0.35) 329.63 else if (p < 0.7) 440.0 else 659.25
                    (sin(2.0 * PI * f * t) + 0.3 * sin(2.0 * PI * f * 2.0 * t)) * 0.65
                }
            }
            samples[i] = (raw * env).toFloat()
        }
        activeVoices.add(ActiveSfxVoice(samples))
    }
}
