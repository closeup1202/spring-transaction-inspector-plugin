package com.visualizetransaction.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator

class TransactionInspectorConfigurable(private val project: Project) : Configurable {

    private var mainPanel: JPanel? = null

    // Inspection Checkboxes
    private val enableSameClassCallDetection = JBCheckBox("Detect same-class @Transactional method calls")
    private val enablePrivateMethodDetection = JBCheckBox("Warn on private methods with @Transactional")
    private val enableFinalMethodDetection = JBCheckBox("Warn on final methods with @Transactional")
    private val enableStaticMethodDetection = JBCheckBox("Warn on static methods with @Transactional")
    private val enableCheckedExceptionRollbackDetection = JBCheckBox("Warn on checked exceptions without rollbackFor")
    private val enableAsyncTransactionalDetection = JBCheckBox("Detect @Async and @Transactional conflicts")
    private val enableReadOnlyWriteCallDetection = JBCheckBox("Detect write method calls from readOnly transactions")

    // N+1 Checkboxes
    private val enableN1Detection = JBCheckBox("Enable N+1 query detection")
    private val checkInStreamOperations = JBCheckBox("Check in stream operations (.map, .flatMap)")
    private val checkInLoops = JBCheckBox("Check in for-each loops")

    // Gutter Icon Checkboxes
    private val showGutterIcons = JBCheckBox("Show gutter icons for @Transactional methods")
    private val showReadOnlyWithDifferentIcon = JBCheckBox("Show different icon for readOnly transactions")

    override fun getDisplayName(): String = "Spring Transaction Inspector"

    override fun createComponent(): JComponent {
        val settings = TransactionInspectorSettings.getInstance(project).state

        enableSameClassCallDetection.isSelected = settings.enableSameClassCallDetection
        enablePrivateMethodDetection.isSelected = settings.enablePrivateMethodDetection
        enableFinalMethodDetection.isSelected = settings.enableFinalMethodDetection
        enableStaticMethodDetection.isSelected = settings.enableStaticMethodDetection
        enableCheckedExceptionRollbackDetection.isSelected = settings.enableCheckedExceptionRollbackDetection
        enableAsyncTransactionalDetection.isSelected = settings.enableAsyncTransactionalDetection
        enableReadOnlyWriteCallDetection.isSelected = settings.enableReadOnlyWriteCallDetection

        enableN1Detection.isSelected = settings.enableN1Detection
        checkInStreamOperations.isSelected = settings.checkInStreamOperations
        checkInLoops.isSelected = settings.checkInLoops

        showGutterIcons.isSelected = settings.showGutterIcons
        showReadOnlyWithDifferentIcon.isSelected = settings.showReadOnlyWithDifferentIcon

        enableN1Detection.addActionListener {
            val enabled = enableN1Detection.isSelected
            checkInStreamOperations.isEnabled = enabled
            checkInLoops.isEnabled = enabled
        }
        checkInStreamOperations.isEnabled = enableN1Detection.isSelected
        checkInLoops.isEnabled = enableN1Detection.isSelected

        showGutterIcons.addActionListener {
            showReadOnlyWithDifferentIcon.isEnabled = showGutterIcons.isSelected
        }
        showReadOnlyWithDifferentIcon.isEnabled = showGutterIcons.isSelected

        mainPanel = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("<html><b>Transaction Inspections</b></html>"), 0)
            .addComponent(enableSameClassCallDetection)
            .addComponent(enablePrivateMethodDetection)
            .addComponent(enableFinalMethodDetection)
            .addComponent(enableStaticMethodDetection)
            .addComponent(enableCheckedExceptionRollbackDetection)
            .addComponent(enableAsyncTransactionalDetection)
            .addComponent(enableReadOnlyWriteCallDetection)

            .addComponent(JSeparator(), 10)

            .addComponent(JBLabel("<html><b>N+1 Query Detection</b></html>"), 10)
            .addComponent(enableN1Detection)
            .addComponent(createIndentedPanel(checkInStreamOperations))
            .addComponent(createIndentedPanel(checkInLoops))

