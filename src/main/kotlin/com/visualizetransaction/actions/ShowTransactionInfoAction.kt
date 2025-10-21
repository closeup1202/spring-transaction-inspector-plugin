package com.visualizetransaction.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

class ShowTransactionInfoAction : AnAction(){

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

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
                "Transaction Visualizer",
                Messages.getWarningIcon()
            )
            return
        }

        val transactional = findTransactionalAnnotation(method)

        if (transactional != null) {
            val info = analyzeTransactional(transactional)
            Messages.showMessageDialog(
                project,
                """
                    Method: ${method.name}
                    Transaction: 👌
                    
                    $info
                """.trimIndent(),
                "Transaction Info",
                Messages.getInformationIcon()
            )
        } else {
            val containingClass = method.containingClass
            val classTransactional = containingClass?.let { findTransactionalAnnotation(it) }

            if (classTransactional != null) {
                Messages.showMessageDialog(
                    project,
                    """
                        Method: ${method.name}
                        Transaction: Inherited at the class level
                        
                        ${analyzeTransactional(classTransactional)}
                    """.trimIndent(),
                    "Transaction Info",
                    Messages.getInformationIcon()
                )
            } else {
                Messages.showMessageDialog(
                    project,
                    "Method - '${method.name}'에는 @Transactional이 없습니다.",
                    "Transaction Info",
                    Messages.getInformationIcon()
                )
            }
        }
    }

    private fun findTransactionalAnnotation(element: PsiModifierListOwner): PsiAnnotation? {
        return element.annotations.firstOrNull { annotation ->
            annotation.qualifiedName == "org.springframework.transaction.annotation.Transactional"
        }
    }

    private fun analyzeTransactional(annotation: PsiAnnotation): String {
        val attributes = mutableListOf<String>()

        val propagation = annotation.findAttributeValue("propagation")
        if (propagation != null) {
            attributes.add("Propagation: ${propagation.text}")
        } else {
            attributes.add("Propagation: REQUIRED (default)")
        }

        val readOnly = annotation.findAttributeValue("readOnly")
        if (readOnly != null) {
            attributes.add("ReadOnly: ${readOnly.text}")
        }

        val timeout = annotation.findAttributeValue("timeout")
        if (timeout != null) {
            attributes.add("Timeout: ${timeout.text}")
        }

        return attributes.joinToString("\n")
    }

    override fun update(e: AnActionEvent) {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = psiFile is PsiJavaFile
    }
}