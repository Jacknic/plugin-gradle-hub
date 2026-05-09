package com.github.jacknic.plugin.gradlehub.config

import com.github.jacknic.plugin.gradlehub.GradleHubBundle
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import com.intellij.openapi.options.Configurable

/**
 * Settings page for the GradleHub plugin.
 *
 * Registered at `Settings → Tools → GradleHub`.
 * Provides configuration for mirror proxy URLs, proxy modes, Gradle home, and version management.
 */
class GradleHubConfigurable : Configurable {

    private val settings = GradleHubSettings.getInstance()

    // UI components
    private lateinit var enabledCheckBox: JBCheckBox
    private lateinit var presetComboBox: JComboBox<String>
    private lateinit var mirrorUrlComboBox: JComboBox<String>
    private lateinit var repositoryMirrorUrlComboBox: JComboBox<String>
    private lateinit var initGradleEnabledCheckBox: JBCheckBox
    private lateinit var repositoryProxyEnabledCheckBox: JBCheckBox
    private lateinit var gradleHomeField: JBTextField
    private lateinit var keepVersionsSpinner: JSpinner

    /** Predefined mirror template: display name → (wrapper URL, repository URL) */
    private data class MirrorTemplate(
        val displayName: String,
        val wrapperUrl: String,
        val repositoryUrl: String,
    )

    private val mirrorTemplates = listOf(
        MirrorTemplate("", "", ""),
        MirrorTemplate(
            GradleHubBundle.message("settings.preset.tencent"),
            "https://mirrors.cloud.tencent.com/gradle/",
            "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/"
        ),
        MirrorTemplate(
            GradleHubBundle.message("settings.preset.aliyun"),
            "https://mirrors.aliyun.com/macports/distfiles/gradle/",
            "https://maven.aliyun.com/repository/public"
        ),
        MirrorTemplate(
            GradleHubBundle.message("settings.preset.huawei"),
            "https://repo.huaweicloud.com/gradle/",
            "https://repo.huaweicloud.com/repository/maven/"
        ),
    )

    /** Predefined wrapper mirror URLs for the combo box */
    private val wrapperMirrorUrls = arrayOf(
        "",
        "https://mirrors.cloud.tencent.com/gradle/",
        "https://mirrors.aliyun.com/macports/distfiles/gradle/",
        "https://repo.huaweicloud.com/gradle/",
    )

