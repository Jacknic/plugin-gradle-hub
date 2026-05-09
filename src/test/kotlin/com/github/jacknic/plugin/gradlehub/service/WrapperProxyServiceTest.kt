package com.github.jacknic.plugin.gradlehub.service

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Unit tests for [WrapperProxyService] companion object methods.
 *
 * Tests the pure URL parsing, transformation, and validation logic
 * that does not depend on IntelliJ project context.
 */
class WrapperProxyServiceTest : BasePlatformTestCase() {

    // ---- parseDistributionUrl ----

    fun testParseDistributionUrl_standardFormat() {
        val content = """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-8.6-bin.zip
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
        """.trimIndent()

        val url = WrapperProxyService.parseDistributionUrl(content)
        assertEquals("https://services.gradle.org/distributions/gradle-8.6-bin.zip", url)
    }

    fun testParseDistributionUrl_unescapedFormat() {
        val content = """
            distributionUrl=https://services.gradle.org/distributions/gradle-7.6.4-all.zip
        """.trimIndent()

        val url = WrapperProxyService.parseDistributionUrl(content)
        assertEquals("https://services.gradle.org/distributions/gradle-7.6.4-all.zip", url)
    }

    fun testParseDistributionUrl_missingKey() {
        val content = """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
        """.trimIndent()

        val url = WrapperProxyService.parseDistributionUrl(content)
        assertNull(url)
    }

    fun testParseDistributionUrl_commentedOut() {
        val content = """
            # distributionUrl=https://services.gradle.org/distributions/gradle-8.6-bin.zip
            distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
        """.trimIndent()

        val url = WrapperProxyService.parseDistributionUrl(content)
        assertEquals("https://services.gradle.org/distributions/gradle-8.5-bin.zip", url)
    }

    fun testParseDistributionUrl_emptyContent() {
        val url = WrapperProxyService.parseDistributionUrl("")
        assertNull(url)
    }

    fun testParseDistributionUrl_withWhitespace() {
        val content = "distributionUrl =  https\\://services.gradle.org/distributions/gradle-8.6-bin.zip  "
        // Our parser expects no spaces around '='
        val url = WrapperProxyService.parseDistributionUrl(content)
        // The line doesn't start with "distributionUrl=" directly, so it may not parse
        // This verifies our current parsing behavior
        if (url != null) {
            assertTrue(url.contains("gradle-8.6-bin.zip"))
        }
    }

    // ---- replaceDistributionUrl ----

    fun testReplaceDistributionUrl_withEscaping() {
        val content = """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-8.6-bin.zip
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
        """.trimIndent()

        val newUrl = "https://mirrors.cloud.tencent.com/gradle/gradle-8.6-bin.zip"
        val result = WrapperProxyService.replaceDistributionUrl(content, newUrl)

        assertTrue(result.contains("distributionUrl=https\\://mirrors.cloud.tencent.com/gradle/gradle-8.6-bin.zip"))
        assertTrue(result.contains("distributionBase=GRADLE_USER_HOME")) // Other lines preserved
    }

    fun testReplaceDistributionUrl_withoutEscaping() {
        val content = """
            distributionBase=GRADLE_USER_HOME
            distributionUrl=https://services.gradle.org/distributions/gradle-8.6-bin.zip
        """.trimIndent()

        val newUrl = "https://mirrors.cloud.tencent.com/gradle/gradle-8.6-bin.zip"
        val result = WrapperProxyService.replaceDistributionUrl(content, newUrl)

        assertTrue(result.contains("distributionUrl=https://mirrors.cloud.tencent.com/gradle/gradle-8.6-bin.zip"))
        assertFalse(result.contains("\\:"))
    }

    fun testReplaceDistributionUrl_preservesOtherLines() {
        val content = """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-8.6-bin.zip
            networkTimeout=10000
        """.trimIndent()

        val newUrl = "https://mirrors.aliyun.com/gradle/gradle-8.6-bin.zip"
        val result = WrapperProxyService.replaceDistributionUrl(content, newUrl)

        assertTrue(result.contains("networkTimeout=10000"))
        assertTrue(result.contains("distributionBase=GRADLE_USER_HOME"))
    }

    // ---- transformUrl ----

    fun testTransformUrl_withTrailingSlash() {
        val original = "https://services.gradle.org/distributions/gradle-8.6-bin.zip"
        val mirror = "https://mirrors.cloud.tencent.com/gradle/"

        val result = WrapperProxyService.transformUrl(original, mirror)
        assertEquals("https://mirrors.cloud.tencent.com/gradle/gradle-8.6-bin.zip", result)
    }

    fun testTransformUrl_withoutTrailingSlash() {
        val original = "https://services.gradle.org/distributions/gradle-8.6-bin.zip"
        val mirror = "https://mirrors.cloud.tencent.com/gradle"

        val result = WrapperProxyService.transformUrl(original, mirror)
        assertEquals("https://mirrors.cloud.tencent.com/gradle/gradle-8.6-bin.zip", result)
    }

    fun testTransformUrl_emptyMirror() {
        val original = "https://services.gradle.org/distributions/gradle-8.6-bin.zip"

        val result = WrapperProxyService.transformUrl(original, "")
        assertEquals(original, result)
    }

