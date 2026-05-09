package com.github.jacknic.plugin.gradlehub.toolWindow

import com.github.jacknic.plugin.gradlehub.GradleHubBundle
import com.github.jacknic.plugin.gradlehub.config.GradleHubSettings
import com.github.jacknic.plugin.gradlehub.model.GradleVersionInfo
import com.github.jacknic.plugin.gradlehub.service.GradleVersionService
import com.github.jacknic.plugin.gradlehub.service.ProxyOrchestrator
import com.github.jacknic.plugin.gradlehub.service.WrapperProxyService
import com.github.jacknic.plugin.gradlehub.service.InitGradleService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.JTabbedPane
import javax.swing.SwingConstants
import javax.swing.table.AbstractTableModel

/**
 * Factory for the GradleHub tool window.
 *
 * Provides two tabs:
 * - Proxy: mirror proxy status and controls
 * - Versions: local Gradle version management (list, switch, cleanup)
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
 * Main panel for the GradleHub tool window with tabs for Proxy and Versions.
 */
private class GradleHubPanel(project: Project) : JBPanel<GradleHubPanel>(BorderLayout()) {

    private val tabbedPane = JTabbedPane()

    init {
        tabbedPane.addTab(
            GradleHubBundle.message("toolWindow.proxyTab"),
            ProxyPanel(project)
        )
        tabbedPane.addTab(
            GradleHubBundle.message("toolWindow.versionsTab"),
            VersionsPanel(project)
        )
        add(tabbedPane, BorderLayout.CENTER)
    }
}

/**
 * Panel showing mirror proxy status and action buttons.
 */
