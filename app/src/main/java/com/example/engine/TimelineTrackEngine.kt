package com.example.engine

import com.example.model.AnimationProject
import com.example.model.CameraEasingCurve
import com.example.model.Frame
import com.example.model.Layer
import com.example.model.SymbolInstanceKeyframe
import com.example.model.TimelineSymbolDefinition
import com.example.model.TimelineSymbolInstance
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class LayerCelSpan(
    val frameIndex: Int,
    val layerIndex: Int,
    val startTick: Int,
    val durationTicks: Int,
    val layer: Layer
) {
    val endTickExclusive: Int get() = startTick + durationTicks
}

/**
 * Independent Per-Timeline-Layer Exposure & Cel Scheduling Engine.
 *
 * Unlike global `Frame.holdFrames`, each timeline layer row maintains its own independent
 * sequence of cels (`Layer.celHoldTicks` + optional `Layer.celStartTick`). Stretching or moving
 * a cel on one timeline layer never shifts or stretches cels on upper or lower timeline layers.
 */
object TimelineTrackEngine {

    fun computeLayerCels(project: AnimationProject, layerIndex: Int): List<LayerCelSpan> {
        val frames = project.frames
        if (frames.isEmpty()) return emptyList()
        val out = ArrayList<LayerCelSpan>(frames.size)
        var cursorTick = 0
        for (fIdx in frames.indices) {
            val frame = frames[fIdx]
            val layer = frame.layers.getOrNull(layerIndex) ?: continue
            val dur = layer.celHoldTicks ?: frame.holdFrames.coerceAtLeast(1)
            if (dur <= 0) continue
            val explicitStart = layer.celStartTick
            val start = if (explicitStart != null) max(cursorTick, explicitStart.coerceAtLeast(0)) else cursorTick
            out.add(
                LayerCelSpan(
                    frameIndex = fIdx,
                    layerIndex = layerIndex,
                    startTick = start,
                    durationTicks = dur,
                    layer = layer
                )
            )
            cursorTick = start + dur
        }
        return out
    }

    fun computeLayerTotalTicks(project: AnimationProject, layerIndex: Int): Int {
        val cels = computeLayerCels(project, layerIndex)
        return cels.lastOrNull()?.endTickExclusive ?: 0
    }

    fun computeTotalTicks(project: AnimationProject, includeCameraRunway: Boolean = false): Int {
        val frames = project.frames
        if (frames.isEmpty()) return 1
        val layerCount = frames.maxOfOrNull { it.layers.size } ?: 1
        var maxTick = 0
        for (lIdx in 0 until layerCount) {
            maxTick = max(maxTick, computeLayerTotalTicks(project, lIdx))
        }
        if (maxTick <= 0) {
            maxTick = MediaAssetManager.computeTotalTicks(frames)
        }
        val symbolMax = project.symbolInstances.maxOfOrNull { inst ->
            inst.startTick + max(1, inst.durationTicks)
        } ?: 0
        maxTick = max(maxTick, symbolMax)
        if (includeCameraRunway) {
            val cam = project.camera3D
            val camMax = cam.keyframes.maxOfOrNull { it.frameIndex + max(1, it.holdTicks) } ?: 0
            val layerTrackMax = cam.layerTransformTracks.flatMap { it.keyframes }
                .maxOfOrNull { it.frameIndex + max(1, it.holdTicks) } ?: 0
            val objTrackMax = cam.scene3DObjects.flatMap { it.keyframes }
                .maxOfOrNull { it.frameIndex + max(1, it.holdTicks) } ?: 0
            maxTick = maxOf(maxTick, camMax, layerTrackMax, objTrackMax)
        }
        return max(1, maxTick)
    }

    fun evaluateSymbolEasing(rawT: Float, kf: SymbolInstanceKeyframe): Float {
        val u = rawT.coerceIn(0f, 1f)
        return when (kf.easing) {
            CameraEasingCurve.LINEAR -> u
            CameraEasingCurve.BEZIER_SUAVE -> {
                val p1y = kf.bezierP1Y.coerceIn(0f, 1.5f)
                val p2y = kf.bezierP2Y.coerceIn(-0.5f, 1f)
                val inv = 1f - u
                (3f * inv * inv * u * p1y + 3f * inv * u * u * p2y + u * u * u).coerceIn(-0.2f, 1.2f)
            }
            CameraEasingCurve.ACELERAR -> u * u * u
            CameraEasingCurve.DESACELERAR -> 1f - (1f - u).pow(3)
            CameraEasingCurve.DEGRAU -> 0f
        }
    }

