package com.visualizetransaction.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.PsiTreeUtil

class ChangeMethodVisibilityFix(
    private val methodName: String
) : LocalQuickFix {

    override fun getName(): String {
        return "Change '$methodName' to public"
    }

    override fun getFamilyName(): String {
        return "Change method visibility"
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val method = PsiTreeUtil.getParentOfType(descriptor.psiElement, PsiMethod::class.java) ?: return
        method.modifierList.setModifierProperty(PsiModifier.PRIVATE, false)
        method.modifierList.setModifierProperty(PsiModifier.PUBLIC, true)
    }
}
