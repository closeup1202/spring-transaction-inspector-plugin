package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/**
 * Inspection to detect INSERT/UPDATE operations in @Transactional(readOnly=true) methods
 *
 * Example:
 * @Transactional(readOnly = true)
 * public void updateUser(User user) {
 *     repository.save(user);  // ❌ WARNING: readOnly=true but performing write operation
 * }
 */
class ReadOnlyTransactionalInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {

            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)

                // Check if method has @Transactional(readOnly=true)
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

            // Check for save, update, delete operations
            if (isWriteOperation(methodName, resolvedMethod)) {
                holder.registerProblem(
                    call as PsiElement,
                    "⚠️ @Transactional(readOnly=true) method should not perform write operations. " +
                            "This may cause unexpected behavior or throw an exception. " +
                            "Remove 'readOnly=true' if write operations are needed.",
                    ProblemHighlightType.WARNING
                )
            }

            // Check for add, remove on lazy collections
            if (isCollectionModificationOperation(methodName)) {
                val caller = (call.methodExpression as? PsiReferenceExpression)?.qualifier as? PsiExpression
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
        // Common repository write method names
        val writePatterns = listOf(
            "save", "saveAll", "saveAndFlush",
            "update", "updateAll",
            "delete", "deleteAll", "deleteById", "deleteInBatch",
            "remove", "removeAll",
            "persist", "merge", "flush"
        )

        if (writePatterns.any { methodName.lowercase().contains(it) }) {
            return true
        }

        // Check method's return type (void or boolean often indicates write)
        if (method != null) {
            val returnType = method.returnType
            if (returnType is PsiPrimitiveType && returnType.kind == PsiPrimitiveType.VOID) {
                return writePatterns.any { methodName.lowercase().startsWith(it) }
            }
        }

        return false
    }

    private fun isCollectionModificationOperation(methodName: String): Boolean {
        return methodName in listOf("add", "addAll", "remove", "removeAll", "clear", "addFirst", "addLast")
    }

    private fun representsLazyCollection(expression: PsiExpression): Boolean {
        if (expression is PsiReferenceExpression) {
            val resolved = expression.resolve()

            if (resolved is PsiField) {
                return hasLazyAnnotation(resolved)
            }

            if (resolved is PsiMethod) {
                // It's a getter method
                val field = findFieldFromGetter(resolved)
                if (field != null) {
                    return hasLazyAnnotation(field)
                }
            }
        }

        return false
    }

    private fun hasLazyAnnotation(field: PsiField): Boolean {
        return field.annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName ?: return@any false

            qualifiedName in listOf(
                "javax.persistence.OneToMany",
                "jakarta.persistence.OneToMany",
                "javax.persistence.ManyToMany",
                "jakarta.persistence.ManyToMany",
                "javax.persistence.ElementCollection",
                "jakarta.persistence.ElementCollection"
            )
        }
    }

    private fun findFieldFromGetter(method: PsiMethod): PsiField? {
        val methodName = method.name

        val fieldName = when {
            methodName.startsWith("get") && methodName.length > 3 -> {
                methodName.substring(3).replaceFirstChar { it.lowercase() }
            }
            methodName.startsWith("is") && methodName.length > 2 -> {
                methodName.substring(2).replaceFirstChar { it.lowercase() }
            }
            else -> return null
        }

        return method.containingClass?.findFieldByName(fieldName, false)
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
