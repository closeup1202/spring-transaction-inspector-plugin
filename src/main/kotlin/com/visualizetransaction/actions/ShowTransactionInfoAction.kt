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
        return ActionUpdateThread.BGT  // 백그라운드에서 실행
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val project = e.project ?: return

        // 커서 위치의 요소 가져오기
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset)

        // 현재 메서드 찾기
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)

        if (method == null) {
            Messages.showMessageDialog(
                project,
                "메서드 안에 커서를 위치시켜주세요!",
                "Transaction Visualizer",
                Messages.getWarningIcon()
            )
            return
        }

        // @Transactional 어노테이션 찾기
        val transactional = findTransactionalAnnotation(method)

        if (transactional != null) {
            val info = analyzeTransactional(transactional)
            Messages.showMessageDialog(
                project,
                """
                    메서드: ${method.name}
                    트랜잭션: 있음
                    
                    $info
                """.trimIndent(),
                "Transaction Info",
                Messages.getInformationIcon()
            )
        } else {
            // 클래스 레벨 @Transactional 확인
            val containingClass = method.containingClass
            val classTransactional = containingClass?.let { findTransactionalAnnotation(it) }

            if (classTransactional != null) {
                Messages.showMessageDialog(
                    project,
                    """
                        메서드: ${method.name}
                        트랜잭션: 클래스 레벨에서 상속됨
                        
                        ${analyzeTransactional(classTransactional)}
                    """.trimIndent(),
                    "Transaction Info",
                    Messages.getInformationIcon()
                )
            } else {
                Messages.showMessageDialog(
                    project,
                    "메서드 '${method.name}'에는 @Transactional이 없습니다.",
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

        // propagation 속성
        val propagation = annotation.findAttributeValue("propagation")
        if (propagation != null) {
            attributes.add("Propagation: ${propagation.text}")
        } else {
            attributes.add("Propagation: REQUIRED (default)")
        }

        // readOnly 속성
        val readOnly = annotation.findAttributeValue("readOnly")
        if (readOnly != null) {
            attributes.add("ReadOnly: ${readOnly.text}")
        }

        // timeout 속성
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