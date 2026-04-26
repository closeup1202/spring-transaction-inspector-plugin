package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.visualizetransaction.quickfixes.ChangeMethodVisibilityFix
import com.visualizetransaction.quickfixes.RemoveFinalModifierFix
import com.visualizetransaction.quickfixes.RemoveStaticModifierFix
import com.visualizetransaction.quickfixes.RemoveTransactionalAnnotationFix
import com.visualizetransaction.settings.TransactionInspectorSettings
import com.visualizetransaction.utils.PsiUtils

class InvalidTransactionalMethodInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val settings = TransactionInspectorSettings.getInstance(holder.project).state
        if (!settings.enablePrivateMethodDetection &&
            !settings.enableFinalMethodDetection &&
            !settings.enableStaticMethodDetection
        ) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)

                val transactional = method.annotations.firstOrNull {
                    PsiUtils.isTransactionalAnnotation(it)
                } ?: return

                if (method.hasModifierProperty(PsiModifier.PRIVATE) && settings.enablePrivateMethodDetection) {
                    holder.registerProblem(
                        transactional as PsiElement,
                        "⚠️ @Transactional on private method '${method.name}' has no effect. " +
                                "Spring AOP cannot proxy private methods.",
                        ProblemHighlightType.WARNING,
                        ChangeMethodVisibilityFix(method.name),
                        RemoveTransactionalAnnotationFix(method.name)
                    )
                }

                if (method.hasModifierProperty(PsiModifier.FINAL) && settings.enableFinalMethodDetection) {
                    holder.registerProblem(
                        transactional as PsiElement,
                        "⚠️ @Transactional on final method '${method.name}' has no effect. " +
                                "Spring AOP cannot override final methods.",
                        ProblemHighlightType.WARNING,
                        RemoveFinalModifierFix(method.name),
                        RemoveTransactionalAnnotationFix(method.name)
                    )
                }

                if (method.hasModifierProperty(PsiModifier.STATIC) && settings.enableStaticMethodDetection) {
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

    override fun getStaticDescription(): String {
        return """
            Detects @Transactional annotation on methods with invalid modifiers (private, final, static).
            Spring AOP proxies cannot intercept private methods, override final methods, or proxy static methods,
            so the @Transactional annotation is silently ignored on these.
        """.trimIndent()
    }
}
