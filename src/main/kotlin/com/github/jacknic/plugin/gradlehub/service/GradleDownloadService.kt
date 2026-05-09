package com.github.jacknic.plugin.gradlehub.service

import com.github.jacknic.plugin.gradlehub.config.GradleHubSettings
import com.github.jacknic.plugin.gradlehub.model.RemoteGradleVersion
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Application-level service for downloading Gradle distributions.
 *
 * Core responsibilities:
 * - Fetch available Gradle versions from the remote API
 * - Download a specific Gradle version (optionally through a mirror)
 * - Place downloaded files in the correct wrapper dists directory structure
 * - Track download progress and support cancellation
 *
 * All pure logic is exposed as companion object methods for testability.
 */
@Service(Service.Level.APP)
class GradleDownloadService {

    companion object {
        private val LOG = Logger.getInstance(GradleDownloadService::class.java)

        const val GRADLE_VERSIONS_API = "https://services.gradle.org/versions/all"
        const val GRADLE_DISTRIBUTIONS_BASE_URL = "https://services.gradle.org/distributions/"

        /** Download buffer size (8 KB). */
        private const val BUFFER_SIZE = 8192

        @JvmStatic
        fun getInstance(): GradleDownloadService =
            ApplicationManager.getApplication().getService(GradleDownloadService::class.java)

        // ---- Pure functions (no IntelliJ API dependency) ----

        /**
         * Build the official distribution URL for a specific Gradle version.
         *
         * @param version the Gradle version string (e.g. "8.6")
         * @param distType the distribution type ("bin" or "all")
         * @return the full distribution download URL
         */
        fun buildDistributionUrl(version: String, distType: String = "bin"): String {
            return "${GRADLE_DISTRIBUTIONS_BASE_URL}gradle-${version}-${distType}.zip"
        }

        /**
         * Build the mirror distribution URL for a specific Gradle version.
         *
         * @param version the Gradle version string
         * @param mirrorUrl the mirror base URL
         * @param distType the distribution type
         * @return the mirror distribution download URL, or the official URL if mirror is blank
         */
        fun buildMirrorDistributionUrl(version: String, mirrorUrl: String, distType: String = "bin"): String {
            if (mirrorUrl.isBlank()) return buildDistributionUrl(version, distType)
            val fileName = "gradle-${version}-${distType}.zip"
            val base = if (mirrorUrl.endsWith("/")) mirrorUrl else "$mirrorUrl/"
            return "$base$fileName"
        }

        /**
         * Compute the distribution hash using the same algorithm as Gradle's PathAssembler.
         *
         * Gradle computes: `BigInteger(1, MD5(url)).toString(36)`
         *
         * @param distributionUrl the distribution URL to hash
         * @return the hash string used as the directory name in `wrapper/dists/`
         */
        fun computeDistributionHash(distributionUrl: String): String {
            val md5 = MessageDigest.getInstance("MD5")
            val digest = md5.digest(distributionUrl.toByteArray(Charsets.UTF_8))
            return BigInteger(1, digest).toString(36)
        }

        /**
         * Build the distribution base name (e.g. "gradle-8.6-bin").
         */
        fun buildDistributionBaseName(version: String, distType: String = "bin"): String {
            return "gradle-${version}-${distType}"
        }

        /**
         * Build the target directory for a downloaded Gradle distribution.
         *
         * The directory structure matches Gradle's wrapper expectations:
         * `wrapper/dists/gradle-{version}-{distType}/{hash}/`
         *
         * @param distsDir the `wrapper/dists` directory
         * @param version the Gradle version
         * @param distType the distribution type
         * @return the target directory file
         */
        fun buildTargetDirectory(distsDir: File, version: String, distType: String = "bin"): File {
            val baseName = buildDistributionBaseName(version, distType)
            val originalUrl = buildDistributionUrl(version, distType)
            val hash = computeDistributionHash(originalUrl)
            return File(File(distsDir, baseName), hash)
        }

        /**
         * Parse the remote versions JSON from the Gradle releases API.
         *
         * The API returns a JSON array of objects with fields like:
         * `version`, `downloadUrl`, `current`, `snapshot`, `broken`, etc.
         *
         * @param json the raw JSON string from the API
         * @return a list of [RemoteGradleVersion], sorted by version descending with current first
         */
        fun parseRemoteVersions(json: String): List<RemoteGradleVersion> {
            val versions = mutableListOf<RemoteGradleVersion>()
            val objectPattern = Regex("""\{([^}]*(?:\{[^}]*\}[^}]*)*)\}""")
            val fieldStringPattern = Regex(""""(\w+)"\s*:\s*"([^"]*)"""")
            val fieldBoolPattern = Regex(""""(\w+)"\s*:\s*(true|false)""")

            for (match in objectPattern.findAll(json)) {
                val obj = match.groupValues[1]
                val stringFields = mutableMapOf<String, String>()
                val boolFields = mutableMapOf<String, Boolean>()

                for (fieldMatch in fieldStringPattern.findAll(obj)) {
                    stringFields[fieldMatch.groupValues[1]] = fieldMatch.groupValues[2]
                }
                for (fieldMatch in fieldBoolPattern.findAll(obj)) {
                    boolFields[fieldMatch.groupValues[1]] = fieldMatch.groupValues[2] == "true"
                }

                val version = stringFields["version"] ?: continue
                val downloadUrl = stringFields["downloadUrl"] ?: buildDistributionUrl(version)

                versions.add(RemoteGradleVersion(
                    version = version,
                    downloadUrl = downloadUrl,
                    isCurrent = boolFields["current"] ?: false,
                    isSnapshot = boolFields["snapshot"] ?: false,
                    isBroken = boolFields["broken"] ?: false,
                ))
            }

            return versions.sortedWith(
                compareByDescending<RemoteGradleVersion> { it.isCurrent }
                    .thenByDescending { it.version }
            )
        }

        /**
         * Download a file from a URL with progress tracking and cancellation support.
         *
         * @param url the URL to download from
         * @param targetFile the file to save the downloaded content to
         * @param onProgress callback receiving (downloaded bytes, total bytes; -1 if unknown)
         * @param isCancelled function returning true to cancel the download
         * @return true if the download completed successfully
         */
        fun downloadFile(
            url: String,
            targetFile: File,
            onProgress: (downloaded: Long, total: Long) -> Unit,
            isCancelled: () -> Boolean = { false }
        ): Boolean {
            var connection: HttpURLConnection? = null
            try {
                connection = URI(url).toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.instanceFollowRedirects = true

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    LOG.warn("Download failed: HTTP $responseCode for $url")
                    return false
                }

                val contentLength = connection.contentLengthLong
                targetFile.parentFile?.mkdirs()

                connection.inputStream.buffered().use { input ->
                    FileOutputStream(targetFile).buffered().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloaded = 0L

                        while (true) {
                            if (isCancelled()) {
                                LOG.info("Download cancelled by user")
                                targetFile.delete()
                                return false
                            }

                            val read = input.read(buffer)
                            if (read == -1) break

                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, if (contentLength > 0) contentLength else -1L)
                        }
                    }
                }

                return true
            } catch (e: Exception) {
                LOG.warn("Download failed for $url", e)
                targetFile.delete()
                return false
            } finally {
                connection?.disconnect()
            }
        }

        /**
         * Extract a zip file to a target directory.
         *
         * Includes security check for zip slip vulnerability.
         * Sets executable permission on `bin/` scripts for Unix compatibility.
         *
         * @param zipFile the zip file to extract
         * @param targetDir the directory to extract into
         * @throws Exception if extraction fails
         */
        fun extractZip(zipFile: File, targetDir: File) {
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val targetFile = File(targetDir, entry.name)

                    // Security: prevent zip slip
                    val canonicalTarget = targetFile.canonicalPath
                    val canonicalDir = targetDir.canonicalPath
                    if (!canonicalTarget.startsWith(canonicalDir + File.separator) &&
                        canonicalTarget != canonicalDir) {
                        LOG.warn("Zip entry outside target directory: ${entry.name}")
                        return@forEach
                    }

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        // Make bin scripts executable on Unix
                        if (entry.name.startsWith("bin/") && !entry.name.endsWith(".bat")) {
                            targetFile.setExecutable(true)
                        }
                    }
                }
            }
        }

        /**
         * Check if a version is already downloaded in the given dists directory.
         *
         * @param distsDir the `wrapper/dists` directory
         * @param version the Gradle version
         * @param distType the distribution type
         * @return true if the version exists with a marker file
         */
        fun isVersionDownloaded(distsDir: File, version: String, distType: String = "bin"): Boolean {
            val targetDir = buildTargetDirectory(distsDir, version, distType)
            val baseName = buildDistributionBaseName(version, distType)
            val markerFile = File(targetDir, "$baseName.zip.ok")
            return markerFile.exists()
        }
    }

    private val settings = GradleHubSettings.getInstance()

    /**
     * Fetch available Gradle versions from the remote API.
     *
     * Filters out broken versions and snapshots by default, returning only
     * stable releases sorted with the current release first.
     *
     * @return a list of available remote versions, or empty list on failure
     */
    fun fetchRemoteVersions(): List<RemoteGradleVersion> {
        return try {
            val connection = URI(GRADLE_VERSIONS_API).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true

            try {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                parseRemoteVersions(json)
                    .filter { !it.isBroken && !it.isSnapshot }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            LOG.warn("Failed to fetch remote Gradle versions", e)
            emptyList()
        }
    }

    /**
     * Get the effective download URL (mirror if configured, otherwise official).
     */
    fun getEffectiveDownloadUrl(version: String, distType: String = "bin"): String {
        val mirrorUrl = settings.mirrorUrl
        return if (mirrorUrl.isNotBlank() && WrapperProxyService.isValidMirrorUrl(mirrorUrl)) {
            buildMirrorDistributionUrl(version, mirrorUrl, distType)
        } else {
            buildDistributionUrl(version, distType)
        }
    }

    /**
     * Download a specific Gradle version and place it in the wrapper dists directory.
     *
     * The distribution is placed in the directory structure expected by Gradle's wrapper:
     * `~/.gradle/wrapper/dists/gradle-{version}-{distType}/{hash}/`
     *
     * Files are placed using the ORIGINAL (official) URL's hash, so they are found
     * by the wrapper when the mirror proxy is not applied. If the mirror proxy is
     * applied when switching, Gradle may re-download from the mirror (which is fast).
     *
     * @param version the Gradle version string (e.g. "8.6")
     * @param distType the distribution type ("bin" or "all")
     * @param onProgress callback for progress updates (downloaded bytes, total bytes)
     * @param isCancelled function that returns true to cancel the download
     * @return the target directory if download was successful, null otherwise
     */
    fun downloadVersion(
        version: String,
        distType: String = "bin",
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): File? {
        val gradleHome = settings.getEffectiveGradleHome()
        val distsDir = File(gradleHome, GradleVersionService.WRAPPER_DISTS_RELATIVE_PATH)
        val targetDir = buildTargetDirectory(distsDir, version, distType)
        val baseName = buildDistributionBaseName(version, distType)
        val zipFile = File(targetDir, "$baseName.zip")
        val markerFile = File(targetDir, "$baseName.zip.ok")

        // Check if already downloaded
        if (markerFile.exists() && zipFile.exists()) {
            LOG.info("Gradle $version already downloaded at ${targetDir.absolutePath}")
            return targetDir
        }

        // Create target directory
        targetDir.mkdirs()

        // Download from mirror (if available) or official
        val downloadUrl = getEffectiveDownloadUrl(version, distType)
        LOG.info("Downloading Gradle $version from $downloadUrl")

        val success = downloadFile(downloadUrl, zipFile, onProgress, isCancelled)
        if (!success) {
            return null
        }

        // Extract the zip
        try {
            LOG.info("Extracting Gradle $version to ${targetDir.absolutePath}")
            extractZip(zipFile, targetDir)
        } catch (e: Exception) {
            LOG.warn("Failed to extract Gradle $version", e)
            return null
        }

        // Create marker file indicating successful download
        try {
            markerFile.createNewFile()
        } catch (e: Exception) {
            LOG.warn("Failed to create marker file for Gradle $version", e)
        }

        LOG.info("Successfully downloaded and extracted Gradle $version to ${targetDir.absolutePath}")
        return targetDir
    }
}
