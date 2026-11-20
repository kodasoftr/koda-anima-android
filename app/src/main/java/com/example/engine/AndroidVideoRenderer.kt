package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.view.Surface
import com.example.model.AnimationProject
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Native Android Hardware-Accelerated Video Renderer for Kodanima 2D.
 * Uses Android MediaCodec + MediaMuxer with strict try/finally native resource cleanup,
 * lockHardwareCanvas() GPU blitting, and KodanimaBitmapPool buffer reuse.
 */
object AndroidVideoRenderer {

    suspend fun renderToMp4(
        context: Context,
        project: AnimationProject,
        onProgress: (Float, String) -> Unit
    ): File = withContext(KodanimaDispatchers.backgroundRender) {
        var width = project.resolution.width
        var height = project.resolution.height
        if (width % 2 != 0) width -= 1
        if (height % 2 != 0) height -= 1
        width = max(160, min(1920, width))
        height = max(160, min(1080, height))

        val fps = max(1, min(60, project.fps))
        val bitRate = width * height * 4

        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val outputFile = File(exportDir, "${project.name.replace(" ", "_")}_kodanima.mp4")
        if (outputFile.exists()) outputFile.delete()

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        var encoder: MediaCodec? = null
        var inputSurface: Surface? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var pooledFrameBitmap: Bitmap? = null

        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            val bufferInfo = MediaCodec.BufferInfo()
            val totalFrames = max(1, project.frames.size)

            fun drainEncoder(endOfStream: Boolean) {
                val enc = encoder ?: return
                val mx = muxer ?: return
                val timeoutUs = if (endOfStream) 10_000L else 1_000L
                while (true) {
                    val encoderStatus = enc.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!endOfStream) break
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (muxerStarted) throw RuntimeException("Format changed twice")
                        val newFormat = enc.outputFormat
                        trackIndex = mx.addTrack(newFormat)
                        mx.start()
                        muxerStarted = true
                    } else if (encoderStatus >= 0) {
                        val encodedData = enc.getOutputBuffer(encoderStatus)
                            ?: throw RuntimeException("encoderOutputBuffer $encoderStatus was null")

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size != 0) {
                            if (!muxerStarted) throw RuntimeException("Muxer hasn't started")
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            mx.writeSampleData(trackIndex, encodedData, bufferInfo)
                        }
                        enc.releaseOutputBuffer(encoderStatus, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }
                }
            }

            // Reuse a single pooled bitmap across the entire video export (zero bitmap churn!)
            pooledFrameBitmap = KodanimaBitmapPool.acquire(width, height, Bitmap.Config.ARGB_8888, Color.WHITE)
            val surface = inputSurface!!

            for (i in project.frames.indices) {
                val frame = project.frames[i]
                onProgress(
                    0.1f + 0.7f * (i.toFloat() / totalFrames),
                    "Codificando quadro ${i + 1}/$totalFrames via hardware GPU..."
                )

                KodanimaGraphicsEngine.drawFrameToBitmap(frame, pooledFrameBitmap, Color.WHITE, clearBitmap = true)

                repeat(max(1, frame.holdFrames)) {
                    drainEncoder(false)
                    val canvas: Canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        surface.lockHardwareCanvas()
                    } else {
                        surface.lockCanvas(null)
                    }
                    try {
                        canvas.drawBitmap(pooledFrameBitmap, 0f, 0f, null)
                    } finally {
                        surface.unlockCanvasAndPost(canvas)
                    }
                }
            }

            onProgress(0.85f, "Finalizando vídeo MP4...")
            encoder.signalEndOfInputStream()
            drainEncoder(true)
        } finally {
            KodanimaBitmapPool.release(pooledFrameBitmap)
            try { encoder?.stop() } catch (_: Throwable) {}
            try { encoder?.release() } catch (_: Throwable) {}
            try { inputSurface?.release() } catch (_: Throwable) {}
            if (muxerStarted) {
                try { muxer?.stop() } catch (_: Throwable) {}
            }
            try { muxer?.release() } catch (_: Throwable) {}
        }

        onProgress(1.0f, "MP4 concluído com sucesso!")
        outputFile
    }

    suspend fun exportFramePackZip(
        context: Context,
        project: AnimationProject,
        onProgress: (Float, String) -> Unit
    ): File = withContext(KodanimaDispatchers.backgroundRender) {
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val zipFile = File(exportDir, "${project.name.replace(" ", "_")}_quadros_png.zip")
        if (zipFile.exists()) zipFile.delete()

        val w = project.resolution.width
        val h = project.resolution.height
        val scratchBmp = KodanimaBitmapPool.acquire(w, h, Bitmap.Config.ARGB_8888, Color.WHITE)

        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                val total = max(1, project.frames.size)
                for (i in project.frames.indices) {
                    onProgress(0.1f + 0.8f * (i.toFloat() / total), "Compactando quadro ${i + 1}/$total...")
                    val frame = project.frames[i]
                    KodanimaGraphicsEngine.drawFrameToBitmap(frame, scratchBmp, Color.WHITE, clearBitmap = true)
                    val entryName = String.format("frame_%06d.png", i + 1)
                    zipOut.putNextEntry(ZipEntry(entryName))
                    scratchBmp.compress(Bitmap.CompressFormat.PNG, 100, zipOut)
                    zipOut.closeEntry()
                }
            }
        } finally {
            KodanimaBitmapPool.release(scratchBmp)
        }

        onProgress(1.0f, "Pacote de quadros PNG criado com sucesso!")
        zipFile
    }
}
