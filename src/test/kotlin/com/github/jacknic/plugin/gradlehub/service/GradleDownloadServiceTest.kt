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
            {"version":"8.6","downloadUrl":"","current":false,"snapshot":false,"broken":false}
        ]"""
        val result = GradleDownloadService.parseRemoteVersions(json)
        // Current version should be first
        assertTrue(result[0].isCurrent)
        assertEquals("8.7", result[0].version)
    }

    fun testParseRemoteVersions_realApiFormat() {
        val json = """[
  {
    "version" : "8.7",
    "buildTime" : "20240614093642",
    "current" : true,
    "snapshot" : false,
    "nightly" : false,
    "releaseNightly" : false,
    "activeRc" : false,
    "rcFor" : "",
    "milestoneFor" : "",
    "broken" : false,
    "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.7-bin.zip",
    "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.7-bin.zip.sha256",
    "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.7-wrapper.jar.sha256"
  },
  {
    "version" : "8.6",
    "buildTime" : "20240202155015",
    "current" : false,
    "snapshot" : false,
    "nightly" : false,
    "releaseNightly" : false,
    "activeRc" : false,
    "rcFor" : "",
    "milestoneFor" : "",
    "broken" : false,
    "downloadUrl" : "https://services.gradle.org/distributions/gradle-8.6-bin.zip",
    "checksumUrl" : "https://services.gradle.org/distributions/gradle-8.6-bin.zip.sha256",
    "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-8.6-wrapper.jar.sha256"
  }
]"""
        val result = GradleDownloadService.parseRemoteVersions(json)
        assertEquals(2, result.size)
        assertTrue(result[0].isCurrent)
        assertEquals("8.7", result[0].version)
        assertFalse(result[1].isCurrent)
        assertEquals("8.6", result[1].version)
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
                onProgress = { _, _ -> }
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
                onProgress = { _, _ -> },
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
