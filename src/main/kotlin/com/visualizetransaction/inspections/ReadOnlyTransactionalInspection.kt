package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.settings.TransactionInspectorSettings
import com.visualizetransaction.utils.PsiUtils

class ReadOnlyTransactionalInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {

            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)

                // Check if this inspection is enabled in settings
                val settings = TransactionInspectorSettings.getInstance(holder.project).state
                if (!settings.enableReadOnlyTransactionalDetection) {
                    return
                }

                val transactionalAnnotation = method.annotations.find {
                    it.qualifiedName == "org.springframework.transaction.annotation.Transactional"
                } ?: return

                val isReadOnly = transactionalAnnotation.findAttributeValue("readOnly")?.let {
                    it.text.lowercase() == "true"
                } ?: false

                if (!isReadOnly) return

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

            if (isWriteOperation(methodName, resolvedMethod)) {
                holder.registerProblem(
                    call as PsiElement,
                    "⚠️ @Transactional(readOnly=true) method should not perform write operations. " +
                            "This may cause unexpected behavior or throw an exception. " +
                            "Remove 'readOnly=true' if write operations are needed.",
                    ProblemHighlightType.WARNING
                )
            }

            if (isCollectionModificationOperation(methodName)) {
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

    private fun isWriteOperation(methodName: String, method: PsiMethod?): Boolean {
        // Use type-based verification for better accuracy
        return PsiUtils.isWriteOperationMethod(methodName, method)
    }

    private fun isCollectionModificationOperation(methodName: String): Boolean {
        return PsiUtils.isCollectionModificationMethod(methodName)
    }

    private fun representsLazyCollection(expression: PsiExpression): Boolean {
        if (expression is PsiReferenceExpression) {
            val resolved = expression.resolve()

            if (resolved is PsiField) {
                return hasLazyAnnotation(resolved)
            }

            if (resolved is PsiMethod) {
                val field = PsiUtils.findFieldFromGetter(resolved)
                if (field != null) {
                    return hasLazyAnnotation(field)
                }
            }
        }

        return false
    }

    private fun hasLazyAnnotation(field: PsiField): Boolean {
        return PsiUtils.hasLazyJpaRelationshipAnnotation(field)
    }


    override fun getStaticDescription(): String {
        return """
            Detects write operations in methods marked with @Transactional(readOnly=true).
            <p>
            When a method is marked as read-only, it should not perform any write operations
            like save, update, delete, or collection modifications. This inspection helps catch
            logical errors and potential performance issues.
            </p>
            <p>
            <b>Example:</b>
            </p>
            <pre>
            @Transactional(readOnly = true)
            public void getAndModify(Long id) {
                User user = repository.findById(id);
                repository.save(user);  // ❌ WARNING
            }
            </pre>
            <p>
            <b>Fix:</b> Remove 'readOnly=true' if write operations are intended.
            </p>
        """.trimIndent()
    }
}
