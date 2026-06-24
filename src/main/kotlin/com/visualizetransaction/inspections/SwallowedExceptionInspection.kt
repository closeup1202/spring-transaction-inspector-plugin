package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiCatchSection
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiThrowStatement
import com.intellij.psi.PsiTryStatement
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.settings.TransactionInspectorSettings
import com.visualizetransaction.utils.PsiUtils

/**
 * Detects exceptions caught and swallowed inside a @Transactional method.
 *
 * Spring's transaction proxy decides whether to roll back by observing the exception that escapes the
 * method. If a catch block neither re-throws nor marks the transaction rollback-only, the proxy sees a
 * normal return and commits — leaving partially-applied changes in the database (silent corruption).
 *
 * A catch block is considered safe when it either:
 *   - re-throws (any throw statement), or
 *   - calls TransactionAspectSupport.currentTransactionStatus().setRollbackOnly().
 */
class SwallowedExceptionInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val settings = TransactionInspectorSettings.getInstance(holder.project).state
        if (!settings.enableSwallowedExceptionDetection) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)

                // Only methods that declare @Transactional themselves. Class-level @Transactional is
                // intentionally excluded to avoid flagging unrelated methods of a transactional service.
                method.annotations.firstOrNull { PsiUtils.isTransactionalAnnotation(it) } ?: return

                val body = method.body ?: return

                PsiTreeUtil.findChildrenOfType(body, PsiTryStatement::class.java).forEach { tryStatement ->
                    // Only consider try statements that belong to THIS method, not to a nested
                    // anonymous class / lambda method whose transactional context differs.
                    if (PsiTreeUtil.getParentOfType(tryStatement, PsiMethod::class.java) != method) {
                        return@forEach
                    }

                    tryStatement.catchSections.forEach { catchSection ->
                        if (isSwallowed(catchSection)) {
                            val anchor: PsiElement = catchSection.parameter ?: catchSection
                            holder.registerProblem(
                                anchor,
                                "⚠️ Exception caught inside a @Transactional method without re-throwing or " +
                                        "rolling back. Spring's proxy will see a normal return and COMMIT, leaving " +
                                        "partial changes in the database. Re-throw the exception, or call " +
                                        "TransactionAspectSupport.currentTransactionStatus().setRollbackOnly().",
                                ProblemHighlightType.WARNING
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isSwallowed(catchSection: PsiCatchSection): Boolean {
        val catchBlock: PsiCodeBlock = catchSection.catchBlock ?: return false

        val reThrows = PsiTreeUtil.findChildrenOfType(catchBlock, PsiThrowStatement::class.java).isNotEmpty()
        if (reThrows) return false

        val marksRollback = PsiTreeUtil.findChildrenOfType(catchBlock, PsiMethodCallExpression::class.java).any {
            it.methodExpression.referenceName == "setRollbackOnly"
        }
        if (marksRollback) return false

        return true
    }

    override fun getStaticDescription(): String {
        return """
            Detects exceptions that are caught and swallowed inside a @Transactional method.
            <p>
            Spring's transaction proxy commits when the method returns normally. If a catch block neither
            re-throws nor calls
            <code>TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()</code>,
            the transaction commits despite the failure, which can persist partial / inconsistent data.
            </p>
            <p><b>Scope:</b> only methods annotated with @Transactional directly are checked; class-level
            @Transactional is intentionally not considered, to avoid noise on unrelated methods.</p>
        """.trimIndent()
    }
}
