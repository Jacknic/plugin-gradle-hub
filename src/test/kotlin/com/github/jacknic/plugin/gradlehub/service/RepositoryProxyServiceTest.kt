package com.github.jacknic.plugin.gradlehub.service

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Unit tests for [RepositoryProxyService] companion object methods.
 *
 * Tests the pure URL generation, injection, and removal logic
 * that does not depend on IntelliJ project context.
 */
class RepositoryProxyServiceTest : BasePlatformTestCase() {

    // ---- detectDslType ----

    fun testDetectDslType_groovyBuildGradle() {
        assertEquals("GROOVY", RepositoryProxyService.detectDslType("build.gradle"))
    }

    fun testDetectDslType_groovySettingsGradle() {
        assertEquals("GROOVY", RepositoryProxyService.detectDslType("settings.gradle"))
    }

    fun testDetectDslType_kotlinBuildGradle() {
        assertEquals("KOTLIN", RepositoryProxyService.detectDslType("build.gradle.kts"))
    }

    fun testDetectDslType_kotlinSettingsGradle() {
        assertEquals("KOTLIN", RepositoryProxyService.detectDslType("settings.gradle.kts"))
    }

    fun testDetectDslType_unknownFile() {
        assertEquals("GROOVY", RepositoryProxyService.detectDslType("unknown.txt"))
    }

    // ---- generateGroovyMirrorBlock ----

    fun testGenerateGroovyMirrorBlock() {
        val result = RepositoryProxyService.generateGroovyMirrorBlock("https://maven.aliyun.com/repository/public")
        assertTrue(result.contains(RepositoryProxyService.MARKER_BEGIN))
        assertTrue(result.contains(RepositoryProxyService.MARKER_END))
        assertTrue(result.contains("maven { url 'https://maven.aliyun.com/repository/public' }"))
        assertTrue(result.contains("allprojects"))
        assertTrue(result.contains("buildscript"))
        assertTrue(result.contains("repositories"))
    }

    fun testGenerateGroovyMirrorBlock_containsTwoRepoBlocks() {
        val result = RepositoryProxyService.generateGroovyMirrorBlock("https://mirror.example.com/")
        // Should have 2 occurrences of maven url (buildscript + project repositories)
        val count = result.split("maven { url 'https://mirror.example.com/' }").size - 1
        assertEquals(2, count)
    }

    // ---- generateKotlinMirrorBlock ----

    fun testGenerateKotlinMirrorBlock() {
        val result = RepositoryProxyService.generateKotlinMirrorBlock("https://maven.aliyun.com/repository/public")
        assertTrue(result.contains(RepositoryProxyService.MARKER_BEGIN))
        assertTrue(result.contains(RepositoryProxyService.MARKER_END))
        assertTrue(result.contains("""maven { url = uri("https://maven.aliyun.com/repository/public") }"""))
        assertTrue(result.contains("allprojects"))
    }

    fun testGenerateKotlinMirrorBlock_containsTwoRepoBlocks() {
        val result = RepositoryProxyService.generateKotlinMirrorBlock("https://mirror.example.com/")
        val count = result.split("""maven { url = uri("https://mirror.example.com/") }""").size - 1
        assertEquals(2, count)
    }

    // ---- generateMirrorBlock ----

    fun testGenerateMirrorBlock_groovy() {
        val result = RepositoryProxyService.generateMirrorBlock(
            "https://maven.aliyun.com/repository/public",
            "GROOVY"
        )
        assertTrue(result.contains("maven { url 'https://maven.aliyun.com/repository/public' }"))
    }

    fun testGenerateMirrorBlock_kotlin() {
        val result = RepositoryProxyService.generateMirrorBlock(
            "https://maven.aliyun.com/repository/public",
            "KOTLIN"
        )
        assertTrue(result.contains("""maven { url = uri("https://maven.aliyun.com/repository/public") }"""))
    }

    // ---- injectMirrorBlock ----

    fun testInjectMirrorBlock_intoExistingContent() {
        val original = """plugins {
            |    id("java")
            |}
            |
            |repositories {
            |    mavenCentral()
            |}
        """.trimMargin()

        val mirrorBlock = """${RepositoryProxyService.MARKER_BEGIN}
            |allprojects { repositories { maven { url 'https://mirror/' } } }
            |${RepositoryProxyService.MARKER_END}
        """.trimMargin()

        val result = RepositoryProxyService.injectMirrorBlock(original, mirrorBlock)
        assertTrue(result.startsWith(RepositoryProxyService.MARKER_BEGIN))
        assertTrue(result.contains("plugins {"))
        assertTrue(result.contains("mavenCentral()"))
    }

    fun testInjectMirrorBlock_alreadyInjected() {
        val content = """${RepositoryProxyService.MARKER_BEGIN}
            |some mirror block
            |${RepositoryProxyService.MARKER_END}
            |
            |repositories { mavenCentral() }
        """.trimMargin()

        val mirrorBlock = "new mirror block"
        val result = RepositoryProxyService.injectMirrorBlock(content, mirrorBlock)
        // Should return original content unchanged
        assertEquals(content, result)
    }

    fun testInjectMirrorBlock_emptyContent() {
        val mirrorBlock = """${RepositoryProxyService.MARKER_BEGIN}
            |mirror
            |${RepositoryProxyService.MARKER_END}
        """.trimMargin()

        val result = RepositoryProxyService.injectMirrorBlock("", mirrorBlock)
        assertTrue(result.startsWith(RepositoryProxyService.MARKER_BEGIN))
    }

