package com.example.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Generates 5 distinct PS2-inspired harmonic ambient startup progressions
 * mapped 1:1 to the 5 Kodanima cold-start splash artworks (variants 1..5):
 *   1 = Alegre (bright uplifting major)
 *   2 = Triste (melancholic minor)
 *   3 = Jazz (7th/9th/13th lounge changes & walking bass)
 *   4 = Hip-Hop (deep boom-bap sub-bass & minor Rhodes stabs)
 *   5 = Pop Bem Harmônica (stacked lush add9/maj7 anthem)
 *
 * Precomputes a 4096-entry sine LUT, sets AUDIO thread priority, pre-fills 2 chunks
 * before starting playback to prevent buffer underrun stuttering on low-end CPUs,
 * and caps master output at 50% volume cleanly.
 */
object SplashAudioEngine {

    private const val SAMPLE_RATE = 44100
    private const val DURATION_SEC = 4.5
    private const val CHUNK_SAMPLES = 4096
    private const val MASTER_VOLUME_SCALE = 0.48 // ~50% peak ceiling (<= 16383)
    private const val TWO_PI = 2.0 * PI

    private const val LUT_SIZE = 4096
    private const val LUT_MASK = LUT_SIZE - 1
    private val SIN_LUT = DoubleArray(LUT_SIZE) { idx ->
        sin(TWO_PI * idx / LUT_SIZE)
    }

    private fun fastSinCycles(cycles: Double): Double {
        val norm = cycles - kotlin.math.floor(cycles)
        val pos = norm * LUT_SIZE
        val i0 = pos.toInt() and LUT_MASK
        val i1 = (i0 + 1) and LUT_MASK
        val frac = pos - i0
        val s0 = SIN_LUT[i0]
        return s0 + (SIN_LUT[i1] - s0) * frac
    }

    private val sessionCounter = AtomicLong(0L)

    @Volatile
    private var activeTrack: AudioTrack? = null

