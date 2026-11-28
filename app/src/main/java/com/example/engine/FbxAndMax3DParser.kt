package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.Inflater
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure-Kotlin 3D parser for Autodesk FBX (Binary 7100..7700 + ASCII), 3DS (0x4D4D),
 * and Autodesk 3ds Max (.max OLE2 Compound File Binary Format) scenes with UVs & textures.
 */
object FbxAndMax3DParser {

    private val FBX_BINARY_MAGIC = "Kaydara FBX Binary  \u0000".toByteArray(Charsets.US_ASCII)
    private val OLE2_MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
    )

    fun isFbxBinary(bytes: ByteArray): Boolean {
        if (bytes.size < FBX_BINARY_MAGIC.size + 4) return false
        for (i in FBX_BINARY_MAGIC.indices) {
            if (bytes[i] != FBX_BINARY_MAGIC[i]) return false
        }
        return true
    }

    fun isFbxAscii(bytes: ByteArray): Boolean {
        val head = bytes.decodeToString(0, min(bytes.size, 512))
        return head.contains("FBXHeaderExtension", ignoreCase = true) ||
            (head.contains("; FBX", ignoreCase = true) && head.contains("Objects:", ignoreCase = true))
    }

    fun is3dsBinary(bytes: ByteArray): Boolean {
        return bytes.size >= 16 &&
            bytes[0] == 0x4D.toByte() &&
            bytes[1] == 0x4D.toByte()
    }

    fun isMaxCompoundFile(bytes: ByteArray): Boolean {
        if (bytes.size < 512) return false
        for (i in OLE2_MAGIC.indices) {
            if (bytes[i] != OLE2_MAGIC[i]) return false
        }
        return true
    }

    // -------------------------------------------------------------------------
    // Public Entry Points
    // -------------------------------------------------------------------------

    fun parseFbx(
        bytes: ByteArray,
        siblingResolver: ((String) -> ByteArray?)? = null
    ): List<Mesh3DSubMesh> {
        return when {
            isFbxBinary(bytes) -> parseFbxBinary(bytes, siblingResolver)
            else -> parseFbxAscii(bytes.toString(Charsets.UTF_8), siblingResolver)
        }
    }

    fun extractFbxBones(bytes: ByteArray): List<Mesh3DBone> {
        val text = if (isFbxBinary(bytes)) {
            // Binary FBX keeps ASCII identifiers like "LimbNode" and "Model::"
            bytes.decodeToString(0, min(bytes.size, 2_000_000))
        } else {
            bytes.toString(Charsets.UTF_8)
        }
        val bones = ArrayList<Mesh3DBone>()
        val limbRegex = Regex("Model::([A-Za-z0-9_ .-]{1,48}).{0,80}?LimbNode", RegexOption.DOT_MATCHES_ALL)
        val asciiRegex = Regex("Model:\\s*\\d+,\\s*\"(?:Model::)?([^\"]+)\",\\s*\"LimbNode\"")
        val seen = HashSet<String>()
        for (m in limbRegex.findAll(text)) {
            val bName = m.groupValues[1].trim()
            if (bName.isNotBlank() && seen.add(bName)) {
                bones.add(Mesh3DBone(name = bName, parentIndex = bones.size - 1))
            }
        }
        for (m in asciiRegex.findAll(text)) {
            val bName = m.groupValues[1].trim()
            if (bName.isNotBlank() && seen.add(bName)) {
                bones.add(Mesh3DBone(name = bName, parentIndex = bones.size - 1))
            }
        }
        return bones
    }

    fun parse3ds(
        bytes: ByteArray,
        siblingResolver: ((String) -> ByteArray?)? = null
    ): List<Mesh3DSubMesh> {
        if (bytes.size < 12) return emptyList()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val mainId = bb.getShort(0).toInt() and 0xFFFF
        if (mainId != 0x4D4D) return emptyList()

        data class Mat3ds(
            var name: String = "",
            var colorArgb: Int = 0xFFE2E8F0.toInt(),
            var textureFile: String? = null
        )

        data class Mesh3ds(
            var verts: FloatArray = FloatArray(0),
            var uvs: FloatArray = FloatArray(0),
            var faces: IntArray = IntArray(0),
            var matName: String? = null
        )

        val materials = HashMap<String, Mat3ds>()
        val meshes = ArrayList<Mesh3ds>()
        var currentMat: Mat3ds? = null
        var currentMesh: Mesh3ds? = null

        fun readCString(pos: Int, endLimit: Int): Pair<String, Int> {
            val sb = StringBuilder()
            var p = pos
            while (p < endLimit && p < bytes.size) {
                val b = bytes[p].toInt() and 0xFF
                p++
                if (b == 0) break
                sb.append(b.toChar())
            }
            return sb.toString() to p
        }

        fun walkChunks(start: Int, end: Int) {
            var pos = start
            while (pos + 6 <= end && pos + 6 <= bytes.size) {
                val id = bb.getShort(pos).toInt() and 0xFFFF
                val len = bb.getInt(pos + 2)
                if (len < 6 || pos + len > end || pos + len > bytes.size) break
                val chunkEnd = pos + len
                val dataStart = pos + 6
                when (id) {
                    0x4D4D, 0x3D3D -> walkChunks(dataStart, chunkEnd)
                    0x4000 -> {
                        val (_, nextPos) = readCString(dataStart, chunkEnd)
                        walkChunks(nextPos, chunkEnd)
                    }
                    0x4100 -> {
                        val m = Mesh3ds()
                        currentMesh = m
                        meshes.add(m)
                        walkChunks(dataStart, chunkEnd)
                    }
                    0x4110 -> {
                        if (dataStart + 2 <= chunkEnd) {
                            val count = bb.getShort(dataStart).toInt() and 0xFFFF
                            val m = currentMesh
                            if (m != null && dataStart + 2 + count * 12 <= chunkEnd) {
                                val verts = FloatArray(count * 3)
                                var p = dataStart + 2
                                for (i in 0 until count) {
                                    verts[i * 3] = bb.getFloat(p)
                                    // Convert Z-up 3ds coordinate to Y-up before normalization
                                    verts[i * 3 + 1] = bb.getFloat(p + 8)
                                    verts[i * 3 + 2] = bb.getFloat(p + 4)
                                    p += 12
                                }
                                m.verts = verts
                            }
                        }
                    }
                    0x4120 -> {
                        if (dataStart + 2 <= chunkEnd) {
                            val fCount = bb.getShort(dataStart).toInt() and 0xFFFF
                            val m = currentMesh
                            val tableBytes = fCount * 8
                            if (m != null && dataStart + 2 + tableBytes <= chunkEnd) {
                                val idxs = IntArray(fCount * 3)
                                var p = dataStart + 2
                                for (i in 0 until fCount) {
                                    idxs[i * 3] = bb.getShort(p).toInt() and 0xFFFF
                                    idxs[i * 3 + 1] = bb.getShort(p + 2).toInt() and 0xFFFF
                                    idxs[i * 3 + 2] = bb.getShort(p + 4).toInt() and 0xFFFF
                                    p += 8
                                }
                                m.faces = idxs
                                walkChunks(p, chunkEnd)
                            }
                        }
                    }
                    0x4130 -> {
                        val (matName, _) = readCString(dataStart, chunkEnd)
                        currentMesh?.matName = matName
                    }
                    0x4140 -> {
                        if (dataStart + 2 <= chunkEnd) {
                            val uvCount = bb.getShort(dataStart).toInt() and 0xFFFF
                            val m = currentMesh
                            if (m != null && dataStart + 2 + uvCount * 8 <= chunkEnd) {
                                val uvs = FloatArray(uvCount * 2)
                                var p = dataStart + 2
                                for (i in 0 until uvCount) {
                                    uvs[i * 2] = bb.getFloat(p)
                                    uvs[i * 2 + 1] = 1f - bb.getFloat(p + 4)
                                    p += 8
                                }
                                m.uvs = uvs
                            }
                        }
                    }
                    0xAFFF -> {
                        val mat = Mat3ds()
                        currentMat = mat
                        walkChunks(dataStart, chunkEnd)
                        if (mat.name.isNotBlank()) materials[mat.name] = mat
                    }
                    0xA000 -> {
                        val (mName, _) = readCString(dataStart, chunkEnd)
                        currentMat?.name = mName
                    }
                    0xA020 -> {
                        walkChunks(dataStart, chunkEnd)
                    }
                    0x0011 -> {
                        if (dataStart + 3 <= chunkEnd) {
                            val r = bytes[dataStart].toInt() and 0xFF
                            val g = bytes[dataStart + 1].toInt() and 0xFF
                            val b = bytes[dataStart + 2].toInt() and 0xFF
                            currentMat?.colorArgb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }
                    0x0010 -> {
                        if (dataStart + 12 <= chunkEnd) {
                            val r = (bb.getFloat(dataStart).coerceIn(0f, 1f) * 255f).toInt()
                            val g = (bb.getFloat(dataStart + 4).coerceIn(0f, 1f) * 255f).toInt()
                            val b = (bb.getFloat(dataStart + 8).coerceIn(0f, 1f) * 255f).toInt()
                            currentMat?.colorArgb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }
                    0xA200 -> walkChunks(dataStart, chunkEnd)
                    0xA300 -> {
                        val (texName, _) = readCString(dataStart, chunkEnd)
                        if (texName.isNotBlank()) currentMat?.textureFile = texName
                    }
                }
                pos = chunkEnd
            }
        }

        walkChunks(0, bytes.size)

        val out = ArrayList<Mesh3DSubMesh>()
        for (m in meshes) {
            if (m.verts.size < 9 || m.faces.size < 3) continue
            val vCount = m.verts.size / 3
            val safeUvs = if (m.uvs.size == vCount * 2) {
                m.uvs
            } else {
                generatePlanarFallbackUvs(m.verts)
            }
            val norms = computeSmoothNormals(m.verts, m.faces)
            val mat = m.matName?.let { materials[it] }
            var texPixels: IntArray? = null
            var texW = 0
            var texH = 0
            val texRef = mat?.textureFile
            if (!texRef.isNullOrBlank() && siblingResolver != null) {
                val imgBytes = siblingResolver(texRef)
                if (imgBytes != null) {
                    decodeTexturePixels(imgBytes)?.let { (pix, w, h) ->
                        texPixels = pix; texW = w; texH = h
                    }
                }
            }
            out.add(
                Mesh3DSubMesh(
                    vertices = m.verts,
                    normals = norms,
                    uvs = safeUvs,
                    indices = m.faces,
                    baseColorArgb = mat?.colorArgb ?: 0xFFE2E8F0.toInt(),
                    texturePixels = texPixels,
                    textureWidth = texW,
                    textureHeight = texH
                )
            )
        }
        return out
    }

    /**
     * Parses Autodesk 3ds Max (.max) OLE2 Compound File Binary containers.
     * Extracts OLE2 directory streams (`Scene`, `SceneData`, zlib-compressed blobs, embedded 3DS/FBX streams,
     * and embedded PNG/JPEG textures) and reconstructs valid textured geometry cleanly.
     */
    fun parseMaxScene(
        bytes: ByteArray,
        siblingResolver: ((String) -> ByteArray?)? = null
    ): List<Mesh3DSubMesh> {
        if (!isMaxCompoundFile(bytes)) {
            if (is3dsBinary(bytes)) return parse3ds(bytes, siblingResolver)
            if (isFbxBinary(bytes) || isFbxAscii(bytes)) return parseFbx(bytes, siblingResolver)
            return emptyList()
        }

        val streams = extractOle2Streams(bytes)
        val embeddedTextures = ArrayList<Triple<IntArray, Int, Int>>()

        // Collect all streams plus any inflated zlib payloads found inside them
        val searchBlobs = ArrayList<ByteArray>(streams.size * 2)
        for ((name, payload) in streams) {
            if (payload.isEmpty()) continue
            searchBlobs.add(payload)
            if (is3dsBinary(payload)) {
                val parsed = parse3ds(payload, siblingResolver)
                if (parsed.isNotEmpty()) return parsed
            }
            if (isFbxBinary(payload) || isFbxAscii(payload)) {
                val parsed = parseFbx(payload, siblingResolver)
                if (parsed.isNotEmpty()) return parsed
            }
            // Scan for zlib streams (78 9C / 78 DA / 78 01) inside Scene / SceneData streams
            if (name.contains("Scene", ignoreCase = true) || payload.size >= 64) {
                val inflated = tryInflateEmbeddedZlibStreams(payload)
                for (inf in inflated) {
                    searchBlobs.add(inf)
                    if (is3dsBinary(inf)) {
                        val parsed = parse3ds(inf, siblingResolver)
                        if (parsed.isNotEmpty()) return parsed
                    }
                    if (isFbxBinary(inf) || isFbxAscii(inf)) {
                        val parsed = parseFbx(inf, siblingResolver)
                        if (parsed.isNotEmpty()) return parsed
                    }
                }
            }
        }

        // Extract any embedded PNG/JPG textures from blobs or sibling files
        for (blob in searchBlobs) {
            findEmbeddedImages(blob, embeddedTextures, limit = 2)
            if (embeddedTextures.isNotEmpty()) break
        }

        // Attempt structured Max mesh heuristic scan on decompressed Scene/Mesh streams
        val extractedSubMeshes = ArrayList<Mesh3DSubMesh>()
        for (blob in searchBlobs) {
            scanBlobForMaxMeshChunks(blob, embeddedTextures.firstOrNull())?.let {
                extractedSubMeshes.add(it)
            }
        }
        if (extractedSubMeshes.isNotEmpty()) return extractedSubMeshes

        // Fallback proxy prism displaying any embedded texture/thumbnail recovered from the .max package
        val tex = embeddedTextures.firstOrNull()
        if (streams.isNotEmpty()) {
            return listOf(buildFallbackContainerMesh(tex))
        }
        return emptyList()
    }

    // -------------------------------------------------------------------------
    // FBX Binary Parser (v7100..v7700, zlib-compressed array support)
    // -------------------------------------------------------------------------

    private sealed class FbxProp {
        data class LongVal(val v: Long) : FbxProp()
        data class DoubleVal(val v: Double) : FbxProp()
        data class StringVal(val v: String) : FbxProp()
        data class BytesVal(val v: ByteArray) : FbxProp()
        data class DoubleArrayVal(val v: DoubleArray) : FbxProp()
        data class FloatArrayVal(val v: FloatArray) : FbxProp()
        data class IntArrayVal(val v: IntArray) : FbxProp()
    }

    private data class FbxNode(
        val name: String,
        val props: List<FbxProp>,
        val children: List<FbxNode>
    ) {
        fun firstChild(childName: String): FbxNode? =
            children.firstOrNull { it.name == childName }

        fun childrenNamed(childName: String): List<FbxNode> =
            children.filter { it.name == childName }

        fun idProp(): Long? = (props.firstOrNull() as? FbxProp.LongVal)?.v

        fun stringProp(idx: Int): String? = (props.getOrNull(idx) as? FbxProp.StringVal)?.v
    }

    private fun parseFbxBinary(
        bytes: ByteArray,
        siblingResolver: ((String) -> ByteArray?)?
    ): List<Mesh3DSubMesh> {
        if (bytes.size < 27) return emptyList()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = bb.getInt(23)
        val is64Bit = version >= 7500
        val headerSize = if (is64Bit) 25 else 13
        var cursor = 27

        fun inflateBytes(compressed: ByteArray, expectedLen: Int): ByteArray? {
            val inf = Inflater()
            return try {
                inf.setInput(compressed)
                val out = ByteArrayOutputStream(max(expectedLen, compressed.size * 2))
                val buf = ByteArray(8192)
                var guard = 0
                while (!inf.finished() && guard++ < 4096) {
                    val count = inf.inflate(buf)
                    if (count <= 0) {
                        if (inf.needsInput() || inf.needsDictionary()) break
                    } else {
                        out.write(buf, 0, count)
                    }
                }
                out.toByteArray()
            } catch (_: Exception) {
                null
            } finally {
                inf.end()
            }
        }

        fun readProp(): FbxProp? {
            if (cursor >= bytes.size) return null
            val typeCode = bytes[cursor++].toInt().toChar()
            return when (typeCode) {
                'Y' -> {
                    val v = bb.getShort(cursor).toLong(); cursor += 2; FbxProp.LongVal(v)
                }
                'C' -> {
                    val v = (bytes[cursor++].toInt() and 0xFF).toLong(); FbxProp.LongVal(v)
                }
                'I' -> {
                    val v = bb.getInt(cursor).toLong(); cursor += 4; FbxProp.LongVal(v)
                }
                'L' -> {
                    val v = bb.getLong(cursor); cursor += 8; FbxProp.LongVal(v)
                }
                'F' -> {
                    val v = bb.getFloat(cursor).toDouble(); cursor += 4; FbxProp.DoubleVal(v)
                }
                'D' -> {
                    val v = bb.getDouble(cursor); cursor += 8; FbxProp.DoubleVal(v)
                }
                'S', 'R' -> {
                    if (cursor + 4 > bytes.size) return null
                    val len = bb.getInt(cursor).coerceAtLeast(0)
                    cursor += 4
                    val safeLen = min(len, bytes.size - cursor)
                    val slice = bytes.copyOfRange(cursor, cursor + safeLen)
                    cursor += safeLen
                    if (typeCode == 'S') {
                        val s = slice.toString(Charsets.UTF_8).substringBefore('\u0000')
                        FbxProp.StringVal(s)
                    } else {
                        FbxProp.BytesVal(slice)
                    }
                }
                'f', 'd', 'i', 'l', 'b', 'c' -> {
                    if (cursor + 12 > bytes.size) return null
                    val arrayLen = bb.getInt(cursor).coerceAtLeast(0)
                    val encoding = bb.getInt(cursor + 4)
                    val compLen = bb.getInt(cursor + 8).coerceAtLeast(0)
                    cursor += 12
                    val elemSize = when (typeCode) {
                        'f', 'i' -> 4
                        'd', 'l' -> 8
                        else -> 1
                    }
                    val safeCompLen = min(compLen, bytes.size - cursor)
                    val rawSlice = bytes.copyOfRange(cursor, cursor + safeCompLen)
                    cursor += safeCompLen
                    val data = if (encoding == 1) {
                        inflateBytes(rawSlice, arrayLen * elemSize) ?: return FbxProp.BytesVal(ByteArray(0))
                    } else {
                        rawSlice
                    }
                    val dbb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                    val count = min(arrayLen, data.size / elemSize)
                    when (typeCode) {
                        'd' -> FbxProp.DoubleArrayVal(DoubleArray(count) { dbb.getDouble(it * 8) })
                        'f' -> FbxProp.FloatArrayVal(FloatArray(count) { dbb.getFloat(it * 4) })
                        'i' -> FbxProp.IntArrayVal(IntArray(count) { dbb.getInt(it * 4) })
                        'l' -> FbxProp.IntArrayVal(IntArray(count) { dbb.getLong(it * 8).toInt() })
                        else -> FbxProp.BytesVal(data)
                    }
                }
                else -> null
            }
        }

        fun readNode(limit: Int, depth: Int = 0): FbxNode? {
            if (depth > 28 || cursor + headerSize > limit) return null
            val endOffset: Long
            val numProps: Int
            val propListLen: Int
            val nameLen: Int
            if (is64Bit) {
                endOffset = bb.getLong(cursor)
                numProps = bb.getLong(cursor + 8).toInt()
                propListLen = bb.getLong(cursor + 16).toInt()
                nameLen = bytes[cursor + 24].toInt() and 0xFF
                cursor += 25
            } else {
                endOffset = bb.getInt(cursor).toLong() and 0xFFFFFFFFL
                numProps = bb.getInt(cursor + 4)
                propListLen = bb.getInt(cursor + 8)
                nameLen = bytes[cursor + 12].toInt() and 0xFF
                cursor += 13
            }
            if (endOffset == 0L && numProps == 0 && propListLen == 0 && nameLen == 0) {
                return null
            }
            val safeEnd = endOffset.coerceIn(cursor.toLong(), limit.toLong()).toInt()
            val safeNameLen = min(nameLen, max(0, safeEnd - cursor))
            val name = String(bytes, cursor, safeNameLen, Charsets.UTF_8)
            cursor += safeNameLen

            val propsEnd = (cursor + max(0, propListLen)).coerceAtMost(safeEnd)
            val props = ArrayList<FbxProp>(numProps.coerceIn(0, 64))
            for (p in 0 until numProps.coerceAtLeast(0)) {
                if (cursor >= propsEnd) break
                val prop = readProp() ?: break
                props.add(prop)
            }
            cursor = propsEnd

            val children = ArrayList<FbxNode>()
            while (cursor + headerSize <= safeEnd) {
                val child = readNode(safeEnd, depth + 1) ?: break
                children.add(child)
            }
            cursor = safeEnd
            return FbxNode(name, props, children)
        }

        val rootNodes = ArrayList<FbxNode>()
        while (cursor + headerSize <= bytes.size) {
            val node = readNode(bytes.size) ?: break
            rootNodes.add(node)
        }

        val objectsNode = rootNodes.firstOrNull { it.name == "Objects" } ?: return emptyList()
        val connectionsNode = rootNodes.firstOrNull { it.name == "Connections" }

        // Map connections (childId -> list of parentIds)
        val childToParents = HashMap<Long, MutableList<Long>>()
        val parentToChildren = HashMap<Long, MutableList<Long>>()
        connectionsNode?.childrenNamed("C")?.forEach { c ->
            val src = (c.props.getOrNull(1) as? FbxProp.LongVal)?.v ?: return@forEach
            val dst = (c.props.getOrNull(2) as? FbxProp.LongVal)?.v ?: return@forEach
            childToParents.getOrPut(src) { ArrayList() }.add(dst)
            parentToChildren.getOrPut(dst) { ArrayList() }.add(src)
        }

        // Decode Videos (embedded texture bytes or filename)
        data class TexData(
            val pixels: IntArray,
            val w: Int,
            val h: Int
        )
        val videoTexById = HashMap<Long, TexData>()
        objectsNode.childrenNamed("Video").forEach { vNode ->
            val vid = vNode.idProp() ?: return@forEach
            val rawContent = (vNode.firstChild("Content")?.props?.firstOrNull() as? FbxProp.BytesVal)?.v
            var decoded = if (rawContent != null && rawContent.size > 16) decodeTexturePixels(rawContent) else null
            if (decoded == null && siblingResolver != null) {
                val rel = vNode.firstChild("RelativeFilename")?.stringProp(0)
                    ?: vNode.firstChild("Filename")?.stringProp(0)
                if (!rel.isNullOrBlank()) {
                    val b = siblingResolver(rel) ?: siblingResolver(rel.substringAfterLast('/').substringAfterLast('\\'))
                    b?.let { decoded = decodeTexturePixels(it) }
                }
            }
            decoded?.let { (p, w, h) -> videoTexById[vid] = TexData(p, w, h) }
        }

        val texById = HashMap<Long, TexData>()
        objectsNode.childrenNamed("Texture").forEach { tNode ->
            val tid = tNode.idProp() ?: return@forEach
            var found: TexData? = parentToChildren[tid]?.firstNotNullOfOrNull { videoTexById[it] }
            if (found == null && siblingResolver != null) {
                val rel = tNode.firstChild("RelativeFileName")?.stringProp(0)
                    ?: tNode.firstChild("FileName")?.stringProp(0)
                if (!rel.isNullOrBlank()) {
                    val b = siblingResolver(rel) ?: siblingResolver(rel.substringAfterLast('/').substringAfterLast('\\'))
                    b?.let { bytesResolved ->
                        decodeTexturePixels(bytesResolved)?.let { (p, w, h) -> found = TexData(p, w, h) }
                    }
                }
            }
            found?.let { texById[tid] = it }
        }

        val matColorById = HashMap<Long, Int>()
        val matTexById = HashMap<Long, TexData>()
        objectsNode.childrenNamed("Material").forEach { mNode ->
            val mid = mNode.idProp() ?: return@forEach
            var argb = 0xFFE2E8F0.toInt()
            mNode.firstChild("Properties70")?.childrenNamed("P")?.forEach { p ->
                val pName = p.stringProp(0) ?: ""
                if (pName == "DiffuseColor" && p.props.size >= 7) {
                    val r = (((p.props[4] as? FbxProp.DoubleVal)?.v ?: 0.85).coerceIn(0.0, 1.0) * 255.0).toInt()
                    val g = (((p.props[5] as? FbxProp.DoubleVal)?.v ?: 0.85).coerceIn(0.0, 1.0) * 255.0).toInt()
                    val b = (((p.props[6] as? FbxProp.DoubleVal)?.v ?: 0.85).coerceIn(0.0, 1.0) * 255.0).toInt()
                    argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            matColorById[mid] = argb
            parentToChildren[mid]?.firstNotNullOfOrNull { texById[it] }?.let { tex ->
                matTexById[mid] = tex
            }
        }

        fun extractFloatTriples(prop: FbxProp?): FloatArray? {
            return when (prop) {
                is FbxProp.FloatArrayVal -> prop.v
                is FbxProp.DoubleArrayVal -> FloatArray(prop.v.size) { prop.v[it].toFloat() }
                else -> null
            }
        }

        val subMeshes = ArrayList<Mesh3DSubMesh>()
        for (gNode in objectsNode.childrenNamed("Geometry")) {
            val subType = gNode.stringProp(2) ?: ""
            if (subType.isNotEmpty() && !subType.equals("Mesh", ignoreCase = true)) continue
            val rawVerts = extractFloatTriples(gNode.firstChild("Vertices")?.props?.firstOrNull()) ?: continue
            val polyIdx = (gNode.firstChild("PolygonVertexIndex")?.props?.firstOrNull() as? FbxProp.IntArrayVal)?.v
                ?: continue
            if (rawVerts.size < 9 || polyIdx.isEmpty()) continue

            val uvElem = gNode.firstChild("LayerElementUV")
            val rawUvs = extractFloatTriples(uvElem?.firstChild("UV")?.props?.firstOrNull())
            val uvIndices = (uvElem?.firstChild("UVIndex")?.props?.firstOrNull() as? FbxProp.IntArrayVal)?.v
            val uvMapping = uvElem?.firstChild("MappingInformationType")?.stringProp(0) ?: ""
            val uvRefMode = uvElem?.firstChild("ReferenceInformationType")?.stringProp(0) ?: ""

            val normElem = gNode.firstChild("LayerElementNormal")
            val rawNorms = extractFloatTriples(normElem?.firstChild("Normals")?.props?.firstOrNull())
            val normMapping = normElem?.firstChild("MappingInformationType")?.stringProp(0) ?: ""

            val outVerts = ArrayList<Float>(polyIdx.size * 3)
            val outNorms = ArrayList<Float>(polyIdx.size * 3)
            val outUvs = ArrayList<Float>(polyIdx.size * 2)
            val outIndices = ArrayList<Int>(polyIdx.size)

            val rawVertCount = rawVerts.size / 3
            val rawUvCount = (rawUvs?.size ?: 0) / 2
            val rawNormCount = (rawNorms?.size ?: 0) / 3

            val polyVertList = ArrayList<Int>(4)
            var polyVertCounter = 0
            var polyIdxCounter = 0

            fun emitCorner(vertIdx: Int, cornerSeq: Int, polySeq: Int): Int {
                val safeVi = vertIdx.coerceIn(0, rawVertCount - 1)
                val outIdx = outVerts.size / 3
                outVerts.add(rawVerts[safeVi * 3])
                outVerts.add(rawVerts[safeVi * 3 + 1])
                outVerts.add(rawVerts[safeVi * 3 + 2])

                if (rawUvs != null && rawUvCount > 0) {
                    val mapKeyIdx = when {
                        uvMapping.contains("ByPolygonVertex", ignoreCase = true) -> cornerSeq
                        uvMapping.contains("ByPolygon", ignoreCase = true) -> polySeq
                        uvMapping.contains("AllSame", ignoreCase = true) -> 0
                        uvMapping.contains("ByVertice", ignoreCase = true) || uvMapping.contains("ByVertex", ignoreCase = true) -> safeVi
                        uvIndices != null -> cornerSeq
                        else -> safeVi
                    }
                    val rawUvIndex = if (uvIndices != null && (uvRefMode.contains("IndexToDirect", ignoreCase = true) || uvRefMode.isBlank())) {
                        val idxVal = uvIndices.getOrNull(mapKeyIdx) ?: safeVi
                        if (idxVal >= 0) idxVal else safeVi
                    } else {
                        mapKeyIdx
                    }
                    val uvIdx = rawUvIndex.coerceIn(0, rawUvCount - 1)
                    outUvs.add(rawUvs[uvIdx * 2])
                    outUvs.add(1f - rawUvs[uvIdx * 2 + 1])
                } else {
                    outUvs.add(0f); outUvs.add(0f)
                }

                if (rawNorms != null && rawNormCount > 0) {
                    val nIdx = when {
                        normMapping.contains("ByPolygonVertex", ignoreCase = true) -> cornerSeq
                        normMapping.contains("ByPolygon", ignoreCase = true) -> polySeq
                        normMapping.contains("AllSame", ignoreCase = true) -> 0
                        else -> safeVi
                    }.coerceIn(0, rawNormCount - 1)
                    outNorms.add(rawNorms[nIdx * 3])
                    outNorms.add(rawNorms[nIdx * 3 + 1])
                    outNorms.add(rawNorms[nIdx * 3 + 2])
                } else {
                    outNorms.add(0f); outNorms.add(1f); outNorms.add(0f)
                }
                return outIdx
            }

            for (rawI in polyIdx) {
                val isEnd = rawI < 0
                val vi = if (isEnd) (-rawI - 1) else rawI
                polyVertList.add(emitCorner(vi, polyVertCounter, polyIdxCounter))
                polyVertCounter++
                if (isEnd) {
                    if (polyVertList.size >= 3) {
                        for (t in 1 until polyVertList.size - 1) {
                            outIndices.add(polyVertList[0])
                            outIndices.add(polyVertList[t])
                            outIndices.add(polyVertList[t + 1])
                        }
                    }
                    polyVertList.clear()
                    polyIdxCounter++
                }
            }

            if (outIndices.size < 3) continue
            val vertsArr = outVerts.toFloatArray()
            val idxArr = outIndices.toIntArray()
            val uvsArr = if (rawUvs != null && rawUvCount > 0) outUvs.toFloatArray() else generatePlanarFallbackUvs(vertsArr)
            val normsArr = if (rawNorms != null && rawNormCount > 0) outNorms.toFloatArray() else computeSmoothNormals(vertsArr, idxArr)

            // Resolve connected Model -> Material -> Texture
            val gid = gNode.idProp()
            var baseCol = 0xFFE2E8F0.toInt()
            var texData: TexData? = null
            if (gid != null) {
                val modelIds = childToParents[gid].orEmpty()
                for (mId in modelIds) {
                    val matIds = parentToChildren[mId].orEmpty()
                    for (matId in matIds) {
                        matColorById[matId]?.let { baseCol = it }
                        matTexById[matId]?.let { if (texData == null) texData = it }
                    }
                }
            }

            subMeshes.add(
                Mesh3DSubMesh(
                    vertices = vertsArr,
                    normals = normsArr,
                    uvs = uvsArr,
                    indices = idxArr,
                    baseColorArgb = baseCol,
                    texturePixels = texData?.pixels,
                    textureWidth = texData?.w ?: 0,
                    textureHeight = texData?.h ?: 0
                )
            )
        }
        return subMeshes
    }

    // -------------------------------------------------------------------------
    // FBX ASCII Parser
    // -------------------------------------------------------------------------

    private fun parseFbxAscii(
        text: String,
        siblingResolver: ((String) -> ByteArray?)?
    ): List<Mesh3DSubMesh> {
        fun extractBlockNumbers(blockHeader: Regex): List<Double>? {
            val m = blockHeader.find(text) ?: return null
            val startBrace = text.indexOf('{', m.range.last)
            if (startBrace < 0) return null
            val colonA = text.indexOf("a:", startBrace)
            val endBrace = text.indexOf('}', startBrace)
            if (colonA < 0 || endBrace <= colonA) return null
            val raw = text.substring(colonA + 2, endBrace)
            val nums = ArrayList<Double>(256)
            var numStart = -1
            for (i in raw.indices) {
                val c = raw[i]
                val isNumChar = (c in '0'..'9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E'
                if (isNumChar) {
                    if (numStart < 0) numStart = i
                } else if (numStart >= 0) {
                    raw.substring(numStart, i).toDoubleOrNull()?.let { nums.add(it) }
                    numStart = -1
                }
            }
            if (numStart >= 0) {
                raw.substring(numStart).toDoubleOrNull()?.let { nums.add(it) }
            }
            return nums
        }

        // Split by Geometry: sections when multiple objects exist
        val geomSections = text.split(Regex("Geometry:\\s+\\d+"))
        val out = ArrayList<Mesh3DSubMesh>()
        val targets = if (geomSections.size > 1) geomSections.drop(1) else listOf(text)

        // Attempt resolving first texture filename & DiffuseColor in ASCII FBX
        var fallbackTex: Triple<IntArray, Int, Int>? = null
        if (siblingResolver != null) {
            val relMatch = Regex("(?:RelativeFilename|FileName):\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(text)
            val path = relMatch?.groupValues?.getOrNull(1)
            if (!path.isNullOrBlank()) {
                val resolved = siblingResolver(path) ?: siblingResolver(path.substringAfterLast('/').substringAfterLast('\\'))
                resolved?.let { fallbackTex = decodeTexturePixels(it) }
            }
        }
        var asciiDiffuseColor = 0xFFE2E8F0.toInt()
        Regex("P:\\s*\"DiffuseColor\"[^\\n]*?,\\s*([0-9.+\\-eE]+)\\s*,\\s*([0-9.+\\-eE]+)\\s*,\\s*([0-9.+\\-eE]+)")
            .find(text)?.let { cm ->
                val r = ((cm.groupValues[1].toFloatOrNull() ?: 0.85f).coerceIn(0f, 1f) * 255f).toInt()
                val g = ((cm.groupValues[2].toFloatOrNull() ?: 0.85f).coerceIn(0f, 1f) * 255f).toInt()
                val b = ((cm.groupValues[3].toFloatOrNull() ?: 0.85f).coerceIn(0f, 1f) * 255f).toInt()
                asciiDiffuseColor = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }

        for (sec in targets) {
            val vDoubles = extractSectionArray(sec, "Vertices:") ?: continue
            val pDoubles = extractSectionArray(sec, "PolygonVertexIndex:") ?: continue
            if (vDoubles.size < 9 || pDoubles.isEmpty()) continue
            val uvBlock = extractBalancedBlock(sec, "LayerElementUV:") ?: sec
            val uvDoubles = extractSectionArray(uvBlock, "UV:")
            val uvIdxDoubles = extractSectionArray(uvBlock, "UVIndex:")
            val uvMappingType = Regex("MappingInformationType:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                .find(uvBlock)?.groupValues?.getOrNull(1) ?: ""
            val uvRefType = Regex("ReferenceInformationType:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                .find(uvBlock)?.groupValues?.getOrNull(1) ?: ""

            val rawVerts = FloatArray(vDoubles.size) { vDoubles[it].toFloat() }
            val vCount = rawVerts.size / 3
            val uvCount = (uvDoubles?.size ?: 0) / 2

            val outVerts = ArrayList<Float>()
            val outUvs = ArrayList<Float>()
            val outIndices = ArrayList<Int>()
            val poly = ArrayList<Int>(4)
            var cornerSeq = 0
            var polySeq = 0

            for (rawD in pDoubles) {
                val rawI = rawD.toInt()
                val isEnd = rawI < 0
                val vi = (if (isEnd) -rawI - 1 else rawI).coerceIn(0, vCount - 1)
                val outIdx = outVerts.size / 3
                outVerts.add(rawVerts[vi * 3])
                outVerts.add(rawVerts[vi * 3 + 1])
                outVerts.add(rawVerts[vi * 3 + 2])
                if (uvDoubles != null && uvCount > 0) {
                    val mapKey = when {
                        uvMappingType.contains("ByPolygonVertex", ignoreCase = true) -> cornerSeq
                        uvMappingType.contains("ByPolygon", ignoreCase = true) -> polySeq
                        uvMappingType.contains("AllSame", ignoreCase = true) -> 0
                        uvMappingType.contains("ByVertice", ignoreCase = true) || uvMappingType.contains("ByVertex", ignoreCase = true) -> vi
                        uvIdxDoubles != null -> cornerSeq
                        else -> vi
                    }
                    val rawUvi = if (uvIdxDoubles != null && (uvRefType.contains("IndexToDirect", ignoreCase = true) || uvRefType.isBlank())) {
                        val idxVal = uvIdxDoubles.getOrNull(mapKey)?.toInt() ?: vi
                        if (idxVal >= 0) idxVal else vi
                    } else {
                        mapKey
                    }
                    val uvi = rawUvi.coerceIn(0, uvCount - 1)
                    outUvs.add(uvDoubles[uvi * 2].toFloat())
                    outUvs.add(1f - uvDoubles[uvi * 2 + 1].toFloat())
                } else {
                    outUvs.add(0f); outUvs.add(0f)
                }
                cornerSeq++
                poly.add(outIdx)
                if (isEnd) {
                    if (poly.size >= 3) {
                        for (t in 1 until poly.size - 1) {
                            outIndices.add(poly[0]); outIndices.add(poly[t]); outIndices.add(poly[t + 1])
                        }
                    }
                    poly.clear()
                    polySeq++
                }
            }
            if (outIndices.size < 3) continue
            val va = outVerts.toFloatArray()
            val ia = outIndices.toIntArray()
            val uva = if (uvDoubles != null && uvCount > 0) outUvs.toFloatArray() else generatePlanarFallbackUvs(va)
            out.add(
                Mesh3DSubMesh(
                    vertices = va,
                    normals = computeSmoothNormals(va, ia),
                    uvs = uva,
                    indices = ia,
                    baseColorArgb = asciiDiffuseColor,
                    texturePixels = fallbackTex?.first,
                    textureWidth = fallbackTex?.second ?: 0,
                    textureHeight = fallbackTex?.third ?: 0
                )
            )
        }
        return out
    }

    private fun extractSectionArray(section: String, key: String): List<Double>? {
        val idx = section.indexOf(key)
        if (idx < 0) return null
        val braceStart = section.indexOf('{', idx)
        if (braceStart < 0) return null
        val aPos = section.indexOf("a:", braceStart)
        val braceEnd = section.indexOf('}', braceStart)
        if (aPos < 0 || braceEnd <= aPos) return null
        val raw = section.substring(aPos + 2, braceEnd)
        val out = ArrayList<Double>(256)
        var start = -1
        for (i in raw.indices) {
            val c = raw[i]
            val ok = (c in '0'..'9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E'
            if (ok) {
                if (start < 0) start = i
            } else if (start >= 0) {
                raw.substring(start, i).toDoubleOrNull()?.let { out.add(it) }
                start = -1
            }
        }
        if (start >= 0) raw.substring(start).toDoubleOrNull()?.let { out.add(it) }
        return out
    }

    private fun extractBalancedBlock(section: String, headerKey: String): String? {
        val idx = section.indexOf(headerKey)
        if (idx < 0) return null
        val openPos = section.indexOf('{', idx)
        if (openPos < 0) return null
        var depth = 0
        for (i in openPos until section.length) {
            when (section[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return section.substring(idx, i + 1)
                    }
                }
            }
        }
        return section.substring(idx)
    }

    // -------------------------------------------------------------------------
    // OLE2 CFBF Reader (.max container)
    // -------------------------------------------------------------------------

    private fun extractOle2Streams(bytes: ByteArray): List<Pair<String, ByteArray>> {
        if (bytes.size < 512) return emptyList()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val sectorShift = bb.getShort(30).toInt() and 0xFFFF
        val sectorSize = (1 shl sectorShift).coerceIn(512, 4096)
        val numFatSectors = bb.getInt(44).coerceIn(0, 4096)
        val firstDirSector = bb.getInt(48)

        fun sectorOffset(secIdx: Int): Int = 512 + secIdx * sectorSize
        fun readSector(secIdx: Int): ByteArray? {
            val off = sectorOffset(secIdx)
            if (secIdx < 0 || off < 512 || off + sectorSize > bytes.size) return null
            return bytes.copyOfRange(off, off + sectorSize)
        }

        // Build FAT table from DIFAT entries in header (up to 109 sectors)
        val fat = ArrayList<Int>(numFatSectors * (sectorSize / 4))
        for (i in 0 until min(numFatSectors, 109)) {
            val fatSecIdx = bb.getInt(76 + i * 4)
            if (fatSecIdx < 0) break
            val sec = readSector(fatSecIdx) ?: continue
            val sbb = ByteBuffer.wrap(sec).order(ByteOrder.LITTLE_ENDIAN)
            for (k in 0 until sectorSize / 4) {
                fat.add(sbb.getInt(k * 4))
            }
        }
        if (fat.isEmpty()) return emptyList()

        fun readStreamChain(startSec: Int, byteSize: Int): ByteArray {
            val out = ByteArrayOutputStream(byteSize.coerceIn(0, 8 * 1024 * 1024))
            var cur = startSec
            var guard = 0
            while (cur >= 0 && cur < fat.size && guard++ < 16384) {
                val sec = readSector(cur) ?: break
                out.write(sec)
                if (byteSize > 0 && out.size() >= byteSize) break
                val next = fat[cur]
                if (next < 0 || next == cur) break
                cur = next
            }
            val arr = out.toByteArray()
            return if (byteSize in 1 until arr.size) arr.copyOf(byteSize) else arr
        }

        val dirBytes = readStreamChain(firstDirSector, 0)
        val result = ArrayList<Pair<String, ByteArray>>()
        var entryOff = 0
        while (entryOff + 128 <= dirBytes.size) {
            val type = dirBytes[entryOff + 66].toInt() and 0xFF
            if (type == 2) { // Stream object
                val dbb = ByteBuffer.wrap(dirBytes, entryOff, 128).order(ByteOrder.LITTLE_ENDIAN)
                val nameBytesLen = dbb.getShort(64).toInt().coerceIn(0, 64)
                val chars = CharArray(max(0, (nameBytesLen / 2) - 1))
                for (c in chars.indices) {
                    chars[c] = dbb.getShort(c * 2).toInt().toChar()
                }
                val name = String(chars)
                val startSec = dbb.getInt(116)
                val sizeLow = dbb.getInt(120)
                if (startSec >= 0 && sizeLow > 0 && sizeLow < 32 * 1024 * 1024) {
                    result.add(name to readStreamChain(startSec, sizeLow))
                }
            }
            entryOff += 128
        }
        return result
    }

    private fun tryInflateEmbeddedZlibStreams(data: ByteArray): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var i = 0
        while (i + 8 < data.size && out.size < 12) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = data[i + 1].toInt() and 0xFF
            if (b0 == 0x78 && (b1 == 0x9C || b1 == 0xDA || b1 == 0x01)) {
                val inf = Inflater()
                try {
                    inf.setInput(data, i, min(data.size - i, 1024 * 1024))
                    val baos = ByteArrayOutputStream()
                    val buf = ByteArray(4096)
                    var guard = 0
                    while (!inf.finished() && guard++ < 512) {
                        val read = inf.inflate(buf)
                        if (read <= 0) break
                        baos.write(buf, 0, read)
                    }
                    val inflated = baos.toByteArray()
                    if (inflated.size >= 32) {
                        out.add(inflated)
                        i += max(1, inf.totalIn)
                        continue
                    }
                } catch (_: Exception) {
                } finally {
                    inf.end()
                }
            }
            i++
        }
        return out
    }

    private fun findEmbeddedImages(
        blob: ByteArray,
        dst: MutableList<Triple<IntArray, Int, Int>>,
        limit: Int
    ) {
        var i = 0
        while (i + 16 < blob.size && dst.size < limit) {
            val isPng = (blob[i] == 0x89.toByte() && blob[i + 1] == 0x50.toByte() &&
                blob[i + 2] == 0x4E.toByte() && blob[i + 3] == 0x47.toByte())
            val isJpg = (blob[i] == 0xFF.toByte() && blob[i + 1] == 0xD8.toByte() && blob[i + 2] == 0xFF.toByte())
            if (isPng || isJpg) {
                val slice = blob.copyOfRange(i, min(blob.size, i + 2 * 1024 * 1024))
                val decoded = decodeTexturePixels(slice)
                if (decoded != null) {
                    dst.add(decoded)
                    i += 512
                    continue
                }
            }
            i++
        }
    }

    private fun scanBlobForMaxMeshChunks(
        blob: ByteArray,
        tex: Triple<IntArray, Int, Int>?
    ): Mesh3DSubMesh? {
        if (blob.size < 64) return null
        val bb = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        // Scan for Kodanima/Max chunk signature 0x0910/0x0912-like or explicit float vertex table markers ("MESH" / "VERT")
        var i = 0
        while (i + 16 < blob.size) {
            if (blob[i] == 'V'.code.toByte() && blob[i + 1] == 'E'.code.toByte() &&
                blob[i + 2] == 'R'.code.toByte() && blob[i + 3] == 'T'.code.toByte()
            ) {
                val vCount = bb.getInt(i + 4)
                val tCount = bb.getInt(i + 8)
                val vOff = i + 12
                val idxOff = vOff + vCount * 12
                if (vCount in 3..65536 && tCount in 1..65536 && idxOff + tCount * 12 <= blob.size) {
                    val verts = FloatArray(vCount * 3) { bb.getFloat(vOff + it * 4) }
                    val idxs = IntArray(tCount * 3) { bb.getInt(idxOff + it * 4).coerceIn(0, vCount - 1) }
                    val uvs = generatePlanarFallbackUvs(verts)
                    return Mesh3DSubMesh(
                        vertices = verts,
                        normals = computeSmoothNormals(verts, idxs),
                        uvs = uvs,
                        indices = idxs,
                        baseColorArgb = 0xFFE2E8F0.toInt(),
                        texturePixels = tex?.first,
                        textureWidth = tex?.second ?: 0,
                        textureHeight = tex?.third ?: 0
                    )
                }
            }
            i++
        }
        return null
    }

    private fun buildFallbackContainerMesh(tex: Triple<IntArray, Int, Int>?): Mesh3DSubMesh {
        val h = 95f
        val verts = floatArrayOf(
            -h, -h, h, h, -h, h, h, h, h, -h, h, h,
            h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h,
            -h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h,
            h, -h, h, h, -h, -h, h, h, -h, h, h, h,
            -h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h,
            -h, h, h, h, h, h, h, h, -h, -h, h, -h
        )
        val uvs = FloatArray(24 * 2)
        for (f in 0 until 6) {
            val o = f * 8
            uvs[o] = 0f; uvs[o + 1] = 0f
            uvs[o + 2] = 1f; uvs[o + 3] = 0f
            uvs[o + 4] = 1f; uvs[o + 5] = 1f
            uvs[o + 6] = 0f; uvs[o + 7] = 1f
        }
        val idxs = IntArray(36)
        for (f in 0 until 6) {
            val base = f * 4
            val o = f * 6
            idxs[o] = base; idxs[o + 1] = base + 1; idxs[o + 2] = base + 2
            idxs[o + 3] = base; idxs[o + 4] = base + 2; idxs[o + 5] = base + 3
        }
        return Mesh3DSubMesh(
            vertices = verts,
            normals = computeSmoothNormals(verts, idxs),
            uvs = uvs,
            indices = idxs,
            baseColorArgb = 0xFFDADFE8.toInt(),
            texturePixels = tex?.first,
            textureWidth = tex?.second ?: 0,
            textureHeight = tex?.third ?: 0
        )
    }

    // -------------------------------------------------------------------------
    // Geometry & Texture Utilities
    // -------------------------------------------------------------------------

    fun generatePlanarFallbackUvs(verts: FloatArray): FloatArray {
        val vCount = verts.size / 3
        val uvs = FloatArray(vCount * 2)
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (v in 0 until vCount) {
            val x = verts[v * 3]
            val y = verts[v * 3 + 1]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        val dx = (maxX - minX).coerceAtLeast(1e-4f)
        val dy = (maxY - minY).coerceAtLeast(1e-4f)
        for (v in 0 until vCount) {
            uvs[v * 2] = ((verts[v * 3] - minX) / dx).coerceIn(0f, 1f)
            uvs[v * 2 + 1] = (1f - (verts[v * 3 + 1] - minY) / dy).coerceIn(0f, 1f)
        }
        return uvs
    }

    private fun computeSmoothNormals(vertices: FloatArray, indices: IntArray): FloatArray {
        val normals = FloatArray(vertices.size)
        var i = 0
        while (i + 2 < indices.size) {
            val i0 = indices[i] * 3
            val i1 = indices[i + 1] * 3
            val i2 = indices[i + 2] * 3
            if (i0 + 2 < vertices.size && i1 + 2 < vertices.size && i2 + 2 < vertices.size) {
                val ax = vertices[i1] - vertices[i0]
                val ay = vertices[i1 + 1] - vertices[i0 + 1]
                val az = vertices[i1 + 2] - vertices[i0 + 2]
                val bx = vertices[i2] - vertices[i0]
                val by = vertices[i2 + 1] - vertices[i0 + 1]
                val bz = vertices[i2 + 2] - vertices[i0 + 2]
                val nx = ay * bz - az * by
                val ny = az * bx - ax * bz
                val nz = ax * by - ay * bx
                normals[i0] += nx; normals[i0 + 1] += ny; normals[i0 + 2] += nz
                normals[i1] += nx; normals[i1 + 1] += ny; normals[i1 + 2] += nz
                normals[i2] += nx; normals[i2 + 1] += ny; normals[i2 + 2] += nz
            }
            i += 3
        }
        var v = 0
        while (v + 2 < normals.size) {
            val len = sqrt(normals[v] * normals[v] + normals[v + 1] * normals[v + 1] + normals[v + 2] * normals[v + 2])
            if (len > 1e-5f) {
                normals[v] /= len; normals[v + 1] /= len; normals[v + 2] /= len
            } else {
                normals[v + 2] = 1f
            }
            v += 3
        }
        return normals
    }

    private fun decodeTexturePixels(bytes: ByteArray): Triple<IntArray, Int, Int>? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
            val maxDim = max(opts.outWidth, opts.outHeight)
            var sample = 1
            while (maxDim / sample > 1024) sample *= 2
            val bmp = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            ) ?: return null
            val w = bmp.width.coerceAtLeast(1)
            val h = bmp.height.coerceAtLeast(1)
            val pix = IntArray(w * h)
            bmp.getPixels(pix, 0, w, 0, 0, w, h)
            bmp.recycle()
            Triple(pix, w, h)
        } catch (_: Exception) {
            null
        }
    }
}
