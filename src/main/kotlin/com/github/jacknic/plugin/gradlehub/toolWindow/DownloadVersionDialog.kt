package com.github.jacknic.plugin.gradlehub.toolWindow

import com.github.jacknic.plugin.gradlehub.GradleHubBundle
import com.github.jacknic.plugin.gradlehub.config.GradleHubSettings
import com.github.jacknic.plugin.gradlehub.model.RemoteGradleVersion
import com.github.jacknic.plugin.gradlehub.service.DownloadPhase
import com.github.jacknic.plugin.gradlehub.service.DownloadProgress
import com.github.jacknic.plugin.gradlehub.service.GradleDownloadService
import com.github.jacknic.plugin.gradlehub.service.WrapperProxyService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JOptionPane
import javax.swing.JProgressBar
import javax.swing.SwingConstants

/**
 * Dialog for downloading a specific Gradle version.
 *
 * Features:
 * - Fetch available versions from the Gradle releases API
 * - Manual version input
 * - Distribution type selection (bin/all)
 * - Download progress tracking
 * - One-click switch after download completes
 */
class DownloadVersionDialog(private val project: Project) : DialogWrapper(project) {

    private val downloadService = GradleDownloadService.getInstance()
    private val settings = GradleHubSettings.getInstance()
    private val wrapperService = project.service<WrapperProxyService>()

    // UI components
    private val versionComboBox = JComboBox<String>()
    private val versionField = JBTextField(20)
    private val distTypeComboBox = JComboBox(arrayOf("bin", "all"))
    private val fetchButton = JButton()
    private val downloadButton = JButton()
    private val switchButton = JButton()
    private val progressBar = JProgressBar()
    private val statusLabel = JBLabel().apply { horizontalAlignment = SwingConstants.LEFT }

    /** List of fetched remote versions. */
    private var remoteVersions: List<RemoteGradleVersion> = emptyList()

    /** The version that was successfully downloaded. */
    private var downloadedVersion: String? = null

    /** Flag to signal download cancellation. */
    @Volatile
    private var isCancelled = false

    /** Whether a download is currently in progress. */
    @Volatile
    private var isDownloading = false

    init {
        title = GradleHubBundle.message("download.title")
        setOKButtonText(GradleHubBundle.message("download.close"))
        setCancelButtonText(GradleHubBundle.message("download.cancel"))
        init()

        // Initial button states
        downloadButton.isEnabled = false
        switchButton.isEnabled = false
        progressBar.isVisible = false
        distTypeComboBox.selectedIndex = 0

        // Show mirror info
        updateMirrorInfo()
    }

    override fun createCenterPanel(): javax.swing.JComponent {
        val panel = JBPanel<JBPanel<*>>(BorderLayout(8, 8))

        // ---- Top: Version selection ----
        val selectionPanel = JBPanel<JBPanel<*>>(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(2, 4, 2, 4)
        }

        // Row 0: Version combo (fetched versions)
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        selectionPanel.add(JBLabel(GradleHubBundle.message("download.selectVersion")), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2
        versionComboBox.addActionListener { onVersionSelected() }
        selectionPanel.add(versionComboBox, gbc)

        // Row 1: Fetch button
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0; gbc.gridwidth = 1
        selectionPanel.add(JBLabel(""), gbc)
        gbc.gridx = 1; gbc.weightx = 0.0
        fetchButton.text = GradleHubBundle.message("download.fetchVersions")
        fetchButton.addActionListener { fetchRemoteVersions() }
        selectionPanel.add(fetchButton, gbc)

        // Row 2: Manual version input
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0
        selectionPanel.add(JBLabel(GradleHubBundle.message("download.orEnterVersion")), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.gridwidth = 2
        versionField.emptyText.text = GradleHubBundle.message("download.versionHint")
        versionField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updateDownloadButton()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updateDownloadButton()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updateDownloadButton()
        })
        selectionPanel.add(versionField, gbc)

        // Row 3: Distribution type
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0; gbc.gridwidth = 1
        selectionPanel.add(JBLabel(GradleHubBundle.message("download.distType")), gbc)
        gbc.gridx = 1; gbc.weightx = 0.3
        selectionPanel.add(distTypeComboBox, gbc)

        // Row 4: Mirror info
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.0; gbc.gridwidth = 3
        val mirrorLabel = JBLabel(GradleHubBundle.message(
            "download.mirrorInfo",
            if (settings.mirrorUrl.isNotBlank()) settings.mirrorUrl
            else GradleHubBundle.message("download.noMirror")
        )).apply { setCopyable(true) }
        selectionPanel.add(mirrorLabel, gbc)

        // ---- Middle: Progress ----
        val progressPanel = JBPanel<JBPanel<*>>(BorderLayout(4, 2))
        progressBar.orientation = SwingConstants.HORIZONTAL
        progressBar.minimum = 0
        progressBar.maximum = 100
        progressBar.isVisible = false
        progressPanel.add(progressBar, BorderLayout.CENTER)
        progressPanel.add(statusLabel, BorderLayout.SOUTH)

        // ---- Bottom: Action buttons ----
        val actionPanel = JBPanel<JBPanel<*>>(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 2))
        downloadButton.text = GradleHubBundle.message("download.startDownload")
        downloadButton.addActionListener { startDownload() }
        switchButton.text = GradleHubBundle.message("download.switchToVersion")
        switchButton.addActionListener { switchToDownloadedVersion() }
        actionPanel.add(downloadButton)
        actionPanel.add(switchButton)

