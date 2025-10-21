package com.visualizetransaction.markers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.*
import com.visualizetransaction.TransactionIcons
import com.visualizetransaction.settings.TransactionInspectorSettings
import javax.swing.Icon

class TransactionLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is PsiIdentifier) return null

        val parent = element.parent
        if (parent !is PsiMethod) return null

        val project = element.project
        val settings = TransactionInspectorSettings.getInstance(project).state
        if (!settings.showGutterIcons) {
            return null
        }

        val transactional = findTransactionalAnnotation(parent)
            ?: parent.containingClass?.let { findTransactionalAnnotation(it) }
            ?: return null

        val isReadOnly = isReadOnly(transactional)
        val icon = getIcon(isReadOnly, settings)
        val tooltip = buildTooltip(parent, transactional)

        return LineMarkerInfo(
            element,
            element.textRange,
            icon,
            { tooltip },
            null,
            GutterIconRenderer.Alignment.LEFT
        ) { tooltip }
    }

    private fun getIcon(
        isReadOnly: Boolean,
        settings: TransactionInspectorSettings.State
    ): Icon = if (isReadOnly && settings.showReadOnlyWithDifferentIcon) {
        TransactionIcons.TRANSACTION_READONLY
    } else {
        TransactionIcons.TRANSACTION
    }

    private fun findTransactionalAnnotation(element: PsiModifierListOwner): PsiAnnotation? {
        return element.annotations.firstOrNull { annotation ->
            annotation.qualifiedName == "org.springframework.transaction.annotation.Transactional"
        }
    }

    private fun isReadOnly(annotation: PsiAnnotation): Boolean {
        val readOnly = annotation.findAttributeValue("readOnly")
        return readOnly?.text == "true"
    }

    private fun buildTooltip(method: PsiMethod, annotation: PsiAnnotation): String {
        val parts = mutableListOf<String>()

        parts.add("🔷 Transactional Method: ${method.name}")

        // Propagation
        val propagation = annotation.findAttributeValue("propagation")?.text
            ?: "REQUIRED"
        parts.add("Propagation: $propagation")

        // ReadOnly
        val readOnly = annotation.findAttributeValue("readOnly")?.text
        if (readOnly != null) {
            parts.add("ReadOnly: $readOnly")
        }

        // Timeout
        val timeout = annotation.findAttributeValue("timeout")?.text
        if (timeout != null) {
            parts.add("Timeout: $timeout")
        }

        return parts.joinToString("\n")
    }
}