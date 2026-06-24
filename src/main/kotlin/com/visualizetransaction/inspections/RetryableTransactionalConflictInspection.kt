package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.visualizetransaction.settings.TransactionInspectorSettings
import com.visualizetransaction.utils.PsiUtils

/**
 * Detects @Retryable and @Transactional sharing the same bean method.
 *
 * When both annotations sit on one method the transaction advice runs *inside* the retry advice.
 * If a (checked) exception escapes, the transaction commits (or rolls back) and unwinds before the
 * retry interceptor sees it, so the retry re-enters with a fresh transaction. A side effect such as a
 * balance deduction can therefore run once per attempt — three retries means three deductions.
 *
 * The fix is to split the concerns into two beans: an outer bean carrying @Retryable that delegates to
 * an inner @Transactional bean, so each retry starts a clean transaction boundary.
 */
class RetryableTransactionalConflictInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val settings = TransactionInspectorSettings.getInstance(holder.project).state
        if (!settings.enableRetryableTransactionalDetection) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)
                checkRetryableWithTransactional(method, holder)
            }
        }
    }

    private fun checkRetryableWithTransactional(method: PsiMethod, holder: ProblemsHolder) {
        val retryable = method.annotations.firstOrNull {
            it.qualifiedName == RETRYABLE_FQN
        } ?: return

        // The transaction may be declared on the method directly or inherited from the class.
        val transactionalOnMethod = method.annotations.firstOrNull { PsiUtils.isTransactionalAnnotation(it) }
        val transactionalOnClass = method.containingClass?.annotations?.firstOrNull {
            PsiUtils.isTransactionalAnnotation(it)
        }
        val transactional: PsiAnnotation = transactionalOnMethod ?: transactionalOnClass ?: return

        val source = if (transactionalOnMethod != null) "this method" else "its class"

        holder.registerProblem(
            retryable as PsiElement,
            "⚠️ @Retryable and @Transactional are on the same bean ($source declares @Transactional). " +
                    "The transaction commits/rolls back before the retry fires, so each retry re-runs the work in a " +
                    "fresh transaction — side effects (e.g. balance deductions) can execute once per attempt. " +
                    "Split them: an outer @Retryable bean delegating to an inner @Transactional bean.",
            ProblemHighlightType.WARNING
        )
    }

    companion object {
        private const val RETRYABLE_FQN = "org.springframework.retry.annotation.Retryable"
    }

    override fun getStaticDescription(): String {
        return """
            Detects @Retryable and @Transactional declared on the same bean method.
            <p>
            Because the transaction advice runs inside the retry advice, the transaction is committed or
            rolled back before the retry interceptor observes the failure. The retry then starts a brand-new
            transaction, so any side effect runs again on every attempt (triple retry = triple effect).
            </p>
            <p>
            Separate the concerns into two beans: an outer bean annotated with @Retryable that delegates to
            an inner bean annotated with @Transactional.
            </p>
        """.trimIndent()
    }
}
