package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.quickfixes.RemoveAsyncAnnotationFix
import com.visualizetransaction.quickfixes.RemoveTransactionalAnnotationFix
import com.visualizetransaction.settings.TransactionInspectorSettings
import com.visualizetransaction.utils.PsiUtils

class AsyncTransactionalConflictInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)

                val settings = TransactionInspectorSettings.getInstance(holder.project).state
                if (!settings.enableAsyncTransactionalDetection) {
                    return
                }

                checkAsyncWithTransactional(method, holder)

                checkLazyLoadingInAsync(method, holder)
            }

            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)

                val settings = TransactionInspectorSettings.getInstance(holder.project).state
                if (!settings.enableAsyncTransactionalDetection) {
                    return
                }

                checkSameClassAsyncCall(expression, holder)
            }
        }
    }

    /**
     * Pattern 1: @Async + @Transactional on the same method
     */
    private fun checkAsyncWithTransactional(method: PsiMethod, holder: ProblemsHolder) {
        val asyncAnnotation = method.annotations.firstOrNull {
            it.qualifiedName == "org.springframework.scheduling.annotation.Async"
        } ?: return

        val transactionalAnnotation = method.annotations.firstOrNull {
            PsiUtils.isTransactionalAnnotation(it)
        } ?: return

        holder.registerProblem(
            asyncAnnotation,
            "⚠️ @Async method is also @Transactional. Transaction will NOT propagate to the async thread. " +
                    "Consider removing @Transactional or handling transaction inside the async method.",
            ProblemHighlightType.WARNING,
            RemoveAsyncAnnotationFix(),
            RemoveTransactionalAnnotationFix(method.name)
        )
    }

    /**
     * Pattern 2: Lazy loading in @Async methods
     */
    private fun checkLazyLoadingInAsync(method: PsiMethod, holder: ProblemsHolder) {
        method.annotations.firstOrNull {
            it.qualifiedName == "org.springframework.scheduling.annotation.Async"
        } ?: return

        val methodBody = method.body ?: return

        PsiTreeUtil.findChildrenOfType(methodBody, PsiMethodCallExpression::class.java).forEach { call ->
            if (isLazyRelationshipAccess(call)) {
                holder.registerProblem(
                    call,
                    "⚠️ Potential LazyInitializationException in @Async method. " +
                            "Lazy relationships should be eagerly loaded before passing to async method.",
                    ProblemHighlightType.WARNING
                )
            }
        }
    }

    /**
     * Pattern 3: Same-class @Async method call (AOP proxy bypass)
     */
    private fun checkSameClassAsyncCall(expression: PsiMethodCallExpression, holder: ProblemsHolder) {
        val calledMethod = expression.resolveMethod() ?: return

        calledMethod.annotations.firstOrNull {
            it.qualifiedName == "org.springframework.scheduling.annotation.Async"
        } ?: return

        val callerMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod::class.java) ?: return
        val callerClass = callerMethod.containingClass ?: return
        val calleeClass = calledMethod.containingClass ?: return

        if (callerClass == calleeClass) {
            val qualifier = expression.methodExpression.qualifierExpression
            if (qualifier == null || qualifier is PsiThisExpression) {
                holder.registerProblem(
                    expression.methodExpression as PsiElement,
                    "⚠️ Calling @Async method '${calledMethod.name}' from the same class. " +
                            "Spring AOP proxy will be bypassed and the method will execute synchronously. " +
                            "Extract to a separate @Service or inject self-reference.",
                    ProblemHighlightType.WARNING
                )
            }
        }
    }

    /**
     * Check if a method call is accessing a lazy-loaded relationship
     */
    private fun isLazyRelationshipAccess(call: PsiMethodCallExpression): Boolean {
        val method = call.resolveMethod() ?: return false
        val methodName = method.name

        if (!methodName.startsWith("get") && !methodName.startsWith("is")) {
            return false
        }

        val qualifier = call.methodExpression.qualifierExpression ?: return false
        val type = qualifier.type as? PsiClassType ?: return false
        val resolvedClass = type.resolve() ?: return false

        val hasEntityAnnotation = resolvedClass.annotations.any {
            it.qualifiedName == "javax.persistence.Entity" ||
                    it.qualifiedName == "jakarta.persistence.Entity"
        }

        if (!hasEntityAnnotation) {
            return false
        }

        val fieldName = when {
            methodName.startsWith("get") && methodName.length > 3 -> {
                methodName.substring(3).replaceFirstChar { it.lowercase() }
            }

            methodName.startsWith("is") && methodName.length > 2 -> {
                methodName.substring(2).replaceFirstChar { it.lowercase() }
            }

            else -> return false
        }

        val field = resolvedClass.findFieldByName(fieldName, false) ?: return false

        return field.annotations.any { annotation ->
            if (!PsiUtils.isJpaRelationshipAnnotation(annotation)) {
                return@any false
            }

            val fetchValue = annotation.findAttributeValue("fetch")
            val fetchType = fetchValue?.text

            // OneToMany and ManyToMany are LAZY by default
            if (PsiUtils.isLazyJpaRelationshipAnnotation(annotation)) {
                fetchType == null || !fetchType.contains("EAGER")
            } else {
                // OneToOne and ManyToOne are EAGER by default, only LAZY if explicitly set
                fetchType?.contains("LAZY") == true
            }
        }
    }

    override fun getStaticDescription(): String {
        return """
            Detects conflicts between @Async and @Transactional annotations that can cause runtime issues.
            <p>
            <b>Common Problems:</b>
            </p>
            <ol>
                <li><b>@Async + @Transactional on same method:</b> Transaction context doesn't propagate to async thread</li>
                <li><b>Lazy loading in @Async methods:</b> Can cause LazyInitializationException</li>
                <li><b>Same-class @Async calls:</b> AOP proxy bypass causes synchronous execution</li>
            </ol>
            <p>
            <b>Problem Example 1 - Transaction doesn't propagate:</b>
            </p>
            <pre>
            @Async
            @Transactional  // ❌ Won't work!
            public void processAsync(User user) {
                userRepository.save(user);  // No transaction context!
            }
            </pre>
            <p>
            <b>Problem Example 2 - LazyInitializationException:</b>
            </p>
            <pre>
            @Async
            public void processUserPosts(User user) {
                int count = user.getPosts().size();  // ❌ LazyInitializationException!
            }
            </pre>
            <p>
            <b>Problem Example 3 - AOP proxy bypass:</b>
            </p>
            <pre>
            @Service
            public class UserService {
                public void createUser() {
                    processAsync();  // ❌ Will execute synchronously!
                }

                @Async
                private void processAsync() {
                    // Won't run async!
                }
            }
            </pre>
            <p>
            <b>Solutions:</b>
            </p>
            <ul>
                <li>For #1: Handle transaction inside the async method, not on it</li>
                <li>For #2: Eagerly load relationships before passing to async method</li>
                <li>For #3: Extract to separate service or inject self-reference</li>
            </ul>
        """.trimIndent()
    }
}
