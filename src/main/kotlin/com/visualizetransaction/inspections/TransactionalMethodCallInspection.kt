package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.quickfixes.SuppressWarningFix
import com.visualizetransaction.settings.TransactionInspectorSettings
import com.visualizetransaction.utils.PsiUtils

class TransactionalMethodCallInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)

                val settings = TransactionInspectorSettings.getInstance(holder.project).state

                val calledMethod = expression.resolveMethod() ?: return

                val callingMethod = PsiTreeUtil.getParentOfType(
                    expression,
                    PsiMethod::class.java
                ) ?: return

                if (calledMethod.containingClass != callingMethod.containingClass || !settings.enableSameClassCallDetection) {
                    return
                }

                val hasTransactional = PsiUtils.hasTransactionalAnnotation(calledMethod)
                if (!hasTransactional) {
                    return
                }

                val qualifier = expression.methodExpression.qualifierExpression
                val isThisCall = qualifier == null || qualifier is PsiThisExpression

                if (isThisCall) {
                    // Check if caller has @Transactional
                    val callerHasTransactional = PsiUtils.hasTransactionalAnnotation(callingMethod)

                    // Check if called method has special propagation
                    val propagation = getPropagation(calledMethod)
                    val hasSpecialPropagation = propagation in listOf(
                        "REQUIRES_NEW", "MANDATORY", "NEVER", "NOT_SUPPORTED"
                    )

                    // Differentiate severity based on context
                    if (callerHasTransactional && !hasSpecialPropagation) {
                        // Scenario 1: Caller has @Transactional, called method has no special propagation
                        // This is usually correct - the annotation is redundant but will join existing transaction
                        holder.registerProblem(
                            expression.methodExpression as PsiElement,
                            "ℹ️ Same-class @Transactional method call. " +
                                    "The @Transactional annotation on '${calledMethod.name}' is redundant but will join the existing transaction from '${callingMethod.name}'.",
                            ProblemHighlightType.WEAK_WARNING,
                            SuppressWarningFix()
                        )
                    } else if (hasSpecialPropagation) {
                        // Scenario 2: Called method has special propagation (REQUIRES_NEW, etc.)
                        // This is a problem - the special behavior won't work
                        holder.registerProblem(
                            expression.methodExpression as PsiElement,
                            "⚠️ Same-class @Transactional method call bypasses Spring AOP proxy. " +
                                    "The propagation=$propagation on '${calledMethod.name}' will be ignored. " +
                                    "Consider extracting to a separate service.",
                            ProblemHighlightType.WARNING,
                            SuppressWarningFix()
                        )
                    } else {
                        // Scenario 3: Caller has no @Transactional
                        // The annotation will be completely ignored
                        holder.registerProblem(
                            expression.methodExpression as PsiElement,
                            "⚠️ Same-class @Transactional method call bypasses Spring AOP proxy. " +
                                    "The @Transactional annotation on '${calledMethod.name}' will be ignored.",
                            ProblemHighlightType.WARNING,
                            SuppressWarningFix()
                        )
                    }
                }
            }
        }
    }

    private fun getPropagation(method: PsiMethod): String? {
        // Check method-level Spring @Transactional first (only Spring has propagation attribute)
        val methodAnnotation = method.getAnnotation(
            "org.springframework.transaction.annotation.Transactional"
        )

        if (methodAnnotation != null) {
            val propagationAttr = methodAnnotation.findAttributeValue("propagation")
                ?: return null  // No propagation attribute = REQUIRED (default)

            // "Propagation.REQUIRES_NEW" → "REQUIRES_NEW"
            return propagationAttr.text.substringAfterLast(".")
        }

        // Check class-level Spring @Transactional
        val containingClass = method.containingClass
        val classAnnotation = containingClass?.getAnnotation(
            "org.springframework.transaction.annotation.Transactional"
        )

        if (classAnnotation != null) {
            val propagationAttr = classAnnotation.findAttributeValue("propagation")
                ?: return null  // No propagation attribute = REQUIRED (default)

            // "Propagation.REQUIRES_NEW" → "REQUIRES_NEW"
            return propagationAttr.text.substringAfterLast(".")
        }

        // Jakarta and javax.transaction don't have propagation attribute
        // They have fixed propagation behavior (REQUIRED)
        return null
    }

    override fun getStaticDescription(): String {
        return """
            Detects when a @Transactional method is called from within the same class, bypassing Spring AOP proxy.
            <p>
            <b>Problem:</b>
            </p>
            <p>
            Spring uses AOP (Aspect-Oriented Programming) proxies to implement transaction management.
            When you call a method on <code>this</code> (the current object), you're calling the actual object,
            not the Spring proxy. This means the transaction interceptor never gets invoked, and the
            @Transactional annotation is completely ignored.
            </p>
            <p>
            <b>Problem Example:</b>
            </p>
            <pre>
            @Service
            public class UserService {
                @Transactional
                public void createUser(User user) {
                    userRepository.save(user);
                    updateUserStats();  // ❌ Same-class call - AOP proxy bypassed!
                }

                @Transactional(propagation = Propagation.REQUIRES_NEW)
                public void updateUserStats() {
                    statsRepository.save(new Stats());
                    // Won't run in a new transaction!
                    // Will participate in createUser's transaction instead
                }
            }
            </pre>
            <p>
            In this example, even though <code>updateUserStats()</code> is annotated with
            <code>REQUIRES_NEW</code>, it won't create a new transaction because it's called directly
            (not through the proxy).
            </p>
            <p>
            <b>Why This Happens:</b>
            </p>
            <pre>
            Client → Spring Proxy → Actual Object
                         ↓
                   Transaction Interceptor

            this.method() → Actual Object (no proxy, no transaction!)
            </pre>
            <p>
            <b>Solutions:</b>
            </p>
            <ol>
                <li><b>Extract to a separate service (Recommended):</b>
                    <pre>
            @Service
            public class UserService {
                @Autowired
                private UserStatsService statsService;

                @Transactional
                public void createUser(User user) {
                    userRepository.save(user);
                    statsService.updateUserStats();  // ✅ Different class - goes through proxy
                }
            }

            @Service
            public class UserStatsService {
                @Transactional(propagation = Propagation.REQUIRES_NEW)
                public void updateUserStats() {
                    statsRepository.save(new Stats());  // ✅ Runs in new transaction
                }
            }
                    </pre>
                </li>
                <li><b>Self-injection (not recommended, but possible):</b>
                    <pre>
            @Service
            public class UserService {
                @Autowired
                private UserService self;  // Inject the proxy of itself

                @Transactional
                public void createUser(User user) {
                    userRepository.save(user);
                    self.updateUserStats();  // Goes through proxy
                }

                @Transactional(propagation = Propagation.REQUIRES_NEW)
                public void updateUserStats() {
                    statsRepository.save(new Stats());
                }
            }
                    </pre>
                </li>
                <li><b>Use AspectJ mode (advanced):</b>
                    <pre>
            @EnableTransactionManagement(mode = AdviceMode.ASPECTJ)
            // Requires compile-time or load-time weaving
                    </pre>
                </li>
            </ol>
            <p>
            <b>Impact:</b>
            </p>
            <ul>
                <li>Transaction propagation settings are ignored</li>
                <li>Isolation levels are ignored</li>
                <li>Rollback rules may not work as expected</li>
                <li>Read-only settings are ignored</li>
            </ul>
            <p>
            <b>Customization:</b>
            </p>
            <p>
            You can disable this check in Settings → Tools → Spring Transaction Inspector:
            </p>
            <ul>
                <li>Detect same-class @Transactional method calls</li>
            </ul>
        """.trimIndent()
    }
}