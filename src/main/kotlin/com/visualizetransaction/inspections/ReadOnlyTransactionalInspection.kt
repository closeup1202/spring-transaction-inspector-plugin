package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.settings.TransactionInspectorSettings
import com.visualizetransaction.utils.PsiUtils

class ReadOnlyTransactionalInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val settings = TransactionInspectorSettings.getInstance(holder.project).state
        if (!settings.enableReadOnlyTransactionalDetection) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : JavaElementVisitor() {

            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)

                if (!PsiUtils.isReadOnlyTransactional(method)) return

                method.body?.let { body ->
                    checkForWriteOperations(body, holder)
                }
            }
        }
    }

    private fun checkForWriteOperations(body: PsiCodeBlock, holder: ProblemsHolder) {
        val methodCalls = PsiTreeUtil.findChildrenOfType(body, PsiMethodCallExpression::class.java)

        for (call in methodCalls) {
            val methodName = call.methodExpression.referenceName ?: continue
            val resolvedMethod = call.resolveMethod()

            if (PsiUtils.isWriteOperationMethod(methodName, resolvedMethod)) {
                holder.registerProblem(
                    call as PsiElement,
                    "⚠️ @Transactional(readOnly=true) method should not perform write operations. " +
                            "This may cause unexpected behavior or throw an exception. " +
                            "Remove 'readOnly=true' if write operations are needed.",
                    ProblemHighlightType.WARNING
                )
            }

            if (PsiUtils.isCollectionModificationMethod(methodName)) {
                val caller = call.methodExpression.qualifier as? PsiExpression
                if (caller != null && representsLazyCollection(caller)) {
                    holder.registerProblem(
                        call as PsiElement,
                        "⚠️ @Transactional(readOnly=true) method should not modify collections. " +
                                "This violates the read-only constraint. " +
                                "Remove 'readOnly=true' if modifications are needed.",
                        ProblemHighlightType.WARNING
                    )
                }
            }
        }
    }

    private fun representsLazyCollection(expression: PsiExpression): Boolean {
        return when (expression) {
            is PsiReferenceExpression -> when (val resolved = expression.resolve()) {
                is PsiField -> PsiUtils.hasLazyJpaRelationshipAnnotation(resolved)
                is PsiMethod -> PsiUtils.findFieldFromGetter(resolved)
                    ?.let { PsiUtils.hasLazyJpaRelationshipAnnotation(it) } ?: false
                else -> false
            }
            is PsiMethodCallExpression -> {
                val method = expression.resolveMethod() ?: return false
                val field = PsiUtils.findFieldFromGetter(method) ?: return false
                PsiUtils.hasLazyJpaRelationshipAnnotation(field)
            }
            else -> false
        }
    }

    override fun getStaticDescription(): String {
        return """
            Detects write operations in methods marked with @Transactional(readOnly=true).
            <p>
            When a method is marked as read-only, it should not perform any write operations
            like save, update, delete, or collection modifications. This inspection helps catch
            logical errors and potential performance issues.
            </p>
        """.trimIndent()
    }
}
