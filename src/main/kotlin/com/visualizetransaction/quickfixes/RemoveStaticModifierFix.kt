package com.visualizetransaction.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.PsiTreeUtil

class RemoveStaticModifierFix(
    private val methodName: String
) : LocalQuickFix {

    override fun getName(): String {
        return "Remove 'static' modifier from '$methodName'"
    }

    override fun getFamilyName(): String {
        return "Remove static modifier"
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val method = PsiTreeUtil.getParentOfType(descriptor.psiElement, PsiMethod::class.java) ?: return
        method.modifierList.setModifierProperty(PsiModifier.STATIC, false)
    }
}
