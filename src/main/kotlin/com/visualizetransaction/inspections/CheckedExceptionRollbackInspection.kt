package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.visualizetransaction.quickfixes.AddRollbackForFix
import com.visualizetransaction.settings.TransactionInspectorSettings
import com.visualizetransaction.utils.PsiUtils

class CheckedExceptionRollbackInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val settings = TransactionInspectorSettings.getInstance(holder.project).state
        if (!settings.enableCheckedExceptionRollbackDetection) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)

                val transactional = method.annotations.firstOrNull {
                    PsiUtils.isTransactionalAnnotation(it)
                } ?: return

                if (hasRollbackConfiguration(transactional)) return

                val throwsTypes = method.throwsList.referencedTypes
                if (throwsTypes.isEmpty()) return

                val checkedExceptions = throwsTypes.filter { isCheckedException(it) }
                if (checkedExceptions.isEmpty()) return

                val exceptionNames = checkedExceptions.joinToString(", ") { it.presentableText }

                val quickFixes = mutableListOf<AddRollbackForFix>()
                val isSpringAnnotation = transactional.qualifiedName == PsiUtils.SPRING_TRANSACTIONAL

                if (isSpringAnnotation) {
                    val specificExceptionNames = checkedExceptions
                        .mapNotNull { it.resolve()?.qualifiedName?.substringAfterLast('.') }
                    if (specificExceptionNames.isNotEmpty()) {
                        quickFixes.add(AddRollbackForFix.create(transactional, specificExceptionNames))
                    }
                    quickFixes.add(AddRollbackForFix.create(transactional, listOf("Exception")))
                }

                val configAttribute = if (isSpringAnnotation) "rollbackFor" else "rollbackOn"

                holder.registerProblem(
                    transactional as PsiElement,
                    "⚠️ Method throws checked exception(s) [$exceptionNames] but @Transactional " +
                            "doesn't specify '$configAttribute'. By default, checked exceptions won't trigger " +
                            "rollback, which may cause data inconsistency.",
                    ProblemHighlightType.WARNING,
                    *quickFixes.toTypedArray()
                )
            }
        }
    }

    private fun hasRollbackConfiguration(annotation: PsiAnnotation): Boolean {
        val attributesToCheck = when (annotation.qualifiedName) {
            PsiUtils.SPRING_TRANSACTIONAL -> listOf("rollbackFor", "rollbackForClassName")
            PsiUtils.JAKARTA_TRANSACTIONAL, PsiUtils.JAVAX_TRANSACTIONAL -> listOf("rollbackOn")
            else -> return true
        }

        return attributesToCheck.any { hasExplicitAttribute(annotation, it) }
    }

    private fun hasExplicitAttribute(annotation: PsiAnnotation, attributeName: String): Boolean {
        val attributeValue = annotation.findDeclaredAttributeValue(attributeName) ?: return false

        if (attributeValue is PsiArrayInitializerMemberValue) {
            return attributeValue.initializers.isNotEmpty()
        }

        return true
    }

    private fun isCheckedException(type: PsiType): Boolean {
        val psiClass = (type as? PsiClassType)?.resolve() ?: return false
        val qualifiedName = psiClass.qualifiedName ?: return false

        if (qualifiedName == "java.io.IOException" ||
            qualifiedName == "java.sql.SQLException" ||
            qualifiedName == "java.lang.Exception"
        ) {
            return true
        }

        var current: PsiClass? = psiClass
        while (current != null) {
            val name = current.qualifiedName

            if (name == "java.lang.RuntimeException" || name == "java.lang.Error") {
                return false
            }

            if (name == "java.lang.Exception") {
                return true
            }

            current = current.superClass
        }

        return false
    }

    override fun getStaticDescription(): String {
        return """
            Detects when a @Transactional method throws checked exceptions without specifying rollbackFor.
            <p>
            By default, Spring only rolls back transactions for RuntimeException and Error.
            Checked exceptions (like IOException, SQLException) do NOT trigger rollback,
            which can lead to data inconsistency. Jakarta/javax @Transactional behaves the same way
            unless <code>rollbackOn</code> is configured.
            </p>
        """.trimIndent()
    }
}
