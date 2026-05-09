package com.github.jacknic.plugin.gradlehub.service

import com.github.jacknic.plugin.gradlehub.config.GradleHubSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Project-level service that orchestrates all proxy services.
 *
 * Provides a unified API for applying/restoring all proxy types:
 * - Wrapper proxy ([WrapperProxyService])
 * - Repository proxy ([RepositoryProxyService])
 * - Init.gradle proxy ([InitGradleService])
 *
 * This service coordinates the individual services based on the current settings.
 */
@Service(Service.Level.PROJECT)
class ProxyOrchestrator(val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(ProxyOrchestrator::class.java)
    }

    private val wrapperService = project.service<WrapperProxyService>()
    private val repositoryService = project.service<RepositoryProxyService>()
    private val initGradleService = project.service<InitGradleService>()
    private val settings = GradleHubSettings.getInstance()

    /**
     * Data class representing the aggregated proxy status for all services.
     */
    data class ProxyStatus(
        val wrapperApplied: Boolean,
        val repositoryApplied: Boolean,
        val initGradleApplied: Boolean,
        val mirrorEnabled: Boolean,
        val mirrorUrl: String,
        val repositoryMirrorUrl: String,
        val initGradleEnabled: Boolean,
        val repositoryProxyEnabled: Boolean,
    ) {
        /** True if any proxy type is currently applied */
        val anyApplied: Boolean get() = wrapperApplied || repositoryApplied || initGradleApplied
    }

    /**
     * Get the current status of all proxy services.
     */
    fun getStatus(): ProxyStatus {
        return ProxyStatus(
            wrapperApplied = wrapperService.isProxyApplied(),
            repositoryApplied = repositoryService.isProxyApplied(),
            initGradleApplied = initGradleService.isProxyApplied(),
            mirrorEnabled = settings.mirrorEnabled,
            mirrorUrl = settings.mirrorUrl,
            repositoryMirrorUrl = settings.repositoryMirrorUrl,
            initGradleEnabled = settings.initGradleEnabled,
            repositoryProxyEnabled = settings.repositoryProxyEnabled,
        )
    }

    /**
     * Apply all enabled proxy services.
     *
     * Each proxy type is applied independently based on its settings toggle.
     * Failures in one service do not prevent others from being applied.
     *
     * @return a map of service name to apply result (true = success)
     */
    fun applyAll(): Map<String, Boolean> {
        if (!settings.mirrorEnabled) {
            LOG.info("Mirror proxy is globally disabled")
            return emptyMap()
        }

        val results = mutableMapOf<String, Boolean>()

        // Wrapper proxy is always applied when enabled and URL is configured
        if (settings.mirrorUrl.isNotBlank()) {
            results["wrapper"] = wrapperService.applyProxy()
        }

        // Repository proxy (direct file modification) is optional
        if (settings.repositoryProxyEnabled && settings.repositoryMirrorUrl.isNotBlank()) {
            results["repository"] = repositoryService.applyProxy()
        }

        // Init.gradle proxy is optional
        if (settings.initGradleEnabled && settings.repositoryMirrorUrl.isNotBlank()) {
            results["initGradle"] = initGradleService.applyProxy()
        }

        LOG.info("Proxy apply results: $results")
        return results
    }

    /**
     * Restore all applied proxy services.
     *
     * Each service is restored independently. Failures in one service
     * do not prevent others from being restored.
     *
     * @return a map of service name to restore result (true = success)
     */
    fun restoreAll(): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()

        results["wrapper"] = wrapperService.restoreOriginal()
        results["repository"] = repositoryService.restoreOriginal()
        results["initGradle"] = initGradleService.restoreOriginal()

        LOG.info("Proxy restore results: $results")
        return results
    }

    /**
     * Toggle all proxy services: apply if none are applied, restore if any are applied.
     *
     * @return a map of service name to operation result
     */
    fun toggleAll(): Map<String, Boolean> {
        val status = getStatus()
        return if (status.anyApplied) restoreAll() else applyAll()
    }
}
