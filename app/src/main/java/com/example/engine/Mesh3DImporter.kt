package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class Mesh3DSubMesh(
    val vertices: FloatArray, // x,y,z * vertexCount
    val normals: FloatArray,  // nx,ny,nz * vertexCount
    val uvs: FloatArray,      // u,v * vertexCount (top-left bitmap UV space: u in 0..1, v in 0..1)
    val indices: IntArray,    // triangles (3 * triCount)
    val baseColorArgb: Int = 0xFFE2E8F0.toInt(),
    val texturePixels: IntArray? = null,
    val textureWidth: Int = 0,
    val textureHeight: Int = 0
)

data class Mesh3DAnimSampler(
    val inputTimes: FloatArray,
    val outputValues: FloatArray,
    val interpolation: String = "LINEAR", // "LINEAR", "STEP", "CUBICSPLINE"
    val components: Int = 3
)

data class Mesh3DAnimChannel(
    val targetNode: Int,
    val path: String, // "translation", "rotation", "scale"
    val sampler: Mesh3DAnimSampler
)

data class Mesh3DAnimationClip(
    val name: String,
    val durationSec: Float,
    val channels: List<Mesh3DAnimChannel>
)

data class Mesh3DNodeData(
    val translation: FloatArray = floatArrayOf(0f, 0f, 0f),
    val rotation: FloatArray = floatArrayOf(0f, 0f, 0f, 1f),
    val scale: FloatArray = floatArrayOf(1f, 1f, 1f),
    val matrix: FloatArray? = null,
    val meshIndex: Int = -1,
    val children: IntArray = IntArray(0)
)

data class Mesh3DPrimitiveSource(
    val meshIndex: Int,
    val localVertices: FloatArray,
    val localNormals: FloatArray?,
    val uvs: FloatArray,
    val indices: IntArray,
    val baseColorArgb: Int,
    val texturePixels: IntArray?,
    val textureWidth: Int,
    val textureHeight: Int
)

data class Mesh3DAnimationData(
    val nodes: List<Mesh3DNodeData>,
    val rootNodes: IntArray,
    val primitivesByMesh: Map<Int, List<Mesh3DPrimitiveSource>>,
    val clips: List<Mesh3DAnimationClip>,
    val normCenterX: Float,
    val normCenterY: Float,
    val normCenterZ: Float,
    val normScale: Float
)

data class Mesh3DBone(
    val name: String,
    val parentIndex: Int = -1,
    val head: FloatArray = floatArrayOf(0f, 0f, 0f),
    val tail: FloatArray = floatArrayOf(0f, 0.1f, 0f)
)

data class Mesh3DSceneData(
    val id: String,
    val name: String,
    val subMeshes: List<Mesh3DSubMesh>,
    val boundingRadius: Float,
    val animData: Mesh3DAnimationData? = null,
    val bones: List<Mesh3DBone> = emptyList()
) {
    val totalTriangles: Int
        get() = subMeshes.sumOf { it.indices.size / 3 }
    val hasAnimations: Boolean
        get() = animData != null && animData.clips.isNotEmpty()
    val hasBones: Boolean
        get() = bones.isNotEmpty()
}

enum class BuiltIn3DPrimitive(val uriKey: String, val labelPt: String) {
    CUBE("builtin://cube", "Cubo 3D"),
    PYRAMID("builtin://pyramid", "Pirâmide 3D"),
    SPHERE("builtin://sphere", "Esfera 3D"),
    PLATFORM("builtin://platform", "Palco / Chão 3D")
}

object Mesh3DImporter {

    private val sceneCache = ConcurrentHashMap<String, Mesh3DSceneData>()

    fun clearCache() {
        sceneCache.clear()
    }

    fun getOrLoadMesh(meshAssetPath: String): Mesh3DSceneData? {
        if (meshAssetPath.isBlank()) return null
        val normalizedPath = if (meshAssetPath.startsWith("builtin:") && !meshAssetPath.startsWith("builtin://")) {
            "builtin://" + meshAssetPath.removePrefix("builtin:")
        } else {
            meshAssetPath
        }
        sceneCache[meshAssetPath]?.let { return it }
        sceneCache[normalizedPath]?.let { return it }

        val loaded = when {
            normalizedPath.startsWith("builtin://") -> buildBuiltInPrimitive(normalizedPath)
            else -> {
                val f = File(normalizedPath)
                if (!f.exists()) return null
                parseSceneFile(f, f.parentFile)
            }
        }
        if (loaded != null) {
            sceneCache[meshAssetPath] = loaded
            sceneCache[normalizedPath] = loaded
        }
        return loaded
    }

    fun createBuiltInSceneAsset(primitive: BuiltIn3DPrimitive): Pair<String, Mesh3DSceneData> {
        val data = buildBuiltInPrimitive(primitive.uriKey, primitive.labelPt)
        sceneCache[primitive.uriKey] = data
        return primitive.uriKey to data
    }

