package com.github.jacknic.plugin.gradlehub.service

import com.github.jacknic.plugin.gradlehub.model.RemoteGradleVersion
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files

/**
 * Unit tests for [GradleDownloadService] companion object methods.
 *
 * Tests the pure URL building, hash computation, JSON parsing, and file
 * download logic that does not depend on network or IntelliJ application context.
 */
class GradleDownloadServiceTest : BasePlatformTestCase() {

    // ---- buildDistributionUrl ----

    fun testBuildDistributionUrl_bin() {
        val url = GradleDownloadService.buildDistributionUrl("8.6", "bin")
        assertEquals("https://services.gradle.org/distributions/gradle-8.6-bin.zip", url)
    }

    fun testBuildDistributionUrl_all() {
        val url = GradleDownloadService.buildDistributionUrl("7.6.4", "all")
        assertEquals("https://services.gradle.org/distributions/gradle-7.6.4-all.zip", url)
    }

    fun testBuildDistributionUrl_defaultDistType() {
        val url = GradleDownloadService.buildDistributionUrl("8.7")
        assertEquals("https://services.gradle.org/distributions/gradle-8.7-bin.zip", url)
    }

    // ---- buildMirrorDistributionUrl ----

    fun testBuildMirrorDistributionUrl_withMirror() {
        val url = GradleDownloadService.buildMirrorDistributionUrl(
            "8.6", "https://mirrors.cloud.tencent.com/gradle/", "bin"
        )
        assertEquals("https://mirrors.cloud.tencent.com/gradle/gradle-8.6-bin.zip", url)
    }

    fun testBuildMirrorDistributionUrl_mirrorWithoutTrailingSlash() {
        val url = GradleDownloadService.buildMirrorDistributionUrl(
            "8.6", "https://mirrors.cloud.tencent.com/gradle", "bin"
        )
        assertEquals("https://mirrors.cloud.tencent.com/gradle/gradle-8.6-bin.zip", url)
    }

    fun testBuildMirrorDistributionUrl_blankMirror() {
        val url = GradleDownloadService.buildMirrorDistributionUrl("8.6", "", "bin")
        assertEquals(GradleDownloadService.buildDistributionUrl("8.6", "bin"), url)
    }

    // ---- computeDistributionHash ----

    fun testComputeDistributionHash_consistent() {
        val url = "https://services.gradle.org/distributions/gradle-8.6-bin.zip"
        val hash1 = GradleDownloadService.computeDistributionHash(url)
        val hash2 = GradleDownloadService.computeDistributionHash(url)
        assertEquals(hash1, hash2)
    }

    fun testComputeDistributionHash_differentUrls() {
        val url1 = "https://services.gradle.org/distributions/gradle-8.6-bin.zip"
        val url2 = "https://services.gradle.org/distributions/gradle-8.6-all.zip"
        val hash1 = GradleDownloadService.computeDistributionHash(url1)
        val hash2 = GradleDownloadService.computeDistributionHash(url2)
        assertNotSame(hash1, hash2)
    }

    fun testComputeDistributionHash_nonEmpty() {
        val url = "https://services.gradle.org/distributions/gradle-8.6-bin.zip"
        val hash = GradleDownloadService.computeDistributionHash(url)
        assertTrue(hash.isNotEmpty())
    }

    // ---- buildDistributionBaseName ----

    fun testBuildDistributionBaseName_bin() {
        assertEquals("gradle-8.6-bin", GradleDownloadService.buildDistributionBaseName("8.6", "bin"))
    }

    fun testBuildDistributionBaseName_all() {
        assertEquals("gradle-7.6.4-all", GradleDownloadService.buildDistributionBaseName("7.6.4", "all"))
    }

    // ---- buildTargetDirectory ----

