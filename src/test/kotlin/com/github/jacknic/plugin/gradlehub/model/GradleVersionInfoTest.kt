package com.github.jacknic.plugin.gradlehub.model

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files

/**
 * Unit tests for [GradleVersionInfo] data model and utilities.
 */
class GradleVersionInfoTest : BasePlatformTestCase() {

    fun testFormatFileSize_bytes() {
        assertEquals("512 B", GradleVersionInfo.formatFileSize(512))
    }

    fun testFormatFileSize_kilobytes() {
        assertEquals("1.5 KB", GradleVersionInfo.formatFileSize(1536))
    }

    fun testFormatFileSize_megabytes() {
        assertEquals("150.0 MB", GradleVersionInfo.formatFileSize(150L * 1024 * 1024))
    }

    fun testFormatFileSize_gigabytes() {
        assertEquals("2.5 GB", GradleVersionInfo.formatFileSize((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    fun testSizeFormattedProperty() {
        val info = GradleVersionInfo(
            version = "8.6",
            path = "/tmp/gradle",
            size = 100L * 1024 * 1024,
            lastModified = 0L
        )
        assertEquals("100.0 MB", info.sizeFormatted)
    }

    fun testFromDirectory_invalidDirectory() {
        val tempDir = Files.createTempDirectory("gradlehub-test").toFile()
        val tempFile = File(tempDir, "not-a-dir.txt")
        tempFile.createNewFile()
        try {
            val result = GradleVersionInfo.fromDirectory(tempFile)
            assertNull(result)
        } finally {
            tempFile.delete()
            tempDir.delete()
        }
    }

    fun testFromDirectory_nonExistentDirectory() {
        val result = GradleVersionInfo.fromDirectory(File("/nonexistent/path"))
        assertNull(result)
    }

    fun testDataClassProperties() {
        val info = GradleVersionInfo(
            version = "8.6",
            path = "/home/user/.gradle/wrapper/dists/8.6",
            size = 125829120L,
            lastModified = 1700000000000L
        )
        assertEquals("8.6", info.version)
        assertEquals("/home/user/.gradle/wrapper/dists/8.6", info.path)
        assertEquals(125829120L, info.size)
        assertEquals(1700000000000L, info.lastModified)
    }

    fun testDataClassEquality() {
        val info1 = GradleVersionInfo("8.6", "/path", 100L, 0L)
        val info2 = GradleVersionInfo("8.6", "/path", 100L, 0L)
        assertEquals(info1, info2)
    }

    fun testDataClassCopy() {
        val info = GradleVersionInfo("8.6", "/path", 100L, 0L)
        val copied = info.copy(version = "7.6.4")
        assertEquals("7.6.4", copied.version)
        assertEquals("/path", copied.path)
        assertEquals(100L, copied.size)
    }
}