private class ProxyPanel(project: Project) : SimpleToolWindowPanel(false, true) {

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
                project.service<InitGradleService>().applyProxy()
                refresh()
            }
            restoreInitGradleButton.addActionListener {
                project.service<InitGradleService>().restoreOriginal()
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

    fun refresh() {
        val status = orchestrator.getStatus()

        val version = wrapperService.getCurrentGradleVersion()
        versionLabel.text = GradleHubBundle.message("toolWindow.version", version ?: "N/A")

        val wrapperStatus = if (status.wrapperApplied) {
            GradleHubBundle.message("toolWindow.proxyApplied")
        } else {
            GradleHubBundle.message("toolWindow.proxyNotApplied")
        }
        wrapperStatusLabel.text = GradleHubBundle.message("toolWindow.wrapperStatus", wrapperStatus)

        val repoStatus = if (status.repositoryApplied) {
            GradleHubBundle.message("toolWindow.proxyApplied")
        } else {
            GradleHubBundle.message("toolWindow.proxyNotApplied")
        }
        repositoryStatusLabel.text = GradleHubBundle.message("toolWindow.repositoryStatus", repoStatus)

        val initStatus = if (status.initGradleApplied) {
            GradleHubBundle.message("toolWindow.proxyApplied")
        } else {
            GradleHubBundle.message("toolWindow.proxyNotApplied")
        }
        initGradleStatusLabel.text = GradleHubBundle.message("toolWindow.initGradleStatus", initStatus)

        val mirrorDisplay = settings.mirrorUrl.ifBlank {
            GradleHubBundle.message("toolWindow.notConfigured")
        }
        mirrorUrlLabel.text = GradleHubBundle.message("toolWindow.mirrorUrl", mirrorDisplay)

        val repoMirrorDisplay = settings.repositoryMirrorUrl.ifBlank {
            GradleHubBundle.message("toolWindow.notConfigured")
        }
        repoMirrorUrlLabel.text = GradleHubBundle.message("toolWindow.repoMirrorUrl", repoMirrorDisplay)

        val currentUrl = wrapperService.getCurrentDistributionUrl() ?: "N/A"
        distributionUrlLabel.text = GradleHubBundle.message("toolWindow.distributionUrl", currentUrl)

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

/**
 * Table model for displaying Gradle versions.
 */
private class GradleVersionTableModel : AbstractTableModel() {

    var versions: List<GradleVersionInfo> = emptyList()
        set(value) {
            field = value
            fireTableDataChanged()
        }

    var currentVersion: String? = null

    private val columnNames = listOf(
        GradleHubBundle.message("toolWindow.versionColumn"),
        GradleHubBundle.message("toolWindow.pathColumn"),
        GradleHubBundle.message("toolWindow.sizeColumn"),
    )

    override fun getRowCount(): Int = versions.size

    override fun getColumnCount(): Int = columnNames.size

    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val info = versions[rowIndex]
        val isCurrent = info.version == currentVersion
        return when (columnIndex) {
            0 -> info.version + if (isCurrent) " ${GradleHubBundle.message("toolWindow.currentVersion")}" else ""
            1 -> info.path
            2 -> info.sizeFormatted
            else -> ""
        }
    }

    fun getVersionAt(row: Int): GradleVersionInfo? = versions.getOrNull(row)
}

/**
 * Panel for local Gradle version management.
 */
private class VersionsPanel(private val project: Project) : SimpleToolWindowPanel(false, true) {

    private val versionService = GradleVersionService.getInstance()
    private val wrapperService = project.service<WrapperProxyService>()
    private val settings = GradleHubSettings.getInstance()

    private val tableModel = GradleVersionTableModel()
    private val table = JBTable(tableModel)
    private val infoLabel = JBLabel().apply { horizontalAlignment = SwingConstants.LEFT }
    private val switchButton = JButton()
    private val deleteButton = JButton()
    private val cleanupButton = JButton()
    private val refreshButton = JButton()

    init {
        // Configure table
        table.autoCreateRowSorter = true
        table.rowSelectionAllowed = true
        table.selectionModel.selectionMode = javax.swing.ListSelectionModel.SINGLE_SELECTION
        table.setRowHeight(24)

        // Column widths
        table.columnModel.getColumn(0).preferredWidth = 120
        table.columnModel.getColumn(1).preferredWidth = 400
        table.columnModel.getColumn(2).preferredWidth = 80

        val scrollPane = com.intellij.ui.components.JBScrollPane(table)

        // Button panel
        val buttonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            switchButton.addActionListener { switchVersion() }
            deleteButton.addActionListener { deleteSelectedVersion() }
            cleanupButton.addActionListener { cleanupOldVersions() }
            refreshButton.addActionListener { refreshVersions() }

            add(switchButton)
            add(deleteButton)
            add(cleanupButton)
            add(refreshButton)
        }

        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
        mainPanel.add(infoLabel, BorderLayout.NORTH)
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)

        setContent(mainPanel)
        refreshVersions()
    }

    private fun refreshVersions() {
        val versions = versionService.getInstalledVersions()
        val currentVersion = wrapperService.getCurrentGradleVersion()
        tableModel.currentVersion = currentVersion
        tableModel.versions = versions

        // Update info label
        if (versions.isEmpty()) {
            infoLabel.text = GradleHubBundle.message("toolWindow.noVersions")
        } else {
            val totalSize = GradleVersionService.calculateTotalSize(versions)
            infoLabel.text = GradleHubBundle.message(
                "toolWindow.versionCount",
                versions.size
            ) + " | " + GradleHubBundle.message(
                "toolWindow.totalSize",
                GradleVersionInfo.formatFileSize(totalSize)
            )
        }

        // Update button text and states
        switchButton.text = GradleHubBundle.message("toolWindow.switchVersion")
        deleteButton.text = GradleHubBundle.message("toolWindow.deleteVersion")
        cleanupButton.text = GradleHubBundle.message("toolWindow.cleanupVersions")
        refreshButton.text = GradleHubBundle.message("toolWindow.refreshVersions")

        val selectedRow = table.selectedRow
        val hasSelection = selectedRow >= 0
        switchButton.isEnabled = hasSelection
        deleteButton.isEnabled = hasSelection

        val current = wrapperService.getCurrentGradleVersion()
        val deletable = versionService.getDeletableVersions(current)
        cleanupButton.isEnabled = deletable.isNotEmpty()
    }

    private fun switchVersion() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) return

        val modelRow = table.convertRowIndexToModel(selectedRow)
        val versionInfo = tableModel.getVersionAt(modelRow) ?: return

        val success = wrapperService.switchToVersion(versionInfo.version)
        if (success) {
            infoLabel.text = GradleHubBundle.message("toolWindow.switchSuccess", versionInfo.version)
        } else {
            infoLabel.text = GradleHubBundle.message("toolWindow.switchFailed")
        }
        refreshVersions()
    }

    private fun deleteSelectedVersion() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) return

        val modelRow = table.convertRowIndexToModel(selectedRow)
        val versionInfo = tableModel.getVersionAt(modelRow) ?: return

        // Don't delete current project version
        val currentVersion = wrapperService.getCurrentGradleVersion()
        if (versionInfo.version == currentVersion) return

        val confirmed = JOptionPane.showConfirmDialog(
            this,
            GradleHubBundle.message("toolWindow.confirmDelete.message", versionInfo.version, versionInfo.sizeFormatted),
            GradleHubBundle.message("toolWindow.confirmDelete.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (confirmed == JOptionPane.YES_OPTION) {
            val deleted = GradleVersionService.deleteVersion(versionInfo)
            if (deleted) {
                infoLabel.text = GradleHubBundle.message("toolWindow.deleteSuccess", versionInfo.version)
            } else {
                infoLabel.text = GradleHubBundle.message("toolWindow.deleteFailed", versionInfo.version)
            }
            refreshVersions()
        }
    }

    private fun cleanupOldVersions() {
        val currentVersion = wrapperService.getCurrentGradleVersion()
        val deletable = versionService.getDeletableVersions(currentVersion)
        if (deletable.isEmpty()) return

        val totalSize = GradleVersionService.calculateTotalSize(deletable)
        val confirmed = JOptionPane.showConfirmDialog(
            this,
            GradleHubBundle.message(
                "toolWindow.confirmCleanup.message",
                deletable.size,
                GradleVersionInfo.formatFileSize(totalSize)
            ),
            GradleHubBundle.message("toolWindow.confirmCleanup.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (confirmed == JOptionPane.YES_OPTION) {
            val deleted = versionService.cleanupOldVersions(currentVersion)
            val reclaimedSize = deleted.size
            infoLabel.text = GradleHubBundle.message(
                "toolWindow.cleanupResult",
                deleted.size,
                GradleVersionInfo.formatFileSize(totalSize)
            )
            refreshVersions()
        }
    }
}
