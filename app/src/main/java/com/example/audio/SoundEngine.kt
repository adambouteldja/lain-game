package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.data.GameSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    INK_DRAW,
    UI_TAP
}

/**
 * Lightweight procedural audio engine synthesizing original quiet ambient Wired music
 * and subtle SFX via Android AudioTrack with zero external audio files.
 */
class SoundEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ambientJob: Job? = null

    @Volatile
    private var musicVolume: Float = 0.55f

    @Volatile
    private var sfxVolume: Float = 0.75f

    @Volatile
    private var isMuted: Boolean = false

    @Volatile
    private var isAppForeground: Boolean = true

    fun updateSettings(settings: GameSettings) {
        musicVolume = settings.musicVolume.coerceIn(0f, 1f)
        sfxVolume = settings.sfxVolume.coerceIn(0f, 1f)
        isMuted = settings.isMuted
    }

    fun onResume() {
        isAppForeground = true
        startAmbientLoop()
    }

    fun onPause() {
        isAppForeground = false
        stopAmbientLoop()
    }

    fun release() {
        stopAmbientLoop()
        scope.cancel()
    }

    fun startAmbientLoop() {
        if (ambientJob?.isActive == true) return
        ambientJob = scope.launch {
            val sampleRate = 22050
            val chunkSamples = 2205 // 100ms chunks
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

                // Quiet pentatonic ambient frequencies (D minor / F major9 dreamy bedroom hum)
                val chimeNotes = doubleArrayOf(293.66, 349.23, 440.0, 523.25, 659.25, 587.33)
                var sampleIndex = 0L

                while (isActive && isAppForeground) {
                    val vol = if (isMuted) 0f else musicVolume * 0.16f
                    for (i in 0 until chunkSamples) {
                        val t = (sampleIndex + i).toDouble() / sampleRate
                        if (vol <= 0.001f) {
                            buffer[i] = 0
                        } else {
                            // Soft CRT warm room hum + slow evolving pad chords
                            val slowBreath = 0.65 + 0.35 * sin(2.0 * PI * 0.09 * t)
                            val drone1 = sin(2.0 * PI * 110.0 * t) * 0.32
                            val drone2 = sin(2.0 * PI * 164.81 * t) * 0.24 * slowBreath
                            val drone3 = sin(2.0 * PI * 220.0 * t) * 0.16 * (0.5 + 0.5 * sin(2.0 * PI * 0.05 * t))

                            // Gentle crystalline chime every 3.2 seconds
                            val cyclePos = t % 3.2
                            val noteIdx = ((t / 3.2).toInt() * 7 + 3) % chimeNotes.size
                            val chimeFreq = chimeNotes[noteIdx]
                            val chimeEnv = exp(-cyclePos * 2.2) * (if (cyclePos < 0.03) cyclePos / 0.03 else 1.0)
                            val chime = sin(2.0 * PI * chimeFreq * t) * 0.26 * chimeEnv

                            val sample = ((drone1 + drone2 + drone3 + chime) * vol * Short.MAX_VALUE)
                                .toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            buffer[i] = sample.toShort()
                        }
                    }
                    sampleIndex += chunkSamples
                    track.write(buffer, 0, chunkSamples)
                }
            } catch (_: Throwable) {
                // Gracefully no-op on headless/JVM test environments without audio hardware
            } finally {
                runCatching {
                    track?.stop()
                    track?.release()
                }
            }
        }
    }

    private fun stopAmbientLoop() {
        ambientJob?.cancel()
        ambientJob = null
    }

    fun playSfx(effect: SoundEffect) {
        if (isMuted || sfxVolume <= 0.01f) return
        val gain = sfxVolume * 0.35f
        scope.launch {
            val sampleRate = 22050
            val durationSec = when (effect) {
                SoundEffect.JUMP -> 0.13
                SoundEffect.LAND -> 0.07
                SoundEffect.SHARD -> 0.24
                SoundEffect.SWITCH -> 0.12
                SoundEffect.CHECKPOINT -> 0.28
                SoundEffect.PORTAL -> 0.42
                SoundEffect.ERROR -> 0.20
                SoundEffect.JUDO_REDIRECT -> 0.22
                SoundEffect.INK_DRAW -> 0.09
                SoundEffect.UI_TAP -> 0.06
            }
            val totalSamples = (sampleRate * durationSec).toInt()
            val pcm = ShortArray(totalSamples)
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val progress = i.toDouble() / totalSamples
                val env = (1.0 - progress) * (if (progress < 0.05) progress / 0.05 else 1.0)
                val raw = when (effect) {
                    SoundEffect.JUMP -> {
                        val freq = 260.0 + 340.0 * progress
                        sin(2.0 * PI * freq * t)
                    }
                    SoundEffect.LAND -> {
                        val freq = 130.0 - 60.0 * progress
                        sin(2.0 * PI * freq * t) * exp(-progress * 5.0)
                    }
                    SoundEffect.SHARD -> {
                        val f1 = 659.25
                        val f2 = 987.77
                        (sin(2.0 * PI * f1 * t) * 0.55 + sin(2.0 * PI * f2 * t) * 0.45) * exp(-progress * 3.0)
                    }
                    SoundEffect.SWITCH -> {
                        val freq = if (progress < 0.45) 480.0 else 720.0
                        sin(2.0 * PI * freq * t)
                    }
                    SoundEffect.CHECKPOINT -> {
                        val f = when {
                            progress < 0.33 -> 440.0
                            progress < 0.66 -> 587.33
                            else -> 880.0
                        }
                        sin(2.0 * PI * f * t)
                    }
                    SoundEffect.PORTAL -> {
                        val f = when {
                            progress < 0.25 -> 392.0
                            progress < 0.50 -> 523.25
                            progress < 0.75 -> 659.25
                            else -> 783.99
                        }
                        (sin(2.0 * PI * f * t) + 0.35 * sin(2.0 * PI * f * 2.0 * t)) * 0.7
                    }
                    SoundEffect.ERROR -> {
                        val freq = 180.0 - 70.0 * progress
                        sin(2.0 * PI * freq * t) * 0.8
                    }
                    SoundEffect.JUDO_REDIRECT -> {
                        val f = 329.63 + 220.0 * (1.0 - exp(-progress * 6.0))
                        (sin(2.0 * PI * f * t) * 0.7 + sin(2.0 * PI * f * 1.5 * t) * 0.3)
                    }
                    SoundEffect.INK_DRAW -> {
                        val f = 520.0 + 140.0 * progress
                        sin(2.0 * PI * f * t) * 0.6
                    }
                    SoundEffect.UI_TAP -> {
                        sin(2.0 * PI * 620.0 * t) * 0.5
                    }
                }
                pcm[i] = (raw * env * gain * Short.MAX_VALUE)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }

            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                kotlinx.coroutines.delay((durationSec * 1000).toLong() + 40L)
            } catch (_: Throwable) {
                // Safe fallback in unit tests or unsupported environments
            } finally {
                runCatching {
                    track?.stop()
                    track?.release()
                }
            }
        }
    }
}
