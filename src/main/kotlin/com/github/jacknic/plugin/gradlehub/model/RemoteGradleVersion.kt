package com.github.jacknic.plugin.gradlehub.model

/**
 * Data model representing a remote Gradle version available for download.
 *
 * @property version the Gradle version string (e.g. "8.7")
 * @property downloadUrl the download URL for the `-bin` distribution
 * @property isCurrent whether this is the current stable release
 * @property isSnapshot whether this is a snapshot build
 * @property isBroken whether this version is known to be broken
 */
data class RemoteGradleVersion(
    val version: String,
    val downloadUrl: String,
    val isCurrent: Boolean = false,
    val isSnapshot: Boolean = false,
    val isBroken: Boolean = false,
)
