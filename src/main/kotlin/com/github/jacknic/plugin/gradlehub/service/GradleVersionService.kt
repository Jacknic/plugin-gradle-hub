package com.github.jacknic.plugin.gradlehub.service

import com.github.jacknic.plugin.gradlehub.config.GradleHubSettings
import com.github.jacknic.plugin.gradlehub.model.GradleVersionInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.io.File

/**
 * Application-level service for scanning and managing locally installed Gradle versions.
 *
 * Core responsibilities:
 * - Scan `~/.gradle/wrapper/dists/` for installed Gradle distributions
 * - Provide version listing with size and path information
 * - Version cleanup (single or batch delete) with retention policy
 * - Version switch (update project's `gradle-wrapper.properties`)
 */
@Service(Service.Level.APP)
class GradleVersionService {

    companion object {
        private val LOG = Logger.getInstance(GradleVersionService::class.java)

        /** Relative path to wrapper distributions under GRADLE_USER_HOME */
        const val WRAPPER_DISTS_RELATIVE_PATH = "wrapper/dists"

        // ---- Pure functions (no IntelliJ API dependency) ----

        /**
         * Scan the given Gradle wrapper dists directory and return all installed versions.
         *
         * The directory structure is: `wrapper/dists/<version>/<hash>/`
         * Each version directory may contain multiple hash subdirectories (from different distributionUrl hashes).
         *
         * @param distsDir the `wrapper/dists` directory to scan
         * @return a list of [GradleVersionInfo], sorted by version descending
         */
        fun scanVersions(distsDir: File): List<GradleVersionInfo> {
            if (!distsDir.isDirectory) return emptyList()

            val versions = mutableListOf<GradleVersionInfo>()
            val versionDirs = distsDir.listFiles { file -> file.isDirectory } ?: return emptyList()

            for (versionDir in versionDirs) {
                // Each version directory contains hash subdirectories
                val hashDirs = versionDir.listFiles { file -> file.isDirectory } ?: continue
                for (hashDir in hashDirs) {
                    val info = GradleVersionInfo.fromDirectory(hashDir)
                    if (info != null) {
                        versions.add(info)
                    }
                }
            }

            return versions.sortedByDescending { it.version }
        }

        /**
         * Filter versions to identify which ones can be safely deleted.
         *
         * Protected versions are:
         * - The current project's version (if specified)
         * - Versions within the retention window (keep newest N)
         *
         * @param versions all installed versions
         * @param currentVersion the version currently used by the project (null if unknown)
         * @param keepCount minimum number of versions to retain
         * @return list of versions that can be safely deleted
         */
        fun findDeletableVersions(
            versions: List<GradleVersionInfo>,
            currentVersion: String?,
            keepCount: Int
        ): List<GradleVersionInfo> {
            if (versions.isEmpty()) return emptyList()

            val retained = versions.sortedByDescending { it.lastModified }
                .take(keepCount)
                .map { it.version }
                .toMutableSet()

            // Always protect the current project version
            if (currentVersion != null) {
                retained.add(currentVersion)
            }

            return versions.filter { it.version !in retained }
        }

        /**
         * Delete a single Gradle distribution directory.
         *
         * @param versionInfo the version to delete
         * @return true if the directory was successfully deleted
         */
        fun deleteVersion(versionInfo: GradleVersionInfo): Boolean {
            val dir = File(versionInfo.path)
            if (!dir.isDirectory) return false
            return try {
                dir.deleteRecursively()
            } catch (e: Exception) {
                LOG.warn("Failed to delete version ${versionInfo.version} at ${versionInfo.path}", e)
                false
            }
        }

        /**
         * Calculate total size of all given versions.
         *
         * @param versions the versions to sum
         * @return total size in bytes
         */
        fun calculateTotalSize(versions: List<GradleVersionInfo>): Long {
            return versions.sumOf { it.size }
        }

        @JvmStatic
        fun getInstance(): GradleVersionService =
            ApplicationManager.getApplication().getService(GradleVersionService::class.java)
    }

    private val settings = GradleHubSettings.getInstance()

    /**
     * Get the wrapper dists directory based on settings.
     *
     * @return the `wrapper/dists` directory
     */
    fun getDistsDirectory(): File {
        val gradleHome = settings.getEffectiveGradleHome()
        return File(gradleHome, WRAPPER_DISTS_RELATIVE_PATH)
    }

    /**
     * Scan and return all locally installed Gradle versions.
     *
     * @return list of installed versions, sorted by version descending
     */
    fun getInstalledVersions(): List<GradleVersionInfo> {
        return scanVersions(getDistsDirectory())
    }

    /**
     * Get versions that can be safely deleted based on retention policy.
     *
     * @param currentVersion the version currently used by the project
     * @return list of deletable versions
     */
    fun getDeletableVersions(currentVersion: String?): List<GradleVersionInfo> {
        val versions = getInstalledVersions()
        return findDeletableVersions(versions, currentVersion, settings.keepVersions)
    }

    /**
     * Perform cleanup of old Gradle versions based on retention policy.
     *
     * @param currentVersion the version currently used by the project
     * @return list of deleted version strings
     */
    fun cleanupOldVersions(currentVersion: String?): List<String> {
        val deletable = getDeletableVersions(currentVersion)
        val deleted = mutableListOf<String>()
        for (version in deletable) {
            if (deleteVersion(version)) {
                LOG.info("Deleted Gradle version ${version.version} (${version.sizeFormatted})")
                deleted.add(version.version)
            } else {
                LOG.warn("Failed to delete Gradle version ${version.version}")
            }
        }
        return deleted
    }
}
