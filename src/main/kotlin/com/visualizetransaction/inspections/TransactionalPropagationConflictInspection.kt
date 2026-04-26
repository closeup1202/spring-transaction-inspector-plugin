package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.settings.TransactionInspectorSettings
import com.visualizetransaction.utils.PsiUtils

class TransactionalPropagationConflictInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val settings = TransactionInspectorSettings.getInstance(holder.project).state
        if (!settings.enablePropagationConflictDetection) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)

                val calledMethod = expression.resolveMethod() ?: return
                if (PsiUtils.findTransactionalAnnotation(calledMethod) == null) return

                val callingMethod = PsiTreeUtil.getParentOfType(
                    expression,
                    PsiMethod::class.java
                ) ?: return

                when (PsiUtils.getPropagation(calledMethod)) {
                    "MANDATORY" -> checkMandatory(expression, callingMethod, holder)
                    "NEVER" -> checkNever(expression, callingMethod, holder)
                    "REQUIRES_NEW" -> checkRequiresNew(expression, callingMethod, holder)
                }
            }
        }
    }

    private fun checkMandatory(
        expression: PsiMethodCallExpression,
        callingMethod: PsiMethod,
        holder: ProblemsHolder
    ) {
        if (!PsiUtils.hasTransactionalAnnotation(callingMethod)) {
            holder.registerProblem(
                expression.methodExpression as PsiElement,
                "❌ Method requires active transaction (MANDATORY propagation) but caller has no @Transactional. " +
                        "This will throw IllegalTransactionStateException at runtime.",
                ProblemHighlightType.ERROR
            )
        }
    }

    private fun checkNever(
        expression: PsiMethodCallExpression,
        callingMethod: PsiMethod,
        holder: ProblemsHolder
    ) {
        if (PsiUtils.hasTransactionalAnnotation(callingMethod)) {
            holder.registerProblem(
                expression.methodExpression as PsiElement,
                "❌ Method with NEVER propagation called from transactional context. " +
                        "This will throw IllegalTransactionStateException at runtime.",
                ProblemHighlightType.ERROR
            )
        }
    }

    private fun checkRequiresNew(
        expression: PsiMethodCallExpression,
        callingMethod: PsiMethod,
        holder: ProblemsHolder
    ) {
        if (PsiUtils.hasTransactionalAnnotation(callingMethod)) {
            holder.registerProblem(
                expression.methodExpression as PsiElement,
                "⚠️ Method with REQUIRES_NEW creates independent transaction. " +
                        "If parent transaction rolls back, changes from this call will still be committed. " +
                        "This may cause data inconsistency if not intentional.",
                ProblemHighlightType.WEAK_WARNING
            )
        }
    }

    override fun getStaticDescription(): String {
        return """
            Detects transaction propagation conflicts that will cause runtime exceptions or unexpected behavior.
            Supports Spring (<code>propagation</code> attribute) and Jakarta/javax (<code>value</code> attribute holding TxType).
            <p>
            <b>MANDATORY:</b> must be called within an active transaction or it throws IllegalTransactionStateException.
            </p>
            <p>
            <b>NEVER:</b> must NOT be called from a transactional context or it throws IllegalTransactionStateException.
            </p>
            <p>
            <b>REQUIRES_NEW:</b> always creates a new, independent transaction. The parent transaction's
            commit/rollback no longer covers this call, which can cause data inconsistency.
            </p>
        """.trimIndent()
    }
}
