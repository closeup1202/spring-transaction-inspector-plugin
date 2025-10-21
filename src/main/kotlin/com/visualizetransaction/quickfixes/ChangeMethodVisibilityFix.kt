package com.visualizetransaction.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier

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
        val annotation = descriptor.psiElement
        val method = annotation.parent?.parent as? PsiMethod ?: return

        // private 제거
        method.modifierList.setModifierProperty(PsiModifier.PRIVATE, false)

        // public 추가
        method.modifierList.setModifierProperty(PsiModifier.PUBLIC, true)
    }
}