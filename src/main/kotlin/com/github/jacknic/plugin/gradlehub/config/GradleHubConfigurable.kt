package com.github.jacknic.plugin.gradlehub.config

import com.github.jacknic.plugin.gradlehub.GradleHubBundle
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBPanel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import com.intellij.openapi.options.Configurable

/**
 * Settings page for the GradleHub plugin.
 *
 * Registered at `Settings → Tools → GradleHub`.
 * Provides configuration for mirror proxy URLs, Gradle home, and version management.
 */
class GradleHubConfigurable : Configurable {

    private val settings = GradleHubSettings.getInstance()

    // UI components
    private lateinit var enabledCheckBox: JBCheckBox
    private lateinit var mirrorUrlField: JBTextField
    private lateinit var mirrorUrlComboBox: JComboBox<String>
    private lateinit var repositoryMirrorUrlField: JBTextField
    private lateinit var gradleHomeField: JBTextField
    private lateinit var keepVersionsSpinner: JSpinner

    /** Predefined mirror URL templates for quick selection */
    private val mirrorTemplates = arrayOf(
        "",
        "https://mirrors.cloud.tencent.com/gradle/",
        "https://mirrors.aliyun.com/macports/distfiles/gradle/",
        "https://repo.huaweicloud.com/gradle/",
    )

    /** Predefined repository mirror URLs */
    private val repositoryMirrorTemplates = arrayOf(
        "",
        "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/",
        "https://maven.aliyun.com/repository/public",
        "https://repo.huaweicloud.com/repository/maven/",
    )

    override fun getDisplayName(): String = GradleHubBundle.message("settings.displayName")

    override fun createComponent(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 8, 4, 8)
            weightx = 1.0
        }

        var row = 0

        // --- Enabled checkbox ---
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2
        enabledCheckBox = JBCheckBox(GradleHubBundle.message("settings.enabled"))
        panel.add(enabledCheckBox, gbc)
        row++

        // --- Mirror URL ---
        gbc.gridwidth = 1; gbc.weightx = 0.0
        gbc.gridx = 0; gbc.gridy = row
        panel.add(JBLabel(GradleHubBundle.message("settings.mirrorUrl")), gbc)

        gbc.weightx = 1.0
        gbc.gridx = 1; gbc.gridy = row
        mirrorUrlComboBox = ComboBox(DefaultComboBoxModel(mirrorTemplates))
        mirrorUrlComboBox.setEditable(true)
        mirrorUrlField = JBTextField()
        // Use the combo box as the editor
        panel.add(mirrorUrlComboBox, gbc)
        row++

        // --- Repository Mirror URL ---
        gbc.weightx = 0.0
        gbc.gridx = 0; gbc.gridy = row
        panel.add(JBLabel(GradleHubBundle.message("settings.repositoryMirrorUrl")), gbc)

        gbc.weightx = 1.0
        gbc.gridx = 1; gbc.gridy = row
        repositoryMirrorUrlField = JBTextField()
        panel.add(repositoryMirrorUrlField, gbc)
        row++

        // --- Gradle Home ---
        gbc.weightx = 0.0
        gbc.gridx = 0; gbc.gridy = row
        panel.add(JBLabel(GradleHubBundle.message("settings.gradleHome")), gbc)

        gbc.weightx = 1.0
        gbc.gridx = 1; gbc.gridy = row
        gradleHomeField = JBTextField()
        panel.add(gradleHomeField, gbc)
        row++

        // --- Keep Versions ---
        gbc.weightx = 0.0
        gbc.gridx = 0; gbc.gridy = row
        panel.add(JBLabel(GradleHubBundle.message("settings.keepVersions")), gbc)

        gbc.weightx = 0.3
        gbc.gridx = 1; gbc.gridy = row
        keepVersionsSpinner = JSpinner(SpinnerNumberModel(2, 1, 50, 1))
        panel.add(keepVersionsSpinner, gbc)
        row++

        // --- Tip label ---
        gbc.gridwidth = 2; gbc.weightx = 1.0
        gbc.gridx = 0; gbc.gridy = row
        val tipLabel = JBLabel(GradleHubBundle.message("settings.tip"))
        tipLabel.font = tipLabel.font.deriveFont(tipLabel.font.size2D - 1f)
        panel.add(tipLabel, gbc)

        // Initialize from current settings
        reset()

        return panel
    }

    override fun isModified(): Boolean {
        val currentMirrorUrl = (mirrorUrlComboBox.editor.item as? String ?: "").trim()
        return enabledCheckBox.isSelected != settings.mirrorEnabled ||
                currentMirrorUrl != settings.mirrorUrl ||
                repositoryMirrorUrlField.text.trim() != settings.repositoryMirrorUrl ||
                gradleHomeField.text.trim() != settings.gradleHome ||
                (keepVersionsSpinner.value as? Int ?: 2) != settings.keepVersions
    }

    override fun apply() {
        val mirrorUrl = (mirrorUrlComboBox.editor.item as? String ?: "").trim()
        settings.mirrorEnabled = enabledCheckBox.isSelected
        settings.mirrorUrl = mirrorUrl
        settings.repositoryMirrorUrl = repositoryMirrorUrlField.text.trim()
        settings.gradleHome = gradleHomeField.text.trim()
        settings.keepVersions = keepVersionsSpinner.value as? Int ?: 2
    }

    override fun reset() {
        enabledCheckBox.isSelected = settings.mirrorEnabled
        mirrorUrlComboBox.selectedItem = settings.mirrorUrl
        // If the URL is not in the template list, set it as custom text
        val editor = mirrorUrlComboBox.editor
        if (!mirrorTemplates.contains(settings.mirrorUrl)) {
            editor.item = settings.mirrorUrl
        }
        repositoryMirrorUrlField.text = settings.repositoryMirrorUrl
        gradleHomeField.text = settings.gradleHome
        keepVersionsSpinner.value = settings.keepVersions
    }
}
