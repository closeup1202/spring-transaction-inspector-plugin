package com.visualizetransaction.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.utils.PsiUtils

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
        val element = descriptor.psiElement
        // The descriptor element may be the @Async annotation (when this fix is registered
        // alongside RemoveAsyncAnnotationFix). Walk up to the method and find the
        // @Transactional annotation explicitly.
        if (element is PsiAnnotation && PsiUtils.isTransactionalAnnotation(element)) {
            element.delete()
            return
        }

        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java) ?: return
        val transactional = method.annotations.firstOrNull { PsiUtils.isTransactionalAnnotation(it) } ?: return
        transactional.delete()
    }
}
