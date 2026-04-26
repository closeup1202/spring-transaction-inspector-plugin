package com.visualizetransaction.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.utils.PsiUtils

class ShowTransactionInfoAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val project = e.project ?: return

        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset)
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)

        if (method == null) {
            Messages.showMessageDialog(
                project,
                "Place the cursor inside the method!",
                "Transaction Inspector",
                Messages.getWarningIcon()
            )
            return
        }

        val methodLevel = findTransactionalAnnotation(method)
        if (methodLevel != null) {
            Messages.showMessageDialog(
                project,
                """
                    Method: ${method.name}
                    Transaction: attached to the method

                    ${analyzeTransactional(methodLevel)}
                """.trimIndent(),
                "Transaction Info",
                Messages.getInformationIcon()
            )
            return
        }

        val classLevel = method.containingClass?.let { findTransactionalAnnotation(it) }
        if (classLevel != null) {
            Messages.showMessageDialog(
                project,
                """
                    Method: ${method.name}
                    Transaction: Inherited at the class level

                    ${analyzeTransactional(classLevel)}
                """.trimIndent(),
                "Transaction Info",
                Messages.getInformationIcon()
            )
            return
        }

        Messages.showMessageDialog(
            project,
            "Method '${method.name}' does not have @Transactional annotation.",
            "Transaction Info",
            Messages.getInformationIcon()
        )
    }

    private fun findTransactionalAnnotation(element: PsiModifierListOwner): PsiAnnotation? {
        return element.annotations.firstOrNull { PsiUtils.isTransactionalAnnotation(it) }
    }

    private fun analyzeTransactional(annotation: PsiAnnotation): String {
        val attributes = mutableListOf<String>()

        val propagationAttr = when (annotation.qualifiedName) {
            PsiUtils.SPRING_TRANSACTIONAL -> "propagation"
            else -> "value"
        }
        val propagation = annotation.findAttributeValue(propagationAttr)
        if (propagation != null) {
            attributes.add("Propagation: ${propagation.text}")
        } else {
            attributes.add("Propagation: REQUIRED (default)")
        }

        annotation.findAttributeValue("readOnly")?.let { attributes.add("ReadOnly: ${it.text}") }
        annotation.findAttributeValue("timeout")?.let { attributes.add("Timeout: ${it.text}") }

        return attributes.joinToString("\n")
    }

    override fun update(e: AnActionEvent) {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = psiFile is PsiJavaFile
    }
}