    /** Predefined repository mirror URLs for the combo box */
    private val repositoryMirrorUrls = arrayOf(
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

        // --- Preset mirror template ---
        gbc.gridwidth = 1; gbc.weightx = 0.0
        gbc.gridx = 0; gbc.gridy = row
        panel.add(JBLabel(GradleHubBundle.message("settings.preset")), gbc)

        gbc.weightx = 1.0
        gbc.gridx = 1; gbc.gridy = row
        presetComboBox = ComboBox(DefaultComboBoxModel(mirrorTemplates.map { it.displayName }.toTypedArray()))
        presetComboBox.addActionListener {
            val idx = presetComboBox.selectedIndex
            if (idx > 0) {
                val template = mirrorTemplates[idx]
                mirrorUrlComboBox.selectedItem = template.wrapperUrl
                repositoryMirrorUrlComboBox.selectedItem = template.repositoryUrl
            }
        }
        panel.add(presetComboBox, gbc)
        row++

        // --- Mirror URL ---
        gbc.weightx = 0.0
        gbc.gridx = 0; gbc.gridy = row
        panel.add(JBLabel(GradleHubBundle.message("settings.mirrorUrl")), gbc)

        gbc.weightx = 1.0
        gbc.gridx = 1; gbc.gridy = row
        mirrorUrlComboBox = ComboBox(DefaultComboBoxModel(wrapperMirrorUrls))
        mirrorUrlComboBox.setEditable(true)
        panel.add(mirrorUrlComboBox, gbc)
        row++

        // --- Repository Mirror URL ---
        gbc.weightx = 0.0
        gbc.gridx = 0; gbc.gridy = row
        panel.add(JBLabel(GradleHubBundle.message("settings.repositoryMirrorUrl")), gbc)

        gbc.weightx = 1.0
        gbc.gridx = 1; gbc.gridy = row
        repositoryMirrorUrlComboBox = ComboBox(DefaultComboBoxModel(repositoryMirrorUrls))
        repositoryMirrorUrlComboBox.setEditable(true)
        panel.add(repositoryMirrorUrlComboBox, gbc)
        row++

        // --- Init.gradle enabled ---
        gbc.gridwidth = 2; gbc.weightx = 1.0
        gbc.gridx = 0; gbc.gridy = row
        initGradleEnabledCheckBox = JBCheckBox(GradleHubBundle.message("settings.initGradleEnabled"))
        panel.add(initGradleEnabledCheckBox, gbc)
        row++

        // --- Repository proxy enabled ---
        gbc.gridx = 0; gbc.gridy = row
        repositoryProxyEnabledCheckBox = JBCheckBox(GradleHubBundle.message("settings.repositoryProxyEnabled"))
        panel.add(repositoryProxyEnabledCheckBox, gbc)
        row++

        // --- Gradle Home ---
        gbc.gridwidth = 1; gbc.weightx = 0.0
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
        val currentRepoMirrorUrl = (repositoryMirrorUrlComboBox.editor.item as? String ?: "").trim()
        val currentPresetIdx = presetComboBox.selectedIndex
        // Reset preset combo box to "Custom" if URLs don't match any template
        val effectivePresetIdx = findMatchingPresetIndex(currentMirrorUrl, currentRepoMirrorUrl)

        return enabledCheckBox.isSelected != settings.mirrorEnabled ||
                currentMirrorUrl != settings.mirrorUrl ||
                currentRepoMirrorUrl != settings.repositoryMirrorUrl ||
                initGradleEnabledCheckBox.isSelected != settings.initGradleEnabled ||
                repositoryProxyEnabledCheckBox.isSelected != settings.repositoryProxyEnabled ||
                gradleHomeField.text.trim() != settings.gradleHome ||
                (keepVersionsSpinner.value as? Int ?: 2) != settings.keepVersions ||
                currentPresetIdx != effectivePresetIdx
    }

    override fun apply() {
        settings.mirrorEnabled = enabledCheckBox.isSelected
        settings.mirrorUrl = (mirrorUrlComboBox.editor.item as? String ?: "").trim()
        settings.repositoryMirrorUrl = (repositoryMirrorUrlComboBox.editor.item as? String ?: "").trim()
        settings.initGradleEnabled = initGradleEnabledCheckBox.isSelected
        settings.repositoryProxyEnabled = repositoryProxyEnabledCheckBox.isSelected
        settings.gradleHome = gradleHomeField.text.trim()
        settings.keepVersions = keepVersionsSpinner.value as? Int ?: 2
    }

    override fun reset() {
        enabledCheckBox.isSelected = settings.mirrorEnabled
        mirrorUrlComboBox.selectedItem = settings.mirrorUrl
        if (!wrapperMirrorUrls.contains(settings.mirrorUrl)) {
            mirrorUrlComboBox.editor.item = settings.mirrorUrl
        }
        repositoryMirrorUrlComboBox.selectedItem = settings.repositoryMirrorUrl
        if (!repositoryMirrorUrls.contains(settings.repositoryMirrorUrl)) {
            repositoryMirrorUrlComboBox.editor.item = settings.repositoryMirrorUrl
        }
        initGradleEnabledCheckBox.isSelected = settings.initGradleEnabled
        repositoryProxyEnabledCheckBox.isSelected = settings.repositoryProxyEnabled
        gradleHomeField.text = settings.gradleHome
        keepVersionsSpinner.value = settings.keepVersions
        presetComboBox.selectedIndex = findMatchingPresetIndex(settings.mirrorUrl, settings.repositoryMirrorUrl)
    }

    /**
     * Find the index of the preset template that matches the given URLs.
     *
     * @return the matching template index, or 0 (Custom) if no match
     */
    private fun findMatchingPresetIndex(wrapperUrl: String, repoUrl: String): Int {
        return mirrorTemplates.indexOfFirst {
            it.wrapperUrl == wrapperUrl && it.repositoryUrl == repoUrl
        }.coerceAtLeast(0)
    }
}
