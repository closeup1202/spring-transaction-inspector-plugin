package com.visualizetransaction.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiStatement
import com.intellij.psi.util.PsiTreeUtil

class SuppressWarningFix : LocalQuickFix {

    override fun getName(): String {
        return "Add comment: '// TODO: Extract to separate service'"
    }

    override fun getFamilyName(): String {
        return "Suppress warning with TODO comment"
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val methodCall = PsiTreeUtil.getParentOfType(descriptor.psiElement, PsiMethodCallExpression::class.java)
            ?: return
        val statement = PsiTreeUtil.getParentOfType(methodCall, PsiStatement::class.java) ?: return

        val factory = PsiElementFactory.getInstance(project)
        val comment = factory.createCommentFromText(
            "// TODO: Extract to separate service to make @Transactional work",
            null
        )

        statement.parent.addBefore(comment, statement)
    }
}
