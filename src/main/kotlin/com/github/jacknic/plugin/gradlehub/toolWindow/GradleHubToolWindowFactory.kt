package com.github.jacknic.plugin.gradlehub.toolWindow

import com.github.jacknic.plugin.gradlehub.GradleHubBundle
import com.github.jacknic.plugin.gradlehub.config.GradleHubSettings
import com.github.jacknic.plugin.gradlehub.service.ProxyOrchestrator
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
 * - Wrapper proxy status (applied/not applied)
 * - Repository proxy status (applied/not applied)
 * - Init.gradle proxy status (applied/not applied)
 * - Configured mirror URLs
 * - Action buttons for applying/restoring proxies
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

    private val orchestrator = project.service<ProxyOrchestrator>()
    private val wrapperService = project.service<WrapperProxyService>()
    private val settings = GradleHubSettings.getInstance()

    private val versionLabel = JBLabel().apply { horizontalAlignment = SwingConstants.LEFT }
    private val wrapperStatusLabel = JBLabel().apply { horizontalAlignment = SwingConstants.LEFT }
    private val repositoryStatusLabel = JBLabel().apply { horizontalAlignment = SwingConstants.LEFT }
    private val initGradleStatusLabel = JBLabel().apply { horizontalAlignment = SwingConstants.LEFT }
    private val mirrorUrlLabel = JBLabel().apply { horizontalAlignment = SwingConstants.LEFT }
    private val repoMirrorUrlLabel = JBLabel().apply { horizontalAlignment = SwingConstants.LEFT }
    private val distributionUrlLabel = JBLabel().apply { horizontalAlignment = SwingConstants.LEFT }
    private val applyAllButton = JButton()
    private val restoreAllButton = JButton()
    private val applyWrapperButton = JButton()
    private val restoreWrapperButton = JButton()
    private val applyInitGradleButton = JButton()
    private val restoreInitGradleButton = JButton()
    private val refreshButton = JButton()

    init {
        val infoPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            val centerPanel = JBPanel<JBPanel<*>>(java.awt.GridLayout(0, 1, 2, 4))
            centerPanel.add(versionLabel)
            centerPanel.add(wrapperStatusLabel)
            centerPanel.add(repositoryStatusLabel)
            centerPanel.add(initGradleStatusLabel)
            centerPanel.add(mirrorUrlLabel)
            centerPanel.add(repoMirrorUrlLabel)
            centerPanel.add(distributionUrlLabel)
            add(centerPanel, BorderLayout.CENTER)
        }

        val buttonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            applyAllButton.addActionListener {
                orchestrator.applyAll()
                refresh()
            }
            restoreAllButton.addActionListener {
                orchestrator.restoreAll()
                refresh()
            }
            applyWrapperButton.addActionListener {
                wrapperService.applyProxy()
                refresh()
            }
            restoreWrapperButton.addActionListener {
                wrapperService.restoreOriginal()
                refresh()
            }
            applyInitGradleButton.addActionListener {
                project.service<com.github.jacknic.plugin.gradlehub.service.InitGradleService>().applyProxy()
                refresh()
            }
            restoreInitGradleButton.addActionListener {
                project.service<com.github.jacknic.plugin.gradlehub.service.InitGradleService>().restoreOriginal()
                refresh()
            }
            refreshButton.addActionListener { refresh() }

            add(applyAllButton)
            add(restoreAllButton)
            add(applyWrapperButton)
            add(restoreWrapperButton)
            add(applyInitGradleButton)
            add(restoreInitGradleButton)
            add(refreshButton)
        }

        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
        mainPanel.add(infoPanel, BorderLayout.CENTER)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)

        setContent(mainPanel)
        refresh()
    }

    /**
     * Refresh all displayed information from the current project state and settings.
     */
    fun refresh() {
        val status = orchestrator.getStatus()

        // Version info
        val version = wrapperService.getCurrentGradleVersion()
        versionLabel.text = GradleHubBundle.message("toolWindow.version", version ?: "N/A")

        // Wrapper proxy status
        val wrapperStatus = if (status.wrapperApplied) {
            GradleHubBundle.message("toolWindow.proxyApplied")
        } else {
            GradleHubBundle.message("toolWindow.proxyNotApplied")
        }
        wrapperStatusLabel.text = GradleHubBundle.message("toolWindow.wrapperStatus", wrapperStatus)

        // Repository proxy status
        val repoStatus = if (status.repositoryApplied) {
            GradleHubBundle.message("toolWindow.proxyApplied")
        } else {
            GradleHubBundle.message("toolWindow.proxyNotApplied")
        }
        repositoryStatusLabel.text = GradleHubBundle.message("toolWindow.repositoryStatus", repoStatus)

        // Init.gradle proxy status
        val initStatus = if (status.initGradleApplied) {
            GradleHubBundle.message("toolWindow.proxyApplied")
        } else {
            GradleHubBundle.message("toolWindow.proxyNotApplied")
        }
        initGradleStatusLabel.text = GradleHubBundle.message("toolWindow.initGradleStatus", initStatus)

        // Mirror URL
        val mirrorDisplay = settings.mirrorUrl.ifBlank {
            GradleHubBundle.message("toolWindow.notConfigured")
        }
        mirrorUrlLabel.text = GradleHubBundle.message("toolWindow.mirrorUrl", mirrorDisplay)

        // Repository Mirror URL
        val repoMirrorDisplay = settings.repositoryMirrorUrl.ifBlank {
            GradleHubBundle.message("toolWindow.notConfigured")
        }
        repoMirrorUrlLabel.text = GradleHubBundle.message("toolWindow.repoMirrorUrl", repoMirrorDisplay)

        // Current distribution URL
        val currentUrl = wrapperService.getCurrentDistributionUrl() ?: "N/A"
        distributionUrlLabel.text = GradleHubBundle.message("toolWindow.distributionUrl", currentUrl)

        // Button states
        val canApplyWrapper = !status.wrapperApplied && status.mirrorEnabled && settings.mirrorUrl.isNotBlank()
        val canApplyRepo = !status.repositoryApplied && status.mirrorEnabled && status.repositoryProxyEnabled && settings.repositoryMirrorUrl.isNotBlank()
        val canApplyInitGradle = !status.initGradleApplied && status.mirrorEnabled && status.initGradleEnabled && settings.repositoryMirrorUrl.isNotBlank()
        val canApplyAny = canApplyWrapper || canApplyRepo || canApplyInitGradle

        applyAllButton.text = GradleHubBundle.message("toolWindow.applyAll")
        applyAllButton.isEnabled = canApplyAny

        restoreAllButton.text = GradleHubBundle.message("toolWindow.restoreAll")
        restoreAllButton.isEnabled = status.anyApplied

        applyWrapperButton.text = GradleHubBundle.message("toolWindow.applyProxy")
        applyWrapperButton.isEnabled = canApplyWrapper

        restoreWrapperButton.text = GradleHubBundle.message("toolWindow.restoreOriginal")
        restoreWrapperButton.isEnabled = status.wrapperApplied

        applyInitGradleButton.text = GradleHubBundle.message("toolWindow.applyInitGradle")
        applyInitGradleButton.isEnabled = canApplyInitGradle

        restoreInitGradleButton.text = GradleHubBundle.message("toolWindow.restoreInitGradle")
        restoreInitGradleButton.isEnabled = status.initGradleApplied

        refreshButton.text = GradleHubBundle.message("toolWindow.refresh")
    }
}
