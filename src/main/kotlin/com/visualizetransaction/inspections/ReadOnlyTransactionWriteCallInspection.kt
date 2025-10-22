package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.quickfixes.ChangePropagationToRequiresNewFix
import com.visualizetransaction.settings.TransactionInspectorSettings

class ReadOnlyTransactionWriteCallInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)

                val settings = TransactionInspectorSettings.getInstance(holder.project).state
                if (!settings.enableReadOnlyWriteCallDetection) {
                    return
                }

                val callerMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod::class.java) ?: return

                if (!isReadOnlyTransaction(callerMethod)) {
                    return
                }

                val calledMethod = expression.resolveMethod() ?: return

                if (isWriteCapableTransaction(calledMethod)) {
                    val callerClass = callerMethod.containingClass
                    val calleeClass = calledMethod.containingClass
                    val isSameClassCall = callerClass == calleeClass

                    val message = if (isSameClassCall) {
                        "⚠️ Calling write-capable @Transactional method '${calledMethod.name}' from readOnly transaction " +
                                "within the same class. This has TWO problems: " +
                                "(1) AOP proxy will be bypassed, so propagation settings won't work " +
                                "(2) The method will participate in readOnly transaction, causing write operations to fail. " +
                                "You must extract '${calledMethod.name}' to a separate @Service."
                    } else {
                        "⚠️ Calling write-capable @Transactional method '${calledMethod.name}' from readOnly transaction. " +
                                "With propagation=REQUIRED (default), it will participate in the readOnly transaction, " +
                                "causing write operations to fail. Consider using propagation=REQUIRES_NEW in '${calledMethod.name}'."
                    }

                    if (isSameClassCall) {
                        holder.registerProblem(
                            expression.methodExpression as PsiElement,
                            message,
                            ProblemHighlightType.ERROR
                        )
                    } else {
                        holder.registerProblem(
                            expression.methodExpression as PsiElement,
                            message,
                            ProblemHighlightType.WARNING,
                            ChangePropagationToRequiresNewFix()
                        )
                    }
                }
            }
        }
    }

    /**
     * Check if the method has @Transactional(readOnly=true)
     */
    private fun isReadOnlyTransaction(method: PsiMethod): Boolean {
        val transactional = method.annotations.firstOrNull {
            it.qualifiedName == "org.springframework.transaction.annotation.Transactional"
        } ?: return false

        val readOnlyValue = transactional.findAttributeValue("readOnly")
        return readOnlyValue?.text == "true"
    }

    /**
     * Check if the method has @Transactional with REQUIRED propagation (or default)
     * and is likely to perform write operations
     */
    private fun isWriteCapableTransaction(method: PsiMethod): Boolean {
        val transactional = method.annotations.firstOrNull {
            it.qualifiedName == "org.springframework.transaction.annotation.Transactional"
        } ?: return false

        val readOnlyValue = transactional.findAttributeValue("readOnly")
        if (readOnlyValue?.text == "true") {
            return false
        }

        val propagationValue = transactional.findAttributeValue("propagation")
        val propagationText = propagationValue?.text

        return when {
            propagationText == null -> true
            propagationText.contains("REQUIRED") && !propagationText.contains("REQUIRES_NEW") -> true
            propagationText.contains("SUPPORTS") -> true
            propagationText.contains("MANDATORY") -> true
            else -> false
        }
    }

    override fun getStaticDescription(): String {
        return """
            Detects when a @Transactional(readOnly=true) method calls another @Transactional method with write capability.
            <p>
            <b>Problem:</b>
            </p>
            <p>
            When a readOnly transaction calls a method with <code>propagation=REQUIRED</code> (the default),
            the called method participates in the existing readOnly transaction. This causes write operations to fail with:
            <code>InvalidDataAccessApiUsageException: Write operations are not allowed in read-only mode</code>
            </p>
            <p>
            <b>Problem Example:</b>
            </p>
            <pre>
            @Service
            public class UserService {
                @Transactional(readOnly = true)
                public void viewUserData() {
                    User user = userRepository.findById(1L);
                    updateUserStats();  // ❌ Problem!
                }

                @Transactional  // propagation=REQUIRED is default
                public void updateUserStats() {
                    repository.save(new Stats());  // ❌ Will fail: read-only mode!
                }
            }
            </pre>
            <p>
            <b>Solutions:</b>
            </p>
            <ol>
                <li><b>Use REQUIRES_NEW:</b> Create a new write-capable transaction
                    <pre>
            @Transactional(propagation = Propagation.REQUIRES_NEW)
            public void updateUserStats() {
                repository.save(new Stats());  // ✅ Works in new transaction
            }
                    </pre>
                </li>
                <li><b>Separate concerns:</b> Don't mix read and write in same call chain
                    <pre>
            // Controller/Service layer
            public void handleRequest() {
                userService.viewUserData();      // Read
                userService.updateUserStats();   // Write (separate transaction)
            }
                    </pre>
                </li>
                <li><b>Remove readOnly:</b> If you need write operations
                    <pre>
            @Transactional  // readOnly=false is default
            public void viewUserData() {
                User user = userRepository.findById(1L);
                updateUserStats();  // ✅ Both in write-capable transaction
            }
                    </pre>
                </li>
            </ol>
            <p>
            <b>Safe propagation types in called method:</b>
            </p>
            <ul>
                <li><code>REQUIRES_NEW</code> - Creates new transaction (safe)</li>
                <li><code>NOT_SUPPORTED</code> - Suspends transaction (safe)</li>
                <li><code>NEVER</code> - Executes without transaction (safe)</li>
            </ul>
            <p>
            <b>Unsafe propagation types:</b>
            </p>
            <ul>
                <li><code>REQUIRED</code> (default) - Joins existing readOnly transaction</li>
                <li><code>SUPPORTS</code> - Joins if exists</li>
                <li><code>MANDATORY</code> - Requires existing transaction</li>
            </ul>
        """.trimIndent()
    }
}