    // ---- removeMirrorBlock ----

    fun testRemoveMirrorBlock_fromInjectedContent() {
        val originalContent = """plugins {
            |    id("java")
            |}
        """.trimMargin()

        val mirrorBlock = """${RepositoryProxyService.MARKER_BEGIN}
            |allprojects {
            |    repositories {
            |        maven { url 'https://mirror/' }
            |    }
            |}
            |${RepositoryProxyService.MARKER_END}
        """.trimMargin()

        val injected = RepositoryProxyService.injectMirrorBlock(originalContent, mirrorBlock)
        val restored = RepositoryProxyService.removeMirrorBlock(injected)
        assertEquals(originalContent, restored)
    }

    fun testRemoveMirrorBlock_noMarkerPresent() {
        val content = """plugins {
            |    id("java")
            |}
            |
            |repositories {
            |    mavenCentral()
            |}
        """.trimMargin()

        val result = RepositoryProxyService.removeMirrorBlock(content)
        assertEquals(content, result)
    }

    fun testRemoveMirrorBlock_withGroovyMirrorBlock() {
        val mirrorBlock = RepositoryProxyService.generateGroovyMirrorBlock("https://mirror.example.com/")
        val original = "rootProject.name = 'my-project'\n"
        val injected = RepositoryProxyService.injectMirrorBlock(original, mirrorBlock)

        assertTrue(RepositoryProxyService.containsMirrorBlock(injected))

        val restored = RepositoryProxyService.removeMirrorBlock(injected)
        assertFalse(RepositoryProxyService.containsMirrorBlock(restored))
        assertTrue(restored.contains("rootProject.name = 'my-project'"))
    }

    fun testRemoveMirrorBlock_withKotlinMirrorBlock() {
        val mirrorBlock = RepositoryProxyService.generateKotlinMirrorBlock("https://mirror.example.com/")
        val original = "rootProject.name = \"my-project\"\n"
        val injected = RepositoryProxyService.injectMirrorBlock(original, mirrorBlock)

        val restored = RepositoryProxyService.removeMirrorBlock(injected)
        assertFalse(RepositoryProxyService.containsMirrorBlock(restored))
        assertTrue(restored.contains("rootProject.name = \"my-project\""))
    }

    // ---- containsMirrorBlock ----

    fun testContainsMirrorBlock_present() {
        val content = "some content\n${RepositoryProxyService.MARKER_BEGIN}\nmirror\n${RepositoryProxyService.MARKER_END}\nmore"
        assertTrue(RepositoryProxyService.containsMirrorBlock(content))
    }

    fun testContainsMirrorBlock_absent() {
        val content = "some content without markers"
        assertFalse(RepositoryProxyService.containsMirrorBlock(content))
    }

    // ---- Integration: full inject/remove round-trip ----

    fun testFullRoundTrip_groovySettingsFile() {
        val original = """pluginManagement {
            |    repositories {
            |        mavenCentral()
            |        gradlePluginPortal()
            |    }
            |}
            |
            |rootProject.name = 'demo'
        """.trimMargin()

        val mirrorBlock = RepositoryProxyService.generateMirrorBlock(
            "https://maven.aliyun.com/repository/public",
            "GROOVY"
        )

        // Inject
        val injected = RepositoryProxyService.injectMirrorBlock(original, mirrorBlock)
        assertTrue(RepositoryProxyService.containsMirrorBlock(injected))
        assertTrue(injected.contains("rootProject.name = 'demo'"))
        assertTrue(injected.contains("maven { url 'https://maven.aliyun.com/repository/public' }"))

        // Remove
        val restored = RepositoryProxyService.removeMirrorBlock(injected)
        assertFalse(RepositoryProxyService.containsMirrorBlock(restored))
        assertEquals(original, restored)
    }

    fun testFullRoundTrip_kotlinSettingsFile() {
        val original = """pluginManagement {
            |    repositories {
            |        mavenCentral()
            |        gradlePluginPortal()
            |    }
            |}
            |
            |rootProject.name = "demo"
        """.trimMargin()

        val mirrorBlock = RepositoryProxyService.generateMirrorBlock(
            "https://maven.aliyun.com/repository/public",
            "KOTLIN"
        )

        // Inject
        val injected = RepositoryProxyService.injectMirrorBlock(original, mirrorBlock)
        assertTrue(RepositoryProxyService.containsMirrorBlock(injected))
        assertTrue(injected.contains("""maven { url = uri("https://maven.aliyun.com/repository/public") }"""))

        // Remove
        val restored = RepositoryProxyService.removeMirrorBlock(injected)
        assertFalse(RepositoryProxyService.containsMirrorBlock(restored))
        assertEquals(original, restored)
    }

    fun testFullRoundTrip_complexBuildFile() {
        val original = """plugins {
            |    id 'java'
            |    id 'org.springframework.boot' version '3.2.0'
            |}
            |
            |group = 'com.example'
            |version = '1.0.0'
            |
            |repositories {
            |    mavenCentral()
            |}
            |
            |dependencies {
            |    implementation 'org.springframework.boot:spring-boot-starter'
            |}
        """.trimMargin()

        val mirrorBlock = RepositoryProxyService.generateGroovyMirrorBlock("https://repo.huaweicloud.com/repository/maven/")
        val injected = RepositoryProxyService.injectMirrorBlock(original, mirrorBlock)
        val restored = RepositoryProxyService.removeMirrorBlock(injected)
        assertEquals(original, restored)
    }
}
