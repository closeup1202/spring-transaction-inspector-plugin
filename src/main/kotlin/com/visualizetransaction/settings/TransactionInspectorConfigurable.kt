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
import kotlin.reflect.KMutableProperty1

class TransactionInspectorConfigurable(private val project: Project) : Configurable {

    private data class Toggle(
        val label: String,
        val property: KMutableProperty1<TransactionInspectorSettings.State, Boolean>,
        val checkbox: JBCheckBox = JBCheckBox(label),
        val indented: Boolean = false,
        val parent: KMutableProperty1<TransactionInspectorSettings.State, Boolean>? = null
    )

    private val inspectionToggles = listOf(
        Toggle("Detect same-class @Transactional method calls", TransactionInspectorSettings.State::enableSameClassCallDetection),
        Toggle("Warn on private methods with @Transactional", TransactionInspectorSettings.State::enablePrivateMethodDetection),
        Toggle("Warn on final methods with @Transactional", TransactionInspectorSettings.State::enableFinalMethodDetection),
        Toggle("Warn on static methods with @Transactional", TransactionInspectorSettings.State::enableStaticMethodDetection),
        Toggle("Warn on checked exceptions without rollbackFor", TransactionInspectorSettings.State::enableCheckedExceptionRollbackDetection),
        Toggle("Detect @Async and @Transactional conflicts", TransactionInspectorSettings.State::enableAsyncTransactionalDetection),
        Toggle("Detect write method calls from readOnly transactions", TransactionInspectorSettings.State::enableReadOnlyWriteCallDetection),
        Toggle("Detect write operations in @Transactional(readOnly=true) methods", TransactionInspectorSettings.State::enableReadOnlyTransactionalDetection),
        Toggle("Detect transaction propagation conflicts (MANDATORY/NEVER/REQUIRES_NEW)", TransactionInspectorSettings.State::enablePropagationConflictDetection),
        Toggle("Detect @Retryable and @Transactional on the same bean", TransactionInspectorSettings.State::enableRetryableTransactionalDetection),
        Toggle("Detect exceptions swallowed inside @Transactional methods", TransactionInspectorSettings.State::enableSwallowedExceptionDetection),
        Toggle("Detect external calls (HTTP/email/file/sleep) inside @Transactional methods", TransactionInspectorSettings.State::enableExternalCallDetection)
    )

    private val n1Toggles = listOf(
        Toggle("Enable N+1 query detection", TransactionInspectorSettings.State::enableN1Detection),
        Toggle("Check in stream operations (.map, .flatMap)", TransactionInspectorSettings.State::checkInStreamOperations,
            indented = true, parent = TransactionInspectorSettings.State::enableN1Detection),
        Toggle("Check in for-each loops", TransactionInspectorSettings.State::checkInLoops,
            indented = true, parent = TransactionInspectorSettings.State::enableN1Detection),
        Toggle("Also detect outside @Transactional (OSIV)", TransactionInspectorSettings.State::checkN1OutsideTransactional,
            indented = true, parent = TransactionInspectorSettings.State::enableN1Detection)
    )

    private val gutterToggles = listOf(
        Toggle("Show gutter icons for @Transactional methods", TransactionInspectorSettings.State::showGutterIcons),
        Toggle("Show different icon for readOnly transactions", TransactionInspectorSettings.State::showReadOnlyWithDifferentIcon,
            indented = true, parent = TransactionInspectorSettings.State::showGutterIcons)
    )

    private val allToggles = inspectionToggles + n1Toggles + gutterToggles

    private var mainPanel: JPanel? = null

    override fun getDisplayName(): String = "Spring Transaction Inspector"

    override fun createComponent(): JComponent {
        loadFromState()
        wireParentChildEnablement()

        val builder = FormBuilder.createFormBuilder()
            .addComponent(JBLabel("<html><b>Transaction Inspections</b></html>"), 0)

        inspectionToggles.forEach { builder.addComponent(it.checkbox) }

        builder.addComponent(JSeparator(), 10)
            .addComponent(JBLabel("<html><b>N+1 Query Detection</b></html>"), 10)

        n1Toggles.forEach { builder.addComponent(if (it.indented) indented(it.checkbox) else it.checkbox) }

        builder.addComponent(JSeparator(), 10)
            .addComponent(JBLabel("<html><b>Visual Indicators</b></html>"), 10)

        gutterToggles.forEach { builder.addComponent(if (it.indented) indented(it.checkbox) else it.checkbox) }

        mainPanel = builder.addComponentFillVertically(JPanel(), 0).panel
        return mainPanel!!
    }

    private fun wireParentChildEnablement() {
        val byProperty = allToggles.associateBy { it.property }
        allToggles.forEach { toggle ->
            val parentProp = toggle.parent ?: return@forEach
            val parent = byProperty[parentProp] ?: return@forEach
            val sync = { toggle.checkbox.isEnabled = parent.checkbox.isSelected }
            parent.checkbox.addActionListener { sync() }
            sync()
        }
    }

    private fun indented(component: JComponent): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.emptyLeft(20)
        panel.add(component, BorderLayout.WEST)
        return panel
    }

    private fun loadFromState() {
        val state = TransactionInspectorSettings.getInstance(project).state
        allToggles.forEach { it.checkbox.isSelected = it.property.get(state) }
    }

    override fun isModified(): Boolean {
        val state = TransactionInspectorSettings.getInstance(project).state
        return allToggles.any { it.checkbox.isSelected != it.property.get(state) }
    }

    override fun apply() {
        val state = TransactionInspectorSettings.getInstance(project).state
        allToggles.forEach { it.property.set(state, it.checkbox.isSelected) }
    }

    override fun reset() {
        loadFromState()
    }
}