    fun playStartupTrack(variant: Int, context: Context? = null) {
        val mySession = sessionCounter.incrementAndGet()
        releaseActiveTrack()

        val safeVariant = ((variant - 1).coerceIn(0, 4)) + 1

        val t = Thread({
            var track: AudioTrack? = null
            var audioManager: AudioManager? = null
            var focusRequest: AudioFocusRequest? = null

            try {
                runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }

                if (context != null) {
                    runCatching {
                        val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        audioManager = am
                        if (am != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                                    .setAudioAttributes(
                                        AudioAttributes.Builder()
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                            .build()
                                    )
                                    .build()
                                focusRequest = req
                                am.requestAudioFocus(req)
                            } else {
                                @Suppress("DEPRECATION")
                                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            }
                        }
                    }
                }

                if (sessionCounter.get() != mySession) return@Thread

                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(8192)

                val bufferSizeBytes = (minBuf * 4).coerceAtLeast(65536)

                val created = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferSizeBytes)
                    .build()

                if (sessionCounter.get() != mySession || created.state != AudioTrack.STATE_INITIALIZED) {
                    runCatching { created.release() }
                    return@Thread
                }

                track = created
                activeTrack = created
                runCatching { created.setVolume(1.0f) }

                val totalSamples = (SAMPLE_RATE * DURATION_SEC).toInt()
                val arrangement = arrangementForVariant(safeVariant)
                val fadeInSamples = (SAMPLE_RATE * 0.08).toInt().coerceAtLeast(1)
                val fadeOutStart = (totalSamples * 0.76).toInt()
                val chunk = ShortArray(CHUNK_SAMPLES)

                var offset = 0
                var prefilledChunks = 0
                var playbackStarted = false

                while (offset < totalSamples && sessionCounter.get() == mySession) {
                    val count = minOf(CHUNK_SAMPLES, totalSamples - offset)
                    fillChunkSamples(
                        out = chunk,
                        count = count,
                        startSampleIndex = offset,
                        totalSamples = totalSamples,
                        fadeInSamples = fadeInSamples,
                        fadeOutStart = fadeOutStart,
                        arrangement = arrangement
                    )
                    if (sessionCounter.get() != mySession) break
                    val written = created.write(chunk, 0, count, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) break
                    offset += count
                    prefilledChunks++
                    // Pre-fill 2 chunks before play() so initial playback starts jitter-free on budget phones
                    if (!playbackStarted && (prefilledChunks >= 2 || offset >= totalSamples)) {
                        created.play()
                        playbackStarted = true
                    }
                }
            } catch (_: Throwable) {
                // Fails safely on robolectric or headless audio environments
            } finally {
                if (track != null) {
                    if (activeTrack === track) {
                        activeTrack = null
                    }
                    runCatching {
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            track.pause()
                            track.flush()
                            track.stop()
                        }
                        track.release()
                    }
                }
                if (audioManager != null) {
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                            audioManager?.abandonAudioFocusRequest(focusRequest!!)
                        } else {
                            @Suppress("DEPRECATION")
                            audioManager?.abandonAudioFocus(null)
                        }
                    }
                }
            }
        }, "KodanimaSplashAudio-$mySession")
        t.isDaemon = true
        t.start()
    }

    fun stop() {
        sessionCounter.incrementAndGet()
        releaseActiveTrack()
    }

    private fun releaseActiveTrack() {
        val tr = activeTrack
        activeTrack = null
        if (tr != null) {
            runCatching {
                if (tr.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    tr.pause()
                    tr.flush()
                    tr.stop()
                }
                tr.release()
            }
        }
    }

    private enum class StyleMode {
        ALEGRE,
        TRISTE,
        JAZZ,
        HIP_HOP,
        POP_HARMONICO
    }

    private data class ChordStep(
        val startTimeSec: Double,
        val freqs: DoubleArray,
        val modRatio: Double,
        val staggerSec: Double,
        val decayRate: Double
    )

    private data class SparkleNote(
        val timeSec: Double,
        val freq: Double,
        val amp: Double,
        val decay: Double
    )

    private data class GenreArrangement(
        val style: StyleMode,
        val steps: List<ChordStep>,
        val sparkles: List<SparkleNote>,
        val bassPulseTimes: DoubleArray,
        val bassPulseFreqs: DoubleArray
    )

    private fun arrangementForVariant(variant: Int): GenreArrangement {
        return when (variant) {
            1 -> GenreArrangement( // 1: ALEGRE - Bright sparkling D major sequence (Dmaj7 -> A/C# -> Bm7 -> Gmaj9)
                style = StyleMode.ALEGRE,
                steps = listOf(
                    ChordStep(0.00, doubleArrayOf(293.66, 369.99, 440.00, 554.37, 659.25), modRatio = 2.0, staggerSec = 0.06, decayRate = 1.15),
                    ChordStep(1.05, doubleArrayOf(277.18, 329.63, 440.00, 554.37, 659.25), modRatio = 2.0, staggerSec = 0.05, decayRate = 1.25),
                    ChordStep(2.05, doubleArrayOf(246.94, 369.99, 440.00, 587.33, 739.99), modRatio = 2.0, staggerSec = 0.05, decayRate = 1.10),
                    ChordStep(3.05, doubleArrayOf(196.00, 293.66, 369.99, 440.00, 587.33), modRatio = 3.0, staggerSec = 0.06, decayRate = 0.65)
                ),
                sparkles = listOf(
                    SparkleNote(0.22, 880.00, 0.11, 2.4),
                    SparkleNote(0.55, 1174.66, 0.12, 2.5),
                    SparkleNote(1.30, 1318.51, 0.12, 2.4),
                    SparkleNote(2.35, 1479.98, 0.12, 2.2),
                    SparkleNote(3.35, 1760.00, 0.13, 1.5)
                ),
                bassPulseTimes = doubleArrayOf(0.0, 1.05, 2.05, 3.05),
                bassPulseFreqs = doubleArrayOf(146.83, 138.59, 123.47, 98.00)
            )
            2 -> GenreArrangement( // 2: TRISTE - Melancholic slow Am9 -> Fmaj7 -> Dm9 -> E7sus4
                style = StyleMode.TRISTE,
                steps = listOf(
                    ChordStep(0.00, doubleArrayOf(110.00, 164.81, 261.63, 329.63, 493.88), modRatio = 1.0, staggerSec = 0.12, decayRate = 0.55),
                    ChordStep(1.35, doubleArrayOf(87.31, 174.61, 220.00, 261.63, 329.63), modRatio = 1.0, staggerSec = 0.11, decayRate = 0.60),
                    ChordStep(2.55, doubleArrayOf(146.83, 220.00, 261.63, 329.63, 440.00), modRatio = 1.5, staggerSec = 0.10, decayRate = 0.65),
                    ChordStep(3.45, doubleArrayOf(82.41, 164.81, 246.94, 293.66, 329.63), modRatio = 1.0, staggerSec = 0.10, decayRate = 0.50)
                ),
                sparkles = listOf(
                    SparkleNote(0.70, 659.25, 0.10, 1.6),
                    SparkleNote(1.85, 587.33, 0.11, 1.5),
                    SparkleNote(2.90, 523.25, 0.11, 1.4),
                    SparkleNote(3.65, 493.88, 0.10, 1.1)
                ),
                bassPulseTimes = doubleArrayOf(0.0, 1.35, 2.55, 3.45),
                bassPulseFreqs = doubleArrayOf(110.00, 87.31, 73.42, 82.41)
            )
            3 -> GenreArrangement( // 3: JAZZ - Smooth ii-V-I-vi7th Dm9 -> G13 -> Cmaj9 -> A7#9 with walking bass
                style = StyleMode.JAZZ,
                steps = listOf(
                    ChordStep(0.00, doubleArrayOf(146.83, 261.63, 329.63, 349.23, 440.00), modRatio = 2.0, staggerSec = 0.03, decayRate = 1.30),
                    ChordStep(1.10, doubleArrayOf(196.00, 246.94, 329.63, 349.23, 440.00), modRatio = 2.5, staggerSec = 0.03, decayRate = 1.30),
                    ChordStep(2.15, doubleArrayOf(130.81, 246.94, 293.66, 329.63, 392.00), modRatio = 2.0, staggerSec = 0.04, decayRate = 0.90),
                    ChordStep(3.25, doubleArrayOf(110.00, 207.65, 261.63, 311.13, 392.00), modRatio = 3.0, staggerSec = 0.03, decayRate = 0.85)
                ),
                sparkles = listOf(
                    SparkleNote(0.42, 659.25, 0.10, 3.4),
                    SparkleNote(0.78, 698.46, 0.09, 3.6),
                    SparkleNote(1.52, 880.00, 0.11, 3.2),
                    SparkleNote(2.58, 783.99, 0.11, 2.8),
                    SparkleNote(3.60, 932.33, 0.10, 2.4)
                ),
                bassPulseTimes = doubleArrayOf(0.0, 0.55, 1.10, 1.62, 2.15, 2.70, 3.25, 3.80),
                bassPulseFreqs = doubleArrayOf(146.83, 164.81, 196.00, 146.83, 130.81, 146.83, 110.00, 98.00)
            )
            4 -> GenreArrangement( // 4: HIP-HOP - Boom-bap deep sub-bass + minor Rhodes stabs (Fm9 -> DbMaj9 -> Bbm7 -> C7alt)
                style = StyleMode.HIP_HOP,
                steps = listOf(
                    ChordStep(0.00, doubleArrayOf(174.61, 207.65, 261.63, 311.13, 392.00), modRatio = 1.5, staggerSec = 0.015, decayRate = 2.40),
                    ChordStep(0.95, doubleArrayOf(174.61, 207.65, 261.63, 311.13, 392.00), modRatio = 1.5, staggerSec = 0.015, decayRate = 2.60),
                    ChordStep(1.45, doubleArrayOf(138.59, 174.61, 207.65, 261.63, 311.13), modRatio = 1.5, staggerSec = 0.015, decayRate = 2.00),
                    ChordStep(2.45, doubleArrayOf(116.54, 174.61, 207.65, 261.63, 349.23), modRatio = 2.0, staggerSec = 0.015, decayRate = 2.10),
                    ChordStep(3.40, doubleArrayOf(130.81, 196.00, 233.08, 311.13, 392.00), modRatio = 1.5, staggerSec = 0.02, decayRate = 1.35)
                ),
                sparkles = listOf(
                    SparkleNote(0.48, 783.99, 0.08, 4.5),
                    SparkleNote(1.92, 622.25, 0.09, 4.2),
                    SparkleNote(2.92, 698.46, 0.09, 4.0),
                    SparkleNote(3.75, 783.99, 0.10, 2.8)
                ),
                bassPulseTimes = doubleArrayOf(0.0, 0.72, 1.45, 2.20, 2.45, 3.15, 3.40),
                bassPulseFreqs = doubleArrayOf(87.31, 87.31, 69.30, 69.30, 58.27, 58.27, 65.41)
            )
            else -> GenreArrangement( // 5: POP BEM HARMONICA - Lush stacked anthemic Emaj7 -> Badd9 -> C#m9 -> Amaj9
                style = StyleMode.POP_HARMONICO,
                steps = listOf(
                    ChordStep(0.00, doubleArrayOf(164.81, 246.94, 329.63, 415.30, 493.88, 622.25), modRatio = 2.0, staggerSec = 0.06, decayRate = 0.85),
                    ChordStep(1.10, doubleArrayOf(123.47, 246.94, 329.63, 369.99, 493.88, 554.37), modRatio = 2.0, staggerSec = 0.05, decayRate = 0.85),
                    ChordStep(2.15, doubleArrayOf(138.59, 207.65, 277.18, 329.63, 493.88, 659.25), modRatio = 2.0, staggerSec = 0.05, decayRate = 0.80),
                    ChordStep(3.15, doubleArrayOf(110.00, 220.00, 277.18, 329.63, 415.30, 659.25), modRatio = 2.0, staggerSec = 0.06, decayRate = 0.55)
                ),
                sparkles = listOf(
                    SparkleNote(0.35, 987.77, 0.10, 2.2),
                    SparkleNote(1.45, 1108.73, 0.11, 2.2),
                    SparkleNote(2.50, 1318.51, 0.12, 2.0),
                    SparkleNote(3.45, 1648.14, 0.12, 1.5)
                ),
                bassPulseTimes = doubleArrayOf(0.0, 1.10, 2.15, 3.15),
                bassPulseFreqs = doubleArrayOf(82.41, 123.47, 138.59, 110.00)
            )
        }
    }

    private fun fillChunkSamples(
        out: ShortArray,
        count: Int,
        startSampleIndex: Int,
        totalSamples: Int,
        fadeInSamples: Int,
        fadeOutStart: Int,
        arrangement: GenreArrangement
    ) {
        val fadeOutSpan = (totalSamples - fadeOutStart).coerceAtLeast(1)
        val steps = arrangement.steps
        val sparkles = arrangement.sparkles
        val bassTimes = arrangement.bassPulseTimes
        val bassFreqs = arrangement.bassPulseFreqs

        for (j in 0 until count) {
            val i = startSampleIndex + j
            val t = i.toDouble() / SAMPLE_RATE
            val envAttack = if (i < fadeInSamples) i.toDouble() / fadeInSamples else 1.0
            val envTail = if (i > fadeOutStart) {
                val u = (i - fadeOutStart).toDouble() / fadeOutSpan
                exp(-u * 3.8)
            } else 1.0

            var sum = 0.0

            // Render active harmonic chord steps (with gentle PS2 SPU2 chime FM + warm chorus detune)
            for (sIdx in steps.indices) {
                val step = steps[sIdx]
                if (t < step.startTimeSec) continue
                val stepElapsed = t - step.startTimeSec
                // Rapidly skip faded steps to save CPU cycles
                if (stepElapsed > 3.6 / step.decayRate.coerceAtLeast(0.4)) continue

                val freqs = step.freqs
                for (cIdx in freqs.indices) {
                    val lt = stepElapsed - cIdx * step.staggerSec
                    if (lt < 0.0) continue

                    val attack = if (lt < 0.05) 1.0 - exp(-lt * 45.0) else 1.0
                    val voiceEnv = attack * exp(-lt * (step.decayRate + cIdx * 0.05))
                    if (voiceEnv < 0.004) continue

                    val f = freqs[cIdx]
                    val fmAmt = if (arrangement.style == StyleMode.HIP_HOP) 0.25 else 0.42
                    val fmIndex = fmAmt * exp(-lt * 2.2)
                    val mod = fastSinCycles(f * step.modRatio * lt)
                    val osc = fastSinCycles(f * lt + (fmIndex / TWO_PI) * mod)
                    val detune = fastSinCycles(f * 1.0032 * lt)
                    val amp = if (cIdx == 0) 0.24 else 0.14
                    sum += (osc * 0.68 + detune * 0.32) * voiceEnv * amp
                }
            }

            // Style-specific rhythmic bass or PS2 crystal shimmer notes
            for (bIdx in bassTimes.indices) {
                val bt = t - bassTimes[bIdx]
                if (bt < 0.0 || bt > 1.2) continue
                val bf = bassFreqs[bIdx]
                val decay = when (arrangement.style) {
                    StyleMode.HIP_HOP -> 4.2
                    StyleMode.JAZZ -> 3.6
                    else -> 2.1
                }
                val bEnv = (1.0 - exp(-bt * 65.0)) * exp(-bt * decay)
                if (bEnv < 0.005) continue
                // Boom-bap pitch drop on attack for Hip-Hop kick-bass weight
                val pitchEnv = if (arrangement.style == StyleMode.HIP_HOP) 1.0 + 0.85 * exp(-bt * 38.0) else 1.0
                val bassOsc = fastSinCycles(bf * pitchEnv * bt) + 0.28 * fastSinCycles(bf * 2.0 * bt)
                val bassGain = if (arrangement.style == StyleMode.HIP_HOP) 0.34 else 0.22
                sum += bassOsc * bEnv * bassGain
            }

            for (spIdx in sparkles.indices) {
                val sp = sparkles[spIdx]
                val st = t - sp.timeSec
                if (st < 0.0 || st > 2.0) continue
                val sEnv = (1.0 - exp(-st * 60.0)) * exp(-st * sp.decay)
                if (sEnv < 0.004) continue
                val bell = fastSinCycles(sp.freq * st) + 0.35 * fastSinCycles(sp.freq * 2.0 * st)
                sum += bell * sEnv * sp.amp
            }

            // Soft saturation + strict 50% volume master gain
            val shaped = tanh(sum * envAttack * envTail * 0.95).coerceIn(-0.98, 0.98) * MASTER_VOLUME_SCALE
            out[j] = (shaped * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /**
     * Synthesizes full PCM buffer synchronously for unit test verification.
     */
    internal fun synthesizePs2ChordPcm(variant: Int, totalSamples: Int): ShortArray {
        val safeVariant = ((variant - 1).coerceIn(0, 4)) + 1
        val out = ShortArray(totalSamples)
        fillChunkSamples(
            out = out,
            count = totalSamples,
            startSampleIndex = 0,
            totalSamples = totalSamples,
            fadeInSamples = (SAMPLE_RATE * 0.08).toInt().coerceAtLeast(1),
            fadeOutStart = (totalSamples * 0.76).toInt(),
            arrangement = arrangementForVariant(safeVariant)
        )
        return out
    }
}
