package com.github.jacknic.plugin.gradlehub.toolWindow

import com.github.jacknic.plugin.gradlehub.GradleHubBundle
import com.github.jacknic.plugin.gradlehub.config.GradleHubSettings
import com.github.jacknic.plugin.gradlehub.service.WrapperProxyService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.SwingConstants

/**
 * Factory for the GradleHub tool window.
 *
 * Provides a status panel showing:
 * - Current project's Gradle version
 * - Mirror proxy status (enabled/disabled, applied/not applied)
 * - Configured mirror URL
 * - Action buttons for applying/restoring the proxy
 */
class GradleHubToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = GradleHubPanel(project)
        val content = com.intellij.ui.content.ContentFactory.getInstance()
            .createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true
}

/**
 * Main panel for the GradleHub tool window.
 */
private class GradleHubPanel(project: Project) : SimpleToolWindowPanel(false, true) {

    private val proxyService = project.service<WrapperProxyService>()
    private val settings = GradleHubSettings.getInstance()

    private val versionLabel = JBLabel().apply { horizontalAlignment = SwingConstants.LEFT }
    private val proxyStatusLabel = JBLabel().apply { horizontalAlignment = SwingConstants.LEFT }
    private val mirrorUrlLabel = JBLabel().apply { horizontalAlignment = SwingConstants.LEFT }
    private val distributionUrlLabel = JBLabel().apply { horizontalAlignment = SwingConstants.LEFT }
    private val applyButton = JButton()
    private val restoreButton = JButton()
    private val refreshButton = JButton()

    init {
        val infoPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            val labelsPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                val centerPanel = JBPanel<JBPanel<*>>(java.awt.GridLayout(0, 1, 2, 4))
                centerPanel.add(versionLabel)
                centerPanel.add(proxyStatusLabel)
                centerPanel.add(mirrorUrlLabel)
                centerPanel.add(distributionUrlLabel)
                add(centerPanel, BorderLayout.CENTER)
            }
            add(labelsPanel, BorderLayout.CENTER)
        }

        val buttonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT)).apply {
            applyButton.addActionListener {
                proxyService.applyProxy()
                refresh()
            }
            restoreButton.addActionListener {
                proxyService.restoreOriginal()
                refresh()
            }
            refreshButton.addActionListener { refresh() }
            add(applyButton)
            add(restoreButton)
            add(refreshButton)
        }

        setContent(infoPanel)
        setToolbar(buttonPanel)  // Actually add at bottom via BorderLayout
        // Use a different layout approach
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
        mainPanel.add(infoPanel, BorderLayout.CENTER)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)

        // Rebuild the content
        setContent(mainPanel)

        refresh()
    }

    /**
     * Refresh all displayed information from the current project state and settings.
     */
    fun refresh() {
        // Version info
        val version = proxyService.getCurrentGradleVersion()
        versionLabel.text = GradleHubBundle.message("toolWindow.version", version ?: "N/A")

        // Proxy status
        val isApplied = proxyService.isProxyApplied()
        val statusText = if (isApplied) {
            GradleHubBundle.message("toolWindow.proxyApplied")
        } else {
            GradleHubBundle.message("toolWindow.proxyNotApplied")
        }
        val enabledText = if (settings.mirrorEnabled) "ON" else "OFF"
        proxyStatusLabel.text = GradleHubBundle.message("toolWindow.proxyStatus", "$enabledText / $statusText")

        // Mirror URL
        val mirrorDisplay = settings.mirrorUrl.ifBlank {
            GradleHubBundle.message("toolWindow.notConfigured")
        }
        mirrorUrlLabel.text = GradleHubBundle.message("toolWindow.mirrorUrl", mirrorDisplay)

        // Current distribution URL
        val currentUrl = proxyService.getCurrentDistributionUrl() ?: "N/A"
        distributionUrlLabel.text = GradleHubBundle.message("toolWindow.distributionUrl", currentUrl)

        // Button states
        applyButton.text = GradleHubBundle.message("toolWindow.applyProxy")
        applyButton.isEnabled = !isApplied && settings.mirrorEnabled && settings.mirrorUrl.isNotBlank()

        restoreButton.text = GradleHubBundle.message("toolWindow.restoreOriginal")
        restoreButton.isEnabled = isApplied

        refreshButton.text = GradleHubBundle.message("toolWindow.refresh")
    }
}
