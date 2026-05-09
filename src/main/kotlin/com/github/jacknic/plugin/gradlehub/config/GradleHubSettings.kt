package com.github.jacknic.plugin.gradlehub.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level persistent settings for GradleHub plugin.
 *
 * Stores mirror proxy configuration, Gradle home path, and version management preferences.
 * Persisted to `gradle-hub.xml` in the IDE config directory.
 */
@State(
    name = "com.github.jacknic.plugin.gradlehub.config.GradleHubSettings",
    storages = [Storage("gradle-hub.xml")]
)
class GradleHubSettings : PersistentStateComponent<GradleHubSettings.State> {

    class State {
        var mirrorUrl: String = ""
        var repositoryMirrorUrl: String = ""
        var mirrorEnabled: Boolean = true
        var gradleHome: String = ""
        var keepVersions: Int = 2
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    /** Gradle Wrapper mirror proxy URL prefix, e.g. `https://mirrors.cloud.tencent.com/gradle/` */
    var mirrorUrl: String
        get() = myState.mirrorUrl
        set(value) {
            myState.mirrorUrl = value
        }

    /** Repository mirror proxy URL, e.g. `https://maven.aliyun.com/repository/public` */
    var repositoryMirrorUrl: String
        get() = myState.repositoryMirrorUrl
        set(value) {
            myState.repositoryMirrorUrl = value
        }

    /** Whether the mirror proxy acceleration feature is enabled */
    var mirrorEnabled: Boolean
        get() = myState.mirrorEnabled
        set(value) {
            myState.mirrorEnabled = value
        }

    /** Local Gradle installation directory. Defaults to `~/.gradle` if empty. */
    var gradleHome: String
        get() = myState.gradleHome
        set(value) {
            myState.gradleHome = value
        }

    /** Minimum number of Gradle versions to keep when performing cleanup */
    var keepVersions: Int
        get() = myState.keepVersions
        set(value) {
            myState.keepVersions = value.coerceAtLeast(1)
        }

    /**
     * Returns the effective Gradle home directory.
     * If not configured, falls back to `~/.gradle` or the `GRADLE_USER_HOME` environment variable.
     */
    fun getEffectiveGradleHome(): String {
        if (gradleHome.isNotBlank()) return gradleHome
        val envHome = System.getenv("GRADLE_USER_HOME")
        if (!envHome.isNullOrBlank()) return envHome
        return System.getProperty("user.home") + "/.gradle"
    }

    companion object {
        @JvmStatic
        fun getInstance(): GradleHubSettings =
            ApplicationManager.getApplication().getService(GradleHubSettings::class.java)
    }
}
