package com.visualizetransaction.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation

class RemoveTransactionalAnnotationFix(
    private val methodName: String
) : LocalQuickFix {

    override fun getName(): String {
        return "Remove @Transactional from '$methodName' (not effective anyway)"
    }

    override fun getFamilyName(): String {
        return "Remove @Transactional annotation"
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val annotation = descriptor.psiElement as? PsiAnnotation ?: return
        annotation.delete()
    }
}