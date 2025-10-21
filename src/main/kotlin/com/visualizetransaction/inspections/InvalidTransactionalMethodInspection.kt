package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.*
import com.visualizetransaction.quickfixes.ChangeMethodVisibilityFix
import com.visualizetransaction.quickfixes.RemoveFinalModifierFix
import com.visualizetransaction.quickfixes.RemoveStaticModifierFix
import com.visualizetransaction.quickfixes.RemoveTransactionalAnnotationFix

class InvalidTransactionalMethodInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)

                val transactional = method.annotations.firstOrNull {
                    it.qualifiedName == "org.springframework.transaction.annotation.Transactional"
                } ?: return

                if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
                    holder.registerProblem(
                        transactional as PsiElement,
                        "⚠️ @Transactional on private method '${method.name}' has no effect. " +
                                "Spring AOP cannot proxy private methods.",
                        ProblemHighlightType.WARNING,
                        ChangeMethodVisibilityFix(method.name),
                        RemoveTransactionalAnnotationFix(method.name)
                    )
                }

                if (method.hasModifierProperty(PsiModifier.FINAL)) {
                    holder.registerProblem(
                        transactional as PsiElement,
                        "⚠️ @Transactional on final method '${method.name}' has no effect. " +
                                "Spring AOP cannot override final methods.",
                        ProblemHighlightType.WARNING,
                        RemoveFinalModifierFix(method.name),
                        RemoveTransactionalAnnotationFix(method.name)
                    )
                }

                if (method.hasModifierProperty(PsiModifier.STATIC)) {
                    holder.registerProblem(
                        transactional as PsiElement,
                        "⚠️ @Transactional on static method '${method.name}' has no effect.",
                        ProblemHighlightType.WARNING,
                        RemoveStaticModifierFix(method.name),
                        RemoveTransactionalAnnotationFix(method.name)
                    )
                }
            }
        }
    }
}