package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiThisExpression
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.quickfixes.SuppressWarningFix
import com.visualizetransaction.settings.TransactionInspectorSettings
import com.visualizetransaction.utils.PsiUtils

class TransactionalMethodCallInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val settings = TransactionInspectorSettings.getInstance(holder.project).state
        if (!settings.enableSameClassCallDetection) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)

                val qualifier = expression.methodExpression.qualifierExpression
                val isThisCall = qualifier == null || qualifier is PsiThisExpression
                if (!isThisCall) return

                val calledMethod = expression.resolveMethod() ?: return
                if (!PsiUtils.hasTransactionalAnnotation(calledMethod)) return

                val callingMethod = PsiTreeUtil.getParentOfType(
                    expression,
                    PsiMethod::class.java
                ) ?: return

                if (!PsiUtils.isSameContainingClass(calledMethod, callingMethod)) return

                val callerHasTransactional = PsiUtils.hasTransactionalAnnotation(callingMethod)
                val propagation = PsiUtils.getPropagation(calledMethod)
                val hasSpecialPropagation = propagation in SPECIAL_PROPAGATIONS

                when {
                    callerHasTransactional && !hasSpecialPropagation -> {
                        holder.registerProblem(
                            expression.methodExpression as PsiElement,
                            "ℹ️ Same-class @Transactional method call. " +
                                    "The @Transactional annotation on '${calledMethod.name}' is redundant but will join the existing transaction from '${callingMethod.name}'.",
                            ProblemHighlightType.WEAK_WARNING,
                            SuppressWarningFix()
                        )
                    }
                    hasSpecialPropagation -> {
                        holder.registerProblem(
                            expression.methodExpression as PsiElement,
                            "⚠️ Same-class @Transactional method call bypasses Spring AOP proxy. " +
                                    "The propagation=$propagation on '${calledMethod.name}' will be ignored. " +
                                    "Consider extracting to a separate service.",
                            ProblemHighlightType.WARNING,
                            SuppressWarningFix()
                        )
                    }
                    else -> {
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
    }

    companion object {
        private val SPECIAL_PROPAGATIONS = setOf(
            "REQUIRES_NEW", "MANDATORY", "NEVER", "NOT_SUPPORTED"
        )
    }

    override fun getStaticDescription(): String {
        return """
            Detects when a @Transactional method is called from within the same class, bypassing Spring AOP proxy.
            <p>
            <b>Problem:</b>
            </p>
            <p>
            Spring uses AOP (Aspect-Oriented Programming) proxies to implement transaction management.
            When you call a method on <code>this</code> (the current object), you're calling the actual object,
            not the Spring proxy. This means the transaction interceptor never gets invoked, and the
            @Transactional annotation is completely ignored.
            </p>
            <p>
            <b>Solutions:</b>
            </p>
            <ol>
                <li>Extract the @Transactional method to a separate @Service.</li>
                <li>Inject the proxy of the current bean (self-injection).</li>
                <li>Switch to AspectJ mode (<code>@EnableTransactionManagement(mode = AdviceMode.ASPECTJ)</code>).</li>
            </ol>
        """.trimIndent()
    }
}