    fun testBuildTargetDirectory_structure() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        try {
            val targetDir = GradleDownloadService.buildTargetDirectory(tempDir, "8.6", "bin")
            // Should be: tempDir/gradle-8.6-bin/{hash}/
            assertEquals("gradle-8.6-bin", targetDir.parentFile.name)
            assertTrue(targetDir.name.isNotEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ---- parseRemoteVersions ----

    fun testParseRemoteVersions_emptyJson() {
        val result = GradleDownloadService.parseRemoteVersions("[]")
        assertTrue(result.isEmpty())
    }

    fun testParseRemoteVersions_singleVersion() {
        val json = """[{"version":"8.7","downloadUrl":"https://services.gradle.org/distributions/gradle-8.7-bin.zip","current":true,"snapshot":false,"broken":false}]"""
        val result = GradleDownloadService.parseRemoteVersions(json)
        assertEquals(1, result.size)
        assertEquals("8.7", result[0].version)
        assertTrue(result[0].isCurrent)
        assertFalse(result[0].isSnapshot)
        assertFalse(result[0].isBroken)
    }

    fun testParseRemoteVersions_multipleVersions() {
        val json = """[
            {"version":"8.7","downloadUrl":"https://services.gradle.org/distributions/gradle-8.7-bin.zip","current":true,"snapshot":false,"broken":false},
            {"version":"8.6","downloadUrl":"https://services.gradle.org/distributions/gradle-8.6-bin.zip","current":false,"snapshot":false,"broken":false},
            {"version":"7.6.4","downloadUrl":"https://services.gradle.org/distributions/gradle-7.6.4-bin.zip","current":false,"snapshot":false,"broken":false}
        ]"""
        val result = GradleDownloadService.parseRemoteVersions(json)
        assertEquals(3, result.size)
        // Current version should be first
        assertTrue(result[0].isCurrent)
    }

    fun testParseRemoteVersions_brokenVersion() {
        val json = """[{"version":"6.0","downloadUrl":"https://services.gradle.org/distributions/gradle-6.0-bin.zip","current":false,"snapshot":false,"broken":true}]"""
        val result = GradleDownloadService.parseRemoteVersions(json)
        assertEquals(1, result.size)
        assertTrue(result[0].isBroken)
    }

    fun testParseRemoteVersions_snapshotVersion() {
        val json = """[{"version":"8.8-rc-1","downloadUrl":"https://services.gradle.org/distributions/gradle-8.8-rc-1-bin.zip","current":false,"snapshot":true,"broken":false}]"""
        val result = GradleDownloadService.parseRemoteVersions(json)
        assertEquals(1, result.size)
        assertTrue(result[0].isSnapshot)
        assertFalse(result[0].isStable)
    }

    fun testParseRemoteVersions_nightlyVersion() {
        val json = """[{"version":"9.6.0-20260509002831+0000","downloadUrl":"","current":false,"snapshot":true,"nightly":true,"releaseNightly":false,"broken":false}]"""
        val result = GradleDownloadService.parseRemoteVersions(json)
        assertEquals(1, result.size)
        assertTrue(result[0].isNightly)
        assertFalse(result[0].isStable)
    }

    fun testParseRemoteVersions_rcVersion() {
        val json = """[{"version":"9.5.0-rc-4","downloadUrl":"","current":false,"snapshot":false,"nightly":false,"releaseNightly":false,"broken":false}]"""
        val result = GradleDownloadService.parseRemoteVersions(json)
        assertEquals(1, result.size)
        assertFalse(result[0].isSnapshot)
        assertFalse(result[0].isNightly)
        // RC versions are not stable (version contains "-")
        assertFalse(result[0].isStable)
    }

    fun testParseRemoteVersions_milestoneVersion() {
        val json = """[{"version":"9.6.0-milestone-1","downloadUrl":"","current":false,"snapshot":false,"nightly":false,"releaseNightly":false,"broken":false}]"""
        val result = GradleDownloadService.parseRemoteVersions(json)
        assertEquals(1, result.size)
        // Milestone versions are not stable (version contains "-")
        assertFalse(result[0].isStable)
    }

    fun testParseRemoteVersions_stableVersion() {
        val json = """[{"version":"8.14.5","downloadUrl":"","current":false,"snapshot":false,"nightly":false,"releaseNightly":false,"broken":false}]"""
        val result = GradleDownloadService.parseRemoteVersions(json)
        assertEquals(1, result.size)
        assertTrue(result[0].isStable)
    }

    fun testParseRemoteVersions_missingDownloadUrl() {
        val json = """[{"version":"8.7","current":true,"snapshot":false,"broken":false}]"""
        val result = GradleDownloadService.parseRemoteVersions(json)
        assertEquals(1, result.size)
        // Should fallback to buildDistributionUrl
        assertEquals(GradleDownloadService.buildDistributionUrl("8.7"), result[0].downloadUrl)
    }

    fun testParseRemoteVersions_missingVersion() {
        val json = """[{"downloadUrl":"https://services.gradle.org/distributions/gradle-8.7-bin.zip","current":true}]"""
        val result = GradleDownloadService.parseRemoteVersions(json)
        assertTrue(result.isEmpty())
    }

    fun testParseRemoteVersions_sorting() {
        val json = """[
            {"version":"7.6","downloadUrl":"","current":false,"snapshot":false,"broken":false},
            {"version":"8.7","downloadUrl":"","current":true,"snapshot":false,"broken":false},
            {"version":"8.6","downloadUrl":"","current":false,"snapshot":false,"broken":false},
            {"version":"8.14","downloadUrl":"","current":false,"snapshot":false,"broken":false},
            {"version":"8.9","downloadUrl":"","current":false,"snapshot":false,"broken":false}
        ]"""
        val result = GradleDownloadService.parseRemoteVersions(json)
        // Current version should be first
        assertTrue(result[0].isCurrent)
        assertEquals("8.7", result[0].version)
        // Semantic version sorting: 8.14 > 8.9 > 8.6 > 7.6
        assertEquals("8.14", result[1].version)
        assertEquals("8.9", result[2].version)
        assertEquals("8.6", result[3].version)
        assertEquals("7.6", result[4].version)
    }

    fun testParseRemoteVersions_realApiFormat() {
        val json = """[
  {
    "version" : "9.5.0",
    "buildTime" : "20260428120530+0000",
    "commitId" : "3fe117d68f3907790f3809f121aa36303a9151f8",
    "current" : true,
    "snapshot" : false,
    "nightly" : false,
    "releaseNightly" : false,
    "activeRc" : false,
    "rcFor" : "",
    "milestoneFor" : "",
    "broken" : false,
    "downloadUrl" : "https://services.gradle.org/distributions/gradle-9.5.0-bin.zip",
    "checksumUrl" : "https://services.gradle.org/distributions/gradle-9.5.0-bin.zip.sha256",
    "checksum" : "553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746",
    "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-9.5.0-wrapper.jar.sha256",
    "wrapperChecksum" : "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"
  },
  {
    "version" : "8.14.5",
    "buildTime" : "20260507110329+0000",
    "commitId" : "62345becae08b13e793521816d585102fea66398",
    "current" : false,
    "snapshot" : false,
    "nightly" : false,
    "releaseNightly" : false,
    "activeRc" : false,
    "rcFor" : "",
    "milestoneFor" : "",
    "broken" : false,
    "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.14.5-bin.zip",
    "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.14.5-bin.zip.sha256",
    "checksum" : "6f74b601422d6d6fc4e1f9a1ab6522f642c2fdcbc15ae33ebd30ba3d7198e854",
    "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.14.5-wrapper.jar.sha256",
    "wrapperChecksum" : "7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172"
  }
]"""
        val result = GradleDownloadService.parseRemoteVersions(json)
        assertEquals(2, result.size)
        assertTrue(result[0].isCurrent)
        assertEquals("9.5.0", result[0].version)
        assertFalse(result[0].isNightly)
        assertTrue(result[0].isStable)
        assertFalse(result[1].isCurrent)
        assertEquals("8.14.5", result[1].version)
        assertTrue(result[1].isStable)
    }

    // ---- isVersionDownloaded ----

    fun testIsVersionDownloaded_notExists() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        try {
            assertFalse(GradleDownloadService.isVersionDownloaded(tempDir, "8.6"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testIsVersionDownloaded_existsWithMarker() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        try {
            val targetDir = GradleDownloadService.buildTargetDirectory(tempDir, "8.6", "bin")
            targetDir.mkdirs()
            val baseName = GradleDownloadService.buildDistributionBaseName("8.6")
            // Create the zip file
            File(targetDir, "$baseName.zip").createNewFile()
            // Create the marker file
            File(targetDir, "$baseName.zip.ok").createNewFile()

            assertTrue(GradleDownloadService.isVersionDownloaded(tempDir, "8.6"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testIsVersionDownloaded_existsWithoutMarker() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        try {
            val targetDir = GradleDownloadService.buildTargetDirectory(tempDir, "8.6", "bin")
            targetDir.mkdirs()
            val baseName = GradleDownloadService.buildDistributionBaseName("8.6")
            // Create only the zip file, no marker
            File(targetDir, "$baseName.zip").createNewFile()

            assertFalse(GradleDownloadService.isVersionDownloaded(tempDir, "8.6"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ---- DownloadProgress ----

    fun testDownloadProgress_percentage() {
        val progress = DownloadProgress(downloaded = 50, total = 100, speed = 1024, etaSeconds = 1)
        assertEquals(50, progress.percentage)
    }

    fun testDownloadProgress_percentageUnknown() {
        val progress = DownloadProgress(downloaded = 50, total = -1, speed = 1024, etaSeconds = -1)
        assertEquals(-1, progress.percentage)
    }

    fun testDownloadProgress_isTotalKnown() {
        assertTrue(DownloadProgress(50, 100, 0, 0).isTotalKnown)
        assertFalse(DownloadProgress(50, -1, 0, 0).isTotalKnown)
    }

    fun testDownloadProgress_formatSpeed() {
        assertEquals("512 B/s", DownloadProgress.formatSpeed(512))
        assertEquals("1.5 KB/s", DownloadProgress.formatSpeed(1536))
        assertEquals("2.0 MB/s", DownloadProgress.formatSpeed(2L * 1024 * 1024))
        assertEquals("1.0 GB/s", DownloadProgress.formatSpeed(1L * 1024 * 1024 * 1024))
        assertEquals("", DownloadProgress.formatSpeed(0))
    }

    fun testDownloadProgress_formatEta() {
        assertEquals("", DownloadProgress.formatEta(0))
        assertEquals("", DownloadProgress.formatEta(-1))
        assertEquals("30s", DownloadProgress.formatEta(30))
        assertEquals("1m 30s", DownloadProgress.formatEta(90))
        assertEquals("1h 1m", DownloadProgress.formatEta(3660))
    }

    // ---- extractZip ----

    fun testExtractZip_simpleZip() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        try {
            // Create a simple zip file
            val zipFile = File(tempDir, "test.zip")
            createTestZip(zipFile, mapOf(
                "test-dir/" to null,
                "test-dir/file.txt" to "hello world",
                "bin/gradle" to "#!/bin/sh\necho gradle"
            ))

            val extractDir = File(tempDir, "extracted")
            extractDir.mkdirs()

            GradleDownloadService.extractZip(zipFile, extractDir)

            assertTrue(File(extractDir, "test-dir").isDirectory)
            assertTrue(File(extractDir, "test-dir/file.txt").isFile)
            assertEquals("hello world", File(extractDir, "test-dir/file.txt").readText())
            // bin scripts should be executable
            assertTrue(File(extractDir, "bin/gradle").canExecute())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ---- downloadFile ----

    fun testDownloadFile_nonExistentUrl() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        try {
            val targetFile = File(tempDir, "download.zip")
            val result = GradleDownloadService.downloadFile(
                "http://localhost:1/nonexistent",
                targetFile,
                onProgress = {}
            )
            assertFalse(result)
            assertFalse(targetFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testDownloadFile_cancelled() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        try {
            val targetFile = File(tempDir, "download.zip")
            val result = GradleDownloadService.downloadFile(
                "http://localhost:1/nonexistent",
                targetFile,
                onProgress = {},
                isCancelled = { true }
            )
            assertFalse(result)
            assertFalse(targetFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ---- Helper methods ----

    private fun createTestZip(zipFile: File, entries: Map<String, String?>) {
        java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zos ->
            entries.forEach { (name, content) ->
                val entry = java.util.zip.ZipEntry(name)
                zos.putNextEntry(entry)
                if (content != null) {
                    zos.write(content.toByteArray())
                }
                zos.closeEntry()
            }
        }
    }
}
