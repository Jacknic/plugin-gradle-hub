package com.github.jacknic.plugin.gradlehub.toolWindow

import com.github.jacknic.plugin.gradlehub.GradleHubBundle
import com.github.jacknic.plugin.gradlehub.config.GradleHubSettings
import com.github.jacknic.plugin.gradlehub.config.VersionScanManager
import com.github.jacknic.plugin.gradlehub.model.GradleVersionInfo
import com.github.jacknic.plugin.gradlehub.service.GradleVersionService
import com.github.jacknic.plugin.gradlehub.service.ProxyOrchestrator
import com.github.jacknic.plugin.gradlehub.service.WrapperProxyService
import com.github.jacknic.plugin.gradlehub.service.InitGradleService
import com.intellij.openapi.application.ApplicationManager
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
import javax.swing.JProgressBar
import javax.swing.JTabbedPane
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
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
                setButtonsEnabled(false)
                ApplicationManager.getApplication().executeOnPooledThread {
                    orchestrator.applyAll()
                    ApplicationManager.getApplication().invokeLater { refresh() }
                }
            }
            restoreAllButton.addActionListener {
                setButtonsEnabled(false)
                ApplicationManager.getApplication().executeOnPooledThread {
                    orchestrator.restoreAll()
                    ApplicationManager.getApplication().invokeLater { refresh() }
                }
            }
            applyWrapperButton.addActionListener {
                setButtonsEnabled(false)
                ApplicationManager.getApplication().executeOnPooledThread {
                    wrapperService.applyProxy()
                    ApplicationManager.getApplication().invokeLater { refresh() }
                }
            }
            restoreWrapperButton.addActionListener {
                setButtonsEnabled(false)
                ApplicationManager.getApplication().executeOnPooledThread {
                    wrapperService.restoreOriginal()
                    ApplicationManager.getApplication().invokeLater { refresh() }
                }
            }
            applyInitGradleButton.addActionListener {
                setButtonsEnabled(false)
                ApplicationManager.getApplication().executeOnPooledThread {
                    project.service<InitGradleService>().applyProxy()
                    ApplicationManager.getApplication().invokeLater { refresh() }
                }
            }
            restoreInitGradleButton.addActionListener {
                setButtonsEnabled(false)
                ApplicationManager.getApplication().executeOnPooledThread {
                    project.service<InitGradleService>().restoreOriginal()
                    ApplicationManager.getApplication().invokeLater { refresh() }
                }
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
        setButtonsEnabled(false)

        ApplicationManager.getApplication().executeOnPooledThread {
            val status = orchestrator.getStatus()
            val version = wrapperService.getCurrentGradleVersion()
            val currentUrl = wrapperService.getCurrentDistributionUrl()

            ApplicationManager.getApplication().invokeLater {
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

                distributionUrlLabel.text = GradleHubBundle.message("toolWindow.distributionUrl", currentUrl ?: "N/A")

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
                refreshButton.isEnabled = true
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        applyAllButton.isEnabled = enabled
        restoreAllButton.isEnabled = enabled
        applyWrapperButton.isEnabled = enabled
        restoreWrapperButton.isEnabled = enabled
        applyInitGradleButton.isEnabled = enabled
        restoreInitGradleButton.isEnabled = enabled
        refreshButton.isEnabled = enabled
    }
}

/**
 * Table model for displaying Gradle versions.
 *
 * Supports incremental updates via [replaceVersions] to avoid full table refresh
 * when only rows are added during a scan.
 */
private class GradleVersionTableModel : AbstractTableModel() {

    @Volatile
    var versions: List<GradleVersionInfo> = emptyList()
        private set

    var currentVersion: String? = null

    private val columnNames = listOf(
        GradleHubBundle.message("toolWindow.versionColumn"),
        GradleHubBundle.message("toolWindow.pathColumn"),
        GradleHubBundle.message("toolWindow.sizeColumn"),
    )

    /**
     * Full replacement of the version list.
     * Fires a complete data-changed event.
     */
    fun setVersions(value: List<GradleVersionInfo>) {
        versions = value
        fireTableDataChanged()
    }

    /**
     * Incremental replacement that fires row-level events when possible.
     * Falls back to [fireTableDataChanged] for large changes.
     */
    fun replaceVersions(newVersions: List<GradleVersionInfo>) {
        val oldSize = versions.size
        versions = newVersions
        when {
            oldSize == 0 -> fireTableDataChanged()
            newVersions.size > oldSize -> {
                fireTableRowsInserted(oldSize, newVersions.size - 1)
                // Existing rows may have been re-sorted
                if (newVersions.size > 1) fireTableRowsUpdated(0, oldSize - 1)
            }
            else -> fireTableDataChanged()
        }
    }

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
 *
 * Uses [VersionScanManager] for fully asynchronous scanning with:
 * - Real-time progress bar and status label
 * - Cancel and pause/resume controls
 * - Throttled table updates to prevent excessive repaints
 * - Non-blocking EDT: all file I/O runs off the EDT
 */
private class VersionsPanel(private val project: Project) : SimpleToolWindowPanel(false, true) {

    private val scanManager = VersionScanManager.getInstance()
    private val versionService = GradleVersionService.getInstance()
    private val wrapperService = project.service<WrapperProxyService>()
    private val settings = GradleHubSettings.getInstance()

    /** Cached current Gradle version, updated off-EDT to avoid blocking I/O. */
    @Volatile
    private var cachedCurrentVersion: String? = null

    private val tableModel = GradleVersionTableModel()
    private val table = JBTable(tableModel)
    private val infoLabel = JBLabel().apply { horizontalAlignment = SwingConstants.LEFT }
    private val scanStatusLabel = JBLabel().apply { horizontalAlignment = SwingConstants.LEFT }
    private val progressBar = JProgressBar().apply {
        orientation = SwingConstants.HORIZONTAL
        minimum = 0
        maximum = 100
        isVisible = false
    }
    private val switchButton = JButton()
    private val deleteButton = JButton()
    private val cleanupButton = JButton()
    private val downloadButton = JButton()
    private val refreshButton = JButton()
    private val cancelButton = JButton()
    private val pauseButton = JButton()

    /** Throttle timestamp for table model updates during scanning. */
    @Volatile
    private var lastTableUpdateMs = 0L

    /** Minimum interval between table model updates (ms). */
    private companion object {
        const val TABLE_UPDATE_THROTTLE_MS = 150L
    }

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

        // Progress area
        val progressPanel = JBPanel<JBPanel<*>>(BorderLayout(4, 0)).apply {
            add(progressBar, BorderLayout.CENTER)
            add(scanStatusLabel, BorderLayout.EAST)
        }

        // Top info area
        val topPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(infoLabel, BorderLayout.NORTH)
            add(progressPanel, BorderLayout.SOUTH)
        }

        // Button panel
        val buttonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            switchButton.addActionListener { switchVersion() }
            deleteButton.addActionListener { deleteSelectedVersion() }
            cleanupButton.addActionListener { cleanupOldVersions() }
            downloadButton.addActionListener { openDownloadDialog() }
            refreshButton.addActionListener { startAsyncScan() }
            cancelButton.addActionListener { cancelScan() }
            pauseButton.addActionListener { togglePause() }

            add(switchButton)
            add(deleteButton)
            add(cleanupButton)
            add(downloadButton)
            add(refreshButton)
            add(cancelButton)
            add(pauseButton)
        }

        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
        mainPanel.add(topPanel, BorderLayout.NORTH)
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)

        setContent(mainPanel)
        updateButtonTexts()
        startAsyncScan()
    }

    // ---- Async scan lifecycle ----

    /**
     * Start an asynchronous version scan via [VersionScanManager].
     * All callbacks are received on the EDT.
     */
    private fun startAsyncScan() {
        setScanUiVisible(true)
        progressBar.value = 0
        scanStatusLabel.text = GradleHubBundle.message("toolWindow.scan.starting")

        val distsDir = versionService.getDistsDirectory()
        lastTableUpdateMs = 0L

        // Pre-fetch current version on a background thread to avoid EDT I/O
        ApplicationManager.getApplication().executeOnPooledThread {
            cachedCurrentVersion = wrapperService.getCurrentGradleVersion()

            ApplicationManager.getApplication().invokeLater {
                scanManager.startScan(distsDir) { state ->
                    // Callback is already on EDT (guaranteed by VersionScanManager)
                    handleScanState(state)
                }
            }
        }
    }

    private fun handleScanState(state: VersionScanManager.ScanState) {
        when (state) {
            is VersionScanManager.ScanState.Scanning -> {
                progressBar.value = (state.progress * 100).toInt()
                scanStatusLabel.text = GradleHubBundle.message(
                    "toolWindow.scan.progress",
                    state.scannedCount,
                    state.totalCount
                )

                // Throttled table update
                val now = System.currentTimeMillis()
                if (now - lastTableUpdateMs >= TABLE_UPDATE_THROTTLE_MS || state.scannedCount >= state.totalCount) {
                    lastTableUpdateMs = now
                    tableModel.currentVersion = cachedCurrentVersion
                    tableModel.replaceVersions(state.partialResults)
                }

                // Update pause button text
                pauseButton.text = if (scanManager.isScanPaused) {
                    GradleHubBundle.message("toolWindow.scan.resume")
                } else {
                    GradleHubBundle.message("toolWindow.scan.pause")
                }
            }

            is VersionScanManager.ScanState.Completed -> {
                setScanUiVisible(false)
                scanStatusLabel.text = ""

                tableModel.currentVersion = cachedCurrentVersion
                tableModel.setVersions(state.versions)
                updateInfoLabel(state.versions)
                updateActionButtons()
            }

            is VersionScanManager.ScanState.Cancelled -> {
                setScanUiVisible(false)
                scanStatusLabel.text = GradleHubBundle.message("toolWindow.scan.cancelled")

                if (state.partialResults.isNotEmpty()) {
                    tableModel.currentVersion = cachedCurrentVersion
                    tableModel.setVersions(state.partialResults)
                    updateInfoLabel(state.partialResults)
                }
                updateActionButtons()
            }

            is VersionScanManager.ScanState.Error -> {
                setScanUiVisible(false)
                scanStatusLabel.text = GradleHubBundle.message("toolWindow.scan.error", state.message)

                tableModel.currentVersion = cachedCurrentVersion
                updateActionButtons()
            }

            VersionScanManager.ScanState.Idle -> {
                // No action
            }
        }
    }

    private fun cancelScan() {
        scanManager.cancelScan()
    }

    private fun togglePause() {
        if (scanManager.isScanPaused) {
            scanManager.resumeScan()
            pauseButton.text = GradleHubBundle.message("toolWindow.scan.pause")
        } else {
            scanManager.pauseScan()
            pauseButton.text = GradleHubBundle.message("toolWindow.scan.resume")
        }
    }

    // ---- UI helpers ----

    private fun setScanUiVisible(scanning: Boolean) {
        progressBar.isVisible = scanning
        cancelButton.isVisible = scanning
        pauseButton.isVisible = scanning
        pauseButton.text = GradleHubBundle.message("toolWindow.scan.pause")
        refreshButton.isEnabled = !scanning
        switchButton.isEnabled = !scanning && table.selectedRow >= 0
        deleteButton.isEnabled = !scanning && table.selectedRow >= 0
        cleanupButton.isEnabled = !scanning
        downloadButton.isEnabled = !scanning
    }

    private fun updateInfoLabel(versions: List<GradleVersionInfo>) {
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
    }

    private fun updateButtonTexts() {
        switchButton.text = GradleHubBundle.message("toolWindow.switchVersion")
        deleteButton.text = GradleHubBundle.message("toolWindow.deleteVersion")
        cleanupButton.text = GradleHubBundle.message("toolWindow.cleanupVersions")
        downloadButton.text = GradleHubBundle.message("toolWindow.downloadVersion")
        refreshButton.text = GradleHubBundle.message("toolWindow.refreshVersions")
        cancelButton.text = GradleHubBundle.message("toolWindow.scan.cancel")
        pauseButton.text = GradleHubBundle.message("toolWindow.scan.pause")
    }

    private fun updateActionButtons() {
        updateButtonTexts()
        val selectedRow = table.selectedRow
        val hasSelection = selectedRow >= 0
        switchButton.isEnabled = hasSelection
        deleteButton.isEnabled = hasSelection

        val current = cachedCurrentVersion
        val deletable = GradleVersionService.findDeletableVersions(tableModel.versions, current, settings.keepVersions)
        cleanupButton.isEnabled = deletable.isNotEmpty()
        downloadButton.isEnabled = true
    }

    // ---- Version actions ----

    private fun switchVersion() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) return

        val modelRow = table.convertRowIndexToModel(selectedRow)
        val versionInfo = tableModel.getVersionAt(modelRow) ?: return

        setScanUiVisible(true)
        switchButton.isEnabled = false
        infoLabel.text = GradleHubBundle.message("toolWindow.switchVersion") + "..."

        ApplicationManager.getApplication().executeOnPooledThread {
            val success = wrapperService.switchToVersion(versionInfo.version)
            ApplicationManager.getApplication().invokeLater {
                if (success) {
                    infoLabel.text = GradleHubBundle.message("toolWindow.switchSuccess", versionInfo.version)
                } else {
                    infoLabel.text = GradleHubBundle.message("toolWindow.switchFailed")
                }
                startAsyncScan()
            }
        }
    }

    private fun deleteSelectedVersion() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) return

        val modelRow = table.convertRowIndexToModel(selectedRow)
        val versionInfo = tableModel.getVersionAt(modelRow) ?: return

        // Don't delete current project version
        if (versionInfo.version == cachedCurrentVersion) return

        val confirmed = JOptionPane.showConfirmDialog(
            this,
            GradleHubBundle.message("toolWindow.confirmDelete.message", versionInfo.version, versionInfo.sizeFormatted),
            GradleHubBundle.message("toolWindow.confirmDelete.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (confirmed == JOptionPane.YES_OPTION) {
            setScanUiVisible(true)
            deleteButton.isEnabled = false

            ApplicationManager.getApplication().executeOnPooledThread {
                val deleted = GradleVersionService.deleteVersion(versionInfo)
                ApplicationManager.getApplication().invokeLater {
                    if (deleted) {
                        infoLabel.text = GradleHubBundle.message("toolWindow.deleteSuccess", versionInfo.version)
                    } else {
                        infoLabel.text = GradleHubBundle.message("toolWindow.deleteFailed", versionInfo.version)
                    }
                    startAsyncScan()
                }
            }
        }
    }

    private fun cleanupOldVersions() {
        val currentVersion = cachedCurrentVersion
        val deletable = GradleVersionService.findDeletableVersions(tableModel.versions, currentVersion, settings.keepVersions)
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
            setScanUiVisible(true)
            cleanupButton.isEnabled = false

            ApplicationManager.getApplication().executeOnPooledThread {
                val deleted = versionService.cleanupOldVersions(currentVersion, tableModel.versions)
                ApplicationManager.getApplication().invokeLater {
                    infoLabel.text = GradleHubBundle.message(
                        "toolWindow.cleanupResult",
                        deleted.size,
                        GradleVersionInfo.formatFileSize(totalSize)
                    )
                    startAsyncScan()
                }
            }
        }
    }

    private fun openDownloadDialog() {
        val dialog = DownloadVersionDialog(project)
        dialog.show()

        // Refresh the version list after the dialog closes (download may have added a new version)
        startAsyncScan()
    }
}