        panel.add(selectionPanel, BorderLayout.NORTH)
        panel.add(progressPanel, BorderLayout.CENTER)
        panel.add(actionPanel, BorderLayout.SOUTH)

        return panel
    }

    // ---- Version selection ----

    private fun onVersionSelected() {
        val selected = versionComboBox.selectedItem as? String ?: return
        // If the user selects a version from the combo, fill the text field
        if (selected.isNotBlank() && selected != GradleHubBundle.message("download.noVersionsFound")) {
            versionField.text = selected
        }
    }

    private fun updateDownloadButton() {
        val version = getSelectedVersion()
        downloadButton.isEnabled = version.isNotBlank() && !isDownloading
    }

    private fun getSelectedVersion(): String {
        return versionField.text.trim()
    }

    private fun updateMirrorInfo() {
        // Mirror info is displayed statically; no dynamic update needed
    }

    // ---- Fetch remote versions ----

    private fun fetchRemoteVersions() {
        fetchButton.isEnabled = false
        statusLabel.text = GradleHubBundle.message("download.fetchingVersions")

        ApplicationManager.getApplication().executeOnPooledThread {
            val versions = downloadService.fetchRemoteVersions()

            ApplicationManager.getApplication().invokeLater {
                fetchButton.isEnabled = true
                remoteVersions = versions

                if (versions.isEmpty()) {
                    versionComboBox.model = DefaultComboBoxModel(arrayOf(
                        GradleHubBundle.message("download.noVersionsFound")
                    ))
                    statusLabel.text = GradleHubBundle.message("download.fetchFailed")
                } else {
                    val items = versions.map { v ->
                        v.version + if (v.isCurrent) " (${GradleHubBundle.message("download.latest")})" else ""
                    }.toTypedArray()
                    versionComboBox.model = DefaultComboBoxModel(items)
                    if (items.isNotEmpty()) {
                        versionComboBox.selectedIndex = 0
                        onVersionSelected()
                    }
                    statusLabel.text = GradleHubBundle.message("download.fetchSuccess", versions.size)
                }
                updateDownloadButton()
            }
        }
    }

    // ---- Download ----

    private fun startDownload() {
        val version = getSelectedVersion()
        if (version.isBlank()) return

        // Validate version format
        if (!RemoteGradleVersion.STABLE_VERSION_PATTERN.matches(version)) {
            statusLabel.text = GradleHubBundle.message("download.invalidVersion")
            return
        }

        isCancelled = false
        isDownloading = true
        downloadedVersion = null
        downloadButton.isEnabled = false
        fetchButton.isEnabled = false
        switchButton.isEnabled = false
        progressBar.isVisible = true
        progressBar.isIndeterminate = false
        progressBar.value = 0
        statusLabel.text = GradleHubBundle.message("download.downloading", version)

        val distType = distTypeComboBox.selectedItem as? String ?: "bin"

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = downloadService.downloadVersion(
                version = version,
                distType = distType,
                onProgress = { progress ->
                    ApplicationManager.getApplication().invokeLater {
                        updateDownloadProgress(progress)
                    }
                },
                onPhaseChange = { phase ->
                    ApplicationManager.getApplication().invokeLater {
                        updateDownloadPhase(phase, version)
                    }
                },
                isCancelled = { isCancelled }
            )

            ApplicationManager.getApplication().invokeLater {
                isDownloading = false
                progressBar.isVisible = false
                progressBar.isIndeterminate = false
                fetchButton.isEnabled = true

                if (isCancelled) {
                    statusLabel.text = GradleHubBundle.message("download.cancelled")
                } else if (result != null) {
                    downloadedVersion = version
                    statusLabel.text = GradleHubBundle.message("download.success", version)
                    switchButton.isEnabled = true
                } else {
                    statusLabel.text = GradleHubBundle.message("download.failed", version)
                }

                updateDownloadButton()
            }
        }
    }

    private fun updateDownloadProgress(progress: DownloadProgress) {
        val sizeStr = com.github.jacknic.plugin.gradlehub.model.GradleVersionInfo.formatFileSize(progress.downloaded)

        if (progress.isTotalKnown) {
            val totalStr = com.github.jacknic.plugin.gradlehub.model.GradleVersionInfo.formatFileSize(progress.total)
            val pct = progress.percentage
            progressBar.value = pct
            progressBar.isIndeterminate = false

            val speedStr = DownloadProgress.formatSpeed(progress.speed)
            val etaStr = DownloadProgress.formatEta(progress.etaSeconds)

            statusLabel.text = if (etaStr.isNotEmpty() && speedStr.isNotEmpty()) {
                GradleHubBundle.message("download.progressWithSpeed", sizeStr, totalStr, pct, speedStr, etaStr)
            } else if (speedStr.isNotEmpty()) {
                GradleHubBundle.message("download.progressWithSpeedNoEta", sizeStr, totalStr, pct, speedStr)
            } else {
                GradleHubBundle.message("download.progress", sizeStr, totalStr)
            }
        } else {
            progressBar.isIndeterminate = true
            val speedStr = DownloadProgress.formatSpeed(progress.speed)
            statusLabel.text = if (speedStr.isNotEmpty()) {
                GradleHubBundle.message("download.progressUnknownWithSpeed", sizeStr, speedStr)
            } else {
                GradleHubBundle.message("download.progressUnknown", sizeStr)
            }
        }
    }

    private fun updateDownloadPhase(phase: DownloadPhase, version: String) {
        when (phase) {
            DownloadPhase.DOWNLOADING -> {
                progressBar.isIndeterminate = false
            }
            DownloadPhase.EXTRACTING -> {
                progressBar.isIndeterminate = true
                statusLabel.text = GradleHubBundle.message("download.extracting", version)
            }
            DownloadPhase.COMPLETED -> {
                progressBar.isIndeterminate = false
                progressBar.value = 100
            }
        }
    }

    // ---- Switch to downloaded version ----

    private fun switchToDownloadedVersion() {
        val version = downloadedVersion ?: return

        switchButton.isEnabled = false
        statusLabel.text = GradleHubBundle.message("download.switchToVersion") + "..."

        ApplicationManager.getApplication().executeOnPooledThread {
            val success = wrapperService.switchToVersion(version)
            ApplicationManager.getApplication().invokeLater {
                if (success) {
                    statusLabel.text = GradleHubBundle.message("download.switchSuccess", version)
                } else {
                    statusLabel.text = GradleHubBundle.message("download.switchFailed")
                }

                // Close the dialog after switching
                close(OK_EXIT_CODE)
            }
        }
    }

    // ---- Dialog lifecycle ----

    override fun doOKAction() {
        isCancelled = true
        super.doOKAction()
    }

    override fun doCancelAction() {
        isCancelled = true
        super.doCancelAction()
    }

    override fun getPreferredFocusedComponent(): javax.swing.JComponent? = versionField
}
