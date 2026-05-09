package com.github.jacknic.plugin.gradlehub.config

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Unit tests for [GradleHubSettings] state management.
 *
 * Tests default values, property setters/getters, and utility methods.
 */
class GradleHubSettingsTest : BasePlatformTestCase() {

    private lateinit var settings: GradleHubSettings

    override fun setUp() {
        super.setUp()
        settings = GradleHubSettings()
    }

    fun testDefaultState() {
        assertEquals("", settings.mirrorUrl)
        assertEquals("", settings.repositoryMirrorUrl)
        assertTrue(settings.mirrorEnabled)
        assertTrue(settings.initGradleEnabled)
        assertFalse(settings.repositoryProxyEnabled)
        assertEquals("", settings.gradleHome)
        assertEquals(2, settings.keepVersions)
    }

    fun testMirrorUrlSetterGetter() {
        settings.mirrorUrl = "https://mirrors.cloud.tencent.com/gradle/"
        assertEquals("https://mirrors.cloud.tencent.com/gradle/", settings.mirrorUrl)
    }

    fun testRepositoryMirrorUrlSetterGetter() {
        settings.repositoryMirrorUrl = "https://maven.aliyun.com/repository/public"
        assertEquals("https://maven.aliyun.com/repository/public", settings.repositoryMirrorUrl)
    }

    fun testMirrorEnabledSetterGetter() {
        settings.mirrorEnabled = false
        assertFalse(settings.mirrorEnabled)
        settings.mirrorEnabled = true
        assertTrue(settings.mirrorEnabled)
    }

    fun testInitGradleEnabledSetterGetter() {
        settings.initGradleEnabled = false
        assertFalse(settings.initGradleEnabled)
        settings.initGradleEnabled = true
        assertTrue(settings.initGradleEnabled)
    }

    fun testRepositoryProxyEnabledSetterGetter() {
        settings.repositoryProxyEnabled = true
        assertTrue(settings.repositoryProxyEnabled)
        settings.repositoryProxyEnabled = false
        assertFalse(settings.repositoryProxyEnabled)
    }

    fun testGradleHomeSetterGetter() {
        settings.gradleHome = "/custom/gradle/home"
        assertEquals("/custom/gradle/home", settings.gradleHome)
    }

    fun testKeepVersionsCoercedAtMinimum() {
        settings.keepVersions = 0
        assertEquals(1, settings.keepVersions)

        settings.keepVersions = -5
        assertEquals(1, settings.keepVersions)
    }

    fun testKeepVersionsSetterGetter() {
        settings.keepVersions = 5
        assertEquals(5, settings.keepVersions)
    }

    fun testGetEffectiveGradleHome_configured() {
        settings.gradleHome = "/opt/gradle"
        assertEquals("/opt/gradle", settings.getEffectiveGradleHome())
    }

    fun testGetEffectiveGradleHome_fallbackToUserHome() {
        // When gradleHome is empty and GRADLE_USER_HOME env is not set,
        // it falls back to ~/.gradle
        settings.gradleHome = ""
        val home = settings.getEffectiveGradleHome()
        assertTrue(home.endsWith("/.gradle") || home.contains("GRADLE_USER_HOME"))
    }

    fun testStatePersistence() {
        settings.mirrorUrl = "https://mirrors.aliyun.com/gradle/"
        settings.mirrorEnabled = false
        settings.initGradleEnabled = false
        settings.repositoryProxyEnabled = true
        settings.keepVersions = 3

        val state = settings.state
        assertEquals("https://mirrors.aliyun.com/gradle/", state.mirrorUrl)
        assertFalse(state.mirrorEnabled)
        assertFalse(state.initGradleEnabled)
        assertTrue(state.repositoryProxyEnabled)
        assertEquals(3, state.keepVersions)

        // Load state into a new instance
        val newSettings = GradleHubSettings()
        newSettings.loadState(state)
        assertEquals("https://mirrors.aliyun.com/gradle/", newSettings.mirrorUrl)
        assertFalse(newSettings.mirrorEnabled)
        assertFalse(newSettings.initGradleEnabled)
        assertTrue(newSettings.repositoryProxyEnabled)
        assertEquals(3, newSettings.keepVersions)
    }
}
