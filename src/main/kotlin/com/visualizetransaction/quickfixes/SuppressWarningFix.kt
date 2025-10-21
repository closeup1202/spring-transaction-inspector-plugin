package com.visualizetransaction.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiMethodCallExpression

class SuppressWarningFix : LocalQuickFix {

    override fun getName(): String {
        return "Add comment: '// TODO: Extract to separate service'"
    }

    override fun getFamilyName(): String {
        return "Suppress warning with TODO comment"
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val methodCall = descriptor.psiElement.parent as? PsiMethodCallExpression ?: return
        val factory = PsiElementFactory.getInstance(project)

        val comment = factory.createCommentFromText(
            "// TODO: Extract to separate service to make @Transactional work",
            null
        )

        methodCall.parent.addBefore(comment, methodCall)
    }
}