    /**
     * Evaluates a Flash CS3-style Symbol instance's Motion Tween transform + effect overrides
     * at timeline position [tickFloat]. Coordinates are local to [instance.startTick]..endTick.
     */
    fun evaluateSymbolInstanceAtTick(
        instance: TimelineSymbolInstance,
        tickFloat: Float
    ): SymbolInstanceKeyframe {
        val localTick = (tickFloat - instance.startTick).coerceAtLeast(0f)
        val sorted = instance.keyframes.sortedBy { it.frameIndex }
        if (sorted.isEmpty()) return SymbolInstanceKeyframe(frameIndex = 0)
        if (sorted.size == 1 || !instance.autoInterpolate) {
            val active = sorted.lastOrNull { it.frameIndex.toFloat() <= localTick + 0.001f } ?: sorted.first()
            return active
        }
        if (localTick <= sorted.first().frameIndex) return sorted.first()
        if (localTick >= sorted.last().frameIndex) return sorted.last()

        var prev = sorted.first()
        var next = sorted.last()
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (localTick >= a.frameIndex && localTick <= b.frameIndex) {
                prev = a
                next = b
                break
            }
        }
        if (prev.easing == CameraEasingCurve.DEGRAU) return prev
        val holdOffset = max(0, prev.holdTicks - 1).toFloat()
        val effStart = min(prev.frameIndex + holdOffset, next.frameIndex.toFloat())
        val span = (next.frameIndex - effStart).coerceAtLeast(0.001f)
        if (localTick <= effStart) return prev
        val rawT = ((localTick - effStart) / span).coerceIn(0f, 1f)
        val e = evaluateSymbolEasing(rawT, prev)
        fun lerp(a: Float, b: Float) = a + (b - a) * e

