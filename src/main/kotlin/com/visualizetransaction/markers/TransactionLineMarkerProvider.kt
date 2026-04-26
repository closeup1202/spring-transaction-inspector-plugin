package com.visualizetransaction.markers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.visualizetransaction.TransactionIcons
import com.visualizetransaction.settings.TransactionInspectorSettings
import com.visualizetransaction.utils.PsiUtils
import javax.swing.Icon

class TransactionLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is PsiIdentifier) return null

        val parent = element.parent
        if (parent !is PsiMethod) return null

        val project = element.project
        val settings = TransactionInspectorSettings.getInstance(project).state
        if (!settings.showGutterIcons) return null

        val transactional = findTransactionalAnnotation(parent)
            ?: parent.containingClass?.let { findTransactionalAnnotation(it) }
            ?: return null

        val isReadOnly = PsiUtils.isReadOnly(transactional)
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
        return element.annotations.firstOrNull { PsiUtils.isTransactionalAnnotation(it) }
    }

    private fun buildTooltip(method: PsiMethod, annotation: PsiAnnotation): String {
        val parts = mutableListOf<String>()
        parts.add("🔷 Transactional Method: ${method.name}")

        val propagationAttr = when (annotation.qualifiedName) {
            PsiUtils.SPRING_TRANSACTIONAL -> "propagation"
            else -> "value"
        }
        val propagation = annotation.findAttributeValue(propagationAttr)?.text ?: "REQUIRED"
        parts.add("Propagation: $propagation")

        annotation.findAttributeValue("readOnly")?.let { parts.add("ReadOnly: ${it.text}") }
        annotation.findAttributeValue("timeout")?.let { parts.add("Timeout: ${it.text}") }

        return parts.joinToString("\n")
    }
}
