package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.model.AnimationProject
import com.example.model.Frame
import com.example.model.Layer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * High-performance project compression & packaging engine for Kodanima 2D (.koda2d).
 *
 * Implements the user's storage architecture:
 * 1. While working / Ctrl+S / Save icon: saves uncompressed in internal working directory
 *    (`context.filesDir/active_projects/{id}`) for maximum drawing and editing speed.
 * 2. When closing project / returning to Home: compacts everything (project.json + audio + video media)
 *    into a single optimized compressed package (.koda2d), saves to the chosen folder,
 *    and immediately deletes the uncompressed working directory to reclaim device storage.
 * 3. When opening project: extracts .koda2d into working directory for editing.
 */
object ProjectCompressionManager {

    const val KODA2D_EXTENSION = ".koda2d"

    /**
     * Working directory for uncompressed active projects during editing session.
     */
    fun getWorkingDir(context: Context, projectId: String): File {
        val dir = File(context.filesDir, "active_projects/$projectId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Default storage folder for compressed .koda2d projects.
     */
    fun getDefaultProjectsDirectory(context: Context): File {
        val ext = context.getExternalFilesDir(null)
        val base = ext ?: context.filesDir
        val folder = File(base, "KodanimaProjects")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    /**
     * Resolves the configured storage directory for .koda2d packages.
     */
    fun getProjectsDirectory(context: Context, customPath: String? = null): File {
        if (!customPath.isNullOrBlank()) {
            val customDir = File(customPath)
            if (customDir.exists() && customDir.isDirectory && customDir.canWrite()) {
                return customDir
            }
            try {
                if (customDir.mkdirs()) return customDir
            } catch (_: Exception) {}
        }
        return getDefaultProjectsDirectory(context)
    }

    /**
     * Saves project in uncompressed state in the active working directory (used during active editing / Ctrl+S).
     */
    fun saveWorkingProject(context: Context, project: AnimationProject): File {
        val workingDir = getWorkingDir(context, project.id)
        val projectJsonFile = File(workingDir, "project.json")
        val jsonString = FileImportExportManager.projectToJson(project)
        projectJsonFile.writeText(jsonString)
        return projectJsonFile
    }

    /**
     * Compacts the entire active project into a .koda2d file in the destination folder,
     * and deletes the uncompressed working directory from disk.
     */
    fun compressAndCloseProject(
        context: Context,
        project: AnimationProject,
        customPath: String? = null
    ): File {
        val workingDir = getWorkingDir(context, project.id)
        // Ensure latest data is written before compressing
        val projectJsonFile = File(workingDir, "project.json")
        projectJsonFile.writeText(FileImportExportManager.projectToJson(project))

        val destDir = getProjectsDirectory(context, customPath)
        val safeName = project.name.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "").trim().replace(' ', '_')
        val fileName = if (safeName.isNotEmpty()) "${safeName}_${project.id}$KODA2D_EXTENSION" else "${project.id}$KODA2D_EXTENSION"
        val koda2dFile = File(destDir, fileName)

        // Remove any older .koda2d archives belonging to this same project.id (e.g. after rename)
        destDir.listFiles { f ->
            f.isFile && f.name.endsWith(KODA2D_EXTENSION, ignoreCase = true) && f.absolutePath != koda2dFile.absolutePath
        }?.forEach { existing ->
            val matchesId = existing.nameWithoutExtension.endsWith("_${project.id}") ||
                existing.nameWithoutExtension == project.id ||
                peekProjectHeader(existing)?.id == project.id
            if (matchesId) {
                existing.delete()
            }
        }

        // Zip entire working directory into .koda2d
        ZipOutputStream(BufferedOutputStream(FileOutputStream(koda2dFile))).use { zos ->
            workingDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relPath = file.relativeTo(workingDir).path.replace('\\', '/')
                    val entry = ZipEntry(relPath)
                    zos.putNextEntry(entry)
                    file.inputStream().use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }

        // Clean up uncompressed working directory to keep the device storage lightweight!
        deleteRecursively(workingDir)

        return koda2dFile
    }

    /**
     * Extracts a .koda2d package into the working directory and returns the AnimationProject.
     */
    fun decompressProject(context: Context, koda2dFile: File): AnimationProject {
        require(koda2dFile.exists()) { "Arquivo de projeto .koda2d não encontrado: ${koda2dFile.absolutePath}" }

        // Find or derive projectId from koda2d content or filename
        var projectId = koda2dFile.nameWithoutExtension.substringAfterLast('_')
        if (projectId.isEmpty()) projectId = "koda_${System.currentTimeMillis()}"

        val workingDir = getWorkingDir(context, projectId)
        // Clean out any stale working directory files
        deleteRecursively(workingDir)
        workingDir.mkdirs()

        // Extract package
        ZipInputStream(BufferedInputStream(FileInputStream(koda2dFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val outFile = File(workingDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val projectJsonFile = File(workingDir, "project.json")
        if (!projectJsonFile.exists()) {
            throw IllegalStateException("Arquivo project.json ausente no pacote .koda2d")
        }

        val project = FileImportExportManager.projectFromJson(projectJsonFile.readText())
        return project
    }

    /**
     * Imports a .koda2d project file from an external URI (SAF or Downloads).
     */
    fun importExternalKoda2d(context: Context, uri: Uri, customPath: String? = null): File {
        val destDir = getProjectsDirectory(context, customPath)
        val tempDest = File(destDir, "import_${System.currentTimeMillis()}$KODA2D_EXTENSION")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempDest).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Não foi possível ler o arquivo selecionado")

        // Read real project name to give it a proper filename
        try {
            val project = peekProjectHeader(tempDest)
            if (project != null) {
                val safeName = project.name.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "").trim().replace(' ', '_')
                val finalName = "${safeName}_${project.id}$KODA2D_EXTENSION"
                val finalFile = File(destDir, finalName)
                if (finalFile.absolutePath != tempDest.absolutePath) {
                    tempDest.renameTo(finalFile)
                    return finalFile
                }
            }
        } catch (_: Exception) {}

        return tempDest
    }

    /**
     * Reads metadata and first frame from a .koda2d file without full disk extraction.
     */
    fun peekProjectHeader(koda2dFile: File): AnimationProject? {
        return try {
            ZipFile(koda2dFile).use { zip ->
                val entry = zip.getEntry("project.json") ?: return null
                zip.getInputStream(entry).use { stream ->
                    val jsonStr = InputStreamReader(stream).readText()
                    FileImportExportManager.projectFromJson(jsonStr)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Scans for all saved projects (both compressed .koda2d in the projects folder and any uncompressed active ones).
     */
    fun listAllSavedProjects(context: Context, customPath: String? = null): List<SavedProjectEntry> {
        val entries = mutableListOf<SavedProjectEntry>()
        val seenIds = mutableSetOf<String>()

        // 1. Scan compressed directory (newest first, deduplicated strictly by project.id)
        val projectsDir = getProjectsDirectory(context, customPath)
        val kodaFiles = projectsDir.listFiles { f -> f.isFile && f.name.endsWith(KODA2D_EXTENSION, ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
        kodaFiles?.forEach { file ->
            val project = peekProjectHeader(file)
            if (project != null && !seenIds.contains(project.id)) {
                seenIds.add(project.id)
                entries.add(
                    SavedProjectEntry(
                        project = project,
                        isCompressed = true,
                        fileOnDisk = file,
                        fileSizeBytes = file.length(),
                        lastModified = file.lastModified()
                    )
                )
            }
        }

        // 2. Scan active uncompressed working projects (e.g. if app was killed while editing)
        val activeRoot = File(context.filesDir, "active_projects")
        if (activeRoot.exists()) {
            activeRoot.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    val pJson = File(dir, "project.json")
                    if (pJson.exists()) {
                        try {
                            val project = FileImportExportManager.projectFromJson(pJson.readText())
                            if (!seenIds.contains(project.id)) {
                                seenIds.add(project.id)
                                entries.add(
                                    SavedProjectEntry(
                                        project = project,
                                        isCompressed = false,
                                        fileOnDisk = pJson,
                                        fileSizeBytes = pJson.length(),
                                        lastModified = pJson.lastModified()
                                    )
                                )
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        // 3. Fallback scan of internal .kodanima files not yet seen
        ProjectStorageManager.loadAllProjects(context).forEach { proj ->
            if (!seenIds.contains(proj.id)) {
                seenIds.add(proj.id)
                val f = File(File(context.filesDir, "kodanima_projects"), "${proj.id}.kodanima")
                entries.add(
                    SavedProjectEntry(
                        project = proj,
                        isCompressed = false,
                        fileOnDisk = f,
                        fileSizeBytes = if (f.exists()) f.length() else 0L,
                        lastModified = proj.updatedAt
                    )
                )
            }
        }

        return entries.sortedByDescending { it.lastModified }
    }

    /**
     * Duplicates a project from compressed .koda2d, active working dir, or .kodanima storage
     * with a brand-new ID and unique name.
     */
    fun duplicateSavedProject(
        context: Context,
        sourceProjectId: String,
        newName: String,
        customPath: String? = null,
        compressCopy: Boolean = true
    ): AnimationProject? {
        val entries = listAllSavedProjects(context, customPath)
        val match = entries.firstOrNull { it.project.id == sourceProjectId }
        val sourceProject = if (match != null) {
            if (match.isCompressed && match.fileOnDisk.exists()) {
                peekProjectHeader(match.fileOnDisk) ?: match.project
            } else {
                match.project
            }
        } else {
            ProjectStorageManager.loadAllProjects(context).firstOrNull { it.id == sourceProjectId }
        } ?: return null

        val now = System.currentTimeMillis()
        val copyProj = sourceProject.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = newName,
            createdAt = now,
            updatedAt = now
        )
        ProjectStorageManager.saveProject(context, copyProj)
        if (compressCopy) {
            saveWorkingProject(context, copyProj)
            compressAndCloseProject(context, copyProj, customPath)
        } else {
            saveWorkingProject(context, copyProj)
        }
        return copyProj
    }

    /**
     * Permanently deletes all files associated with a project (both active working directory and .koda2d package).
     */
    fun deleteProjectEntirely(context: Context, projectId: String, customPath: String? = null): Boolean {
        var deletedAny = false

        // Delete active working dir
        val workingDir = File(context.filesDir, "active_projects/$projectId")
        if (workingDir.exists()) {
            deletedAny = deleteRecursively(workingDir) || deletedAny
        }

        // Delete .koda2d file(s) in destination directory matching projectId
        val destDir = getProjectsDirectory(context, customPath)
        destDir.listFiles { f ->
            f.isFile && f.name.endsWith(KODA2D_EXTENSION, ignoreCase = true)
        }?.forEach { f ->
            val matches = f.nameWithoutExtension.endsWith("_$projectId") ||
                f.nameWithoutExtension == projectId ||
                peekProjectHeader(f)?.id == projectId
            if (matches && f.delete()) deletedAny = true
        }

        return deletedAny
    }

    /**
     * Compacts all active working projects now to reclaim disk space.
     */
    fun compactAllWorkingProjects(context: Context, customPath: String? = null): Int {
        var count = 0
        val activeRoot = File(context.filesDir, "active_projects")
        if (!activeRoot.exists()) return 0

        activeRoot.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val pJson = File(dir, "project.json")
                if (pJson.exists()) {
                    try {
                        val project = FileImportExportManager.projectFromJson(pJson.readText())
                        compressAndCloseProject(context, project, customPath)
                        count++
                    } catch (_: Exception) {}
                }
            }
        }
        return count
    }

    private fun deleteRecursively(fileOrDir: File): Boolean {
        if (fileOrDir.isDirectory) {
            fileOrDir.listFiles()?.forEach { deleteRecursively(it) }
        }
        return fileOrDir.delete()
    }
}

/**
 * Summary descriptor of a saved project with disk size and compression status.
 */
data class SavedProjectEntry(
    val project: AnimationProject,
    val isCompressed: Boolean,
    val fileOnDisk: File,
    val fileSizeBytes: Long,
    val lastModified: Long
) {
    val formattedSize: String
        get() {
            val kb = fileSizeBytes / 1024.0
            return if (kb > 1024) {
                String.format("%.1f MB", kb / 1024.0)
            } else {
                String.format("%.1f KB", kb)
            }
        }
}