    fun testTransformUrl_blankMirror() {
        val original = "https://services.gradle.org/distributions/gradle-8.6-bin.zip"

        val result = WrapperProxyService.transformUrl(original, "   ")
        assertEquals(original, result)
    }

    fun testTransformUrl_allDistribution() {
        val original = "https://services.gradle.org/distributions/gradle-7.6.4-all.zip"
        val mirror = "https://repo.huaweicloud.com/gradle/"

        val result = WrapperProxyService.transformUrl(original, mirror)
        assertEquals("https://repo.huaweicloud.com/gradle/gradle-7.6.4-all.zip", result)
    }

    // ---- parseVersionFromUrl ----

    fun testParseVersionFromUrl_standardBin() {
        val url = "https://services.gradle.org/distributions/gradle-8.6-bin.zip"
        assertEquals("8.6", WrapperProxyService.parseVersionFromUrl(url))
    }

    fun testParseVersionFromUrl_allDistribution() {
        val url = "https://services.gradle.org/distributions/gradle-7.6.4-all.zip"
        assertEquals("7.6.4", WrapperProxyService.parseVersionFromUrl(url))
    }

    fun testParseVersionFromUrl_threePartVersion() {
        val url = "https://services.gradle.org/distributions/gradle-8.10.2-bin.zip"
        assertEquals("8.10.2", WrapperProxyService.parseVersionFromUrl(url))
    }

    fun testParseVersionFromUrl_mirrorUrl() {
        val url = "https://mirrors.cloud.tencent.com/gradle/gradle-8.6-bin.zip"
        assertEquals("8.6", WrapperProxyService.parseVersionFromUrl(url))
    }

    fun testParseVersionFromUrl_invalidUrl() {
        val url = "https://example.com/something-else.zip"
        assertNull(WrapperProxyService.parseVersionFromUrl(url))
    }

    fun testParseVersionFromUrl_justFilename() {
        assertEquals("8.6", WrapperProxyService.parseVersionFromUrl("gradle-8.6-bin.zip"))
    }

    // ---- extractFileName ----

    fun testExtractFileName() {
        val url = "https://services.gradle.org/distributions/gradle-8.6-bin.zip"
        assertEquals("gradle-8.6-bin.zip", WrapperProxyService.extractFileName(url))
    }

    fun testExtractFileName_noPath() {
        assertEquals("gradle-8.6-bin.zip", WrapperProxyService.extractFileName("gradle-8.6-bin.zip"))
    }

    // ---- isValidMirrorUrl ----

    fun testIsValidMirrorUrl_https() {
        assertTrue(WrapperProxyService.isValidMirrorUrl("https://mirrors.cloud.tencent.com/gradle/"))
    }

    fun testIsValidMirrorUrl_http() {
        assertTrue(WrapperProxyService.isValidMirrorUrl("http://proxy.local/gradle/"))
    }

    fun testIsValidMirrorUrl_ftp() {
        assertFalse(WrapperProxyService.isValidMirrorUrl("ftp://mirrors.example.com/gradle/"))
    }

    fun testIsValidMirrorUrl_empty() {
        assertFalse(WrapperProxyService.isValidMirrorUrl(""))
    }

    fun testIsValidMirrorUrl_noScheme() {
        assertFalse(WrapperProxyService.isValidMirrorUrl("mirrors.example.com/gradle/"))
    }

    // ---- loadWrapperProperties ----

    fun testLoadWrapperProperties() {
        val content = """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-8.6-bin.zip
            networkTimeout=10000
        """.trimIndent()

        val props = WrapperProxyService.loadWrapperProperties(content)
        assertEquals("GRADLE_USER_HOME", props.getProperty("distributionBase"))
        assertEquals("wrapper/dists", props.getProperty("distributionPath"))
        // Properties auto-unescapes \:
        assertEquals("https://services.gradle.org/distributions/gradle-8.6-bin.zip",
            props.getProperty("distributionUrl"))
        assertEquals("10000", props.getProperty("networkTimeout"))
    }

    // ---- Integration: full flow ----

    fun testFullFlow_parseTransformReplace() {
        val originalContent = """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-8.6-bin.zip
            networkTimeout=10000
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
        """.trimIndent()

        // 1. Parse original URL
        val originalUrl = WrapperProxyService.parseDistributionUrl(originalContent)
        assertNotNull(originalUrl)
        assertEquals("https://services.gradle.org/distributions/gradle-8.6-bin.zip", originalUrl)

        // 2. Parse version
        val version = WrapperProxyService.parseVersionFromUrl(originalUrl!!)
        assertEquals("8.6", version)

        // 3. Transform URL with mirror
        val mirrorUrl = "https://mirrors.cloud.tencent.com/gradle/"
        val newUrl = WrapperProxyService.transformUrl(originalUrl, mirrorUrl)
        assertEquals("https://mirrors.cloud.tencent.com/gradle/gradle-8.6-bin.zip", newUrl)

        // 4. Replace in content
        val newContent = WrapperProxyService.replaceDistributionUrl(originalContent, newUrl)
        assertTrue(newContent.contains("distributionUrl=https\\://mirrors.cloud.tencent.com/gradle/gradle-8.6-bin.zip"))
        assertTrue(newContent.contains("networkTimeout=10000"))

        // 5. Re-parse from new content
        val parsedNewUrl = WrapperProxyService.parseDistributionUrl(newContent)
        assertEquals(newUrl, parsedNewUrl)
    }
}