        return SymbolInstanceKeyframe(
            frameIndex = localTick.toInt(),
            x = lerp(prev.x, next.x),
            y = lerp(prev.y, next.y),
            scaleX = lerp(prev.scaleX, next.scaleX),
            scaleY = lerp(prev.scaleY, next.scaleY),
            rotationZ = lerp(prev.rotationZ, next.rotationZ),
            opacity = lerp(prev.opacity, next.opacity).coerceIn(0f, 1f),
            glowRadius = lerp(prev.glowRadius, next.glowRadius).coerceAtLeast(0f),
            blurRadius = lerp(prev.blurRadius, next.blurRadius).coerceAtLeast(0f),
            easing = prev.easing
        )
    }

    /**
     * Resolves which internal Frame inside [symbol] should display at [timelineTickFloat].
     */
    fun resolveSymbolInternalFrame(
        symbol: TimelineSymbolDefinition,
        instance: TimelineSymbolInstance,
        timelineTickFloat: Float
    ): Frame? {
        if (symbol.frames.isEmpty()) return null
        val localTick = (timelineTickFloat - instance.startTick).toInt().coerceAtLeast(0)
        val symTotalTicks = symbol.frames.sumOf { max(1, it.holdFrames) }.coerceAtLeast(1)
        val wrappedTick = if (instance.loopPlayback) localTick % symTotalTicks else localTick.coerceAtMost(symTotalTicks - 1)
        var cursor = 0
        for (f in symbol.frames) {
            val dur = max(1, f.holdFrames)
            if (wrappedTick < cursor + dur) return f
            cursor += dur
        }
        return symbol.frames.last()
    }

    fun resolveCelAtTick(project: AnimationProject, layerIndex: Int, tick: Int): LayerCelSpan? {
        if (tick < 0) return null
        val cels = computeLayerCels(project, layerIndex)
        for (cel in cels) {
            if (tick in cel.startTick until cel.endTickExclusive) {
                return cel
            }
        }
        return null
    }

    fun computeCelStartTick(project: AnimationProject, layerIndex: Int, frameIndex: Int): Int {
        val cels = computeLayerCels(project, layerIndex)
        cels.firstOrNull { it.frameIndex == frameIndex }?.let { return it.startTick }
        // Fallback when frameIndex slot has celHoldTicks == 0 on this layer
        return MediaAssetManager.computeFrameStartTick(project.frames, frameIndex).coerceAtLeast(0)
    }

    fun resolvePreferredFrameIndexAtTick(
        project: AnimationProject,
        activeLayerIndex: Int,
        tick: Int,
        fallbackFrameIndex: Int = 0
    ): Int {
        val frames = project.frames
        if (frames.isEmpty()) return 0
        val clampedTick = tick.coerceAtLeast(0)
        resolveCelAtTick(project, activeLayerIndex, clampedTick)?.let { return it.frameIndex }
        val layerCount = frames.maxOfOrNull { it.layers.size } ?: 1
        for (lIdx in 0 until layerCount) {
            resolveCelAtTick(project, lIdx, clampedTick)?.let { return it.frameIndex }
        }
        // Nearest prior cel on activeLayer or clamp
        val activeCels = computeLayerCels(project, activeLayerIndex)
        val prior = activeCels.lastOrNull { it.startTick <= clampedTick }
        if (prior != null) return prior.frameIndex
        return fallbackFrameIndex.coerceIn(0, frames.lastIndex)
    }

    /**
     * Composes a unified renderable [Frame] at [tick], pulling each timeline layer's independent cel
     * active at that exact timeline tick while leaving other layers completely decoupled.
     */
    fun composeFrameAtTick(
        project: AnimationProject,
        tick: Int,
        activeLayerIndex: Int,
        fallbackFrameIndex: Int = 0
    ): Frame {
        val frames = project.frames
        if (frames.isEmpty()) return Frame()
        val safeTick = tick.coerceAtLeast(0)
        val layerCount = frames.maxOfOrNull { it.layers.size } ?: 1
        val composedLayers = ArrayList<Layer>(layerCount)
        var resolvedActiveFrameIdx: Int? = null

        for (lIdx in 0 until layerCount) {
            val cel = resolveCelAtTick(project, lIdx, safeTick)
            if (cel != null) {
                if (lIdx == activeLayerIndex) resolvedActiveFrameIdx = cel.frameIndex
                composedLayers.add(cel.layer)
            } else {
                val template = frames.firstOrNull()?.layers?.getOrNull(lIdx)
                composedLayers.add(
                    Layer(
                        id = template?.id ?: UUID.randomUUID().toString(),
                        name = template?.name ?: "Camada ${lIdx + 1}",
                        isVisible = false,
                        isLocked = template?.isLocked ?: false,
                        opacity = 0f,
                        strokes = emptyList(),
                        celHoldTicks = 0
                    )
                )
            }
        }

        val hostIdx = (resolvedActiveFrameIdx ?: resolvePreferredFrameIndexAtTick(project, activeLayerIndex, safeTick, fallbackFrameIndex))
            .coerceIn(0, frames.lastIndex)
        val hostFrame = frames[hostIdx]
        return hostFrame.copy(layers = composedLayers)
    }

    /**
     * Collects distinct previous or next cels per timeline layer for onion skinning so a cel
     * held across multiple ticks never ghosts over itself.
     */
    fun resolveOnionSkinLayersAtTick(
        project: AnimationProject,
        tick: Int,
        stepOffset: Int
    ): List<Layer> {
        if (stepOffset == 0 || project.frames.isEmpty()) return emptyList()
        val layerCount = project.frames.maxOfOrNull { it.layers.size } ?: 1
        val result = ArrayList<Layer>(layerCount)
        for (lIdx in 0 until layerCount) {
            val cels = computeLayerCels(project, lIdx)
            if (cels.isEmpty()) continue
            val currentPos = cels.indexOfFirst { tick in it.startTick until it.endTickExclusive }
            val targetCel = if (currentPos >= 0) {
                val targetPos = currentPos + stepOffset
                cels.getOrNull(targetPos)
            } else if (stepOffset < 0) {
                val beforeCels = cels.filter { it.endTickExclusive <= tick }
                val idx = beforeCels.size + stepOffset
                beforeCels.getOrNull(idx)
            } else {
                val afterCels = cels.filter { it.startTick > tick }
                afterCels.getOrNull(stepOffset - 1)
            }
            if (targetCel != null && targetCel.layer.isVisible) {
                result.add(targetCel.layer)
            }
        }
        return result
    }

    private fun isBlankPlaceholderCel(layer: Layer): Boolean {
        return layer.strokes.isEmpty() &&
            layer.imageAttachment == null &&
            layer.referenceImageUri == null &&
            !layer.isBucketLayer
    }

    /**
     * Materializes explicit `celHoldTicks` on every layer of a frame when decoupling per-layer timing,
     * so modifying one layer never changes any other layer's existing duration.
     */
    private fun materializeFrameLayerDurations(frame: Frame): Frame {
        val baseHold = frame.holdFrames.coerceAtLeast(1)
        var changed = false
        val mapped = frame.layers.map { layer ->
            if (layer.celHoldTicks == null) {
                changed = true
                layer.copy(celHoldTicks = baseHold)
            } else layer
        }
        return if (changed) frame.copy(layers = mapped) else frame
    }

    /**
     * Stretches or shrinks ONLY the cel `(layerIndex, frameIndex)` to [newHoldTicks] without changing
     * any other timeline layer.
     */
    fun setLayerCelDuration(
        project: AnimationProject,
        layerIndex: Int,
        frameIndex: Int,
        newHoldTicks: Int
    ): AnimationProject {
        val frames = project.frames
        if (frameIndex !in frames.indices) return project
        val targetHold = newHoldTicks.coerceIn(1, 240)
        val mutableFrames = frames.map { materializeFrameLayerDurations(it) }.toMutableList()
        val targetFrame = mutableFrames[frameIndex]
        if (layerIndex !in targetFrame.layers.indices) return project

        val curLayer = targetFrame.layers[layerIndex]
        val curHold = (curLayer.celHoldTicks ?: targetFrame.holdFrames).coerceAtLeast(1)
        val delta = targetHold - curHold

        val updatedTargetLayers = targetFrame.layers.toMutableList()
        updatedTargetLayers[layerIndex] = curLayer.copy(celHoldTicks = targetHold)
        mutableFrames[frameIndex] = targetFrame.copy(layers = updatedTargetLayers)

        // When stretching right, absorb only immediately-following blank placeholder slots on THIS layer
        // (left behind by other layers having more frames) so total project length doesn't bloat unnecessarily.
        if (delta > 0) {
            var remainingToAbsorb = delta
            var probeIdx = frameIndex + 1
            while (probeIdx < mutableFrames.size && remainingToAbsorb > 0) {
                val probeFrame = mutableFrames[probeIdx]
                val probeLayer = probeFrame.layers.getOrNull(layerIndex) ?: break
                val probeDur = probeLayer.celHoldTicks ?: 1
                if (probeDur <= 0) {
                    probeIdx++
                    continue
                }
                if (probeLayer.celStartTick == null && isBlankPlaceholderCel(probeLayer)) {
                    val pLayers = probeFrame.layers.toMutableList()
                    if (probeDur <= remainingToAbsorb) {
                        pLayers[layerIndex] = probeLayer.copy(celHoldTicks = 0)
                        remainingToAbsorb -= probeDur
                    } else {
                        pLayers[layerIndex] = probeLayer.copy(celHoldTicks = probeDur - remainingToAbsorb)
                        remainingToAbsorb = 0
                    }
                    mutableFrames[probeIdx] = probeFrame.copy(layers = pLayers)
                    probeIdx++
                } else {
                    break
                }
            }
        }

        return project.copy(frames = mutableFrames, updatedAt = System.currentTimeMillis())
    }

    /**
     * Moves a single cel `(layerIndex, frameIndex)` along its own timeline layer track to [targetTick]
     * without affecting any other timeline layer.
     */
    fun moveLayerCelToTick(
        project: AnimationProject,
        layerIndex: Int,
        frameIndex: Int,
        targetTick: Int
    ): AnimationProject {
        val frames = project.frames
        if (frameIndex !in frames.indices) return project
        val mutableFrames = frames.map { materializeFrameLayerDurations(it) }.toMutableList()
        val curCels = computeLayerCels(project.copy(frames = mutableFrames), layerIndex)
        val celPos = curCels.indexOfFirst { it.frameIndex == frameIndex }
        if (celPos == -1) return project

        val movingCel = curCels[celPos]
        val prevEndTick = if (celPos > 0) curCels[celPos - 1].endTickExclusive else 0
        val clampedStart = targetTick.coerceIn(prevEndTick, 4096)

        val fObj = mutableFrames[frameIndex]
        val lObj = fObj.layers[layerIndex]
        val updatedLayers = fObj.layers.toMutableList()
        updatedLayers[layerIndex] = lObj.copy(
            celStartTick = clampedStart,
            celHoldTicks = (lObj.celHoldTicks ?: movingCel.durationTicks).coerceAtLeast(1)
        )
        mutableFrames[frameIndex] = fObj.copy(layers = updatedLayers)

        // Ensure subsequent cels on this layer preserve relative order without overlapping
        var minNextStart = clampedStart + movingCel.durationTicks
        for (i in (celPos + 1) until curCels.size) {
            val nextCel = curCels[i]
            val nf = mutableFrames[nextCel.frameIndex]
            val nl = nf.layers[layerIndex]
            val curStart = nextCel.startTick
            if (curStart < minNextStart) {
                val nLayers = nf.layers.toMutableList()
                nLayers[layerIndex] = nl.copy(celStartTick = minNextStart)
                mutableFrames[nextCel.frameIndex] = nf.copy(layers = nLayers)
                minNextStart += nextCel.durationTicks
            } else {
                break
            }
        }

        return project.copy(frames = mutableFrames, updatedAt = System.currentTimeMillis())
    }

    /**
     * Ensures there is an active editable cel on [layerIndex] covering [tick].
     * Returns `(updatedProject, celFrameIndex)` without touching other layers' timing.
     */
    fun ensureCelAtTick(
        project: AnimationProject,
        layerIndex: Int,
        tick: Int
    ): Pair<AnimationProject, Int> {
        val safeTick = tick.coerceAtLeast(0)
        resolveCelAtTick(project, layerIndex, safeTick)?.let { return project to it.frameIndex }

        val mutableFrames = project.frames.map { materializeFrameLayerDurations(it) }.toMutableList()
        val templateLayers = mutableFrames.firstOrNull()?.layers ?: listOf(Layer(name = "Camada 1"))
        val safeLayerIdx = layerIndex.coerceIn(0, templateLayers.lastIndex)

        // Reuse an absorbed blank slot if one is unused after prior cels, otherwise append a new frame slot
        val cels = computeLayerCels(project.copy(frames = mutableFrames), safeLayerIdx)
        val lastActiveFrameIdx = cels.lastOrNull()?.frameIndex ?: -1
        val reusableIdx = mutableFrames.indices.firstOrNull { fIdx ->
            fIdx > lastActiveFrameIdx &&
                (mutableFrames[fIdx].layers.getOrNull(safeLayerIdx)?.celHoldTicks ?: 1) <= 0
        }

        if (reusableIdx != null) {
            val rf = mutableFrames[reusableIdx]
            val rLayers = rf.layers.toMutableList()
            rLayers[safeLayerIdx] = rLayers[safeLayerIdx].copy(
                celHoldTicks = 1,
                celStartTick = safeTick
            )
            mutableFrames[reusableIdx] = rf.copy(layers = rLayers)
            return project.copy(frames = mutableFrames, updatedAt = System.currentTimeMillis()) to reusableIdx
        }

        val newFrameLayers = templateLayers.mapIndexed { idx, l ->
            l.copy(
                id = UUID.randomUUID().toString(),
                strokes = emptyList(),
                imageAttachment = null,
                celHoldTicks = if (idx == safeLayerIdx) 1 else 0,
                celStartTick = if (idx == safeLayerIdx) safeTick else null
            )
        }
        val newIdx = mutableFrames.size
        mutableFrames.add(Frame(layers = newFrameLayers, holdFrames = 1))
        return project.copy(frames = mutableFrames, updatedAt = System.currentTimeMillis()) to newIdx
    }

    fun tickToTimeMs(tick: Int, fps: Int): Long {
        val safeFps = max(1, fps)
        return (tick.coerceAtLeast(0).toLong() * 1000L) / safeFps
    }

    fun timeMsToTickFloat(timeMs: Long, fps: Int): Float {
        val safeFps = max(1, fps)
        return ((timeMs.coerceAtLeast(0L).toDouble() * safeFps.toDouble()) / 1000.0).toFloat()
    }
}
