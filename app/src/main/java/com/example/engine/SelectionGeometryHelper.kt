package com.example.engine

import com.example.model.DrawingStroke
import com.example.model.SelectionRegion
import com.example.model.StrokePoint
import com.example.model.ToolType
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class SelectionSplitResult(
    val capturedInside: List<DrawingStroke>,
    val stationaryOutside: List<DrawingStroke>,
    val affectedOriginalIds: Set<String>
)

/**
 * Exact 2D polygon-clipping and stroke-splitting geometry engine for Lasso & Magic Wand tools.
 * Splits strokes cleanly at polygon boundaries so moving/transforming/deleting a selection
 * moves ONLY the content inside the selection boundary without leaving duplicates underneath
 * or pulling stroke segments outside the boundary.
 */
object SelectionGeometryHelper {

    fun isPointInPolygon(x: Float, y: Float, poly: List<StrokePoint>): Boolean {
        if (poly.size < 3) return false
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val pi = poly[i]
            val pj = poly[j]
            if ((pi.y > y) != (pj.y > y)) {
                val denom = (pj.y - pi.y)
                val xCross = (pj.x - pi.x) * (y - pi.y) / (if (abs(denom) < 1e-5f) 1e-5f else denom) + pi.x
                if (x < xCross) {
                    inside = !inside
                }
            }
            j = i
        }
        return inside
    }

    fun computePolygonRowIntersections(y: Float, poly: List<StrokePoint>): List<Float> {
        if (poly.size < 3) return emptyList()
        val xs = ArrayList<Float>(8)
        var j = poly.size - 1
        for (i in poly.indices) {
            val pi = poly[i]
            val pj = poly[j]
            if ((pi.y > y) != (pj.y > y)) {
                val denom = pj.y - pi.y
                if (abs(denom) > 1e-6f) {
                    val xCross = (pj.x - pi.x) * (y - pi.y) / denom + pi.x
                    xs.add(xCross)
                }
            }
            j = i
        }
        xs.sort()
        return xs
    }

    private fun computeBounds(points: List<StrokePoint>): FloatArray {
        if (points.isEmpty()) return floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    fun withRecomputedBounds(stroke: DrawingStroke): DrawingStroke {
        val b = computeBounds(stroke.points)
        return stroke.copy(minX = b[0], minY = b[1], maxX = b[2], maxY = b[3])
    }

    fun transformSelectionPolygon(sel: SelectionRegion): List<StrokePoint> {
        if (sel.points.isEmpty()) return emptyList()
        if (sel.offsetX == 0f && sel.offsetY == 0f && sel.scaleX == 1f && sel.scaleY == 1f && sel.rotationDegrees == 0f) {
            return sel.points
        }
        val cx = sel.centerX
        val cy = sel.centerY
        val rad = Math.toRadians(sel.rotationDegrees.toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()
        return sel.points.map { pt ->
            val dx = (pt.x - cx) * sel.scaleX
            val dy = (pt.y - cy) * sel.scaleY
            val rx = dx * cosR - dy * sinR
            val ry = dx * sinR + dy * cosR
            pt.copy(x = rx + cx + sel.offsetX, y = ry + cy + sel.offsetY)
        }
    }

    fun transformStrokeWithSelection(
        stroke: DrawingStroke,
        sel: SelectionRegion,
        authorIdOverride: String? = null
    ): DrawingStroke {
        val cx = sel.centerX
        val cy = sel.centerY
        val rad = Math.toRadians(sel.rotationDegrees.toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()

        fun mapPt(pt: StrokePoint): StrokePoint {
            val dx = (pt.x - cx) * sel.scaleX
            val dy = (pt.y - cy) * sel.scaleY
            val rx = dx * cosR - dy * sinR
            val ry = dx * sinR + dy * cosR
            return pt.copy(x = rx + cx + sel.offsetX, y = ry + cy + sel.offsetY)
        }

        val newPts = stroke.points.map { mapPt(it) }
        val newClip = stroke.clipPolygon?.map { mapPt(it) }
        return withRecomputedBounds(
            stroke.copy(
                id = UUID.randomUUID().toString(),
                authorId = authorIdOverride ?: stroke.authorId,
                points = newPts,
                clipPolygon = newClip
            )
        )
    }

    /**
     * Splits a polyline cleanly at all polygon boundary intersections.
     * Returns Pair(insidePolylines, outsidePolylines).
     */
    fun clipPolylineByPolygon(
        points: List<StrokePoint>,
        poly: List<StrokePoint>
    ): Pair<List<List<StrokePoint>>, List<List<StrokePoint>>> {
        if (points.isEmpty() || poly.size < 3) return Pair(emptyList(), emptyList())
        if (points.size == 1) {
            val p = points[0]
            return if (isPointInPolygon(p.x, p.y, poly)) {
                Pair(listOf(points), emptyList())
            } else {
                Pair(emptyList(), listOf(points))
            }
        }

        val insideRuns = ArrayList<List<StrokePoint>>()
        val outsideRuns = ArrayList<List<StrokePoint>>()
        var curIn = ArrayList<StrokePoint>()
        var curOut = ArrayList<StrokePoint>()

        fun flushIn() {
            if (curIn.size >= 2) {
                insideRuns.add(curIn)
            }
            curIn = ArrayList()
        }

        fun flushOut() {
            if (curOut.size >= 2) {
                outsideRuns.add(curOut)
            }
            curOut = ArrayList()
        }

        fun appendToRun(run: ArrayList<StrokePoint>, a: StrokePoint, b: StrokePoint) {
            if (run.isEmpty()) {
                run.add(a)
                run.add(b)
            } else {
                val last = run.last()
                if (abs(last.x - a.x) > 0.05f || abs(last.y - a.y) > 0.05f) {
                    // Break discontinuous sub-run
                    if (run === curIn) {
                        flushIn()
                        curIn.add(a)
                        curIn.add(b)
                    } else {
                        flushOut()
                        curOut.add(a)
                        curOut.add(b)
                    }
                } else {
                    run.add(b)
                }
            }
        }

        val tBuffer = ArrayList<Float>(16)
        for (i in 0 until points.lastIndex) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val dx = p1.x - p0.x
            val dy = p1.y - p0.y

            tBuffer.clear()
            tBuffer.add(0f)
            tBuffer.add(1f)

            var pjIdx = poly.lastIndex
            for (piIdx in poly.indices) {
                val a = poly[piIdx]
                val b = poly[pjIdx]
                val ex = b.x - a.x
                val ey = b.y - a.y
                val denom = dx * ey - dy * ex
                if (abs(denom) > 1e-6f) {
                    val rx = a.x - p0.x
                    val ry = a.y - p0.y
                    val t = (rx * ey - ry * ex) / denom
                    val u = (rx * dy - ry * dx) / denom
                    if (t > 1e-4f && t < 1f - 1e-4f && u >= -1e-4f && u <= 1f + 1e-4f) {
                        tBuffer.add(t)
                    }
                }
                pjIdx = piIdx
            }

            tBuffer.sort()
            var prevT = tBuffer[0]
            for (k in 1 until tBuffer.size) {
                val nextT = tBuffer[k]
                if (nextT - prevT < 1e-4f) continue
                val midT = (prevT + nextT) * 0.5f
                val mx = p0.x + dx * midT
                val my = p0.y + dy * midT

                val sa = StrokePoint(
                    x = p0.x + dx * prevT,
                    y = p0.y + dy * prevT,
                    pressure = p0.pressure + (p1.pressure - p0.pressure) * prevT
                )
                val sb = StrokePoint(
                    x = p0.x + dx * nextT,
                    y = p0.y + dy * nextT,
                    pressure = p0.pressure + (p1.pressure - p0.pressure) * nextT
                )

                if (isPointInPolygon(mx, my, poly)) {
                    flushOut()
                    appendToRun(curIn, sa, sb)
                } else {
                    flushIn()
                    appendToRun(curOut, sa, sb)
                }
                prevT = nextT
            }
        }
        flushIn()
        flushOut()
        return Pair(insideRuns, outsideRuns)
    }

    private fun expandShapeOutlineToPolyline(stroke: DrawingStroke): List<StrokePoint> {
        if (stroke.points.size < 2) return stroke.points
        val first = stroke.points.first()
        val last = stroke.points.last()
        return when (stroke.tool) {
            ToolType.LINE -> listOf(first, last)
            ToolType.RECTANGLE -> listOf(
                StrokePoint(min(first.x, last.x), min(first.y, last.y), first.pressure),
                StrokePoint(max(first.x, last.x), min(first.y, last.y), first.pressure),
                StrokePoint(max(first.x, last.x), max(first.y, last.y), last.pressure),
                StrokePoint(min(first.x, last.x), max(first.y, last.y), last.pressure),
                StrokePoint(min(first.x, last.x), min(first.y, last.y), first.pressure)
            )
            ToolType.CIRCLE -> {
                val left = min(first.x, last.x)
                val top = min(first.y, last.y)
                val right = max(first.x, last.x)
                val bottom = max(first.y, last.y)
                val cx = (left + right) * 0.5f
                val cy = (top + bottom) * 0.5f
                val rx = (right - left) * 0.5f
                val ry = (bottom - top) * 0.5f
                List(37) { idx ->
                    val ang = idx * (2.0 * Math.PI / 36.0)
                    StrokePoint(
                        x = cx + (rx * cos(ang)).toFloat(),
                        y = cy + (ry * sin(ang)).toFloat(),
                        pressure = first.pressure
                    )
                }
            }
            else -> stroke.points
        }
    }

    fun splitStrokesByPolygon(
        strokes: List<DrawingStroke>,
        poly: List<StrokePoint>
    ): SelectionSplitResult {
        if (strokes.isEmpty() || poly.size < 3) {
            return SelectionSplitResult(emptyList(), emptyList(), emptySet())
        }
        val polyBounds = computeBounds(poly)
        val pMinX = polyBounds[0]
        val pMinY = polyBounds[1]
        val pMaxX = polyBounds[2]
        val pMaxY = polyBounds[3]

        val capturedInside = ArrayList<DrawingStroke>()
        val stationaryOutside = ArrayList<DrawingStroke>()
        val affectedIds = LinkedHashSet<String>()

        for (stroke in strokes) {
            if (!stroke.intersects(pMinX - 2f, pMinY - 2f, pMaxX + 2f, pMaxY + 2f)) {
                continue
            }

            if (stroke.tool == ToolType.BUCKET) {
                val inSpans = ArrayList<StrokePoint>()
                val outSpans = ArrayList<StrokePoint>()
                val pairCount = (stroke.points.size / 2) * 2
                var i = 0
                while (i < pairCount) {
                    val p0 = stroke.points[i]
                    val p1 = stroke.points[i + 1]
                    val y = p0.y
                    val left = min(p0.x, p1.x)
                    val right = max(p0.x, p1.x)
                    if (y < pMinY || y > pMaxY || right < pMinX || left > pMaxX) {
                        outSpans.add(p0)
                        outSpans.add(p1)
                    } else {
                        val crossings = computePolygonRowIntersections(y, poly)
                        val cuts = ArrayList<Float>(crossings.size + 2)
                        cuts.add(left)
                        for (cx in crossings) {
                            if (cx > left + 0.05f && cx < right - 0.05f) cuts.add(cx)
                        }
                        cuts.add(right)
                        for (cIdx in 0 until cuts.lastIndex) {
                            val sx = cuts[cIdx]
                            val ex = cuts[cIdx + 1]
                            if (ex - sx < 0.01f) continue
                            val midX = (sx + ex) * 0.5f
                            if (isPointInPolygon(midX, y, poly)) {
                                inSpans.add(StrokePoint(sx, y, p0.pressure))
                                inSpans.add(StrokePoint(ex, y, p1.pressure))
                            } else {
                                outSpans.add(StrokePoint(sx, y, p0.pressure))
                                outSpans.add(StrokePoint(ex, y, p1.pressure))
                            }
                        }
                    }
                    i += 2
                }
                if (inSpans.isNotEmpty()) {
                    affectedIds.add(stroke.id)
                    capturedInside.add(
                        withRecomputedBounds(stroke.copy(id = UUID.randomUUID().toString(), points = inSpans))
                    )
                    if (outSpans.isNotEmpty()) {
                        stationaryOutside.add(
                            withRecomputedBounds(stroke.copy(id = UUID.randomUUID().toString(), points = outSpans))
                        )
                    }
                }
                continue
            }

            if ((stroke.tool == ToolType.RECTANGLE || stroke.tool == ToolType.CIRCLE || stroke.tool == ToolType.LASSO) && stroke.isFilled) {
                val polyCenterHit = stroke.points.any { isPointInPolygon(it.x, it.y, poly) } ||
                    poly.any { it.x in stroke.minX..stroke.maxX && it.y in stroke.minY..stroke.maxY }
                if (polyCenterHit) {
                    val allInside = stroke.points.isNotEmpty() && stroke.points.all { isPointInPolygon(it.x, it.y, poly) }
                    affectedIds.add(stroke.id)
                    if (allInside) {
                        capturedInside.add(stroke.copy(id = UUID.randomUUID().toString()))
                    } else {
                        capturedInside.add(stroke.copy(id = UUID.randomUUID().toString(), clipPolygon = poly))
                    }
                }
                continue
            }

            val sourcePts = when (stroke.tool) {
                ToolType.LINE, ToolType.RECTANGLE, ToolType.CIRCLE -> expandShapeOutlineToPolyline(stroke)
                else -> stroke.points
            }
            val effectiveTool = when (stroke.tool) {
                ToolType.LINE, ToolType.RECTANGLE, ToolType.CIRCLE -> ToolType.PEN
                else -> stroke.tool
            }

            val (insideRuns, outsideRuns) = clipPolylineByPolygon(sourcePts, poly)
            if (insideRuns.isEmpty()) {
                continue
            }

            affectedIds.add(stroke.id)
            if (outsideRuns.isEmpty() && stroke.tool !in listOf(ToolType.LINE, ToolType.RECTANGLE, ToolType.CIRCLE)) {
                capturedInside.add(withRecomputedBounds(stroke.copy(id = UUID.randomUUID().toString())))
            } else {
                for (run in insideRuns) {
                    capturedInside.add(
                        withRecomputedBounds(
                            stroke.copy(
                                id = UUID.randomUUID().toString(),
                                tool = effectiveTool,
                                points = run
                            )
                        )
                    )
                }
                for (run in outsideRuns) {
                    stationaryOutside.add(
                        withRecomputedBounds(
                            stroke.copy(
                                id = UUID.randomUUID().toString(),
                                tool = effectiveTool,
                                points = run
                            )
                        )
                    )
                }
            }
        }

        return SelectionSplitResult(
            capturedInside = capturedInside,
            stationaryOutside = stationaryOutside,
            affectedOriginalIds = affectedIds
        )
    }

    /**
     * Splits strokes according to active selection polarity.
     * When [isInverted] is false, captures strokes inside [poly].
     * When [isInverted] is true, protects content inside [poly] and captures everything outside [poly].
     */
    fun splitStrokesBySelection(
        strokes: List<DrawingStroke>,
        poly: List<StrokePoint>,
        isInverted: Boolean,
        boundsWidth: Float = 1920f,
        boundsHeight: Float = 1080f
    ): SelectionSplitResult {
        if (!isInverted) return splitStrokesByPolygon(strokes, poly)
        if (strokes.isEmpty() || poly.size < 3) {
            return SelectionSplitResult(emptyList(), emptyList(), emptySet())
        }

        val polyBounds = computeBounds(poly)
        val pMinX = polyBounds[0]
        val pMinY = polyBounds[1]
        val pMaxX = polyBounds[2]
        val pMaxY = polyBounds[3]

        val capturedSelected = ArrayList<DrawingStroke>()
        val stationaryProtected = ArrayList<DrawingStroke>()
        val affectedIds = LinkedHashSet<String>()

        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            if (!stroke.intersects(pMinX - 2f, pMinY - 2f, pMaxX + 2f, pMaxY + 2f)) {
                // Entire stroke is outside the polygon -> belongs to inverted selection!
                affectedIds.add(stroke.id)
                capturedSelected.add(withRecomputedBounds(stroke.copy(id = UUID.randomUUID().toString())))
                continue
            }

            if (stroke.tool == ToolType.BUCKET) {
                val inSpans = ArrayList<StrokePoint>()
                val outSpans = ArrayList<StrokePoint>()
                val pairCount = (stroke.points.size / 2) * 2
                var i = 0
                while (i < pairCount) {
                    val p0 = stroke.points[i]
                    val p1 = stroke.points[i + 1]
                    val y = p0.y
                    val left = min(p0.x, p1.x)
                    val right = max(p0.x, p1.x)
                    if (y < pMinY || y > pMaxY || right < pMinX || left > pMaxX) {
                        outSpans.add(p0)
                        outSpans.add(p1)
                    } else {
                        val crossings = computePolygonRowIntersections(y, poly)
                        val cuts = ArrayList<Float>(crossings.size + 2)
                        cuts.add(left)
                        for (cx in crossings) {
                            if (cx > left + 0.05f && cx < right - 0.05f) cuts.add(cx)
                        }
                        cuts.add(right)
                        for (cIdx in 0 until cuts.lastIndex) {
                            val sx = cuts[cIdx]
                            val ex = cuts[cIdx + 1]
                            if (ex - sx < 0.01f) continue
                            val midX = (sx + ex) * 0.5f
                            if (isPointInPolygon(midX, y, poly)) {
                                inSpans.add(StrokePoint(sx, y, p0.pressure))
                                inSpans.add(StrokePoint(ex, y, p1.pressure))
                            } else {
                                outSpans.add(StrokePoint(sx, y, p0.pressure))
                                outSpans.add(StrokePoint(ex, y, p1.pressure))
                            }
                        }
                    }
                    i += 2
                }
                if (outSpans.isNotEmpty()) {
                    affectedIds.add(stroke.id)
                    capturedSelected.add(
                        withRecomputedBounds(stroke.copy(id = UUID.randomUUID().toString(), points = outSpans))
                    )
                    if (inSpans.isNotEmpty()) {
                        stationaryProtected.add(
                            withRecomputedBounds(stroke.copy(id = UUID.randomUUID().toString(), points = inSpans))
                        )
                    }
                }
                continue
            }

            if ((stroke.tool == ToolType.RECTANGLE || stroke.tool == ToolType.CIRCLE || stroke.tool == ToolType.LASSO) && stroke.isFilled) {
                val allInside = stroke.points.isNotEmpty() && stroke.points.all { isPointInPolygon(it.x, it.y, poly) }
                if (!allInside) {
                    affectedIds.add(stroke.id)
                    capturedSelected.add(
                        stroke.copy(
                            id = UUID.randomUUID().toString(),
                            clipPolygon = poly,
                            clipInverted = true,
                            clipBoundsWidth = boundsWidth,
                            clipBoundsHeight = boundsHeight
                        )
                    )
                }
                continue
            }

            val sourcePts = when (stroke.tool) {
                ToolType.LINE, ToolType.RECTANGLE, ToolType.CIRCLE -> expandShapeOutlineToPolyline(stroke)
                else -> stroke.points
            }
            val effectiveTool = when (stroke.tool) {
                ToolType.LINE, ToolType.RECTANGLE, ToolType.CIRCLE -> ToolType.PEN
                else -> stroke.tool
            }

            val (insideRuns, outsideRuns) = clipPolylineByPolygon(sourcePts, poly)
            if (outsideRuns.isEmpty()) {
                continue
            }

            affectedIds.add(stroke.id)
            if (insideRuns.isEmpty() && stroke.tool !in listOf(ToolType.LINE, ToolType.RECTANGLE, ToolType.CIRCLE)) {
                capturedSelected.add(withRecomputedBounds(stroke.copy(id = UUID.randomUUID().toString())))
            } else {
                for (run in outsideRuns) {
                    capturedSelected.add(
                        withRecomputedBounds(
                            stroke.copy(
                                id = UUID.randomUUID().toString(),
                                tool = effectiveTool,
                                points = run
                            )
                        )
                    )
                }
                for (run in insideRuns) {
                    stationaryProtected.add(
                        withRecomputedBounds(
                            stroke.copy(
                                id = UUID.randomUUID().toString(),
                                tool = effectiveTool,
                                points = run
                            )
                        )
                    )
                }
            }
        }

        return SelectionSplitResult(
            capturedInside = capturedSelected,
            stationaryOutside = stationaryProtected,
            affectedOriginalIds = affectedIds
        )
    }
}
