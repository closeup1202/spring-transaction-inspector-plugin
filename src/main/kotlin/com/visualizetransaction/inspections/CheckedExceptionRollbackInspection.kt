package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.visualizetransaction.quickfixes.AddRollbackForFix
import com.visualizetransaction.settings.TransactionInspectorSettings

class CheckedExceptionRollbackInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)

                val transactional = method.annotations.firstOrNull {
                    it.qualifiedName == "org.springframework.transaction.annotation.Transactional"
                } ?: return

                val settings = TransactionInspectorSettings.getInstance(holder.project).state

                if (!settings.enableCheckedExceptionRollbackDetection) {
                    return
                }

                val hasRollbackFor = hasExplicitAttribute(transactional, "rollbackFor") ||
                        hasExplicitAttribute(transactional, "rollbackForClassName")

                if (hasRollbackFor) {
                    return
                }

                val throwsTypes = method.throwsList.referencedTypes
                if (throwsTypes.isEmpty()) {
                    return
                }

                val checkedExceptions = throwsTypes.filter { isCheckedException(it) }

                if (checkedExceptions.isNotEmpty()) {
                    val exceptionNames = checkedExceptions.joinToString(", ") { it.presentableText }

                    val exceptionClassNames = checkedExceptions.mapNotNull { exceptionType ->
                        exceptionType?.resolve()?.qualifiedName
                    }

                    val quickFixes = mutableListOf<AddRollbackForFix>()

                    if (exceptionClassNames.isNotEmpty()) {
                        val specificExceptionNames = exceptionClassNames.map { it.substringAfterLast('.') }
                        quickFixes.add(AddRollbackForFix.create(transactional, specificExceptionNames))
                    }

                    quickFixes.add(AddRollbackForFix.create(transactional, listOf("Exception")))

                    holder.registerProblem(
                        transactional as PsiElement,
                        "⚠️ Method throws checked exception(s) [$exceptionNames] but @Transactional " +
                                "doesn't specify 'rollbackFor'. By default, checked exceptions won't trigger " +
                                "rollback, which may cause data inconsistency.",
                        ProblemHighlightType.WARNING,
                        *quickFixes.toTypedArray()
                    )
                }
            }
        }
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
            which can lead to data inconsistency.
            </p>
            <p>
            <b>Problem Example:</b>
            </p>
            <pre>
            @Transactional
            public void processFile(File file) throws IOException {
                repository.save(data);  // DB INSERT
                Files.copy(...);        // IOException occurs
                // DB data is committed but file operation failed!
            }
            </pre>
            <p>
            <b>Solution:</b>
            </p>
            <pre>
            @Transactional(rollbackFor = Exception.class)
            public void processFile(File file) throws IOException {
                // Now IOException will trigger rollback
            }
            </pre>
        """.trimIndent()
    }
}