            .addComponent(JSeparator(), 10)

            .addComponent(JBLabel("<html><b>Visual Indicators</b></html>"), 10)
            .addComponent(showGutterIcons)
            .addComponent(createIndentedPanel(showReadOnlyWithDifferentIcon))

            .addComponentFillVertically(JPanel(), 0)
            .panel

        return mainPanel!!
    }

    private fun createIndentedPanel(component: JComponent): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.emptyLeft(20)
        panel.add(component, BorderLayout.WEST)
        return panel
    }

    override fun isModified(): Boolean {
        val settings = TransactionInspectorSettings.getInstance(project).state

        return enableSameClassCallDetection.isSelected != settings.enableSameClassCallDetection ||
                enablePrivateMethodDetection.isSelected != settings.enablePrivateMethodDetection ||
                enableFinalMethodDetection.isSelected != settings.enableFinalMethodDetection ||
                enableStaticMethodDetection.isSelected != settings.enableStaticMethodDetection ||
                enableCheckedExceptionRollbackDetection.isSelected != settings.enableCheckedExceptionRollbackDetection ||
                enableAsyncTransactionalDetection.isSelected != settings.enableAsyncTransactionalDetection ||
                enableReadOnlyWriteCallDetection.isSelected != settings.enableReadOnlyWriteCallDetection ||
                enableN1Detection.isSelected != settings.enableN1Detection ||
                checkInStreamOperations.isSelected != settings.checkInStreamOperations ||
                checkInLoops.isSelected != settings.checkInLoops ||
                showGutterIcons.isSelected != settings.showGutterIcons ||
                showReadOnlyWithDifferentIcon.isSelected != settings.showReadOnlyWithDifferentIcon
    }

    override fun apply() {
        val settings = TransactionInspectorSettings.getInstance(project)
        settings.state.enableSameClassCallDetection = enableSameClassCallDetection.isSelected
        settings.state.enablePrivateMethodDetection = enablePrivateMethodDetection.isSelected
        settings.state.enableFinalMethodDetection = enableFinalMethodDetection.isSelected
        settings.state.enableStaticMethodDetection = enableStaticMethodDetection.isSelected
        settings.state.enableCheckedExceptionRollbackDetection = enableCheckedExceptionRollbackDetection.isSelected
        settings.state.enableAsyncTransactionalDetection = enableAsyncTransactionalDetection.isSelected
        settings.state.enableReadOnlyWriteCallDetection = enableReadOnlyWriteCallDetection.isSelected

        settings.state.enableN1Detection = enableN1Detection.isSelected
        settings.state.checkInStreamOperations = checkInStreamOperations.isSelected
        settings.state.checkInLoops = checkInLoops.isSelected

        settings.state.showGutterIcons = showGutterIcons.isSelected
        settings.state.showReadOnlyWithDifferentIcon = showReadOnlyWithDifferentIcon.isSelected
    }

    override fun reset() {
        val settings = TransactionInspectorSettings.getInstance(project).state

        enableSameClassCallDetection.isSelected = settings.enableSameClassCallDetection
        enablePrivateMethodDetection.isSelected = settings.enablePrivateMethodDetection
        enableFinalMethodDetection.isSelected = settings.enableFinalMethodDetection
        enableStaticMethodDetection.isSelected = settings.enableStaticMethodDetection
        enableCheckedExceptionRollbackDetection.isSelected = settings.enableCheckedExceptionRollbackDetection
        enableAsyncTransactionalDetection.isSelected = settings.enableAsyncTransactionalDetection
        enableReadOnlyWriteCallDetection.isSelected = settings.enableReadOnlyWriteCallDetection

        enableN1Detection.isSelected = settings.enableN1Detection
        checkInStreamOperations.isSelected = settings.checkInStreamOperations
        checkInLoops.isSelected = settings.checkInLoops

        showGutterIcons.isSelected = settings.showGutterIcons
        showReadOnlyWithDifferentIcon.isSelected = settings.showReadOnlyWithDifferentIcon
    }
}