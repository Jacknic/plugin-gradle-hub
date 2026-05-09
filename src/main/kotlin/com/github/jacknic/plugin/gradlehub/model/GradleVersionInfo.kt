package com.github.jacknic.plugin.gradlehub.model

import java.io.File

/**
 * Data model representing a locally installed Gradle version.
 *
 * @property version the Gradle version string (e.g. "8.6")
 * @property path the absolute path to the distribution directory
 * @property size total size in bytes of the distribution files
 * @property lastModified timestamp of the most recently modified file in the distribution
 */
data class GradleVersionInfo(
    val version: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
) {
    /** Human-readable size string (e.g. "125.3 MB") */
    val sizeFormatted: String
        get() = formatFileSize(size)

    companion object {
        private const val KB: Long = 1024
        private const val MB = KB * 1024
        private const val GB = MB * 1024

        fun formatFileSize(bytes: Long): String = when {
            bytes >= GB -> "%.1f GB".format(bytes.toDouble() / GB)
            bytes >= MB -> "%.1f MB".format(bytes.toDouble() / MB)
            bytes >= KB -> "%.1f KB".format(bytes.toDouble() / KB)
            else -> "$bytes B"
        }

        /**
         * Scan a Gradle distribution directory and create a [GradleVersionInfo] instance.
         * The directory is typically under `~/.gradle/wrapper/dists/<version-hash>/`.
         *
         * @param directory the distribution directory to scan
         * @return a [GradleVersionInfo] or null if the directory is invalid
         */
        fun fromDirectory(directory: File): GradleVersionInfo? {
            if (!directory.isDirectory) return null
            val version = extractVersionFromDirectoryName(directory) ?: return null
            val (totalSize, lastModified) = calculateDirectoryStats(directory)
            return GradleVersionInfo(
                version = version,
                path = directory.absolutePath,
                size = totalSize,
                lastModified = lastModified,
            )
        }

        /**
         * Attempt to extract the Gradle version from a distribution directory name.
         * Distribution directories are typically named like `8.6` or contain a version in the path.
         */
        private fun extractVersionFromDirectoryName(dir: File): String? {
            // The wrapper dists structure is: ~/.gradle/wrapper/dists/<version-hash>/<hash>/
            // The version is typically encoded in the parent directory name
            val parentName = dir.parentFile?.name ?: return null
            // Try to extract version from parent name (e.g. "8.6" from "8.6/abcdef...")
            val versionRegex = Regex("""^(\d+\.\d+(?:\.\d+)?)$""")
            return versionRegex.find(parentName)?.groupValues?.get(1)
                ?: extractVersionFromDistPath(dir)
        }

        /**
         * Fallback: try to extract version from the distribution jar/zip files inside the directory.
         */
        private fun extractVersionFromDistPath(dir: File): String? {
            val files = dir.listFiles() ?: return null
            for (file in files) {
                val name = file.name
                val regex = Regex("""gradle-(\d+\.\d+(?:\.\d+)?)""")
                val match = regex.find(name)
                if (match != null) return match.groupValues[1]
            }
            return null
        }

        private fun calculateDirectoryStats(dir: File): Pair<Long, Long> {
            var totalSize = 0L
            var lastModified = 0L
            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    totalSize += file.length()
                    val mod = file.lastModified()
                    if (mod > lastModified) lastModified = mod
                }
            }
            return totalSize to lastModified
        }
    }
}
