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
        cropToCamera: Boolean = true,
        onProgress: (Float, String) -> Unit
    ): File = withContext(KodanimaDispatchers.backgroundRender) {
        val useCameraCrop = cropToCamera && project.camera3D.isEnabled
        val has3DObjects = project.camera3D.scene3DObjects.any { it.isVisible && it.meshAssetPath.isNotBlank() }
        val rawW = (if (useCameraCrop) project.camera3D.captureWidth else project.resolution.width).coerceAtLeast(160)
        val rawH = (if (useCameraCrop) project.camera3D.captureHeight else project.resolution.height).coerceAtLeast(160)
        val longEdge = max(rawW, rawH)
        val scaleDown = if (longEdge > 1920) 1920f / longEdge.toFloat() else 1f
        var width = ((rawW * scaleDown).toInt().coerceIn(160, 1920)) and 1.inv()
        var height = ((rawH * scaleDown).toInt().coerceIn(160, 1920)) and 1.inv()
        if (width < 160) width = 160
        if (height < 160) height = 160

        val fps = max(1, min(60, project.fps))
        val bitRate = ((width.toLong() * height.toLong() * fps.coerceIn(12, 60) * 0.24f)).toInt()
            .coerceIn(8_000_000, 28_000_000)

        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val outputFile = File(exportDir, "${project.name.replace(" ", "_")}_kodanima.mp4")
        if (outputFile.exists()) outputFile.delete()

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            runCatching {
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            }
        }

        var encoder: MediaCodec? = null
        var inputSurface: Surface? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var pooledFrameBitmap: Bitmap? = null
        val blitPaint = android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG or
                android.graphics.Paint.FILTER_BITMAP_FLAG or
                android.graphics.Paint.DITHER_FLAG
        )

        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            val bufferInfo = MediaCodec.BufferInfo()
            val totalTicks = TimelineTrackEngine.computeTotalTicks(project, includeCameraRunway = useCameraCrop || has3DObjects)

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
            val dstRect = android.graphics.Rect(0, 0, width, height)

            for (tick in 0 until totalTicks) {
                val frame = TimelineTrackEngine.composeFrameAtTick(project, tick, activeLayerIndex = 0, fallbackFrameIndex = 0)
                onProgress(
                    0.1f + 0.7f * (tick.toFloat() / totalTicks.coerceAtLeast(1)),
                    "Codificando quadro ${tick + 1}/$totalTicks via hardware GPU..."
                )

                if (useCameraCrop || has3DObjects || project.camera3D.postEffects.hasAnyEffect() || project.canvasBackground.show3DInCanvas) {
                    Camera3DEngine.renderFrameWithCameraAndEffects(
                        frame = frame,
                        settings = project.camera3D,
                        frameIndex = tick,
                        projectWidth = project.resolution.width,
                        projectHeight = project.resolution.height,
                        targetBitmap = pooledFrameBitmap,
                        cropToCamera = useCameraCrop,
                        background = project.canvasBackground,
                        renderTransparentAsSky = true,
                        tickFloat = tick.toFloat(),
                        fps = fps
                    )
                } else {
                    KodanimaGraphicsEngine.drawFrameToBitmap(
                        frame = frame,
                        targetBitmap = pooledFrameBitmap,
                        clearBitmap = true,
                        background = project.canvasBackground,
                        drawSkyWhenTransparent = true
                    )
                }

                drainEncoder(false)
                val canvas: Canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        surface.lockHardwareCanvas()
                    } catch (_: Throwable) {
                        surface.lockCanvas(null)
                    }
                } else {
                    surface.lockCanvas(null)
                }
                try {
                    canvas.drawBitmap(pooledFrameBitmap, null, dstRect, blitPaint)
                } finally {
                    surface.unlockCanvasAndPost(canvas)
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
        cropToCamera: Boolean = true,
        onProgress: (Float, String) -> Unit
    ): File = withContext(KodanimaDispatchers.backgroundRender) {
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val zipFile = File(exportDir, "${project.name.replace(" ", "_")}_quadros_png.zip")
        if (zipFile.exists()) zipFile.delete()

        val useCameraCrop = cropToCamera && project.camera3D.isEnabled
        val has3DObjects = project.camera3D.scene3DObjects.any { it.isVisible && it.meshAssetPath.isNotBlank() }
        val w = if (useCameraCrop) project.camera3D.captureWidth.coerceIn(160, 4096) else project.resolution.width
        val h = if (useCameraCrop) project.camera3D.captureHeight.coerceIn(160, 4096) else project.resolution.height
        val scratchBmp = KodanimaBitmapPool.acquire(w, h, Bitmap.Config.ARGB_8888, Color.TRANSPARENT)

        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                val totalTicks = TimelineTrackEngine.computeTotalTicks(project, includeCameraRunway = useCameraCrop || has3DObjects)
                for (tick in 0 until totalTicks) {
                    onProgress(0.1f + 0.8f * (tick.toFloat() / totalTicks.coerceAtLeast(1)), "Compactando quadro ${tick + 1}/$totalTicks...")
                    val frame = TimelineTrackEngine.composeFrameAtTick(project, tick, activeLayerIndex = 0, fallbackFrameIndex = 0)
                    val exportFps = max(1, min(60, project.fps))
                    if (useCameraCrop || has3DObjects || project.camera3D.postEffects.hasAnyEffect() || project.canvasBackground.show3DInCanvas) {
                        Camera3DEngine.renderFrameWithCameraAndEffects(
                            frame = frame,
                            settings = project.camera3D,
                            frameIndex = tick,
                            projectWidth = project.resolution.width,
                            projectHeight = project.resolution.height,
                            targetBitmap = scratchBmp,
                            cropToCamera = useCameraCrop,
                            background = project.canvasBackground,
                            renderTransparentAsSky = false,
                            tickFloat = tick.toFloat(),
                            fps = exportFps
                        )
                    } else {
                        KodanimaGraphicsEngine.drawFrameToBitmap(
                            frame = frame,
                            targetBitmap = scratchBmp,
                            clearBitmap = true,
                            background = project.canvasBackground,
                            drawSkyWhenTransparent = false
                        )
                    }
                    val entryName = String.format("frame_%06d.png", tick + 1)
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
