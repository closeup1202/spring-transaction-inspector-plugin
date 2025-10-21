package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.quickfixes.SuppressWarningFix
import com.visualizetransaction.settings.TransactionVisualizerSettings

class TransactionalMethodCallInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)

                val settings = TransactionVisualizerSettings.getInstance(holder.project).state

                // 호출되는 메서드 찾기
                val calledMethod = expression.resolveMethod() ?: return

                // 호출하는 메서드 찾기
                val callingMethod = PsiTreeUtil.getParentOfType(
                    expression,
                    PsiMethod::class.java
                ) ?: return

                // 같은 클래스인지 확인
                if (calledMethod.containingClass != callingMethod.containingClass || !settings.enableSameClassCallDetection) {
                    return
                }

                // 호출되는 메서드에 @Transactional이 있는지 확인
                val hasTransactional = hasTransactionalAnnotation(calledMethod)
                if (!hasTransactional) {
                    return
                }

                // this 호출인지 확인 (명시적이든 암묵적이든)
                val qualifier = expression.methodExpression.qualifierExpression
                val isThisCall = qualifier == null || qualifier is PsiThisExpression

                if (isThisCall) {
                    holder.registerProblem(
                        expression.methodExpression as PsiElement,  // 명시적 캐스팅
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