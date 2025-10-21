package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.quickfixes.SuppressWarningFix
import com.visualizetransaction.settings.TransactionInspectorSettings

class TransactionalMethodCallInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)

                val settings = TransactionInspectorSettings.getInstance(holder.project).state

                val calledMethod = expression.resolveMethod() ?: return

                val callingMethod = PsiTreeUtil.getParentOfType(
                    expression,
                    PsiMethod::class.java
                ) ?: return

                if (calledMethod.containingClass != callingMethod.containingClass || !settings.enableSameClassCallDetection) {
                    return
                }

                val hasTransactional = hasTransactionalAnnotation(calledMethod)
                if (!hasTransactional) {
                    return
                }

                val qualifier = expression.methodExpression.qualifierExpression
                val isThisCall = qualifier == null || qualifier is PsiThisExpression

                if (isThisCall) {
                    holder.registerProblem(
                        expression.methodExpression as PsiElement,
                        "⚠️ Same-class @Transactional method call bypasses Spring AOP proxy. " +
                                "The @Transactional annotation on '${calledMethod.name}' will be ignored.",
                        ProblemHighlightType.WARNING,
                        SuppressWarningFix()
                    )
                }
            }
        }
    }

    private fun hasTransactionalAnnotation(method: PsiMethod): Boolean {
        return method.annotations.any {
            it.qualifiedName == "org.springframework.transaction.annotation.Transactional"
        }
    }
}