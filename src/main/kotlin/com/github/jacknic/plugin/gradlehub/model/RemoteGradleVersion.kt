package com.github.jacknic.plugin.gradlehub.model

/**
 * Data model representing a remote Gradle version available for download.
 *
 * @property version the Gradle version string (e.g. "8.7")
 * @property downloadUrl the download URL for the `-bin` distribution
 * @property isCurrent whether this is the current stable release
 * @property isSnapshot whether this is a snapshot build
 * @property isBroken whether this version is known to be broken
 * @property isNightly whether this is a nightly build
 */
data class RemoteGradleVersion(
    val version: String,
    val downloadUrl: String,
    val isCurrent: Boolean = false,
    val isSnapshot: Boolean = false,
    val isBroken: Boolean = false,
    val isNightly: Boolean = false,
) {
    /** Whether this is a stable release (not snapshot, nightly, RC, or milestone). */
    val isStable: Boolean
        get() = !isSnapshot && !isBroken && !isNightly
                && !version.contains("-rc-")
                && !version.contains("-milestone-")
                && !version.contains("-")

    companion object {
        /** Stable version pattern: major.minor or major.minor.patch (e.g. "8.7", "8.14.5"). */
        val STABLE_VERSION_PATTERN = Regex("""^\d+\.\d+(?:\.\d+)?$""")
    }
}
