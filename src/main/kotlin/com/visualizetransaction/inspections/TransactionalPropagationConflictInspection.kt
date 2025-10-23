package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.settings.TransactionInspectorSettings

class TransactionalPropagationConflictInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)

                val settings = TransactionInspectorSettings.getInstance(holder.project).state
                if (!settings.enablePropagationConflictDetection) return

                val calledMethod = expression.resolveMethod() ?: return
                val callingMethod = PsiTreeUtil.getParentOfType(
                    expression,
                    PsiMethod::class.java
                ) ?: return

                // Check if the called method has @Transactional
                if (!hasTransactionalAnnotation(calledMethod)) return

                // Get propagation type
                val propagation = getPropagation(calledMethod) ?: return

                // Check for conflicts
                when (propagation) {
                    "MANDATORY" -> checkMandatory(expression, callingMethod, holder)
                    "NEVER" -> checkNever(expression, callingMethod, holder)
                    "REQUIRES_NEW" -> checkRequiresNew(expression, callingMethod, holder)
                }
            }
        }
    }

    private fun checkMandatory(
        expression: PsiMethodCallExpression,
        callingMethod: PsiMethod,
        holder: ProblemsHolder
    ) {
        // MANDATORY requires active transaction
        if (!isCallerTransactional(callingMethod)) {
            holder.registerProblem(
                expression.methodExpression as PsiElement,
                "❌ Method requires active transaction (MANDATORY propagation) but caller has no @Transactional. " +
                        "This will throw IllegalTransactionStateException at runtime.",
                ProblemHighlightType.ERROR
            )
        }
    }

    private fun checkNever(
        expression: PsiMethodCallExpression,
        callingMethod: PsiMethod,
        holder: ProblemsHolder
    ) {
        // NEVER must NOT be called within transaction
        if (isCallerTransactional(callingMethod)) {
            holder.registerProblem(
                expression.methodExpression as PsiElement,
                "❌ Method with NEVER propagation called from transactional context. " +
                        "This will throw IllegalTransactionStateException at runtime.",
                ProblemHighlightType.ERROR
            )
        }
    }

    private fun checkRequiresNew(
        expression: PsiMethodCallExpression,
        callingMethod: PsiMethod,
        holder: ProblemsHolder
    ) {
        // REQUIRES_NEW creates an independent transaction
        if (isCallerTransactional(callingMethod)) {
            holder.registerProblem(
                expression.methodExpression as PsiElement,
                "⚠️ Method with REQUIRES_NEW creates independent transaction. " +
                        "If parent transaction rolls back, changes from this call will still be committed. " +
                        "This may cause data inconsistency if not intentional.",
                ProblemHighlightType.WEAK_WARNING
            )
        }
    }

    private fun hasTransactionalAnnotation(method: PsiMethod): Boolean {
        return method.annotations.any {
            it.qualifiedName == "org.springframework.transaction.annotation.Transactional"
        }
    }

    private fun getPropagation(method: PsiMethod): String? {
        val annotation = method.getAnnotation(
            "org.springframework.transaction.annotation.Transactional"
        ) ?: return null

        val propagationAttr = annotation.findAttributeValue("propagation")
            ?: return null  // No propagation attribute = REQUIRED (default)

        // "Propagation.REQUIRES_NEW" → "REQUIRES_NEW"
        return propagationAttr.text.substringAfterLast(".")
    }

    private fun isCallerTransactional(method: PsiMethod): Boolean {
        // Check method-level @Transactional
        if (method.hasAnnotation("org.springframework.transaction.annotation.Transactional")) {
            return true
        }

        // Check class-level @Transactional
        val containingClass = method.containingClass
        if (containingClass?.hasAnnotation("org.springframework.transaction.annotation.Transactional") == true) {
            return true
        }

        return false
    }

    override fun getStaticDescription(): String {
        return """
            Detects transaction propagation conflicts that will cause runtime exceptions or unexpected behavior.
            <p>
            <b>MANDATORY Propagation:</b>
            </p>
            <p>
            A method with <code>@Transactional(propagation = Propagation.MANDATORY)</code> MUST be called
            within an active transaction. If called without a transaction, it throws
            <code>IllegalTransactionStateException</code>.
            </p>
            <p>
            <b>Example - MANDATORY Violation:</b>
            </p>
            <pre>
            @Service
            public class InventoryService {
                // ❌ No @Transactional
                public void updateInventory(Long productId) {
                    decreaseStock(productId, 10);  // Will throw exception!
                }

                @Transactional(propagation = Propagation.MANDATORY)
                public void decreaseStock(Long productId, int quantity) {
                    // This MUST run within a transaction
                    Product product = productRepository.findById(productId);
                    product.setStock(product.getStock() - quantity);
                }
            }
            </pre>
            <p>
            <b>Fix:</b> Add <code>@Transactional</code> to the caller method.
            </p>
            <pre>
            @Transactional  // ✅ Now it works
            public void updateInventory(Long productId) {
                decreaseStock(productId, 10);
            }
            </pre>
            <hr>
            <p>
            <b>NEVER Propagation:</b>
            </p>
            <p>
            A method with <code>@Transactional(propagation = Propagation.NEVER)</code> must NOT be called
            within a transaction. If called within a transaction, it throws
            <code>IllegalTransactionStateException</code>.
            </p>
            <p>
            <b>Example - NEVER Violation:</b>
            </p>
            <pre>
            @Service
            public class UserService {
                @Transactional  // ❌ Has transaction
                public void registerUser(User user) {
                    userRepository.save(user);
                    emailService.sendWelcomeEmail(user);  // Will throw exception!
                }
            }

            @Service
            public class EmailService {
                @Transactional(propagation = Propagation.NEVER)
                public void sendWelcomeEmail(User user) {
                    // This must NOT run in a transaction
                    // (external API calls shouldn't be in transactions)
                    externalEmailService.send(user.getEmail(), "Welcome!");
                }
            }
            </pre>
            <p>
            <b>Fix:</b> Move the call outside the transaction or use events.
            </p>
            <pre>
            @Service
            public class UserService {
                @Transactional
                public void registerUser(User user) {
                    userRepository.save(user);
                    // Transaction commits here
                }

                // Separate method without transaction
                public void sendWelcomeEmail(User user) {
                    emailService.sendWelcomeEmail(user);  // ✅ No transaction
                }
            }
            </pre>
            <hr>
            <p>
            <b>REQUIRES_NEW Propagation:</b>
            </p>
            <p>
            A method with <code>@Transactional(propagation = Propagation.REQUIRES_NEW)</code> always creates
            a new, independent transaction. This can lead to data inconsistency if not used carefully.
            </p>
            <p>
            <b>Example - REQUIRES_NEW Risk:</b>
            </p>
            <pre>
            @Service
            public class OrderService {
                @Transactional
                public void createOrder(Order order) {
                    orderRepository.save(order);  // Transaction 1

                    paymentService.processPayment(order);  // Transaction 2 (independent)

                    order.setStatus("PAID");
                    // ⚠️ If exception here, order rolls back but payment is already committed!
                }
            }

            @Service
            public class PaymentService {
                @Transactional(propagation = Propagation.REQUIRES_NEW)
                public void processPayment(Order order) {
                    Payment payment = new Payment(order.getAmount());
                    paymentRepository.save(payment);
                    // This commits immediately, separate from parent transaction
                }
            }
            </pre>
            <p>
            <b>When to use REQUIRES_NEW:</b>
            </p>
            <ul>
                <li>Audit logging (must persist even if main transaction fails)</li>
                <li>Independent counters/statistics</li>
                <li>Operations that should succeed regardless of parent transaction outcome</li>
            </ul>
            <p>
            <b>Customization:</b>
            </p>
            <p>
            You can disable this check in Settings → Tools → Spring Transaction Inspector:
            </p>
            <ul>
                <li>Enable transaction propagation conflict detection</li>
            </ul>
        """.trimIndent()
    }
}
