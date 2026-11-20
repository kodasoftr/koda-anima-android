package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import com.example.model.Frame
import com.example.model.SpecialMediaAsset
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Result of importing a video file: returns the video/gif visual reference track
 * AND an automatically demuxed audio track if an audio stream was found inside the video.
 */
data class ImportedMediaBundle(
    val primaryAsset: SpecialMediaAsset,
    val extractedAudioAsset: SpecialMediaAsset? = null
)

object MediaAssetManager {

    private const val TAG = "MediaAssetManager"

    // Playback player (synced Play/Pause)
    private var activeMediaPlayer: MediaPlayer? = null
    private var activeAudioPath: String? = null

    // Lip-sync scrub player (pinça audio scrubbing)
    private var scrubPlayer: MediaPlayer? = null
    private var scrubAudioPath: String? = null
    private var lastScrubDispatchAtMs: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scrubStopRunnable: Runnable? = null

    // Video & GIF frame decoder cache
    private val videoDecoderLock = Any()
    private var cachedVideoRetriever: MediaMetadataRetriever? = null
    private var cachedVideoPath: String? = null
    @Suppress("DEPRECATION")
    private var cachedGifMovie: Movie? = null
    private var cachedGifPath: String? = null
    private var cachedGifDurationMs: Int = 1000
    private val videoFrameCache = object : LruCache<String, Bitmap>(24) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
            // Let GC collect evicted frame bitmaps safely while still potentially drawn on UI thread
        }
    }

    // -------------------------------------------------------------------------
    // Timeline Timing Helpers (Weighted by Frame.holdFrames & Project.fps)
    // -------------------------------------------------------------------------

    fun computeTotalTicks(frames: List<Frame>): Int {
        var total = 0
        for (f in frames) {
            total += max(1, f.holdFrames)
        }
        return max(1, total)
    }

    fun computeFrameStartTick(frames: List<Frame>, frameIndex: Int): Int {
        if (frames.isEmpty() || frameIndex <= 0) return 0
        val limit = min(frameIndex, frames.size)
        var tick = 0
        for (i in 0 until limit) {
            tick += max(1, frames[i].holdFrames)
        }
        return tick
    }

    fun computeTimeMsForFrame(frames: List<Frame>, frameIndex: Int, fps: Int): Long {
        val safeFps = max(1, fps)
        val startTick = computeFrameStartTick(frames, frameIndex)
        return (startTick * 1000L) / safeFps
    }

    fun computeTotalDurationMs(frames: List<Frame>, fps: Int): Long {
        val safeFps = max(1, fps)
        val totalTicks = computeTotalTicks(frames)
        return (totalTicks * 1000L) / safeFps
    }

    fun findFrameIndexAtTimeMs(frames: List<Frame>, timeMs: Long, fps: Int): Int {
        if (frames.isEmpty()) return 0
        val safeFps = max(1, fps)
        val clampedMs = max(0L, timeMs)
        val targetTick = ((clampedMs * safeFps) / 1000L).toInt()
        var acc = 0
        for (i in frames.indices) {
            acc += max(1, frames[i].holdFrames)
            if (targetTick < acc) return i
        }
        return frames.lastIndex
    }

    // -------------------------------------------------------------------------
    // Media Import + Automatic Audio Extraction from Video + Full PCM Waveform
    // -------------------------------------------------------------------------

    fun importMediaFile(context: Context, uri: Uri, defaultType: String): SpecialMediaAsset? {
        return importMediaBundle(context, uri, defaultType)?.primaryAsset
    }

    fun importMediaBundle(context: Context, uri: Uri, defaultType: String): ImportedMediaBundle? {
        return try {
            val resolver = context.contentResolver
            var displayName = "Midia_${System.currentTimeMillis()}"
            try {
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                }
            } catch (_: Exception) {}

            val mimeType = resolver.getType(uri) ?: ""
            val isVideo = mimeType.startsWith("video") ||
                displayName.endsWith(".mp4", true) ||
                displayName.endsWith(".mkv", true) ||
                displayName.endsWith(".webm", true) ||
                displayName.endsWith(".mov", true) ||
                displayName.endsWith(".3gp", true)
            val isAudio = mimeType.startsWith("audio") ||
                displayName.endsWith(".mp3", true) ||
                displayName.endsWith(".wav", true) ||
                displayName.endsWith(".ogg", true) ||
                displayName.endsWith(".m4a", true) ||
                displayName.endsWith(".aac", true)
            val isGif = mimeType.contains("gif") || displayName.endsWith(".gif", true)

            val resolvedType = when {
                isVideo -> "video"
                isGif -> "gif"
                isAudio -> "audio"
                else -> defaultType
            }

            val ext = displayName.substringAfterLast('.', if (resolvedType == "audio") "mp3" else "mp4")
            val mediaDir = File(context.filesDir, "media_tracks").apply { if (!exists()) mkdirs() }
            val targetFile = File(mediaDir, "kodanima_media_${System.currentTimeMillis()}.$ext")

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            val durationSec = probeMediaDurationSeconds(targetFile)

            if (resolvedType == "audio") {
                val peaks = extractWaveformPeaks(targetFile.absolutePath, targetBars = 128)
                val audioAsset = SpecialMediaAsset(
                    name = displayName,
                    uriString = targetFile.absolutePath,
                    type = "audio",
                    durationSeconds = durationSec,
                    audioVolumePercent = 100,
                    waveformPeaks = peaks
                )
                return ImportedMediaBundle(primaryAsset = audioAsset, extractedAudioAsset = null)
            }

            val visualAsset = SpecialMediaAsset(
                name = displayName,
                uriString = targetFile.absolutePath,
                type = resolvedType,
                durationSeconds = durationSec,
                opacityPercent = 65,
                scalePercent = 100.0
            )

            // If importing a video, automatically demux embedded audio track if present
            val extractedAudio = if (resolvedType == "video") {
                extractAudioFromVideo(context, targetFile, displayName, fallbackDurationSec = durationSec)
            } else null

            ImportedMediaBundle(
                primaryAsset = visualAsset,
                extractedAudioAsset = extractedAudio
            )
        } catch (e: Exception) {
            Log.e(TAG, "importMediaBundle failed: ${e.message}")
            null
        }
    }

    private fun probeMediaDurationSeconds(file: File): Double {
        var durationSec = 5.0
        var mmr: MediaMetadataRetriever? = null
        try {
            mmr = MediaMetadataRetriever()
            mmr.setDataSource(file.absolutePath)
            val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durStr != null) {
                val ms = durStr.toLongOrNull() ?: 5000L
                if (ms > 0L) durationSec = ms / 1000.0
            }
        } catch (_: Exception) {
        } finally {
            try { mmr?.release() } catch (_: Exception) {}
        }
        return durationSec
    }

    /**
     * Extracts the embedded audio track from a video container (.mp4/.mkv/.webm) into a standalone
     * playable audio file (.m4a fast mux copy, or .wav PCM decode fallback) and computes full waveform peaks.
     */
    fun extractAudioFromVideo(
        context: Context,
        videoFile: File,
        videoDisplayName: String,
        fallbackDurationSec: Double = 5.0
    ): SpecialMediaAsset? {
        if (!videoFile.exists()) return null
        val mediaDir = File(context.filesDir, "media_tracks").apply { if (!exists()) mkdirs() }
        val baseTitle = videoDisplayName.substringBeforeLast('.')
        val audioDisplayName = "$baseTitle (Áudio).m4a"

        // Pass 1: Fast remux using MediaExtractor + MediaMuxer (zero re-encoding quality loss)
        val m4aOut = File(mediaDir, "extracted_audio_${System.currentTimeMillis()}.m4a")
        val fastSuccess = tryRemuxAudioTrack(videoFile, m4aOut)
        if (fastSuccess && m4aOut.exists() && m4aOut.length() > 512L) {
            val dur = probeMediaDurationSeconds(m4aOut).takeIf { it > 0.05 } ?: fallbackDurationSec
            val peaks = extractWaveformPeaks(m4aOut.absolutePath, targetBars = 128)
            return SpecialMediaAsset(
                name = audioDisplayName,
                uriString = m4aOut.absolutePath,
                type = "audio",
                startSeconds = 0.0,
                durationSeconds = dur,
                audioVolumePercent = 100,
                waveformPeaks = peaks
            )
        } else {
            try { if (m4aOut.exists()) m4aOut.delete() } catch (_: Exception) {}
        }

        // Pass 2: Decode audio stream via MediaCodec to uncompressed 16-bit PCM WAV (handles Vorbis/Opus/MKV/WebM)
        val wavOut = File(mediaDir, "extracted_audio_${System.currentTimeMillis()}.wav")
        val wavDuration = tryDecodeAudioTrackToWav(videoFile, wavOut)
        if (wavDuration > 0.0 && wavOut.exists() && wavOut.length() > 64L) {
            val peaks = extractWaveformPeaks(wavOut.absolutePath, targetBars = 128)
            return SpecialMediaAsset(
                name = "$baseTitle (Áudio).wav",
                uriString = wavOut.absolutePath,
                type = "audio",
                startSeconds = 0.0,
                durationSeconds = wavDuration,
                audioVolumePercent = 100,
                waveformPeaks = peaks
            )
        } else {
            try { if (wavOut.exists()) wavOut.delete() } catch (_: Exception) {}
        }

        return null
    }

    private fun tryRemuxAudioTrack(sourceVideo: File, outM4a: File): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(sourceVideo.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) return false

            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""
            // MediaMuxer MPEG_4 officially supports AAC (mp4a) & MPEG audio cleanly
            if (!mime.contains("mp4a", true) && !mime.contains("aac", true) && !mime.contains("mpeg", true)) {
                return false
            }

            extractor.selectTrack(audioTrackIndex)
            muxer = MediaMuxer(outM4a.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val dstTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val maxInputSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                max(64 * 1024, audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            } else 256 * 1024
            val buffer = ByteBuffer.allocate(maxInputSize)
            val info = MediaCodec.BufferInfo()

            while (true) {
                info.offset = 0
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                info.size = sampleSize
                info.presentationTimeUs = max(0L, extractor.sampleTime)
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(dstTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Fast audio remux skipped/failed: ${e.message}")
            false
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun tryDecodeAudioTrackToWav(sourceVideo: File, outWav: File): Double {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var fos: FileOutputStream? = null
        return try {
            extractor.setDataSource(sourceVideo.absolutePath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || format == null) return 0.0
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return 0.0
            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

            extractor.selectTrack(audioTrackIndex)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            fos = FileOutputStream(outWav)
            // Write 44-byte placeholder WAV header, filled once total PCM bytes known
            fos.write(ByteArray(44))

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var totalPcmBytes = 0L

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val size = extractor.readSampleData(inBuf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, size, max(0L, extractor.sampleTime), 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 5000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = codec.outputFormat
                        if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            val chunk = ByteArray(info.size)
                            outBuf.get(chunk)
                            fos.write(chunk)
                            totalPcmBytes += info.size
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
            fos.close()
            fos = null

            if (totalPcmBytes <= 0L) return 0.0
            writeWavHeader(outWav, totalPcmBytes, sampleRate, channelCount, 16)
            val bytesPerSecond = max(1, sampleRate * channelCount * 2).toDouble()
            totalPcmBytes.toDouble() / bytesPerSecond
        } catch (e: Exception) {
            Log.w(TAG, "PCM WAV fallback failed: ${e.message}")
            0.0
        } finally {
            try { fos?.close() } catch (_: Exception) {}
            try { codec?.stop(); codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun writeWavHeader(file: File, totalAudioLen: Long, sampleRate: Int, channels: Int, bitsPerSample: Int) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8L
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen.toInt())
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate.toInt())
            putShort(blockAlign)
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(totalAudioLen.toInt())
        }.array()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header)
        }
    }

    /**
     * Decodes genuine PCM RMS amplitude peaks across the entire audio file so timeline "varetinhas"
     * accurately reflect the real vocal/music envelope from start to finish.
     */
    fun extractWaveformPeaks(filePath: String, targetBars: Int = 128): List<Float> {
        val file = File(filePath)
        if (!file.exists()) return emptyList()
        val barCount = targetBars.coerceIn(24, 256)

        // Fast path for PCM .wav files
        if (filePath.endsWith(".wav", ignoreCase = true) && file.length() > 44L) {
            extractWavPeaksFast(file, barCount)?.let { return it }
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(file.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = fmt
                    break
                }
            }
            if (trackIndex < 0 || format == null) return generateFallbackWaveform(barCount)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return generateFallbackWaveform(barCount)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                max(100_000L, format.getLong(MediaFormat.KEY_DURATION))
            } else {
                (probeMediaDurationSeconds(file) * 1_000_000.0).toLong().coerceAtLeast(100_000L)
            }
            val barSumSq = DoubleArray(barCount)
            val barCounts = IntArray(barCount)
            val barMax = FloatArray(barCount)

            extractor.selectTrack(trackIndex)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var iterations = 0
            val maxIterations = 8000 // Guard against malformed files

            while (!outputDone && iterations < maxIterations) {
                iterations++
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(4000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val size = extractor.readSampleData(inBuf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, size, max(0L, extractor.sampleTime), 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 4000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size >= 2) {
                        val barIdx = (((info.presentationTimeUs.coerceAtLeast(0L).toDouble() / durationUs.toDouble()) * barCount).toInt())
                            .coerceIn(0, barCount - 1)
                        val shortBuf = outBuf.duplicate().apply {
                            position(info.offset)
                            limit(info.offset + info.size)
                            order(ByteOrder.LITTLE_ENDIAN)
                        }.asShortBuffer()
                        val sampleLen = shortBuf.remaining()
                        val step = max(1, sampleLen / 48)
                        var s = 0
                        while (s < sampleLen) {
                            val v = abs(shortBuf.get(s).toFloat()) / 32768f
                            barSumSq[barIdx] += (v * v).toDouble()
                            barCounts[barIdx]++
                            if (v > barMax[barIdx]) barMax[barIdx] = v
                            s += step
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    // Wait briefly for trailing frames
                    if (iterations % 60 == 0 && barCounts.sum() > 0) break
                }
            }

            normalizeWaveformBars(barSumSq, barCounts, barMax, barCount)
        } catch (e: Exception) {
            Log.w(TAG, "Waveform extraction fallback used: ${e.message}")
            generateFallbackWaveform(barCount)
        } finally {
            try { codec?.stop(); codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun extractWavPeaksFast(file: File, barCount: Int): List<Float>? {
        return try {
            val dataBytes = file.length() - 44L
            if (dataBytes <= 0L) return null
            val totalShorts = dataBytes / 2L
            val shortsPerBar = max(1L, totalShorts / barCount)
            val barSumSq = DoubleArray(barCount)
            val barCounts = IntArray(barCount)
            val barMax = FloatArray(barCount)
            val buf = ByteArray(16384)
            FileInputStream(file).use { fis ->
                fis.skip(44L)
                var shortPos = 0L
                while (true) {
                    val read = fis.read(buf)
                    if (read < 2) break
                    val bb = ByteBuffer.wrap(buf, 0, read and 0x7FFFFFFE).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val count = bb.remaining()
                    val stride = max(1, count / 256)
                    var i = 0
                    while (i < count) {
                        val barIdx = ((shortPos + i) / shortsPerBar).toInt().coerceIn(0, barCount - 1)
                        val v = abs(bb.get(i).toFloat()) / 32768f
                        barSumSq[barIdx] += (v * v).toDouble()
                        barCounts[barIdx]++
                        if (v > barMax[barIdx]) barMax[barIdx] = v
                        i += stride
                    }
                    shortPos += count
                }
            }
            normalizeWaveformBars(barSumSq, barCounts, barMax, barCount)
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeWaveformBars(
        barSumSq: DoubleArray,
        barCounts: IntArray,
        barMax: FloatArray,
        barCount: Int
    ): List<Float> {
        val raw = FloatArray(barCount)
        var globalMax = 0.001f
        for (i in 0 until barCount) {
            val cnt = barCounts[i]
            val rms = if (cnt > 0) sqrt(barSumSq[i] / cnt).toFloat() else 0f
            val combined = rms * 0.65f + barMax[i] * 0.35f
            raw[i] = combined
            if (combined > globalMax) globalMax = combined
        }
        val result = ArrayList<Float>(barCount)
        for (i in 0 until barCount) {
            val norm = (raw[i] / globalMax).coerceIn(0f, 1f)
            result.add((0.06f + norm * 0.94f).coerceIn(0.06f, 1.0f))
        }
        return result
    }

    private fun generateFallbackWaveform(barCount: Int): List<Float> {
        return List(barCount) { idx ->
            val phase = idx * 0.42
            (0.18f + 0.62f * abs(kotlin.math.sin(phase)).toFloat()).coerceIn(0.08f, 0.95f)
        }
    }

    // -------------------------------------------------------------------------
    // Audio Playback (Synchronized with Play / Pause / Mute / Volume / Seek)
    // -------------------------------------------------------------------------

    fun playAudioTrack(
        filePath: String,
        startMs: Int = 0,
        isLooping: Boolean = true,
        volumePercent: Int = 100,
        muted: Boolean = false
    ) {
        try {
            endAudioScrub()
            val file = File(filePath)
            if (!file.exists()) return
            val vol = if (muted) 0f else (volumePercent.coerceIn(0, 100) / 100f)

            val existing = activeMediaPlayer
            if (existing != null && activeAudioPath == file.absolutePath) {
                existing.setVolume(vol, vol)
                existing.isLooping = isLooping
                val dur = runCatching { existing.duration }.getOrDefault(0)
                val targetPos = if (dur > 0) startMs.coerceIn(0, max(0, dur - 10)) else max(0, startMs)
                seekPlayerCompat(existing, targetPos.toLong())
                if (!existing.isPlaying) {
                    existing.start()
                }
                return
            }

            stopAudio()
            activeAudioPath = file.absolutePath
            activeMediaPlayer = MediaPlayer().apply {
                setOnErrorListener { mp, _, _ ->
                    try { mp.release() } catch (_: Exception) {}
                    if (activeMediaPlayer === mp) {
                        activeMediaPlayer = null
                        activeAudioPath = null
                    }
                    true
                }
                setDataSource(file.absolutePath)
                prepare()
                setVolume(vol, vol)
                val dur = runCatching { duration }.getOrDefault(0)
                val targetPos = if (dur > 0) startMs.coerceIn(0, max(0, dur - 10)) else max(0, startMs)
                seekPlayerCompat(this, targetPos.toLong())
                this.isLooping = isLooping
                start()
            }
        } catch (e: Exception) {
            stopAudio()
            Log.e(TAG, "playAudioTrack failed: ${e.message}")
        }
    }

    fun pauseAudio() {
        try {
            if (activeMediaPlayer?.isPlaying == true) {
                activeMediaPlayer?.pause()
            }
        } catch (_: Exception) {}
    }

    fun seekAudio(ms: Int) {
        try {
            val mp = activeMediaPlayer ?: return
            val dur = runCatching { mp.duration }.getOrDefault(0)
            val target = if (dur > 0) ms.coerceIn(0, max(0, dur - 10)) else max(0, ms)
            seekPlayerCompat(mp, target.toLong())
        } catch (_: Exception) {}
    }

    fun updateAudioVolume(volumePercent: Int, muted: Boolean) {
        val vol = if (muted) 0f else (volumePercent.coerceIn(0, 100) / 100f)
        try {
            activeMediaPlayer?.setVolume(vol, vol)
            scrubPlayer?.setVolume(vol, vol)
        } catch (_: Exception) {}
    }

    fun stopAudio() {
        endAudioScrub()
        try {
            activeMediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            activeMediaPlayer?.release()
        } catch (_: Exception) {}
        activeMediaPlayer = null
        activeAudioPath = null
    }

    // -------------------------------------------------------------------------
    // Lip-Sync Audio Scrubbing (Pinça Drag)
    // -------------------------------------------------------------------------

    fun scrubAudioAtMs(
        filePath: String,
        targetMs: Long,
        volumePercent: Int = 100,
        muted: Boolean = false
    ) {
        if (muted || volumePercent <= 0) return
        val now = System.currentTimeMillis()
        if (now - lastScrubDispatchAtMs < 52L) return
        lastScrubDispatchAtMs = now

        val file = File(filePath)
        if (!file.exists()) return
        val vol = (volumePercent.coerceIn(0, 100) / 100f)

        try {
            if (scrubPlayer == null || scrubAudioPath != file.absolutePath) {
                releaseScrubPlayer()
                scrubAudioPath = file.absolutePath
                scrubPlayer = MediaPlayer().apply {
                    setOnErrorListener { mp, _, _ ->
                        try { mp.release() } catch (_: Exception) {}
                        if (scrubPlayer === mp) {
                            scrubPlayer = null
                            scrubAudioPath = null
                        }
                        true
                    }
                    setDataSource(file.absolutePath)
                    prepare()
                }
            }

            val player = scrubPlayer ?: return
            val dur = runCatching { player.duration }.getOrDefault(0).toLong()
            if (dur <= 0L) return
            val safeMs = targetMs.coerceIn(0L, max(0L, dur - 30L))
            player.setVolume(vol, vol)
            seekPlayerCompat(player, safeMs)
            if (!player.isPlaying) {
                player.start()
            }

            scrubStopRunnable?.let { mainHandler.removeCallbacks(it) }
            val stopRun = Runnable {
                try {
                    if (scrubPlayer?.isPlaying == true) {
                        scrubPlayer?.pause()
                    }
                } catch (_: Exception) {}
            }
            scrubStopRunnable = stopRun
            mainHandler.postDelayed(stopRun, 88L)
        } catch (e: Exception) {
            Log.w(TAG, "scrubAudioAtMs error: ${e.message}")
        }
    }

    fun endAudioScrub() {
        scrubStopRunnable?.let { mainHandler.removeCallbacks(it) }
        scrubStopRunnable = null
        try {
            if (scrubPlayer?.isPlaying == true) {
                scrubPlayer?.pause()
            }
        } catch (_: Exception) {}
    }

    private fun releaseScrubPlayer() {
        scrubStopRunnable?.let { mainHandler.removeCallbacks(it) }
        scrubStopRunnable = null
        try {
            scrubPlayer?.stop()
        } catch (_: Exception) {}
        try {
            scrubPlayer?.release()
        } catch (_: Exception) {}
        scrubPlayer = null
        scrubAudioPath = null
    }

    private fun seekPlayerCompat(mp: MediaPlayer, ms: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mp.seekTo(ms, MediaPlayer.SEEK_CLOSEST)
        } else {
            mp.seekTo(ms.toInt())
        }
    }

    // -------------------------------------------------------------------------
    // Video & Animated GIF Frame Extraction for Canvas Overlay
    // -------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    fun getVideoFrameAtMs(filePath: String, type: String, timeMs: Long): Bitmap? {
        val file = File(filePath)
        if (!file.exists()) return null
        val bucketMs = (max(0L, timeMs) / 33L) * 33L
        val cacheKey = "${file.absolutePath}@$bucketMs"
        videoFrameCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return cached
        }

        synchronized(videoDecoderLock) {
            videoFrameCache.get(cacheKey)?.let { cached ->
                if (!cached.isRecycled) return cached
            }

            if (type == "gif" || filePath.endsWith(".gif", ignoreCase = true)) {
                return decodeGifFrameLocked(file.absolutePath, bucketMs, cacheKey)
            }

            return decodeVideoFrameLocked(file.absolutePath, bucketMs, cacheKey)
        }
    }

    private fun decodeVideoFrameLocked(path: String, bucketMs: Long, cacheKey: String): Bitmap? {
        return try {
            if (cachedVideoRetriever == null || cachedVideoPath != path) {
                try { cachedVideoRetriever?.release() } catch (_: Exception) {}
                cachedVideoPath = path
                cachedVideoRetriever = MediaMetadataRetriever().apply {
                    setDataSource(path)
                }
            }
            val mmr = cachedVideoRetriever ?: return null
            val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durMs = durStr?.toLongOrNull()?.coerceAtLeast(33L) ?: 10_000L
            val targetMs = if (durMs > 0L) (bucketMs % durMs) else bucketMs
            val timeUs = targetMs * 1000L

            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                mmr.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    960,
                    540
                ) ?: mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } else {
                mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            if (frame != null) {
                videoFrameCache.put(cacheKey, frame)
            }
            frame
        } catch (e: Exception) {
            Log.w(TAG, "decodeVideoFrameLocked error: ${e.message}")
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun decodeGifFrameLocked(path: String, bucketMs: Long, cacheKey: String): Bitmap? {
        return try {
            if (cachedGifMovie == null || cachedGifPath != path) {
                cachedGifPath = path
                cachedGifMovie = Movie.decodeFile(path)
                cachedGifDurationMs = (cachedGifMovie?.duration() ?: 1000).coerceAtLeast(100)
            }
            val movie = cachedGifMovie ?: return null
            val w = movie.width().coerceAtLeast(1)
            val h = movie.height().coerceAtLeast(1)
            movie.setTime((bucketMs % cachedGifDurationMs).toInt())
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            movie.draw(canvas, 0f, 0f)
            videoFrameCache.put(cacheKey, bmp)
            bmp
        } catch (e: Exception) {
            Log.w(TAG, "decodeGifFrameLocked error: ${e.message}")
            null
        }
    }

    fun releaseVideoDecoder() {
        synchronized(videoDecoderLock) {
            try { cachedVideoRetriever?.release() } catch (_: Exception) {}
            cachedVideoRetriever = null
            cachedVideoPath = null
            cachedGifMovie = null
            cachedGifPath = null
            videoFrameCache.evictAll()
        }
    }

    fun releaseAll() {
        stopAudio()
        releaseScrubPlayer()
        releaseVideoDecoder()
    }

    /**
     * Deletes the physical imported file from internal storage to save device disk space.
     */
    fun deleteMediaFile(filePath: String): Boolean {
        return try {
            if (activeAudioPath == filePath) stopAudio()
            if (scrubAudioPath == filePath) releaseScrubPlayer()
            if (cachedVideoPath == filePath || cachedGifPath == filePath) releaseVideoDecoder()
            val file = File(filePath)
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
