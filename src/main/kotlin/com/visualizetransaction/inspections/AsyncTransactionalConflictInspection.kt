package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiThisExpression
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.quickfixes.RemoveAsyncAnnotationFix
import com.visualizetransaction.quickfixes.RemoveTransactionalAnnotationFix
import com.visualizetransaction.settings.TransactionInspectorSettings
import com.visualizetransaction.utils.PsiUtils

class AsyncTransactionalConflictInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val settings = TransactionInspectorSettings.getInstance(holder.project).state
        if (!settings.enableAsyncTransactionalDetection) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)
                checkAsyncWithTransactional(method, holder)
                checkLazyLoadingInAsync(method, holder)
            }

            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                checkSameClassAsyncCall(expression, holder)
            }
        }
    }

    /**
     * Pattern 1: @Async + @Transactional on the same method.
     */
    private fun checkAsyncWithTransactional(method: PsiMethod, holder: ProblemsHolder) {
        val asyncAnnotation = method.annotations.firstOrNull {
            it.qualifiedName == ASYNC_FQN
        } ?: return

        method.annotations.firstOrNull { PsiUtils.isTransactionalAnnotation(it) } ?: return

        holder.registerProblem(
            asyncAnnotation,
            "⚠️ @Async method is also @Transactional. Transaction will NOT propagate to the async thread. " +
                    "Consider removing @Transactional or handling transaction inside the async method.",
            ProblemHighlightType.WARNING,
            RemoveAsyncAnnotationFix(),
            RemoveTransactionalAnnotationFix(method.name)
        )
    }

    /**
     * Pattern 2: lazy-loading inside an @Async body.
     * Walks every method-call expression so chains like `user.getOrders().get(0).getItems()` are caught
     * (not only the first-level getter).
     */
    private fun checkLazyLoadingInAsync(method: PsiMethod, holder: ProblemsHolder) {
        method.annotations.firstOrNull { it.qualifiedName == ASYNC_FQN } ?: return

        val methodBody = method.body ?: return

        PsiTreeUtil.findChildrenOfType(methodBody, PsiMethodCallExpression::class.java).forEach { call ->
            if (isLazyRelationshipAccess(call)) {
                holder.registerProblem(
                    call,
                    "⚠️ Potential LazyInitializationException in @Async method. " +
                            "Lazy relationships should be eagerly loaded before passing to async method.",
                    ProblemHighlightType.WARNING
                )
            }
        }
    }

    /**
     * Pattern 3: same-class @Async call (AOP proxy bypass).
     */
    private fun checkSameClassAsyncCall(expression: PsiMethodCallExpression, holder: ProblemsHolder) {
        val calledMethod = expression.resolveMethod() ?: return
        calledMethod.annotations.firstOrNull { it.qualifiedName == ASYNC_FQN } ?: return

        val callerMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod::class.java) ?: return
        if (!PsiUtils.isSameContainingClass(callerMethod, calledMethod)) return

        val qualifier = expression.methodExpression.qualifierExpression
        if (qualifier != null && qualifier !is PsiThisExpression) return

        holder.registerProblem(
            expression.methodExpression as PsiElement,
            "⚠️ Calling @Async method '${calledMethod.name}' from the same class. " +
                    "Spring AOP proxy will be bypassed and the method will execute synchronously. " +
                    "Extract to a separate @Service or inject self-reference.",
            ProblemHighlightType.WARNING
        )
    }

    private fun isLazyRelationshipAccess(call: PsiMethodCallExpression): Boolean {
        val method = call.resolveMethod() ?: return false
        val methodName = method.name

        if (!methodName.startsWith("get") && !methodName.startsWith("is")) return false

        val qualifier = call.methodExpression.qualifierExpression ?: return false
        val type = qualifier.type as? PsiClassType ?: return false
        val resolvedClass = type.resolve() ?: return false

        val hasEntityAnnotation = resolvedClass.annotations.any {
            it.qualifiedName == "javax.persistence.Entity" ||
                    it.qualifiedName == "jakarta.persistence.Entity"
        }
        if (!hasEntityAnnotation) return false

        val field = PsiUtils.findFieldFromGetter(method) ?: return false

        return field.annotations.any { annotation ->
            PsiUtils.isJpaRelationshipAnnotation(annotation) && PsiUtils.isEffectivelyLazy(annotation)
        }
    }

    companion object {
        private const val ASYNC_FQN = "org.springframework.scheduling.annotation.Async"
    }

    override fun getStaticDescription(): String {
        return """
            Detects conflicts between @Async and @Transactional that cause runtime issues:
            <ol>
                <li>@Async + @Transactional on same method — transaction context does not propagate.</li>
                <li>Lazy access in @Async methods — LazyInitializationException.</li>
                <li>Same-class @Async calls — AOP proxy bypass causes synchronous execution.</li>
            </ol>
        """.trimIndent()
    }
}
