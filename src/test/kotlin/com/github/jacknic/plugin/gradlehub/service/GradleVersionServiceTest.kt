package com.github.jacknic.plugin.gradlehub.service

import com.github.jacknic.plugin.gradlehub.model.GradleVersionInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files

/**
 * Unit tests for [GradleVersionService] companion object methods.
 *
 * Tests the pure scanning, filtering, and deletion logic
 * that does not depend on IntelliJ application context.
 */
class GradleVersionServiceTest : BasePlatformTestCase() {

    // ---- scanVersions ----

    fun testScanVersions_emptyDirectory() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        try {
            val result = GradleVersionService.scanVersions(tempDir)
            assertTrue(result.isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testScanVersions_nonExistentDirectory() {
        val result = GradleVersionService.scanVersions(File("/nonexistent/path"))
        assertTrue(result.isEmpty())
    }

    fun testScanVersions_notDirectory() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        val tempFile = File(tempDir, "not-a-dir.txt")
        tempFile.createNewFile()
        try {
            val result = GradleVersionService.scanVersions(tempFile)
            assertTrue(result.isEmpty())
        } finally {
            tempFile.delete()
            tempDir.deleteRecursively()
        }
    }

    fun testScanVersions_validStructure() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        try {
            // Create: wrapper/dists/8.6/abc123/
            val versionDir = File(tempDir, "8.6")
            val hashDir = File(versionDir, "abc123")
            hashDir.mkdirs()
            val distFile = File(hashDir, "gradle-8.6-bin.zip")
            distFile.writeText("fake content")

            val result = GradleVersionService.scanVersions(tempDir)
            assertEquals(1, result.size)
            assertEquals("8.6", result[0].version)
            assertTrue(result[0].path.contains("abc123"))
            assertTrue(result[0].size > 0)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testScanVersions_multipleVersions() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        try {
            // Create 8.6
            val v86Dir = File(tempDir, "8.6")
            val v86Hash = File(v86Dir, "hash1")
            v86Hash.mkdirs()
            File(v86Hash, "gradle-8.6-bin.zip").writeText("a")

            // Create 7.6.4
            val v76Dir = File(tempDir, "7.6.4")
            val v76Hash = File(v76Dir, "hash2")
            v76Hash.mkdirs()
            File(v76Hash, "gradle-7.6.4-all.zip").writeText("bb")

            val result = GradleVersionService.scanVersions(tempDir)
            assertEquals(2, result.size)
            // Sorted by version descending
            assertEquals("8.6", result[0].version)
            assertEquals("7.6.4", result[1].version)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testScanVersions_multipleHashDirsForSameVersion() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        try {
            // Two hash dirs under the same version (from different distributionUrl)
            val versionDir = File(tempDir, "8.6")
            val hash1 = File(versionDir, "hash1")
            hash1.mkdirs()
            File(hash1, "gradle-8.6-bin.zip").writeText("bin content")

            val hash2 = File(versionDir, "hash2")
            hash2.mkdirs()
            File(hash2, "gradle-8.6-all.zip").writeText("all content longer")

            val result = GradleVersionService.scanVersions(tempDir)
            assertEquals(2, result.size)
            // Both should have version "8.6"
            assertTrue(result.all { it.version == "8.6" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testScanVersions_emptyVersionDir() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        try {
            // Version directory with no hash subdirectories
            val versionDir = File(tempDir, "8.6")
            versionDir.mkdirs()

            val result = GradleVersionService.scanVersions(tempDir)
            assertTrue(result.isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testScanVersions_hashDirWithoutValidVersion() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        try {
            // A hash directory whose parent doesn't match version pattern
            val invalidDir = File(tempDir, "invalid-name")
            val hashDir = File(invalidDir, "hash1")
            hashDir.mkdirs()
            // No gradle-*.zip file, so version extraction will fail
            File(hashDir, "readme.txt").writeText("not a gradle dist")

            val result = GradleVersionService.scanVersions(tempDir)
            // fromDirectory returns null because no version can be extracted
            assertTrue(result.isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ---- findDeletableVersions ----

    fun testFindDeletableVersions_emptyList() {
        val result = GradleVersionService.findDeletableVersions(emptyList(), null, 2)
        assertTrue(result.isEmpty())
    }

    fun testFindDeletableVersions_fewerThanKeepCount() {
        val versions = listOf(
            GradleVersionInfo("8.6", "/path/8.6", 100L, 3000L),
            GradleVersionInfo("7.6", "/path/7.6", 80L, 2000L),
        )
        val result = GradleVersionService.findDeletableVersions(versions, null, 3)
        assertTrue(result.isEmpty())
    }

    fun testFindDeletableVersions_exceedsKeepCount() {
        val versions = listOf(
            GradleVersionInfo("8.6", "/path/8.6", 100L, 5000L),
            GradleVersionInfo("8.5", "/path/8.5", 90L, 4000L),
            GradleVersionInfo("7.6", "/path/7.6", 80L, 3000L),
            GradleVersionInfo("7.5", "/path/7.5", 70L, 2000L),
        )
        val result = GradleVersionService.findDeletableVersions(versions, null, 2)
        // Keep newest 2 by lastModified: 8.6 (5000), 8.5 (4000)
        // Deletable: 7.6 (3000), 7.5 (2000)
        assertEquals(2, result.size)
        assertTrue(result.any { it.version == "7.6" })
        assertTrue(result.any { it.version == "7.5" })
    }

    fun testFindDeletableVersions_protectsCurrentVersion() {
        val versions = listOf(
            GradleVersionInfo("8.6", "/path/8.6", 100L, 1000L),
            GradleVersionInfo("7.6", "/path/7.6", 80L, 2000L),
        )
        // keepCount=1 keeps 7.6 (newest by lastModified), but 8.6 is current project version
        val result = GradleVersionService.findDeletableVersions(versions, "8.6", 1)
        // 7.6 is kept (newest), 8.6 is protected (current version)
        assertTrue(result.isEmpty())
    }

    fun testFindDeletableVersions_currentVersionNotInList() {
        val versions = listOf(
            GradleVersionInfo("8.6", "/path/8.6", 100L, 1000L),
        )
        // Current version "7.6" is not in the list, but keepCount=1 protects 8.6
        val result = GradleVersionService.findDeletableVersions(versions, "7.6", 1)
        assertTrue(result.isEmpty())
    }

    fun testFindDeletableVersions_keepCountIsOne() {
        val versions = listOf(
            GradleVersionInfo("8.6", "/path/8.6", 100L, 5000L),
            GradleVersionInfo("7.6", "/path/7.6", 80L, 3000L),
        )
        val result = GradleVersionService.findDeletableVersions(versions, null, 1)
        // Keep only newest by lastModified: 8.6
        assertEquals(1, result.size)
        assertEquals("7.6", result[0].version)
    }

    // ---- deleteVersion ----

    fun testDeleteVersion_success() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        try {
            val versionDir = File(tempDir, "8.6")
            versionDir.mkdirs()
            File(versionDir, "gradle-8.6-bin.zip").writeText("content")

            val info = GradleVersionInfo("8.6", versionDir.absolutePath, 7L, 0L)
            assertTrue(GradleVersionService.deleteVersion(info))
            assertFalse(versionDir.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testDeleteVersion_nonExistentPath() {
        val info = GradleVersionInfo("8.6", "/nonexistent/path/8.6", 0L, 0L)
        assertFalse(GradleVersionService.deleteVersion(info))
    }

    fun testDeleteVersion_fileInsteadOfDirectory() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        try {
            val tempFile = File(tempDir, "not-a-dir")
            tempFile.writeText("content")

            val info = GradleVersionInfo("8.6", tempFile.absolutePath, 7L, 0L)
            assertFalse(GradleVersionService.deleteVersion(info))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ---- calculateTotalSize ----

    fun testCalculateTotalSize_emptyList() {
        assertEquals(0L, GradleVersionService.calculateTotalSize(emptyList()))
    }

    fun testCalculateTotalSize_singleVersion() {
        val versions = listOf(GradleVersionInfo("8.6", "/path", 1024L, 0L))
        assertEquals(1024L, GradleVersionService.calculateTotalSize(versions))
    }

    fun testCalculateTotalSize_multipleVersions() {
        val versions = listOf(
            GradleVersionInfo("8.6", "/path1", 100L, 0L),
            GradleVersionInfo("7.6", "/path2", 200L, 0L),
            GradleVersionInfo("6.9", "/path3", 300L, 0L),
        )
        assertEquals(600L, GradleVersionService.calculateTotalSize(versions))
    }
}
