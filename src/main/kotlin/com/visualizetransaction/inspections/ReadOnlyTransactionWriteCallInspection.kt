package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.quickfixes.ChangePropagationToRequiresNewFix
import com.visualizetransaction.settings.TransactionInspectorSettings
import com.visualizetransaction.utils.PsiUtils

class ReadOnlyTransactionWriteCallInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val settings = TransactionInspectorSettings.getInstance(holder.project).state
        if (!settings.enableReadOnlyWriteCallDetection) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)

                val callerMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod::class.java) ?: return
                if (!PsiUtils.isReadOnlyTransactional(callerMethod)) return

                val calledMethod = expression.resolveMethod() ?: return
                if (!isWriteCapableTransaction(calledMethod)) return

                val isSameClassCall = PsiUtils.isSameContainingClass(callerMethod, calledMethod)

                if (isSameClassCall) {
                    holder.registerProblem(
                        expression.methodExpression as PsiElement,
                        "⚠️ Calling write-capable @Transactional method '${calledMethod.name}' from readOnly transaction " +
                                "within the same class. This has TWO problems: " +
                                "(1) AOP proxy will be bypassed, so propagation settings won't work " +
                                "(2) The method will participate in readOnly transaction, causing write operations to fail. " +
                                "You must extract '${calledMethod.name}' to a separate @Service.",
                        ProblemHighlightType.ERROR
                    )
                } else {
                    holder.registerProblem(
                        expression.methodExpression as PsiElement,
                        "⚠️ Calling write-capable @Transactional method '${calledMethod.name}' from readOnly transaction. " +
                                "With propagation=REQUIRED (default), it will participate in the readOnly transaction, " +
                                "causing write operations to fail. Consider using propagation=REQUIRES_NEW in '${calledMethod.name}'.",
                        ProblemHighlightType.WARNING,
                        ChangePropagationToRequiresNewFix()
                    )
                }
            }
        }
    }

    private fun isWriteCapableTransaction(method: PsiMethod): Boolean {
        val transactional: PsiAnnotation = method.annotations.firstOrNull {
            it.qualifiedName == PsiUtils.SPRING_TRANSACTIONAL
        } ?: return false

        if (PsiUtils.isReadOnly(transactional)) return false

        return when (PsiUtils.getPropagation(method)) {
            "REQUIRED", "SUPPORTS", "MANDATORY" -> true
            else -> false
        }
    }

    override fun getStaticDescription(): String {
        return """
            Detects when a @Transactional(readOnly=true) method calls another @Transactional method with write capability.
            <p>
            When a readOnly transaction calls a method with <code>propagation=REQUIRED</code> (the default),
            the called method participates in the existing readOnly transaction. This causes write operations to fail with:
            <code>InvalidDataAccessApiUsageException: Write operations are not allowed in read-only mode</code>
            </p>
            <p>
            Safe propagation types in the called method: <code>REQUIRES_NEW</code>, <code>NOT_SUPPORTED</code>, <code>NEVER</code>.
            </p>
        """.trimIndent()
    }
}
