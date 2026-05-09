package com.github.jacknic.plugin.gradlehub.service

import com.github.jacknic.plugin.gradlehub.config.GradleHubSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * Project-level service that manages repository mirror proxy injection.
 *
 * Injects mirror repository declarations into Gradle build files
 * (`settings.gradle` / `build.gradle`) using the `allprojects` pattern.
 * The injected block is wrapped in marker comments for identification and removal.
 *
 * All pure transformation logic is exposed as companion object methods for testability.
 */
@Service(Service.Level.PROJECT)
class RepositoryProxyService(val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(RepositoryProxyService::class.java)

        const val MARKER_BEGIN = "// GradleHub-Mirror-Begin"
        const val MARKER_END = "// GradleHub-Mirror-End"
        const val BACKUP_SUFFIX = ".gradlehub.bak"

        /**
         * Detect the Gradle DSL type from the file extension.
         *
         * @param fileName the file name (e.g. "build.gradle.kts")
         * @return [DslType.KOTLIN] for `.kts` files, [DslType.GROOVY] otherwise
         */
        fun detectDslType(fileName: String): String = when {
            fileName.endsWith(".kts") -> DslType.KOTLIN
            else -> DslType.GROOVY
        }

        /**
         * Generate a Groovy DSL mirror repository block.
         *
         * Produces an `allprojects` block that adds the mirror repository
         * to both `buildscript` and project repositories.
         *
         * @param mirrorUrl the repository mirror URL
         * @return the generated Groovy DSL code block
         */
        fun generateGroovyMirrorBlock(mirrorUrl: String): String {
            return """
                |$MARKER_BEGIN
                |allprojects {
                |    buildscript {
                |        repositories {
                |            maven { url '$mirrorUrl' }
                |        }
                |    }
                |    repositories {
                |        maven { url '$mirrorUrl' }
                |    }
                |}
                |$MARKER_END
            """.trimMargin()
        }

        /**
         * Generate a Kotlin DSL mirror repository block.
         *
         * Produces an `allprojects` block that adds the mirror repository
         * to both `buildscript` and project repositories.
         *
         * @param mirrorUrl the repository mirror URL
         * @return the generated Kotlin DSL code block
         */
        fun generateKotlinMirrorBlock(mirrorUrl: String): String {
            return """
                |$MARKER_BEGIN
                |allprojects {
                |    buildscript {
                |        repositories {
                |            maven { url = uri("$mirrorUrl") }
                |        }
                |    }
                |    repositories {
                |        maven { url = uri("$mirrorUrl") }
                |    }
                |}
                |$MARKER_END
            """.trimMargin()
        }

        /**
         * Generate a mirror repository block for the specified DSL type.
         *
         * @param mirrorUrl the repository mirror URL
         * @param dslType the Gradle DSL type
         * @return the generated code block
         */
        fun generateMirrorBlock(mirrorUrl: String, dslType: String): String {
            return when (dslType) {
                DslType.KOTLIN -> generateKotlinMirrorBlock(mirrorUrl)
                else -> generateGroovyMirrorBlock(mirrorUrl)
            }
        }

        /**
         * Inject a mirror block at the beginning of the file content.
         *
         * If the content already contains GradleHub markers, no injection is performed.
         *
         * @param content the original file content
         * @param mirrorBlock the mirror block to inject
         * @return the modified content, or the original if already injected
         */
        fun injectMirrorBlock(content: String, mirrorBlock: String): String {
            if (content.contains(MARKER_BEGIN)) return content
            return "$mirrorBlock\n\n$content"
        }

        /**
         * Remove the GradleHub mirror block from the file content.
         *
         * Removes everything between [MARKER_BEGIN] and [MARKER_END] (inclusive),
         * plus any leading/trailing blank lines around the removed section.
         *
         * @param content the file content containing the mirror block
         * @return the content with the mirror block removed
         */
        fun removeMirrorBlock(content: String): String {
            if (!content.contains(MARKER_BEGIN)) return content
            val regex = Regex(
                Regex.escape(MARKER_BEGIN) + ".*?" + Regex.escape(MARKER_END),
                RegexOption.DOT_MATCHES_ALL
            )
            var result = regex.replace(content) { "" }
            // Clean up excessive blank lines left after removal
            result = result.replace(Regex("\n{3,}"), "\n\n")
            return result.trim()
        }

        /**
         * Check if the content contains a GradleHub mirror block.
         */
        fun containsMirrorBlock(content: String): Boolean = content.contains(MARKER_BEGIN)

        /**
         * List of candidate build file names in priority order.
         * Settings files are preferred because they apply to all subprojects.
         */
        val GRADLE_FILE_CANDIDATES = listOf(
            "settings.gradle.kts", "settings.gradle",
            "build.gradle.kts", "build.gradle"
        )
    }

    /**
     * Find the primary Gradle build/settings file in the project root.
     *
     * Searches for files in priority order: `settings.gradle.kts` > `settings.gradle`
     * > `build.gradle.kts` > `build.gradle`.
     *
     * @return the first found [VirtualFile], or null if none exist
     */
    fun findGradleBuildFile(): VirtualFile? {
        val basePath = project.basePath ?: return null
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return null
        for (name in GRADLE_FILE_CANDIDATES) {
            val file = baseDir.findChild(name)
            if (file != null && !file.isDirectory) return file
        }
        return null
    }

    /**
     * Check if the repository mirror proxy is currently applied to any project build file.
     */
    fun isProxyApplied(): Boolean {
        val file = findGradleBuildFile() ?: return false
        val content = String(file.contentsToByteArray(), Charsets.UTF_8)
        return containsMirrorBlock(content)
    }

    /**
     * Apply the repository mirror proxy to the project's Gradle build file.
     *
     * Steps:
     * 1. Validate settings (enabled, URL configured)
     * 2. Find the target Gradle file
     * 3. Create a `.gradlehub.bak` backup
     * 4. Inject the mirror repository block
     *
     * @return true if the proxy was successfully applied, false otherwise
     */
    fun applyProxy(): Boolean {
        val settings = GradleHubSettings.getInstance()
        if (!settings.mirrorEnabled || !settings.repositoryProxyEnabled) {
            LOG.warn("Repository mirror proxy is disabled in settings")
            return false
        }
        if (settings.repositoryMirrorUrl.isBlank()) {
            LOG.warn("Repository mirror URL is not configured")
            return false
        }

        val targetFile = findGradleBuildFile()
        if (targetFile == null) {
            LOG.warn("No Gradle build file found in project: ${project.name}")
            return false
        }

        val content = String(targetFile.contentsToByteArray(), Charsets.UTF_8)
        if (containsMirrorBlock(content)) {
            LOG.info("Repository mirror already applied to ${targetFile.name}")
            return false
        }

        val dslType = detectDslType(targetFile.name)
        val mirrorBlock = generateMirrorBlock(settings.repositoryMirrorUrl, dslType)
        val newContent = injectMirrorBlock(content, mirrorBlock)

        try {
            WriteCommandAction.runWriteCommandAction(project) {
                // Create backup
                val parent = targetFile.parent
                if (parent != null) {
                    val backupName = targetFile.name + BACKUP_SUFFIX
                    val existingBackup = parent.findChild(backupName)
                    if (existingBackup == null) {
                        val backup = parent.createChildData(this, backupName)
                        VfsUtil.saveText(backup, content)
                        LOG.info("Created backup: $backupName")
                    }
                }
                VfsUtil.saveText(targetFile, newContent)
                LOG.info("Applied repository mirror to ${targetFile.name}")
            }
            return true
        } catch (e: Exception) {
            LOG.error("Failed to apply repository mirror", e)
            return false
        }
    }

    /**
     * Restore the original Gradle build file from the backup.
     *
     * Looks for a `.gradlehub.bak` backup file and restores the original content.
     * If no backup exists, removes the marked mirror block from the current content.
     *
     * @return true if the original configuration was successfully restored, false otherwise
     */
    fun restoreOriginal(): Boolean {
        val targetFile = findGradleBuildFile()
        if (targetFile == null) {
            LOG.info("No Gradle build file found")
            return false
        }

        val content = String(targetFile.contentsToByteArray(), Charsets.UTF_8)
        if (!containsMirrorBlock(content)) {
            LOG.info("Repository mirror is not applied; nothing to restore")
            return false
        }

        // Try to restore from backup first
        val parent = targetFile.parent
        val backupName = targetFile.name + BACKUP_SUFFIX
        val backupFile = parent?.findChild(backupName)

        try {
            WriteCommandAction.runWriteCommandAction(project) {
                if (backupFile != null && backupFile.exists()) {
                    val originalContent = String(backupFile.contentsToByteArray(), Charsets.UTF_8)
                    VfsUtil.saveText(targetFile, originalContent)
                    backupFile.delete(this)
                    LOG.info("Restored from backup and removed $backupName")
                } else {
                    // Fallback: remove the marked section
                    val cleanedContent = removeMirrorBlock(content)
                    VfsUtil.saveText(targetFile, cleanedContent)
                    LOG.info("Removed repository mirror block from ${targetFile.name}")
                }
            }
            return true
        } catch (e: Exception) {
            LOG.error("Failed to restore repository configuration", e)
            return false
        }
    }
}
