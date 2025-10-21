package com.visualizetransaction.markers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.*
import javax.swing.Icon

class TransactionLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // 메서드 식별자만 처리 (메서드 이름 부분)
        if (element !is PsiIdentifier) return null

        val parent = element.parent
        if (parent !is PsiMethod) return null

        // @Transactional 어노테이션 찾기
        val transactional = findTransactionalAnnotation(parent)
            ?: parent.containingClass?.let { findTransactionalAnnotation(it) }
            ?: return null

        // 아이콘과 툴팁 결정
        val isReadOnly = isReadOnly(transactional)
        val icon = if (isReadOnly) getReadOnlyIcon() else getTransactionIcon()
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

    private fun getTransactionIcon(): Icon {
        return AllIcons.Nodes.DataTables  // 실제 존재하는 아이콘!
    }

    private fun getReadOnlyIcon(): Icon {
        return AllIcons.Actions.Show  // 실제 존재하는 아이콘!
    }
}