    fun importFromUri(context: Context, uri: Uri): Pair<String, Mesh3DSceneData>? {
        val rawName = queryDisplayName(context, uri) ?: "cena_3d_${System.currentTimeMillis()}"
        val decodedName = safeUrlDecode(rawName)
        val ext = decodedName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.isEmpty()) return null
        return importFromBytes(context, decodedName, ext, bytes)
    }

    fun importFromBytes(
        context: Context,
        displayName: String,
        extensionHint: String,
        bytes: ByteArray
    ): Pair<String, Mesh3DSceneData>? {
        val vaultRoot = File(context.filesDir, "scenes3d/${UUID.randomUUID()}")
        vaultRoot.mkdirs()

        val ext = extensionHint.lowercase(Locale.ROOT).ifBlank {
            detectFormatByHeader(bytes, displayName)
        }
        return if (ext == "zip" || isZipHeader(bytes)) {
            extractZipToVault(bytes, vaultRoot)
            val mainFile = findPrimarySceneFile(vaultRoot) ?: return null
            val scene = parseSceneFile(mainFile, vaultRoot, displayName.substringBeforeLast('.')) ?: return null
            sceneCache[mainFile.absolutePath] = scene
            mainFile.absolutePath to scene
        } else if (ext == "rar" || isRarHeader(bytes)) {
            extractRarToVault(bytes, vaultRoot)
            val mainFile = findPrimarySceneFile(vaultRoot) ?: return null
            val scene = parseSceneFile(mainFile, vaultRoot, displayName.substringBeforeLast('.')) ?: return null
            sceneCache[mainFile.absolutePath] = scene
            mainFile.absolutePath to scene
        } else {
            val safeFileName = sanitizeFileName(displayName.ifBlank { "cena_3d.$ext" }).let {
                if (it.contains('.')) it else "$it.$ext"
            }
            val destFile = File(vaultRoot, safeFileName)
            FileOutputStream(destFile).use { it.write(bytes) }
            val scene = parseSceneFile(destFile, vaultRoot, displayName.substringBeforeLast('.')) ?: return null
            sceneCache[destFile.absolutePath] = scene
            destFile.absolutePath to scene
        }
    }

    fun parseSceneBytesForTest(
        bytes: ByteArray,
        fileName: String,
        siblingResolver: ((String) -> ByteArray?)? = null
    ): Mesh3DSceneData? {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            .ifBlank { detectFormatByHeader(bytes, fileName) }
        if (ext == "rar" || isRarHeader(bytes)) {
            val archiveMap = extractRarArchiveMap(bytes)
            val mainName = findPrimarySceneEntryName(archiveMap.keys) ?: return null
            val mainBytes = archiveMap[mainName] ?: return null
            val archiveResolver: (String) -> ByteArray? = { ref ->
                val clean = safeUrlDecode(ref).replace('\\', '/').trim().removePrefix("./")
                val baseOnly = clean.substringAfterLast('/')
                archiveMap.entries.firstOrNull { (k, _) ->
                    k.equals(clean, ignoreCase = true) || k.substringAfterLast('/').equals(baseOnly, ignoreCase = true)
                }?.value ?: siblingResolver?.invoke(ref)
            }
            return parseSceneBytesForTest(mainBytes, mainName, archiveResolver)
        }
        val baseName = fileName.substringBeforeLast('.').ifBlank { "Cena 3D" }
        var parsedAnimData: Mesh3DAnimationData? = null
        var parsedBones: List<Mesh3DBone> = emptyList()
        val rawMeshes = when (ext) {
            "glb" -> parseGlbWithAnim(bytes, siblingResolver)?.let { (m, a, b) -> parsedAnimData = a; parsedBones = b; m } ?: emptyList()
            "gltf" -> parseGltfJsonWithAnim(bytes.toString(Charsets.UTF_8), null, siblingResolver)?.let { (m, a, b) -> parsedAnimData = a; parsedBones = b; m } ?: emptyList()
            "fbx" -> {
                parsedBones = FbxAndMax3DParser.extractFbxBones(bytes)
                FbxAndMax3DParser.parseFbx(bytes, siblingResolver)
            }
            "3ds" -> FbxAndMax3DParser.parse3ds(bytes, siblingResolver)
            "max" -> FbxAndMax3DParser.parseMaxScene(bytes, siblingResolver)
            "obj" -> parseObj(bytes.toString(Charsets.UTF_8), siblingResolver)
            "stl" -> parseStl(bytes)
            "ply" -> parsePly(bytes.toString(Charsets.UTF_8))
            else -> when {
                isGlbHeader(bytes) -> parseGlbWithAnim(bytes, siblingResolver)?.let { (m, a, b) -> parsedAnimData = a; parsedBones = b; m } ?: emptyList()
                FbxAndMax3DParser.isFbxBinary(bytes) || FbxAndMax3DParser.isFbxAscii(bytes) -> {
                    parsedBones = FbxAndMax3DParser.extractFbxBones(bytes)
                    FbxAndMax3DParser.parseFbx(bytes, siblingResolver)
                }
                FbxAndMax3DParser.is3dsBinary(bytes) -> FbxAndMax3DParser.parse3ds(bytes, siblingResolver)
                FbxAndMax3DParser.isMaxCompoundFile(bytes) -> FbxAndMax3DParser.parseMaxScene(bytes, siblingResolver)
                else -> parseObj(bytes.toString(Charsets.UTF_8), siblingResolver)
            }
        }
        if (rawMeshes.isEmpty()) return null
        return normalizeAndWrapScene(UUID.randomUUID().toString(), baseName, rawMeshes, animData = parsedAnimData, bones = parsedBones)
    }

    private fun parseSceneFile(
        file: File,
        vaultDir: File?,
        overrideName: String? = null
    ): Mesh3DSceneData? {
        val bytes = try {
            FileInputStream(file).use { it.readBytes() }
        } catch (_: Exception) {
            return null
        }
        if (bytes.isEmpty()) return null
        val ext = file.extension.lowercase(Locale.ROOT).ifBlank { detectFormatByHeader(bytes, file.name) }
        val cleanName = (overrideName ?: file.nameWithoutExtension).ifBlank { "Cena 3D" }
        val siblingIndex = if (vaultDir != null && vaultDir.exists()) {
            vaultDir.walkTopDown().filter { it.isFile }.toList()
        } else {
            emptyList()
        }
        val resolver: (String) -> ByteArray? = { refPath ->
            resolveSiblingBytes(refPath, file.parentFile, vaultDir, siblingIndex)
        }

        var parsedAnimData: Mesh3DAnimationData? = null
        var parsedBones: List<Mesh3DBone> = emptyList()
        var rawMeshes = when (ext) {
            "glb" -> parseGlbWithAnim(bytes, resolver)?.let { (m, a, b) -> parsedAnimData = a; parsedBones = b; m } ?: emptyList()
            "gltf" -> parseGltfJsonWithAnim(bytes.toString(Charsets.UTF_8), null, resolver)?.let { (m, a, b) -> parsedAnimData = a; parsedBones = b; m } ?: emptyList()
            "fbx" -> {
                parsedBones = FbxAndMax3DParser.extractFbxBones(bytes)
                FbxAndMax3DParser.parseFbx(bytes, resolver)
            }
            "3ds" -> FbxAndMax3DParser.parse3ds(bytes, resolver)
            "max" -> FbxAndMax3DParser.parseMaxScene(bytes, resolver)
            "obj" -> parseObj(bytes.toString(Charsets.UTF_8), resolver)
            "stl" -> parseStl(bytes)
            "ply" -> parsePly(bytes.toString(Charsets.UTF_8))
            else -> when {
                isGlbHeader(bytes) -> parseGlbWithAnim(bytes, resolver)?.let { (m, a, b) -> parsedAnimData = a; parsedBones = b; m } ?: emptyList()
                FbxAndMax3DParser.isFbxBinary(bytes) || FbxAndMax3DParser.isFbxAscii(bytes) -> {
                    parsedBones = FbxAndMax3DParser.extractFbxBones(bytes)
                    FbxAndMax3DParser.parseFbx(bytes, resolver)
                }
                FbxAndMax3DParser.is3dsBinary(bytes) -> FbxAndMax3DParser.parse3ds(bytes, resolver)
                FbxAndMax3DParser.isMaxCompoundFile(bytes) -> FbxAndMax3DParser.parseMaxScene(bytes, resolver)
                bytes.decodeToString(0, min(bytes.size, 64)).trimStart().startsWith("{") ->
                    parseGltfJsonWithAnim(bytes.toString(Charsets.UTF_8), null, resolver)?.let { (m, a, b) -> parsedAnimData = a; parsedBones = b; m } ?: emptyList()
                else -> parseObj(bytes.toString(Charsets.UTF_8), resolver)
            }
        }
        if (rawMeshes.isEmpty()) return null
        if (rawMeshes.all { it.texturePixels == null } && siblingIndex.isNotEmpty()) {
            val fallbackImgFile = findBestFallbackTextureFile(siblingIndex, cleanName)
            val fallbackTex = fallbackImgFile?.let {
                try { decodeBitmapPixels(it.readBytes()) } catch (_: Exception) { null }
            }
            if (fallbackTex != null) {
                rawMeshes = rawMeshes.map { sm ->
                    sm.copy(
                        texturePixels = fallbackTex.first,
                        textureWidth = fallbackTex.second,
                        textureHeight = fallbackTex.third
                    )
                }
            }
        }
        return normalizeAndWrapScene(UUID.randomUUID().toString(), cleanName, rawMeshes, animData = parsedAnimData, bones = parsedBones)
    }

    private fun findBestFallbackTextureFile(files: List<File>, modelName: String): File? {
        val imgExts = setOf("png", "jpg", "jpeg", "webp")
        val imgs = files.filter { it.extension.lowercase(Locale.ROOT) in imgExts }
        if (imgs.isEmpty()) return null
        val preferred = imgs.firstOrNull {
            val n = it.nameWithoutExtension.lowercase(Locale.ROOT)
            n.contains("albedo") || n.contains("diffuse") || n.contains("basecolor") ||
                n.contains("color") || n.contains(modelName.lowercase(Locale.ROOT))
        }
        return preferred ?: imgs.first()
    }

    private fun resolveSiblingBytes(
        rawRef: String,
        localDir: File?,
        vaultDir: File?,
        indexedFiles: List<File>
    ): ByteArray? {
        val clean = safeUrlDecode(rawRef).replace('\\', '/').trim().removePrefix("./")
        if (clean.startsWith("data:", ignoreCase = true)) {
            return decodeDataUri(clean)
        }
        val baseOnly = clean.substringAfterLast('/')
        val candidates = listOfNotNull(
            localDir?.let { File(it, clean) },
            vaultDir?.let { File(it, clean) },
            localDir?.let { File(it, baseOnly) },
            vaultDir?.let { File(it, baseOnly) }
        )
        for (c in candidates) {
            if (c.exists() && c.isFile) {
                return try { c.readBytes() } catch (_: Exception) { null }
            }
        }
        val match = indexedFiles.firstOrNull { f ->
            f.name.equals(baseOnly, ignoreCase = true) ||
                f.absolutePath.replace('\\', '/').endsWith("/$clean", ignoreCase = true)
        }
        return if (match != null) try { match.readBytes() } catch (_: Exception) { null } else null
    }

    private fun extractZipToVault(zipBytes: ByteArray, destDir: File) {
        val canonicalDest = destDir.canonicalPath
        ZipInputStream(BufferedInputStream(zipBytes.inputStream())).use { zis ->
            var entry = zis.nextEntry
            val buf = ByteArray(16384)
            while (entry != null) {
                if (!entry.isDirectory) {
                    val decodedEntry = safeUrlDecode(entry.name).replace('\\', '/')
                    val outFile = File(destDir, decodedEntry)
                    if (outFile.canonicalPath.startsWith(canonicalDest + File.separator)) {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            var read = zis.read(buf)
                            while (read > 0) {
                                fos.write(buf, 0, read)
                                read = zis.read(buf)
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun findPrimarySceneFile(dir: File): File? {
        val priority = listOf("glb", "gltf", "fbx", "obj", "3ds", "max", "ply", "stl")
        val files = dir.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.toList()
        for (ext in priority) {
            val found = files.firstOrNull { it.extension.equals(ext, ignoreCase = true) }
            if (found != null) return found
        }
        return null
    }

    // -------------------------------------------------------------------------
    // glTF 2.0 / GLB Binary Importer (with Nodes, Hierarchies, UV & Textures)
    // -------------------------------------------------------------------------

    private fun isGlbHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 12 &&
            bytes[0] == 'g'.code.toByte() &&
            bytes[1] == 'l'.code.toByte() &&
            bytes[2] == 'T'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()
    }

    private fun isZipHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte()
    }

    fun isRarHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 7 &&
            bytes[0] == 0x52.toByte() &&
            bytes[1] == 0x61.toByte() &&
            bytes[2] == 0x72.toByte() &&
            bytes[3] == 0x21.toByte() &&
            bytes[4] == 0x1A.toByte() &&
            bytes[5] == 0x07.toByte()
    }

    private fun extractRarToVault(rarBytes: ByteArray, destDir: File) {
        val canonicalDest = destDir.canonicalPath
        val extracted = extractRarArchiveMap(rarBytes)
        for ((relPath, payload) in extracted) {
            val outFile = File(destDir, safeUrlDecode(relPath).replace('\\', '/'))
            if (outFile.canonicalPath.startsWith(canonicalDest + File.separator)) {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { it.write(payload) }
            }
        }
    }

    fun extractRarArchiveMap(rarBytes: ByteArray): Map<String, ByteArray> {
        val result = LinkedHashMap<String, ByteArray>()
        if (rarBytes.size < 14 || !isRarHeader(rarBytes)) return result
        val bb = ByteBuffer.wrap(rarBytes).order(ByteOrder.LITTLE_ENDIAN)
        val isRar5 = rarBytes.size >= 8 && rarBytes[6] == 0x01.toByte() && rarBytes[7] == 0x00.toByte()
        if (!isRar5) {
            // RAR4 block walker (marker at 0..6, archive/file blocks follow)
            var pos = 7
            while (pos + 7 <= rarBytes.size) {
                val headType = rarBytes[pos + 2].toInt() and 0xFF
                val flags = bb.getShort(pos + 3).toInt() and 0xFFFF
                val headSize = (bb.getShort(pos + 5).toInt() and 0xFFFF).coerceAtLeast(7)
                if (pos + headSize > rarBytes.size) break
                if (headType == 0x74 && pos + 32 <= rarBytes.size) { // File header
                    val packSizeLow = bb.getInt(pos + 7).toLong() and 0xFFFFFFFFL
                    val method = rarBytes[pos + 25].toInt() and 0xFF
                    val nameSize = (bb.getShort(pos + 26).toInt() and 0xFFFF).coerceIn(0, 512)
                    val isHighPack = (flags and 0x0100) != 0
                    val nameOffset = pos + if (isHighPack) 40 else 32
                    val safeNameLen = min(nameSize, max(0, pos + headSize - nameOffset))
                    val rawName = if (safeNameLen > 0) {
                        String(rarBytes, nameOffset, safeNameLen, Charsets.UTF_8)
                            .substringBefore('\u0000').replace('\\', '/')
                    } else ""
                    val dataStart = pos + headSize
                    val packLen = packSizeLow.coerceIn(0L, (rarBytes.size - dataStart).toLong()).toInt()
                    val payload = if (packLen > 0) rarBytes.copyOfRange(dataStart, dataStart + packLen) else ByteArray(0)
                    if (rawName.isNotBlank() && payload.isNotEmpty() && method == 0x30) {
                        result[rawName] = payload
                    }
                    pos = dataStart + packLen
                    continue
                }
                val addSize = if ((flags and 0x8000) != 0 && pos + 11 <= rarBytes.size) {
                    (bb.getInt(pos + 7).toLong() and 0xFFFFFFFFL).toInt()
                } else 0
                val step = (headSize + max(0, addSize)).coerceAtLeast(7)
                pos += step
            }
        }
        // Fallback signature scan inside stored/uncompressed RAR blocks if no stored files matched
        if (result.isEmpty()) {
            var idx = 0
            while (idx + 12 < rarBytes.size) {
                if (rarBytes[idx] == 'g'.code.toByte() && rarBytes[idx + 1] == 'l'.code.toByte() &&
                    rarBytes[idx + 2] == 'T'.code.toByte() && rarBytes[idx + 3] == 'F'.code.toByte() &&
                    idx + 12 <= rarBytes.size
                ) {
                    val glbLen = bb.getInt(idx + 8)
                    if (glbLen in 32..(rarBytes.size - idx)) {
                        result["model_embedded.glb"] = rarBytes.copyOfRange(idx, idx + glbLen)
                        break
                    }
                }
                idx++
            }
        }
        return result
    }

    private fun findPrimarySceneEntryName(names: Collection<String>): String? {
        val priority = listOf("glb", "gltf", "fbx", "obj", "3ds", "max", "ply", "stl")
        for (ext in priority) {
            val found = names.firstOrNull { it.substringAfterLast('.', "").equals(ext, ignoreCase = true) }
            if (found != null) return found
        }
        return null
    }

    private fun parseGlbWithAnim(
        bytes: ByteArray,
        siblingResolver: ((String) -> ByteArray?)? = null
    ): Triple<List<Mesh3DSubMesh>, Mesh3DAnimationData?, List<Mesh3DBone>>? {
        if (bytes.size < 20 || !isGlbHeader(bytes)) return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(12)
        var jsonStr: String? = null
        var binChunk: ByteArray? = null

        while (bb.remaining() >= 8) {
            val chunkLen = bb.int
            val chunkType = bb.int
            if (chunkLen < 0 || chunkLen > bb.remaining()) break
            val chunkData = ByteArray(chunkLen)
            bb.get(chunkData)
            when (chunkType) {
                0x4E4F534A -> jsonStr = String(chunkData, Charsets.UTF_8) // JSON
                0x004E4942 -> if (binChunk == null) binChunk = chunkData // BIN
            }
        }
        if (jsonStr.isNullOrBlank()) return null
        return parseGltfJsonWithAnim(jsonStr, binChunk, siblingResolver)
    }

    private fun parseGltfJsonWithAnim(
        jsonText: String,
        embeddedBinChunk: ByteArray?,
        siblingResolver: ((String) -> ByteArray?)?
    ): Triple<List<Mesh3DSubMesh>, Mesh3DAnimationData?, List<Mesh3DBone>>? {
        val root = try {
            JSONObject(jsonText)
        } catch (_: Exception) {
            return null
        }

        val buffersArr = root.optJSONArray("buffers") ?: JSONArray()
        val buffers = ArrayList<ByteArray>(buffersArr.length().coerceAtLeast(1))
        for (i in 0 until buffersArr.length()) {
            val bObj = buffersArr.optJSONObject(i)
            val uri = bObj?.optString("uri", "") ?: ""
            val resolved = when {
                uri.isBlank() && i == 0 && embeddedBinChunk != null -> embeddedBinChunk
                uri.startsWith("data:", ignoreCase = true) -> decodeDataUri(uri) ?: ByteArray(0)
                uri.isNotBlank() && siblingResolver != null -> siblingResolver(uri) ?: ByteArray(0)
                else -> ByteArray(0)
            }
            buffers.add(resolved)
        }
        if (buffers.isEmpty() && embeddedBinChunk != null) {
            buffers.add(embeddedBinChunk)
        }

        val bufferViews = root.optJSONArray("bufferViews") ?: JSONArray()
        val accessors = root.optJSONArray("accessors") ?: JSONArray()
        val materials = root.optJSONArray("materials") ?: JSONArray()
        val textures = root.optJSONArray("textures") ?: JSONArray()
        val images = root.optJSONArray("images") ?: JSONArray()
        val meshes = root.optJSONArray("meshes") ?: JSONArray()
        val nodes = root.optJSONArray("nodes") ?: JSONArray()

        // Decode image textures lazily per image index
        val imagePixelCache = HashMap<Int, Triple<IntArray, Int, Int>?>()
        fun getImagePixels(imageIdx: Int): Triple<IntArray, Int, Int>? {
            if (imageIdx < 0 || imageIdx >= images.length()) return null
            if (imagePixelCache.containsKey(imageIdx)) return imagePixelCache[imageIdx]
            val imgObj = images.optJSONObject(imageIdx) ?: return null
            val imgBytes: ByteArray? = when {
                imgObj.has("bufferView") -> {
                    val bvIdx = imgObj.optInt("bufferView", -1)
                    val bv = bufferViews.optJSONObject(bvIdx)
                    if (bv != null) {
                        val bIdx = bv.optInt("buffer", 0)
                        val buf = buffers.getOrNull(bIdx)
                        val off = bv.optInt("byteOffset", 0)
                        val len = bv.optInt("byteLength", 0)
                        if (buf != null && off >= 0 && off + len <= buf.size) {
                            buf.copyOfRange(off, off + len)
                        } else null
                    } else null
                }
                imgObj.has("uri") -> {
                    val uri = imgObj.optString("uri", "")
                    when {
                        uri.startsWith("data:", ignoreCase = true) -> decodeDataUri(uri)
                        uri.isNotBlank() && siblingResolver != null -> siblingResolver(uri)
                        else -> null
                    }
                }
                else -> null
            }
            val decoded = if (imgBytes != null && imgBytes.isNotEmpty()) decodeBitmapPixels(imgBytes) else null
            imagePixelCache[imageIdx] = decoded
            return decoded
        }

        fun readAccessorFloats(accIdx: Int, expectedComponents: Int): FloatArray? {
            if (accIdx < 0 || accIdx >= accessors.length()) return null
            val acc = accessors.optJSONObject(accIdx) ?: return null
            val bvIdx = acc.optInt("bufferView", -1)
            if (bvIdx < 0 || bvIdx >= bufferViews.length()) return null
            val bv = bufferViews.optJSONObject(bvIdx) ?: return null
            val buf = buffers.getOrNull(bv.optInt("buffer", 0)) ?: return null

            val count = acc.optInt("count", 0)
            if (count <= 0) return null
            val compType = acc.optInt("componentType", 5126)
            val normalized = acc.optBoolean("normalized", false)
            val accOff = acc.optInt("byteOffset", 0) + bv.optInt("byteOffset", 0)
            val compByteSize = when (compType) {
                5120, 5121 -> 1
                5122, 5123 -> 2
                5125, 5126 -> 4
                else -> 4
            }
            val typeStr = acc.optString("type", "VEC3")
            val compCount = when (typeStr) {
                "SCALAR" -> 1
                "VEC2" -> 2
                "VEC3" -> 3
                "VEC4" -> 4
                else -> expectedComponents
            }
            val defaultStride = compByteSize * compCount
            val stride = bv.optInt("byteStride", defaultStride).coerceAtLeast(defaultStride)
            val out = FloatArray(count * expectedComponents)
            val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)

            for (i in 0 until count) {
                val base = accOff + i * stride
                for (c in 0 until min(compCount, expectedComponents)) {
                    val p = base + c * compByteSize
                    if (p + compByteSize > buf.size) break
                    val v = when (compType) {
                        5126 -> bb.getFloat(p)
                        5120 -> if (normalized) max(-1f, buf[p].toFloat() / 127f) else buf[p].toFloat()
                        5121 -> if (normalized) (buf[p].toInt() and 0xFF) / 255f else (buf[p].toInt() and 0xFF).toFloat()
                        5122 -> {
                            val s = bb.getShort(p).toFloat()
                            if (normalized) max(-1f, s / 32767f) else s
                        }
                        5123 -> {
                            val us = (bb.getShort(p).toInt() and 0xFFFF).toFloat()
                            if (normalized) us / 65535f else us
                        }
                        5125 -> (bb.getInt(p).toLong() and 0xFFFFFFFFL).toFloat()
                        else -> bb.getFloat(p)
                    }
                    out[i * expectedComponents + c] = v
                }
            }
            return out
        }

        fun readAccessorIndices(accIdx: Int, vertexCount: Int): IntArray {
            if (accIdx < 0 || accIdx >= accessors.length()) {
                return IntArray(vertexCount) { it }
            }
            val acc = accessors.optJSONObject(accIdx) ?: return IntArray(vertexCount) { it }
            val bvIdx = acc.optInt("bufferView", -1)
            val bv = bufferViews.optJSONObject(bvIdx) ?: return IntArray(vertexCount) { it }
            val buf = buffers.getOrNull(bv.optInt("buffer", 0)) ?: return IntArray(vertexCount) { it }
            val count = acc.optInt("count", 0)
            val compType = acc.optInt("componentType", 5123)
            val compBytes = when (compType) {
                5121 -> 1
                5123 -> 2
                5125 -> 4
                else -> 2
            }
            val stride = bv.optInt("byteStride", compBytes).coerceAtLeast(compBytes)
            val baseOff = acc.optInt("byteOffset", 0) + bv.optInt("byteOffset", 0)
            val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
            val out = IntArray(count)
            val maxValidVert = (vertexCount - 1).coerceAtLeast(0)
            for (i in 0 until count) {
                val p = baseOff + i * stride
                if (p + compBytes > buf.size) break
                val rawIdx = when (compType) {
                    5121 -> buf[p].toInt() and 0xFF
                    5123 -> bb.getShort(p).toInt() and 0xFFFF
                    5125 -> bb.getInt(p)
                    else -> bb.getShort(p).toInt() and 0xFFFF
                }
                out[i] = rawIdx.coerceIn(0, maxValidVert)
            }
            return out
        }

        val collected = ArrayList<Mesh3DSubMesh>()
        val animPrimitivesMap = HashMap<Int, List<Mesh3DPrimitiveSource>>()

        fun getOrBuildPrimitives(meshIdx: Int): List<Mesh3DPrimitiveSource> {
            animPrimitivesMap[meshIdx]?.let { return it }
            val meshObj = meshes.optJSONObject(meshIdx) ?: return emptyList()
            val prims = meshObj.optJSONArray("primitives") ?: return emptyList()
            val list = ArrayList<Mesh3DPrimitiveSource>(prims.length())
            for (pIdx in 0 until prims.length()) {
                val prim = prims.optJSONObject(pIdx) ?: continue
                val mode = prim.optInt("mode", 4) // 4 = TRIANGLES
                if (mode != 4) continue
                val attrs = prim.optJSONObject("attributes") ?: continue
                val posAcc = attrs.optInt("POSITION", -1)
                val rawPos = readAccessorFloats(posAcc, 3) ?: continue
                val vCount = rawPos.size / 3
                if (vCount < 3) continue
                val rawNorm = readAccessorFloats(attrs.optInt("NORMAL", -1), 3)
                val uvAcc0 = attrs.optInt("TEXCOORD_0", -1)
                val uvAcc1 = attrs.optInt("TEXCOORD_1", -1)
                val rawUv = readAccessorFloats(if (uvAcc0 >= 0) uvAcc0 else uvAcc1, 2)
                    ?: FloatArray(vCount * 2)
                val indices = readAccessorIndices(prim.optInt("indices", -1), vCount)

                var baseCol = 0xFFE2E8F0.toInt()
                var texTri: Triple<IntArray, Int, Int>? = null
                val matIdx = prim.optInt("material", -1)
                if (matIdx in 0 until materials.length()) {
                    val mat = materials.optJSONObject(matIdx)
                    val pbr = mat?.optJSONObject("pbrMetallicRoughness")
                    val specGloss = mat?.optJSONObject("extensions")?.optJSONObject("KHR_materials_pbrSpecularGlossiness")
                    val factorArr = pbr?.optJSONArray("baseColorFactor") ?: specGloss?.optJSONArray("diffuseFactor")
                    if (factorArr != null && factorArr.length() >= 3) {
                        val r = (factorArr.optDouble(0, 1.0).toFloat().coerceIn(0f, 1f) * 255f).toInt()
                        val g = (factorArr.optDouble(1, 1.0).toFloat().coerceIn(0f, 1f) * 255f).toInt()
                        val b = (factorArr.optDouble(2, 1.0).toFloat().coerceIn(0f, 1f) * 255f).toInt()
                        val a = if (factorArr.length() >= 4) {
                            (factorArr.optDouble(3, 1.0).toFloat().coerceIn(0f, 1f) * 255f).toInt()
                        } else 255
                        baseCol = (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    val texInfo = pbr?.optJSONObject("baseColorTexture")
                        ?: specGloss?.optJSONObject("diffuseTexture")
                        ?: mat?.optJSONObject("emissiveTexture")
                    val texIdx = texInfo?.optInt("index", -1) ?: -1
                    if (texIdx in 0 until textures.length()) {
                        val texObj = textures.optJSONObject(texIdx)
                        val srcImg = texObj?.optInt("source", -1) ?: -1
                        texTri = getImagePixels(srcImg)
                    }
                }
                list.add(
                    Mesh3DPrimitiveSource(
                        meshIndex = meshIdx,
                        localVertices = rawPos,
                        localNormals = rawNorm,
                        uvs = rawUv,
                        indices = indices,
                        baseColorArgb = baseCol,
                        texturePixels = texTri?.first,
                        textureWidth = texTri?.second ?: 0,
                        textureHeight = texTri?.third ?: 0
                    )
                )
            }
            animPrimitivesMap[meshIdx] = list
            return list
        }

        fun emitMeshWithMatrix(meshIdx: Int, worldMat: FloatArray) {
            val primSources = getOrBuildPrimitives(meshIdx)
            for (src in primSources) {
                collected.add(transformPrimitiveWithMatrix(src, worldMat))
            }
        }

        fun visitNode(nodeIdx: Int, parentMat: FloatArray) {
            val node = nodes.optJSONObject(nodeIdx) ?: return
            val localMat = nodeLocalMatrix(node)
            val worldMat = mulMat4(parentMat, localMat)
            if (node.has("mesh")) {
                emitMeshWithMatrix(node.optInt("mesh", -1), worldMat)
            }
            val children = node.optJSONArray("children")
            if (children != null) {
                for (c in 0 until children.length()) {
                    visitNode(children.optInt(c, -1), worldMat)
                }
            }
        }

        val scenes = root.optJSONArray("scenes")
        val sceneIdx = root.optInt("scene", 0)
        val rootScene = scenes?.optJSONObject(sceneIdx)
        val rootNodesArr = rootScene?.optJSONArray("nodes")
        val ident = identityMat4()
        val resolvedRootNodes = ArrayList<Int>()

        if (rootNodesArr != null && rootNodesArr.length() > 0) {
            for (i in 0 until rootNodesArr.length()) {
                val rIdx = rootNodesArr.optInt(i, -1)
                if (rIdx >= 0) resolvedRootNodes.add(rIdx)
                visitNode(rIdx, ident)
            }
        } else if (nodes.length() > 0) {
            val childSet = HashSet<Int>()
            for (i in 0 until nodes.length()) {
                val ch = nodes.optJSONObject(i)?.optJSONArray("children") ?: continue
                for (c in 0 until ch.length()) childSet.add(ch.optInt(c, -1))
            }
            for (i in 0 until nodes.length()) {
                if (i !in childSet) {
                    resolvedRootNodes.add(i)
                    visitNode(i, ident)
                }
            }
        } else {
            for (m in 0 until meshes.length()) {
                emitMeshWithMatrix(m, ident)
            }
        }

        // Parse glTF 2.0 node animations (translation, rotation, scale channels)
        var animData: Mesh3DAnimationData? = null
        val animsArr = root.optJSONArray("animations")
        if (animsArr != null && animsArr.length() > 0 && nodes.length() > 0 && animPrimitivesMap.isNotEmpty()) {
            val clips = ArrayList<Mesh3DAnimationClip>()
            for (aIdx in 0 until animsArr.length()) {
                val animObj = animsArr.optJSONObject(aIdx) ?: continue
                val samplersArr = animObj.optJSONArray("samplers") ?: continue
                val channelsArr = animObj.optJSONArray("channels") ?: continue
                val parsedSamplers = ArrayList<Mesh3DAnimSampler?>(samplersArr.length())
                for (sIdx in 0 until samplersArr.length()) {
                    val sObj = samplersArr.optJSONObject(sIdx)
                    if (sObj == null) {
                        parsedSamplers.add(null)
                        continue
                    }
                    val inputAcc = sObj.optInt("input", -1)
                    val outputAcc = sObj.optInt("output", -1)
                    val interp = sObj.optString("interpolation", "LINEAR").uppercase(Locale.ROOT)
                    val times = readAccessorFloats(inputAcc, 1)
                    val outType = accessors.optJSONObject(outputAcc)?.optString("type", "VEC3") ?: "VEC3"
                    val comps = when (outType) {
                        "VEC4" -> 4
                        "VEC3" -> 3
                        "VEC2" -> 2
                        "SCALAR" -> 1
                        else -> 3
                    }
                    val values = readAccessorFloats(outputAcc, comps)
                    if (times != null && values != null && times.isNotEmpty()) {
                        parsedSamplers.add(Mesh3DAnimSampler(times, values, interp, comps))
                    } else {
                        parsedSamplers.add(null)
                    }
                }
                val parsedChannels = ArrayList<Mesh3DAnimChannel>()
                var maxDur = 0f
                for (cIdx in 0 until channelsArr.length()) {
                    val cObj = channelsArr.optJSONObject(cIdx) ?: continue
                    val target = cObj.optJSONObject("target") ?: continue
                    val targetNode = target.optInt("node", -1)
                    val path = target.optString("path", "")
                    val samplerIdx = cObj.optInt("sampler", -1)
                    if (targetNode in 0 until nodes.length() && path in setOf("translation", "rotation", "scale")) {
                        val smp = parsedSamplers.getOrNull(samplerIdx)
                        if (smp != null) {
                            parsedChannels.add(Mesh3DAnimChannel(targetNode, path, smp))
                            val lastT = smp.inputTimes.lastOrNull() ?: 0f
                            if (lastT > maxDur) maxDur = lastT
                        }
                    }
                }
                if (parsedChannels.isNotEmpty()) {
                    clips.add(
                        Mesh3DAnimationClip(
                            name = animObj.optString("name", "Animação ${aIdx + 1}"),
                            durationSec = maxDur.coerceAtLeast(0.05f),
                            channels = parsedChannels
                        )
                    )
                }
            }
            if (clips.isNotEmpty()) {
                val nodeList = ArrayList<Mesh3DNodeData>(nodes.length())
                for (i in 0 until nodes.length()) {
                    val nObj = nodes.optJSONObject(i) ?: JSONObject()
                    val matArr = nObj.optJSONArray("matrix")
                    val mat = if (matArr != null && matArr.length() == 16) {
                        FloatArray(16) { matArr.optDouble(it, 0.0).toFloat() }
                    } else null
                    val tArr = nObj.optJSONArray("translation")
                    val rArr = nObj.optJSONArray("rotation")
                    val sArr = nObj.optJSONArray("scale")
                    val chArr = nObj.optJSONArray("children")
                    val chInts = if (chArr != null) {
                        IntArray(chArr.length()) { chArr.optInt(it, -1) }
                    } else IntArray(0)
                    nodeList.add(
                        Mesh3DNodeData(
                            translation = floatArrayOf(
                                tArr?.optDouble(0, 0.0)?.toFloat() ?: 0f,
                                tArr?.optDouble(1, 0.0)?.toFloat() ?: 0f,
                                tArr?.optDouble(2, 0.0)?.toFloat() ?: 0f
                            ),
                            rotation = floatArrayOf(
                                rArr?.optDouble(0, 0.0)?.toFloat() ?: 0f,
                                rArr?.optDouble(1, 0.0)?.toFloat() ?: 0f,
                                rArr?.optDouble(2, 0.0)?.toFloat() ?: 0f,
                                rArr?.optDouble(3, 1.0)?.toFloat() ?: 1f
                            ),
                            scale = floatArrayOf(
                                sArr?.optDouble(0, 1.0)?.toFloat() ?: 1f,
                                sArr?.optDouble(1, 1.0)?.toFloat() ?: 1f,
                                sArr?.optDouble(2, 1.0)?.toFloat() ?: 1f
                            ),
                            matrix = mat,
                            meshIndex = nObj.optInt("mesh", -1),
                            children = chInts
                        )
                    )
                }
                animData = Mesh3DAnimationData(
                    nodes = nodeList,
                    rootNodes = resolvedRootNodes.toIntArray(),
                    primitivesByMesh = animPrimitivesMap,
                    clips = clips,
                    normCenterX = 0f,
                    normCenterY = 0f,
                    normCenterZ = 0f,
                    normScale = 1f
                )
            }
        }

        // Extract skeleton bones from glTF skins.joints & node hierarchy
        val bones = ArrayList<Mesh3DBone>()
        val skinsArr = root.optJSONArray("skins")
        if (skinsArr != null && skinsArr.length() > 0 && nodes.length() > 0) {
            val nodeWorldPos = HashMap<Int, FloatArray>()
            val parentOfNode = HashMap<Int, Int>()
            fun captureJointWorld(nIdx: Int, parentIdx: Int, parentMat: FloatArray) {
                val nd = nodes.optJSONObject(nIdx) ?: return
                if (parentIdx >= 0) parentOfNode[nIdx] = parentIdx
                val wm = mulMat4(parentMat, nodeLocalMatrix(nd))
                nodeWorldPos[nIdx] = floatArrayOf(wm[12], wm[13], wm[14])
                val ch = nd.optJSONArray("children")
                if (ch != null) {
                    for (c in 0 until ch.length()) captureJointWorld(ch.optInt(c, -1), nIdx, wm)
                }
            }
            for (rIdx in resolvedRootNodes) captureJointWorld(rIdx, -1, ident)

            val jointSet = LinkedHashSet<Int>()
            for (s in 0 until skinsArr.length()) {
                val jArr = skinsArr.optJSONObject(s)?.optJSONArray("joints") ?: continue
                for (j in 0 until jArr.length()) {
                    val jIdx = jArr.optInt(j, -1)
                    if (jIdx in 0 until nodes.length()) jointSet.add(jIdx)
                }
            }
            val jointToBoneIdx = HashMap<Int, Int>()
            for ((bIdx, jNodeIdx) in jointSet.withIndex()) {
                jointToBoneIdx[jNodeIdx] = bIdx
            }
            for (jNodeIdx in jointSet) {
                val nObj = nodes.optJSONObject(jNodeIdx)
                val bName = nObj?.optString("name", "")?.ifBlank { "Osso_$jNodeIdx" } ?: "Osso_$jNodeIdx"
                val parentNode = parentOfNode[jNodeIdx]
                val pBoneIdx = if (parentNode != null) (jointToBoneIdx[parentNode] ?: -1) else -1
                val head = nodeWorldPos[jNodeIdx] ?: floatArrayOf(0f, 0f, 0f)
                val firstChild = nObj?.optJSONArray("children")?.optInt(0, -1) ?: -1
                val tail = nodeWorldPos[firstChild] ?: floatArrayOf(head[0], head[1] + 0.15f, head[2])
                bones.add(Mesh3DBone(name = bName, parentIndex = pBoneIdx, head = head, tail = tail))
            }
        }

        return Triple(collected, animData, bones)
    }

    private fun transformPrimitiveWithMatrix(src: Mesh3DPrimitiveSource, worldMat: FloatArray): Mesh3DSubMesh {
        val rawPos = src.localVertices
        val vCount = rawPos.size / 3
        val xfPos = FloatArray(rawPos.size)
        for (v in 0 until vCount) {
            val x = rawPos[v * 3]
            val y = rawPos[v * 3 + 1]
            val z = rawPos[v * 3 + 2]
            xfPos[v * 3] = worldMat[0] * x + worldMat[4] * y + worldMat[8] * z + worldMat[12]
            xfPos[v * 3 + 1] = worldMat[1] * x + worldMat[5] * y + worldMat[9] * z + worldMat[13]
            xfPos[v * 3 + 2] = worldMat[2] * x + worldMat[6] * y + worldMat[10] * z + worldMat[14]
        }
        val rawNorm = src.localNormals
        val xfNorm = if (rawNorm != null && rawNorm.size == xfPos.size) {
            FloatArray(xfPos.size).also { outN ->
                for (v in 0 until vCount) {
                    val nx = rawNorm[v * 3]
                    val ny = rawNorm[v * 3 + 1]
                    val nz = rawNorm[v * 3 + 2]
                    val rx = worldMat[0] * nx + worldMat[4] * ny + worldMat[8] * nz
                    val ry = worldMat[1] * nx + worldMat[5] * ny + worldMat[9] * nz
                    val rz = worldMat[2] * nx + worldMat[6] * ny + worldMat[10] * nz
                    val len = sqrt(rx * rx + ry * ry + rz * rz).coerceAtLeast(1e-5f)
                    outN[v * 3] = rx / len
                    outN[v * 3 + 1] = ry / len
                    outN[v * 3 + 2] = rz / len
                }
            }
        } else {
            computeNormals(xfPos, src.indices)
        }
        return Mesh3DSubMesh(
            vertices = xfPos,
            normals = xfNorm,
            uvs = src.uvs,
            indices = src.indices,
            baseColorArgb = src.baseColorArgb,
            texturePixels = src.texturePixels,
            textureWidth = src.textureWidth,
            textureHeight = src.textureHeight
        )
    }

    fun evaluateSceneAtTime(scene: Mesh3DSceneData, timeSec: Float): List<Mesh3DSubMesh> {
        val anim = scene.animData ?: return scene.subMeshes
        val clip = anim.clips.firstOrNull() ?: return scene.subMeshes
        val dur = clip.durationSec.coerceAtLeast(0.05f)
        val t = ((timeSec % dur) + dur) % dur

        // Mutable TRS state per node initialized to rest pose
        val nodeCount = anim.nodes.size
        val trans = Array(nodeCount) { idx -> anim.nodes[idx].translation.copyOf() }
        val rot = Array(nodeCount) { idx -> anim.nodes[idx].rotation.copyOf() }
        val scl = Array(nodeCount) { idx -> anim.nodes[idx].scale.copyOf() }
        val overridden = BooleanArray(nodeCount)

        for (ch in clip.channels) {
            val nIdx = ch.targetNode
            if (nIdx !in 0 until nodeCount) continue
            val smp = ch.sampler
            val sampled = sampleAnimSampler(smp, t, isQuat = ch.path == "rotation")
            when (ch.path) {
                "translation" -> if (sampled.size >= 3) {
                    trans[nIdx][0] = sampled[0]; trans[nIdx][1] = sampled[1]; trans[nIdx][2] = sampled[2]
                    overridden[nIdx] = true
                }
                "rotation" -> if (sampled.size >= 4) {
                    rot[nIdx][0] = sampled[0]; rot[nIdx][1] = sampled[1]; rot[nIdx][2] = sampled[2]; rot[nIdx][3] = sampled[3]
                    overridden[nIdx] = true
                }
                "scale" -> if (sampled.size >= 3) {
                    scl[nIdx][0] = sampled[0]; scl[nIdx][1] = sampled[1]; scl[nIdx][2] = sampled[2]
                    overridden[nIdx] = true
                }
            }
        }

        val outList = ArrayList<Mesh3DSubMesh>(scene.subMeshes.size)
        val cx = anim.normCenterX; val cy = anim.normCenterY; val cz = anim.normCenterZ
        val nScale = anim.normScale

        fun visitEval(nodeIdx: Int, parentMat: FloatArray) {
            if (nodeIdx !in 0 until nodeCount) return
            val node = anim.nodes[nodeIdx]
            val localMat = if (!overridden[nodeIdx] && node.matrix != null) {
                node.matrix
            } else {
                trsToMat4(trans[nodeIdx], rot[nodeIdx], scl[nodeIdx])
            }
            val worldMat = mulMat4(parentMat, localMat)
            if (node.meshIndex >= 0) {
                val prims = anim.primitivesByMesh[node.meshIndex].orEmpty()
                for (src in prims) {
                    val rawSub = transformPrimitiveWithMatrix(src, worldMat)
                    val vOut = FloatArray(rawSub.vertices.size)
                    var i = 0
                    while (i + 2 < rawSub.vertices.size) {
                        vOut[i] = (rawSub.vertices[i] - cx) * nScale
                        vOut[i + 1] = -(rawSub.vertices[i + 1] - cy) * nScale
                        vOut[i + 2] = (rawSub.vertices[i + 2] - cz) * nScale
                        i += 3
                    }
                    val nOut = FloatArray(vOut.size)
                    var nIdx = 0
                    while (nIdx + 2 < nOut.size) {
                        nOut[nIdx] = rawSub.normals[nIdx]
                        nOut[nIdx + 1] = -rawSub.normals[nIdx + 1]
                        nOut[nIdx + 2] = rawSub.normals[nIdx + 2]
                        nIdx += 3
                    }
                    outList.add(rawSub.copy(vertices = vOut, normals = nOut))
                }
            }
            for (chIdx in node.children) {
                visitEval(chIdx, worldMat)
            }
        }

        val ident = identityMat4()
        for (rIdx in anim.rootNodes) {
            visitEval(rIdx, ident)
        }
        return if (outList.isEmpty()) scene.subMeshes else outList
    }

    private fun sampleAnimSampler(smp: Mesh3DAnimSampler, t: Float, isQuat: Boolean): FloatArray {
        val times = smp.inputTimes
        val vals = smp.outputValues
        val comp = smp.components.coerceAtLeast(1)
        val n = times.size
        if (n <= 0) return FloatArray(comp)
        val strideMul = if (smp.interpolation == "CUBICSPLINE") 3 else 1
        fun readVal(keyIdx: Int, subIdx: Int = if (strideMul == 3) 1 else 0): FloatArray {
            val base = (keyIdx * strideMul + subIdx) * comp
            return FloatArray(comp) { c -> vals.getOrElse(base + c) { 0f } }
        }
        if (t <= times[0] || n == 1) return readVal(0)
        if (t >= times[n - 1]) return readVal(n - 1)

        var k0 = 0
        for (i in 0 until n - 1) {
            if (t >= times[i] && t <= times[i + 1]) {
                k0 = i
                break
            }
        }
        val k1 = (k0 + 1).coerceAtMost(n - 1)
        if (smp.interpolation == "STEP") return readVal(k0)
        val dt = (times[k1] - times[k0]).coerceAtLeast(1e-5f)
        val alpha = ((t - times[k0]) / dt).coerceIn(0f, 1f)
        val v0 = readVal(k0)
        val v1 = readVal(k1)
        val out = FloatArray(comp)
        if (isQuat && comp == 4) {
            var dot = v0[0] * v1[0] + v0[1] * v1[1] + v0[2] * v1[2] + v0[3] * v1[3]
            val sign = if (dot < 0f) -1f else 1f
            for (c in 0 until 4) {
                out[c] = v0[c] + (v1[c] * sign - v0[c]) * alpha
            }
            val len = sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2] + out[3] * out[3]).coerceAtLeast(1e-5f)
            for (c in 0 until 4) out[c] /= len
        } else {
            for (c in 0 until comp) {
                out[c] = v0[c] + (v1[c] - v0[c]) * alpha
            }
        }
        return out
    }

    private fun trsToMat4(t: FloatArray, r: FloatArray, s: FloatArray): FloatArray {
        val tx = t[0]; val ty = t[1]; val tz = t[2]
        val qx = r[0]; val qy = r[1]; val qz = r[2]; val qw = r[3]
        val sx = s[0]; val sy = s[1]; val sz = s[2]
        val x2 = qx + qx; val y2 = qy + qy; val z2 = qz + qz
        val xx = qx * x2; val xy = qx * y2; val xz = qx * z2
        val yy = qy * y2; val yz = qy * z2; val zz = qz * z2
        val wx = qw * x2; val wy = qw * y2; val wz = qw * z2
        return floatArrayOf(
            (1f - (yy + zz)) * sx, (xy + wz) * sx, (xz - wy) * sx, 0f,
            (xy - wz) * sy, (1f - (xx + zz)) * sy, (yz + wx) * sy, 0f,
            (xz + wy) * sz, (yz - wx) * sz, (1f - (xx + yy)) * sz, 0f,
            tx, ty, tz, 1f
        )
    }

    // -------------------------------------------------------------------------
    // Wavefront OBJ + MTL Importer (with Negative Indices, Quads/N-Gons & UV Textures)
    // -------------------------------------------------------------------------

    private data class MtlMaterial(
        var diffuseColor: Int = 0xFFE2E8F0.toInt(),
        var texturePixels: IntArray? = null,
        var textureWidth: Int = 0,
        var textureHeight: Int = 0
    )

    private fun parseObj(
        objText: String,
        siblingResolver: ((String) -> ByteArray?)?
    ): List<Mesh3DSubMesh> {
        val posList = ArrayList<Float>(1024)
        val uvList = ArrayList<Float>(1024)
        val normList = ArrayList<Float>(1024)
        val materialsMap = HashMap<String, MtlMaterial>()

        data class GroupBucket(
            val matName: String,
            val vertBuilder: MutableList<Float> = ArrayList(),
            val normBuilder: MutableList<Float> = ArrayList(),
            val uvBuilder: MutableList<Float> = ArrayList(),
            val idxBuilder: MutableList<Int> = ArrayList(),
            val keyToIndex: HashMap<String, Int> = HashMap()
        )

        val buckets = LinkedHashMap<String, GroupBucket>()
        var activeMatName = "__default__"

        fun extractMtlTexturePath(rawArg: String): String {
            val quoteMatch = Regex("[\"']([^\"']+)[\"']").findAll(rawArg).lastOrNull()
            if (quoteMatch != null) return quoteMatch.groupValues[1].trim()
            val cleaned = rawArg.replace(Regex("-(?:bm|blendu|blendv|clamp|imfchan|mm|o|s|t|texres)\\s+[^\\s]+(?:\\s+[-+]?\\d*\\.?\\d+){0,3}"), " ").trim()
            return if (cleaned.isNotBlank()) cleaned else rawArg.split(Regex("\\s+")).lastOrNull().orEmpty()
        }

        fun parseMtl(mtlText: String) {
            var curName: String? = null
            mtlText.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                val parts = line.split(Regex("\\s+"), limit = 2)
                val kw = parts[0].lowercase(Locale.ROOT)
                val arg = parts.getOrNull(1)?.trim() ?: ""
                when (kw) {
                    "newmtl" -> {
                        curName = arg
                        materialsMap.getOrPut(arg) { MtlMaterial() }
                    }
                    "kd" -> {
                        val c = curName?.let { materialsMap[it] } ?: return@forEach
                        val vals = arg.split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }
                        if (vals.size >= 3) {
                            val r = (vals[0].coerceIn(0f, 1f) * 255f).toInt()
                            val g = (vals[1].coerceIn(0f, 1f) * 255f).toInt()
                            val b = (vals[2].coerceIn(0f, 1f) * 255f).toInt()
                            c.diffuseColor = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }
                    "map_kd", "map_ka", "map_ke", "map_d" -> {
                        val c = curName?.let { materialsMap[it] } ?: return@forEach
                        if (kw != "map_kd" && c.texturePixels != null) return@forEach
                        val texPath = extractMtlTexturePath(arg)
                        if (texPath.isNotBlank() && siblingResolver != null) {
                            val imgBytes = siblingResolver(texPath)
                                ?: siblingResolver(texPath.substringAfterLast('/').substringAfterLast('\\'))
                            if (imgBytes != null) {
                                decodeBitmapPixels(imgBytes)?.let { (pix, w, h) ->
                                    c.texturePixels = pix
                                    c.textureWidth = w
                                    c.textureHeight = h
                                }
                            }
                        }
                    }
                }
            }
        }

        fun resolveIndex(rawIdx: Int, count: Int): Int {
            return if (rawIdx > 0) (rawIdx - 1) else (count + rawIdx)
        }

        objText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val ws = line.indexOfFirst { it.isWhitespace() }
            val kw = (if (ws > 0) line.substring(0, ws) else line).lowercase(Locale.ROOT)
            val rest = if (ws > 0) line.substring(ws + 1).trim() else ""

            when (kw) {
                "mtllib" -> {
                    if (rest.isNotBlank() && siblingResolver != null) {
                        val mtlDirect = siblingResolver(rest.trim('"', '\''))
                        if (mtlDirect != null) {
                            parseMtl(mtlDirect.toString(Charsets.UTF_8))
                        } else {
                            val mtlCandidates = Regex("[\"']([^\"']+)[\"']|\\S+").findAll(rest)
                                .map { it.groupValues[1].ifBlank { it.value }.trim('"', '\'') }
                            mtlCandidates.forEach { candidate ->
                                siblingResolver(candidate)?.let { mb -> parseMtl(mb.toString(Charsets.UTF_8)) }
                            }
                        }
                    }
                }
                "usemtl" -> {
                    activeMatName = rest.ifBlank { "__default__" }
                }
                "v" -> {
                    val nums = rest.split(Regex("\\s+"))
                    if (nums.size >= 3) {
                        posList.add(nums[0].toFloatOrNull() ?: 0f)
                        posList.add(nums[1].toFloatOrNull() ?: 0f)
                        posList.add(nums[2].toFloatOrNull() ?: 0f)
                    }
                }
                "vt" -> {
                    val nums = rest.split(Regex("\\s+"))
                    if (nums.size >= 2) {
                        val u = nums[0].toFloatOrNull() ?: 0f
                        val v = nums[1].toFloatOrNull() ?: 0f
                        uvList.add(u)
                        // OBJ v=0 is bottom, Bitmap v=0 is top
                        uvList.add(1f - v)
                    }
                }
                "vn" -> {
                    val nums = rest.split(Regex("\\s+"))
                    if (nums.size >= 3) {
                        normList.add(nums[0].toFloatOrNull() ?: 0f)
                        normList.add(nums[1].toFloatOrNull() ?: 0f)
                        normList.add(nums[2].toFloatOrNull() ?: 0f)
                    }
                }
                "f" -> {
                    val tokens = rest.split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (tokens.size < 3) return@forEach
                    val bucket = buckets.getOrPut(activeMatName) { GroupBucket(activeMatName) }
                    val vCountTotal = posList.size / 3
                    val vtCountTotal = uvList.size / 2
                    val vnCountTotal = normList.size / 3

                    fun emitFaceVertex(tok: String): Int {
                        bucket.keyToIndex[tok]?.let { return it }
                        val slashes = tok.split('/')
                        val vRaw = slashes.getOrNull(0)?.toIntOrNull() ?: 1
                        val vtRaw = slashes.getOrNull(1)?.toIntOrNull()
                        val vnRaw = slashes.getOrNull(2)?.toIntOrNull()

                        val vi = resolveIndex(vRaw, vCountTotal).coerceIn(0, max(0, vCountTotal - 1))
                        val newIdx = bucket.vertBuilder.size / 3
                        if (vCountTotal > 0) {
                            bucket.vertBuilder.add(posList[vi * 3])
                            bucket.vertBuilder.add(posList[vi * 3 + 1])
                            bucket.vertBuilder.add(posList[vi * 3 + 2])
                        } else {
                            bucket.vertBuilder.add(0f); bucket.vertBuilder.add(0f); bucket.vertBuilder.add(0f)
                        }

                        if (vtRaw != null && vtCountTotal > 0) {
                            val vti = resolveIndex(vtRaw, vtCountTotal).coerceIn(0, vtCountTotal - 1)
                            bucket.uvBuilder.add(uvList[vti * 2])
                            bucket.uvBuilder.add(uvList[vti * 2 + 1])
                        } else {
                            bucket.uvBuilder.add(0f); bucket.uvBuilder.add(0f)
                        }

                        if (vnRaw != null && vnCountTotal > 0) {
                            val vni = resolveIndex(vnRaw, vnCountTotal).coerceIn(0, vnCountTotal - 1)
                            bucket.normBuilder.add(normList[vni * 3])
                            bucket.normBuilder.add(normList[vni * 3 + 1])
                            bucket.normBuilder.add(normList[vni * 3 + 2])
                        } else {
                            bucket.normBuilder.add(0f); bucket.normBuilder.add(1f); bucket.normBuilder.add(0f)
                        }

                        bucket.keyToIndex[tok] = newIdx
                        return newIdx
                    }

                    val faceVerts = tokens.map { emitFaceVertex(it) }
                    for (t in 1 until faceVerts.size - 1) {
                        bucket.idxBuilder.add(faceVerts[0])
                        bucket.idxBuilder.add(faceVerts[t])
                        bucket.idxBuilder.add(faceVerts[t + 1])
                    }
                }
            }
        }

        val out = ArrayList<Mesh3DSubMesh>()
        for (b in buckets.values) {
            if (b.idxBuilder.isEmpty() || b.vertBuilder.size < 9) continue
            val verts = b.vertBuilder.toFloatArray()
            val idxs = b.idxBuilder.toIntArray()
            val norms = if (normList.isEmpty()) computeNormals(verts, idxs) else b.normBuilder.toFloatArray()
            val uvs = b.uvBuilder.toFloatArray()
            val mat = materialsMap[b.matName]
            out.add(
                Mesh3DSubMesh(
                    vertices = verts,
                    normals = norms,
                    uvs = uvs,
                    indices = idxs,
                    baseColorArgb = mat?.diffuseColor ?: 0xFFE2E8F0.toInt(),
                    texturePixels = mat?.texturePixels,
                    textureWidth = mat?.textureWidth ?: 0,
                    textureHeight = mat?.textureHeight ?: 0
                )
            )
        }
        return out
    }

    // -------------------------------------------------------------------------
    // STL & PLY Fallbacks
    // -------------------------------------------------------------------------

    private fun parseStl(bytes: ByteArray): List<Mesh3DSubMesh> {
        if (bytes.size < 84) return emptyList()
        val headText = bytes.decodeToString(0, min(bytes.size, 256)).lowercase(Locale.ROOT)
        if (headText.trimStart().startsWith("solid") && headText.contains("facet")) {
            val verts = ArrayList<Float>()
            val norms = ArrayList<Float>()
            val uvs = ArrayList<Float>()
            val idxs = ArrayList<Int>()
            bytes.toString(Charsets.UTF_8).lineSequence().forEach { l ->
                val t = l.trim()
                if (t.startsWith("vertex ")) {
                    val p = t.removePrefix("vertex ").trim().split(Regex("\\s+"))
                    if (p.size >= 3) {
                        val vIdx = verts.size / 3
                        verts.add(p[0].toFloatOrNull() ?: 0f)
                        verts.add(p[1].toFloatOrNull() ?: 0f)
                        verts.add(p[2].toFloatOrNull() ?: 0f)
                        uvs.add(0f); uvs.add(0f)
                        idxs.add(vIdx)
                    }
                }
            }
            if (idxs.size >= 3) {
                val va = verts.toFloatArray()
                val ia = idxs.toIntArray()
                return listOf(Mesh3DSubMesh(va, computeNormals(va, ia), uvs.toFloatArray(), ia))
            }
        }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val triCount = bb.getInt(80).coerceIn(0, (bytes.size - 84) / 50)
        if (triCount <= 0) return emptyList()
        val verts = FloatArray(triCount * 9)
        val uvs = FloatArray(triCount * 6)
        val idxs = IntArray(triCount * 3) { it }
        var off = 84
        for (i in 0 until triCount) {
            for (v in 0 until 3) {
                val p = off + 12 + v * 12
                verts[i * 9 + v * 3] = bb.getFloat(p)
                verts[i * 9 + v * 3 + 1] = bb.getFloat(p + 4)
                verts[i * 9 + v * 3 + 2] = bb.getFloat(p + 8)
            }
            off += 50
        }
        return listOf(Mesh3DSubMesh(verts, computeNormals(verts, idxs), uvs, idxs))
    }

    private fun parsePly(text: String): List<Mesh3DSubMesh> {
        val lines = text.lines()
        var vertCount = 0
        var faceCount = 0
        var headerEnd = -1
        for (i in lines.indices) {
            val l = lines[i].trim()
            if (l.startsWith("element vertex ")) vertCount = l.removePrefix("element vertex ").trim().toIntOrNull() ?: 0
            if (l.startsWith("element face ")) faceCount = l.removePrefix("element face ").trim().toIntOrNull() ?: 0
            if (l == "end_header") { headerEnd = i; break }
        }
        if (headerEnd < 0 || vertCount <= 0) return emptyList()
        val verts = FloatArray(vertCount * 3)
        val uvs = FloatArray(vertCount * 2)
        var cursor = headerEnd + 1
        for (v in 0 until vertCount) {
            if (cursor >= lines.size) break
            val p = lines[cursor++].trim().split(Regex("\\s+"))
            verts[v * 3] = p.getOrNull(0)?.toFloatOrNull() ?: 0f
            verts[v * 3 + 1] = p.getOrNull(1)?.toFloatOrNull() ?: 0f
            verts[v * 3 + 2] = p.getOrNull(2)?.toFloatOrNull() ?: 0f
        }
        val idxList = ArrayList<Int>(faceCount * 3)
        for (f in 0 until faceCount) {
            if (cursor >= lines.size) break
            val p = lines[cursor++].trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
            val n = p.firstOrNull() ?: 0
            if (n >= 3 && p.size >= n + 1) {
                for (t in 1 until n - 1) {
                    idxList.add(p[1])
                    idxList.add(p[1 + t])
                    idxList.add(p[1 + t + 1])
                }
            }
        }
        if (idxList.size < 3) return emptyList()
        val ia = idxList.toIntArray()
        return listOf(Mesh3DSubMesh(verts, computeNormals(verts, ia), uvs, ia))
    }

    // -------------------------------------------------------------------------
    // Built-in Textured 3D Primitives (Instant Test & Scene Blocking)
    // -------------------------------------------------------------------------

    private fun buildBuiltInPrimitive(uriKey: String, customName: String? = null): Mesh3DSceneData {
        val cleanName = customName ?: BuiltIn3DPrimitive.entries.firstOrNull { it.uriKey == uriKey }?.labelPt ?: "Objeto 3D"
        val checkerW = 64
        val checkerH = 64
        val texPixels = IntArray(checkerW * checkerH)
        for (y in 0 until checkerH) {
            for (x in 0 until checkerW) {
                val isBorder = x < 2 || x >= checkerW - 2 || y < 2 || y >= checkerH - 2
                val isSubLine = (x % 16 == 0) || (y % 16 == 0)
                val fx = x.toFloat() / checkerW
                val fy = y.toFloat() / checkerH
                val gradBase = (224f - fy * 46f + fx * 18f).toInt().coerceIn(130, 245)
                val shade = when {
                    isBorder -> (gradBase + 22).coerceAtMost(255)
                    isSubLine -> (gradBase - 24).coerceAtLeast(110)
                    else -> gradBase
                }
                val r = (shade * 0.94f).toInt().coerceIn(0, 255)
                val g = (shade * 0.97f).toInt().coerceIn(0, 255)
                val b = (shade * 1.03f).toInt().coerceIn(0, 255)
                texPixels[y * checkerW + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val subMesh = when (uriKey) {
            BuiltIn3DPrimitive.PYRAMID.uriKey -> {
                val h = 110f
                val w = 95f
                val verts = floatArrayOf(
                    // Front
                    0f, -h, 0f, -w, h, w, w, h, w,
                    // Right
                    0f, -h, 0f, w, h, w, w, h, -w,
                    // Back
                    0f, -h, 0f, w, h, -w, -w, h, -w,
                    // Left
                    0f, -h, 0f, -w, h, -w, -w, h, w,
                    // Base 2 tris
                    -w, h, w, -w, h, -w, w, h, -w,
                    -w, h, w, w, h, -w, w, h, w
                )
                val uvs = FloatArray(18 * 2)
                for (t in 0 until 6) {
                    val o = t * 6
                    uvs[o] = 0.5f; uvs[o + 1] = 0f
                    uvs[o + 2] = 0f; uvs[o + 3] = 1f
                    uvs[o + 4] = 1f; uvs[o + 5] = 1f
                }
                val idxs = IntArray(18) { it }
                Mesh3DSubMesh(verts, computeNormals(verts, idxs), uvs, idxs, 0xFFFFB74D.toInt(), texPixels, checkerW, checkerH)
            }
            BuiltIn3DPrimitive.SPHERE.uriKey -> {
                val stacks = 10
                val slices = 16
                val radius = 105f
                val vList = ArrayList<Float>()
                val nList = ArrayList<Float>()
                val uvList = ArrayList<Float>()
                val iList = ArrayList<Int>()
                for (st in 0..stacks) {
                    val phi = (st.toFloat() / stacks) * PI.toFloat()
                    val y = -cos(phi)
                    val sinPhi = sin(phi)
                    for (sl in 0..slices) {
                        val theta = (sl.toFloat() / slices) * 2f * PI.toFloat()
                        val x = sinPhi * cos(theta)
                        val z = sinPhi * sin(theta)
                        vList.add(x * radius); vList.add(y * radius); vList.add(z * radius)
                        nList.add(x); nList.add(y); nList.add(z)
                        uvList.add(sl.toFloat() / slices); uvList.add(st.toFloat() / stacks)
                    }
                }
                val stride = slices + 1
                for (st in 0 until stacks) {
                    for (sl in 0 until slices) {
                        val a = st * stride + sl
                        val b = a + stride
                        iList.add(a); iList.add(b); iList.add(a + 1)
                        iList.add(a + 1); iList.add(b); iList.add(b + 1)
                    }
                }
                Mesh3DSubMesh(
                    vList.toFloatArray(), nList.toFloatArray(), uvList.toFloatArray(), iList.toIntArray(),
                    0xFF4FC3F7.toInt(), texPixels, checkerW, checkerH
                )
            }
            BuiltIn3DPrimitive.PLATFORM.uriKey -> {
                val sx = 240f
                val sy = 14f
                val sz = 240f
                val idxsAndData = buildCubeGeometry(sx, sy, sz)
                Mesh3DSubMesh(
                    idxsAndData.first,
                    computeNormals(idxsAndData.first, idxsAndData.third),
                    idxsAndData.second,
                    idxsAndData.third,
                    0xFFB0BEC5.toInt(),
                    texPixels,
                    checkerW,
                    checkerH
                )
            }
            else -> {
                val idxsAndData = buildCubeGeometry(95f, 95f, 95f)
                Mesh3DSubMesh(
                    idxsAndData.first,
                    computeNormals(idxsAndData.first, idxsAndData.third),
                    idxsAndData.second,
                    idxsAndData.third,
                    0xFF80CBC4.toInt(),
                    texPixels,
                    checkerW,
                    checkerH
                )
            }
        }
        return normalizeAndWrapScene(uriKey, cleanName, listOf(subMesh), targetRadius = 115f)
    }

    private fun buildCubeGeometry(hx: Float, hy: Float, hz: Float): Triple<FloatArray, FloatArray, IntArray> {
        val faceVerts = floatArrayOf(
            // Front (+Z)
            -hx, -hy, hz, hx, -hy, hz, hx, hy, hz, -hx, hy, hz,
            // Back (-Z)
            hx, -hy, -hz, -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz,
            // Left (-X)
            -hx, -hy, -hz, -hx, -hy, hz, -hx, hy, hz, -hx, hy, -hz,
            // Right (+X)
            hx, -hy, hz, hx, -hy, -hz, hx, hy, -hz, hx, hy, hz,
            // Top (-Y in screen coords, up)
            -hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz,
            // Bottom (+Y)
            -hx, hy, hz, hx, hy, hz, hx, hy, -hz, -hx, hy, -hz
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
        return Triple(faceVerts, uvs, idxs)
    }

    // -------------------------------------------------------------------------
    // Geometry Normalization & Math Utilities
    // -------------------------------------------------------------------------

    private fun normalizeAndWrapScene(
        id: String,
        name: String,
        subMeshes: List<Mesh3DSubMesh>,
        targetRadius: Float = 140f,
        animData: Mesh3DAnimationData? = null,
        bones: List<Mesh3DBone> = emptyList()
    ): Mesh3DSceneData {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (sm in subMeshes) {
            var i = 0
            while (i + 2 < sm.vertices.size) {
                val x = sm.vertices[i]
                val y = sm.vertices[i + 1]
                val z = sm.vertices[i + 2]
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
                i += 3
            }
        }
        if (minX == Float.MAX_VALUE) {
            return Mesh3DSceneData(id, name, subMeshes, targetRadius, animData, bones)
        }
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val cz = (minZ + maxZ) * 0.5f
        val extent = max(max(maxX - minX, maxY - minY), maxZ - minZ).coerceAtLeast(1e-4f)
        val scale = (targetRadius * 2f) / extent

        val normalized = subMeshes.map { sm ->
            val vOut = FloatArray(sm.vertices.size)
            var i = 0
            while (i + 2 < sm.vertices.size) {
                vOut[i] = (sm.vertices[i] - cx) * scale
                // Keep Android 3D screen-down Y convention (-Y = up) for external glTF/OBJ models
                vOut[i + 1] = -(sm.vertices[i + 1] - cy) * scale
                vOut[i + 2] = (sm.vertices[i + 2] - cz) * scale
                i += 3
            }
            // Flip Y component of normals so lighting matches the Y-flipped normalized mesh
            val nOut = if (sm.normals.size == vOut.size) {
                FloatArray(vOut.size).also { arr ->
                    var ni = 0
                    while (ni + 2 < arr.size) {
                        arr[ni] = sm.normals[ni]
                        arr[ni + 1] = -sm.normals[ni + 1]
                        arr[ni + 2] = sm.normals[ni + 2]
                        ni += 3
                    }
                }
            } else {
                computeNormals(vOut, sm.indices)
            }
            val safeUvs = ensureNonZeroUvs(vOut, sm.uvs)
            sm.copy(vertices = vOut, normals = nOut, uvs = safeUvs)
        }
        val normalizedBones = bones.map { b ->
            b.copy(
                head = floatArrayOf(
                    (b.head[0] - cx) * scale,
                    -(b.head[1] - cy) * scale,
                    (b.head[2] - cz) * scale
                ),
                tail = floatArrayOf(
                    (b.tail[0] - cx) * scale,
                    -(b.tail[1] - cy) * scale,
                    (b.tail[2] - cz) * scale
                )
            )
        }
        val finalAnimData = animData?.copy(
            normCenterX = cx,
            normCenterY = cy,
            normCenterZ = cz,
            normScale = scale
        )
        return Mesh3DSceneData(
            id = id,
            name = name,
            subMeshes = normalized,
            boundingRadius = targetRadius,
            animData = finalAnimData,
            bones = normalizedBones
        )
    }

    private fun ensureNonZeroUvs(vertices: FloatArray, existingUvs: FloatArray): FloatArray {
        val vCount = vertices.size / 3
        if (existingUvs.size >= vCount * 2) {
            var anyNonZero = false
            for (k in 0 until vCount * 2) {
                if (kotlin.math.abs(existingUvs[k]) > 1e-4f) {
                    anyNonZero = true
                    break
                }
            }
            if (anyNonZero) return existingUvs
        }
        return FbxAndMax3DParser.generatePlanarFallbackUvs(vertices)
    }

    private fun computeNormals(vertices: FloatArray, indices: IntArray): FloatArray {
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

    private fun decodeBitmapPixels(bytes: ByteArray): Triple<IntArray, Int, Int>? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            val maxDim = max(opts.outWidth, opts.outHeight)
            var sample = 1
            while (maxDim / sample > 1024) sample *= 2
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
            val w = bmp.width.coerceAtLeast(1)
            val h = bmp.height.coerceAtLeast(1)
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            bmp.recycle()
            Triple(pixels, w, h)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeDataUri(uri: String): ByteArray? {
        val comma = uri.indexOf(',')
        if (comma < 0) return null
        val payload = uri.substring(comma + 1)
        return try {
            Base64.decode(payload, Base64.DEFAULT)
        } catch (_: Exception) {
            null
        }
    }

    private fun identityMat4(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    private fun mulMat4(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                r[col * 4 + row] =
                    a[row] * b[col * 4] +
                    a[4 + row] * b[col * 4 + 1] +
                    a[8 + row] * b[col * 4 + 2] +
                    a[12 + row] * b[col * 4 + 3]
            }
        }
        return r
    }

    private fun nodeLocalMatrix(node: JSONObject): FloatArray {
        val matArr = node.optJSONArray("matrix")
        if (matArr != null && matArr.length() == 16) {
            return FloatArray(16) { matArr.optDouble(it, 0.0).toFloat() }
        }
        val t = node.optJSONArray("translation")
        val r = node.optJSONArray("rotation")
        val s = node.optJSONArray("scale")
        val tx = t?.optDouble(0, 0.0)?.toFloat() ?: 0f
        val ty = t?.optDouble(1, 0.0)?.toFloat() ?: 0f
        val tz = t?.optDouble(2, 0.0)?.toFloat() ?: 0f
        val sx = s?.optDouble(0, 1.0)?.toFloat() ?: 1f
        val sy = s?.optDouble(1, 1.0)?.toFloat() ?: 1f
        val sz = s?.optDouble(2, 1.0)?.toFloat() ?: 1f
        val qx = r?.optDouble(0, 0.0)?.toFloat() ?: 0f
        val qy = r?.optDouble(1, 0.0)?.toFloat() ?: 0f
        val qz = r?.optDouble(2, 0.0)?.toFloat() ?: 0f
        val qw = r?.optDouble(3, 1.0)?.toFloat() ?: 1f

        val x2 = qx + qx; val y2 = qy + qy; val z2 = qz + qz
        val xx = qx * x2; val xy = qx * y2; val xz = qx * z2
        val yy = qy * y2; val yz = qy * z2; val zz = qz * z2
        val wx = qw * x2; val wy = qw * y2; val wz = qw * z2

        return floatArrayOf(
            (1f - (yy + zz)) * sx, (xy + wz) * sx, (xz - wy) * sx, 0f,
            (xy - wz) * sy, (1f - (xx + zz)) * sy, (yz + wx) * sy, 0f,
            (xz + wy) * sz, (yz - wx) * sz, (1f - (xx + yy)) * sz, 0f,
            tx, ty, tz, 1f
        )
    }

    private fun detectFormatByHeader(bytes: ByteArray, fileName: String): String {
        if (isGlbHeader(bytes)) return "glb"
        if (isZipHeader(bytes)) return "zip"
        if (isRarHeader(bytes)) return "rar"
        if (FbxAndMax3DParser.isFbxBinary(bytes) || FbxAndMax3DParser.isFbxAscii(bytes)) return "fbx"
        if (FbxAndMax3DParser.is3dsBinary(bytes)) return "3ds"
        if (FbxAndMax3DParser.isMaxCompoundFile(bytes)) return "max"
        val head = bytes.decodeToString(0, min(bytes.size, 64)).trimStart()
        if (head.startsWith("{")) return "gltf"
        if (head.lowercase(Locale.ROOT).startsWith("ply")) return "ply"
        if (head.lowercase(Locale.ROOT).startsWith("solid")) return "stl"
        return fileName.substringAfterLast('.', "obj").lowercase(Locale.ROOT)
    }

    /**
     * Exports a SceneObject3D with its effective UVs and composited UV paint texture
     * as a portable ZIP bundle containing `.obj`, `.mtl`, and `texture_uv.png`.
     */
    fun exportSceneObject3DAsObjZipBytes(
        obj: com.example.model.SceneObject3D,
        meshData: Mesh3DSceneData
    ): ByteArray {
        val baseName = sanitizeFileName(obj.name).substringBeforeLast('.').ifBlank { "objeto_3d" }
        val activeSlot = obj.uvSlots.getOrNull(obj.activeUvSlotIndex)
            ?: obj.uvSlots.firstOrNull()
            ?: com.example.model.ObjectUVMapSlot()
        val firstSm = meshData.subMeshes.firstOrNull()
        val compTex = Studio3DPaintEngine.resolveCompositeObjectTexture(
            obj = obj,
            basePixels = firstSm?.texturePixels,
            baseW = firstSm?.textureWidth ?: 0,
            baseH = firstSm?.textureHeight ?: 0,
            baseColorArgb = firstSm?.baseColorArgb ?: 0xFFE2E8F0.toInt()
        )
        val texW = compTex?.width ?: firstSm?.textureWidth ?: 256
        val texH = compTex?.height ?: firstSm?.textureHeight ?: 256
        val texPixels = compTex?.pixels ?: firstSm?.texturePixels ?: IntArray(texW * texH) { 0xFFE2E8F0.toInt() }
        val pngBytes = ByteArrayOutputStream().use { pngOut ->
            val bmp = Bitmap.createBitmap(texW.coerceAtLeast(1), texH.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            bmp.setPixels(texPixels, 0, texW.coerceAtLeast(1), 0, 0, texW.coerceAtLeast(1), texH.coerceAtLeast(1))
            bmp.compress(Bitmap.CompressFormat.PNG, 100, pngOut)
            bmp.recycle()
            pngOut.toByteArray()
        }

        val objSb = StringBuilder(4096)
        objSb.append("# Kodanima 3D Studio Export: ").append(obj.name).append('\n')
        objSb.append("mtllib ").append(baseName).append(".mtl\n")
        objSb.append("o ").append(baseName).append('\n')
        objSb.append("usemtl material_uv\n")

        var vertOffset = 1
        var customUvOffset = 0
        for (sm in meshData.subMeshes) {
            val effUvs = Studio3DPaintEngine.generateUVsForProjection(sm.vertices, sm.uvs, activeSlot, customUvOffset)
            val vCount = sm.vertices.size / 3
            for (v in 0 until vCount) {
                val x = sm.vertices[v * 3]
                val y = -sm.vertices[v * 3 + 1] // Convert back from Android screen-down Y to standard Y-up OBJ
                val z = sm.vertices[v * 3 + 2]
                objSb.append(String.format(Locale.US, "v %.5f %.5f %.5f\n", x, y, z))
            }
            for (v in 0 until vCount) {
                val u = if (v * 2 < effUvs.size) effUvs[v * 2] else 0f
                val vTex = if (v * 2 + 1 < effUvs.size) (1f - effUvs[v * 2 + 1]) else 0f
                objSb.append(String.format(Locale.US, "vt %.5f %.5f\n", u, vTex))
            }
            for (v in 0 until vCount) {
                val nx = if (v * 3 < sm.normals.size) sm.normals[v * 3] else 0f
                val ny = if (v * 3 + 1 < sm.normals.size) -sm.normals[v * 3 + 1] else 1f
                val nz = if (v * 3 + 2 < sm.normals.size) sm.normals[v * 3 + 2] else 0f
                objSb.append(String.format(Locale.US, "vn %.5f %.5f %.5f\n", nx, ny, nz))
            }
            var t = 0
            while (t + 2 < sm.indices.size) {
                val a = sm.indices[t] + vertOffset
                val b = sm.indices[t + 1] + vertOffset
                val c = sm.indices[t + 2] + vertOffset
                objSb.append("f $a/$a/$a $b/$b/$b $c/$c/$c\n")
                t += 3
            }
            vertOffset += vCount
            customUvOffset += vCount
        }

        val mtlText = buildString {
            append("# Kodanima 3D Studio Material\n")
            append("newmtl material_uv\n")
            append("Kd 1.000 1.000 1.000\n")
            append("Ka 0.200 0.200 0.200\n")
            append("Ks 0.150 0.150 0.150\n")
            append("d 1.0\n")
            append("map_Kd texture_uv.png\n")
        }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("$baseName.obj"))
            zos.write(objSb.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("$baseName.mtl"))
            zos.write(mtlText.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("texture_uv.png"))
            zos.write(pngBytes)
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun safeUrlDecode(raw: String): String {
        return try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (_: Exception) {
            raw
        }
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "cena_3d" }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        } ?: uri.lastPathSegment
    }
}
