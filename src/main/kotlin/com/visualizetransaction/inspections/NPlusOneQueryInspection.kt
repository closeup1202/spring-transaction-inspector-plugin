package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiField
import com.intellij.psi.PsiForeachStatement
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.settings.TransactionInspectorSettings
import com.visualizetransaction.utils.PsiUtils

class NPlusOneQueryInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val settings = TransactionInspectorSettings.getInstance(holder.project).state
        if (!settings.enableN1Detection) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : JavaElementVisitor() {

            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)

                if (!settings.checkN1OutsideTransactional && !PsiUtils.hasTransactionalAnnotation(method)) {
                    return
                }

                method.body?.let { body ->
                    if (settings.checkInLoops) {
                        checkLazyAccessInForeach(body, holder)
                    }
                    if (settings.checkInStreamOperations) {
                        checkLazyAccessInStreams(body, holder)
                    }
                }
            }
        }
    }

    private fun checkLazyAccessInForeach(body: PsiCodeBlock, holder: ProblemsHolder) {
        val foreachStatements = PsiTreeUtil.findChildrenOfType(body, PsiForeachStatement::class.java)

        for (loop in foreachStatements) {
            val loopBody = loop.body ?: continue
            val fieldAccesses = PsiTreeUtil.findChildrenOfType(loopBody, PsiReferenceExpression::class.java)

            for (access in fieldAccesses) {
                if (isLazyCollection(access)) {
                    holder.registerProblem(
                        access as PsiElement,
                        "⚠️ Potential N+1 query: Accessing lazy collection inside loop. " +
                                "Each iteration may trigger a separate query. Consider using @EntityGraph or fetch join.",
                        ProblemHighlightType.WARNING
                    )
                }
            }
        }
    }

    private fun checkLazyAccessInStreams(body: PsiCodeBlock, holder: ProblemsHolder) {
        val streamCalls = PsiTreeUtil.findChildrenOfType(body, PsiMethodCallExpression::class.java)

        for (call in streamCalls) {
            val methodName = call.methodExpression.referenceName ?: continue
            if (methodName !in STREAM_OPS) continue

            val lambda = call.argumentList.expressions.firstOrNull() as? PsiLambdaExpression ?: continue
            val lazyAccess = findLazyFieldAccess(lambda) ?: continue

            holder.registerProblem(
                lazyAccess as PsiElement,
                "⚠️ Potential N+1 query: Lazy collection accessed in stream.$methodName(). " +
                        "Consider using @EntityGraph or fetch join.",
                ProblemHighlightType.WARNING
            )
        }
    }

    private fun findLazyFieldAccess(element: PsiElement?): PsiReferenceExpression? {
        if (element == null) return null
        return PsiTreeUtil.findChildrenOfType(element, PsiReferenceExpression::class.java)
            .firstOrNull { isLazyCollection(it) }
    }

    private fun isLazyCollection(expression: PsiReferenceExpression): Boolean {
        val resolved = expression.resolve()

        if (resolved is PsiField) {
            return checkFieldAnnotations(resolved)
        }

        if (resolved is PsiMethod) {
            val field = PsiUtils.findFieldFromGetter(resolved)
            if (field != null) {
                return checkFieldAnnotations(field)
            }
        }

        return false
    }

    private fun checkFieldAnnotations(field: PsiField): Boolean {
        return field.annotations.any { annotation ->
            PsiUtils.isJpaRelationshipAnnotation(annotation) && PsiUtils.isEffectivelyLazy(annotation)
        }
    }

    companion object {
        private val STREAM_OPS = setOf("map", "flatMap", "forEach", "filter")
    }

    override fun getStaticDescription(): String {
        return """
            Detects potential N+1 query problems when lazy-loaded collections are accessed in loops or stream operations.
            <p>
            By default this inspection only fires inside @Transactional methods to keep noise low.
            For OSIV/Open-EntityManager-In-View setups where lazy access happens outside @Transactional,
            enable <i>"Also detect outside @Transactional"</i> in
            <b>Settings → Tools → Spring Transaction Inspector</b>.
            </p>
        """.trimIndent()
    }
}
