package com.github.jacknic.plugin.gradlehub.service

import com.github.jacknic.plugin.gradlehub.config.GradleHubSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.LocalFileSystem
import java.util.Properties

/**
 * Project-level service that manages the Gradle Wrapper mirror proxy.
 *
 * Core responsibilities:
 * - Parse and modify `gradle-wrapper.properties` to replace `distributionUrl` with a mirror URL
 * - Create `.gradlehub.bak` backup before any modification
 * - Restore original configuration from backup when proxy is disabled
 *
 * All pure URL transformation logic is exposed as companion object methods for testability.
 */
@Service(Service.Level.PROJECT)
class WrapperProxyService(val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(WrapperProxyService::class.java)

        const val WRAPPER_PROPERTIES_RELATIVE_PATH = "gradle/wrapper/gradle-wrapper.properties"
        const val BACKUP_SUFFIX = ".gradlehub.bak"

        // ---- Pure URL transformation functions (no IntelliJ API dependency) ----

        /**
         * Parse the `distributionUrl` value from `gradle-wrapper.properties` content.
         *
         * Handles escaped colons (`\:`) which Gradle uses in its properties format.
         * Skips comment lines (starting with `#`) and blank lines.
         *
         * @param content the full text content of `gradle-wrapper.properties`
         * @return the unescaped distribution URL, or null if not found
         */
        fun parseDistributionUrl(content: String): String? {
            for (line in content.lines()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                if (trimmed.startsWith("distributionUrl=")) {
                    val rawValue = trimmed.removePrefix("distributionUrl=").trim()
                    return rawValue.replace("\\:", ":")
                }
            }
            return null
        }

        /**
         * Replace the `distributionUrl` value in the properties content.
         *
         * Preserves the original escaping style: if the original URL used `\:` escaping,
         * the new URL will also use it.
         *
         * @param content the full text content of `gradle-wrapper.properties`
         * @param newUrl the new distribution URL (unescaped)
         * @return the modified content with the new URL
         */
        fun replaceDistributionUrl(content: String, newUrl: String): String {
            val usesEscaping = content.lines()
                .filter { it.trim().startsWith("distributionUrl=") }
                .any { it.contains("\\:") }

            val escapedUrl = if (usesEscaping) newUrl.replace(":", "\\:") else newUrl

            return content.lines().joinToString("\n") { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("distributionUrl=")) {
                    "distributionUrl=$escapedUrl"
                } else {
                    line
                }
            }
        }

        /**
         * Transform a Gradle distribution URL by replacing its base with a mirror URL.
         *
         * Example:
         * - original: `https://services.gradle.org/distributions/gradle-8.6-bin.zip`
         * - mirrorUrl: `https://mirrors.cloud.tencent.com/gradle/`
         * - result: `https://mirrors.cloud.tencent.com/gradle/gradle-8.6-bin.zip`
         *
         * @param originalUrl the original distribution URL
         * @param mirrorUrl the mirror base URL (may or may not end with `/`)
         * @return the transformed URL, or the original if mirror is blank or transformation fails
         */
        fun transformUrl(originalUrl: String, mirrorUrl: String): String {
            if (mirrorUrl.isBlank()) return originalUrl
            val fileName = originalUrl.substringAfterLast("/")
            if (fileName.isEmpty()) return originalUrl
            val base = if (mirrorUrl.endsWith("/")) mirrorUrl else "$mirrorUrl/"
            return "$base$fileName"
        }

        /**
         * Extract the distribution filename from a URL.
         * e.g. `https://services.gradle.org/distributions/gradle-8.6-bin.zip` → `gradle-8.6-bin.zip`
         */
        fun extractFileName(url: String): String = url.substringAfterLast("/")

        /**
         * Parse the Gradle version number from a distribution URL or filename.
         *
         * Supports formats:
         * - `gradle-8.6-bin.zip` → `8.6`
         * - `gradle-7.6.4-all.zip` → `7.6.4`
         * - `gradle-8.6-src.zip` → `8.6`
         *
         * @param url the full distribution URL or just the filename
         * @return the version string, or null if parsing fails
         */
        fun parseVersionFromUrl(url: String): String? {
            val fileName = extractFileName(url)
            val regex = Regex("""gradle-(\d+\.\d+(?:\.\d+)?)-\w+\.zip""")
            return regex.find(fileName)?.groupValues?.get(1)
        }

        /**
         * Validate that a mirror URL is a well-formed HTTP(S) URL.
         *
         * @param url the mirror URL to validate
         * @return true if the URL starts with `http://` or `https://`
         */
        fun isValidMirrorUrl(url: String): Boolean {
            val trimmed = url.trim()
            return trimmed.startsWith("http://") || trimmed.startsWith("https://")
        }

        /**
         * Load `gradle-wrapper.properties` content into a [Properties] object.
         *
         * @param content the file content as string
         * @return a [Properties] instance with parsed key-value pairs
         */
        fun loadWrapperProperties(content: String): Properties {
            val props = Properties()
            props.load(content.byteInputStream(Charsets.UTF_8))
            return props
        }
    }

    // ---- Instance methods (require project context) ----

    /**
     * Find the `gradle-wrapper.properties` file in the current project.
     *
     * Searches at `<project-root>/gradle/wrapper/gradle-wrapper.properties`.
     *
     * @return the [VirtualFile] if found, null otherwise
     */
    fun findWrapperPropertiesFile(): VirtualFile? {
        val basePath = project.basePath ?: return null
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return null
        return baseDir.findFileByRelativePath(WRAPPER_PROPERTIES_RELATIVE_PATH)
    }

    /**
     * Get the current `distributionUrl` from the project's `gradle-wrapper.properties`.
     *
     * @return the unescaped distribution URL, or null if the file doesn't exist or the key is missing
     */
    fun getCurrentDistributionUrl(): String? {
        val file = findWrapperPropertiesFile() ?: return null
        val content = String(file.contentsToByteArray(), Charsets.UTF_8)
        return parseDistributionUrl(content)
    }

    /**
     * Get the current Gradle version used by the project.
     *
     * @return the version string (e.g. "8.6"), or null if it cannot be determined
     */
    fun getCurrentGradleVersion(): String? {
        val url = getCurrentDistributionUrl() ?: return null
        return parseVersionFromUrl(url)
    }

    /**
     * Check if the mirror proxy is currently applied to the project.
     *
     * This is determined by the existence of a `.gradlehub.bak` backup file
     * alongside the `gradle-wrapper.properties` file.
     *
     * @return true if a backup file exists (indicating proxy has been applied)
     */
    fun isProxyApplied(): Boolean {
        val file = findWrapperPropertiesFile() ?: return false
        val parent = file.parent ?: return false
        val backupFile = parent.findChild(file.name + BACKUP_SUFFIX)
        return backupFile != null && backupFile.exists()
    }

    /**
     * Get the original (pre-proxy) `distributionUrl` from the backup file.
     *
     * @return the original URL if proxy is applied, null otherwise
     */
    fun getOriginalDistributionUrl(): String? {
        if (!isProxyApplied()) return null
        val file = findWrapperPropertiesFile() ?: return null
        val parent = file.parent ?: return null
        val backupFile = parent.findChild(file.name + BACKUP_SUFFIX) ?: return null
        val content = String(backupFile.contentsToByteArray(), Charsets.UTF_8)
        return parseDistributionUrl(content)
    }

    /**
     * Apply the mirror proxy to the project's `gradle-wrapper.properties`.
     *
     * Steps:
     * 1. Validate that mirror is enabled and URL is configured
     * 2. Check that proxy is not already applied
     * 3. Create a `.gradlehub.bak` backup of the original file
     * 4. Replace the `distributionUrl` with the mirror-transformed URL
     *
     * @return true if the proxy was successfully applied, false otherwise
     */
    fun applyProxy(): Boolean {
        val settings = GradleHubSettings.getInstance()
        if (!settings.mirrorEnabled) {
            LOG.warn("Mirror proxy is disabled in settings")
            return false
        }
        if (settings.mirrorUrl.isBlank()) {
            LOG.warn("Mirror URL is not configured")
            return false
        }
        if (!isValidMirrorUrl(settings.mirrorUrl)) {
            LOG.warn("Invalid mirror URL: ${settings.mirrorUrl}")
            return false
        }
        if (isProxyApplied()) {
            LOG.info("Proxy is already applied to this project")
            return false
        }

        val file = findWrapperPropertiesFile()
        if (file == null) {
            LOG.warn("gradle-wrapper.properties not found in project: ${project.name}")
            return false
        }

        val content = String(file.contentsToByteArray(), Charsets.UTF_8)
        val originalUrl = parseDistributionUrl(content)
        if (originalUrl == null) {
            LOG.warn("distributionUrl not found in gradle-wrapper.properties")
            return false
        }

        val newUrl = transformUrl(originalUrl, settings.mirrorUrl)
        if (newUrl == originalUrl) {
            LOG.info("Mirror transformation did not change the URL")
            return false
        }

        val newContent = replaceDistributionUrl(content, newUrl)
        val finalContent = newContent

        try {
            WriteCommandAction.runWriteCommandAction(project) {
                // Create backup of original content
                val parent = file.parent
                if (parent != null) {
                    val backupName = file.name + BACKUP_SUFFIX
                    val existingBackup = parent.findChild(backupName)
                    if (existingBackup == null) {
                        val backup = parent.createChildData(this, backupName)
                        VfsUtil.saveText(backup, content)
                        LOG.info("Created backup: $backupName")
                    }
                }
                // Write modified content
                VfsUtil.saveText(file, finalContent)
                LOG.info("Applied mirror proxy: $originalUrl → $newUrl")
            }
            return true
        } catch (e: Exception) {
            LOG.error("Failed to apply mirror proxy", e)
            return false
        }
    }

    /**
     * Restore the original `gradle-wrapper.properties` from the backup file.
     *
     * Steps:
     * 1. Check that proxy is currently applied (backup exists)
     * 2. Read the original content from the backup
     * 3. Restore the original content to `gradle-wrapper.properties`
     * 4. Delete the backup file
     *
     * @return true if the original configuration was successfully restored, false otherwise
     */
    fun restoreOriginal(): Boolean {
        if (!isProxyApplied()) {
            LOG.info("Proxy is not applied; nothing to restore")
            return false
        }

        val file = findWrapperPropertiesFile()
        if (file == null) {
            LOG.warn("gradle-wrapper.properties not found in project: ${project.name}")
            return false
        }

        val parent = file.parent
        if (parent == null) {
            LOG.warn("Cannot access parent directory of gradle-wrapper.properties")
            return false
        }

        val backupName = file.name + BACKUP_SUFFIX
        val backupFile = parent.findChild(backupName)
        if (backupFile == null) {
            LOG.warn("Backup file not found: $backupName")
            return false
        }

        val originalContent = String(backupFile.contentsToByteArray(), Charsets.UTF_8)

        try {
            WriteCommandAction.runWriteCommandAction(project) {
                VfsUtil.saveText(file, originalContent)
                backupFile.delete(this)
                LOG.info("Restored original gradle-wrapper.properties and removed backup")
            }
            return true
        } catch (e: Exception) {
            LOG.error("Failed to restore original configuration", e)
            return false
        }
    }

    /**
     * Toggle the mirror proxy: apply if not applied, restore if already applied.
     *
     * @return true if the operation succeeded, false otherwise
     */
    fun toggleProxy(): Boolean {
        return if (isProxyApplied()) restoreOriginal() else applyProxy()
    }
}
