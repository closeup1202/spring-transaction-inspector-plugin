package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.visualizetransaction.quickfixes.ChangeMethodVisibilityFix
import com.visualizetransaction.quickfixes.RemoveFinalModifierFix
import com.visualizetransaction.quickfixes.RemoveStaticModifierFix
import com.visualizetransaction.quickfixes.RemoveTransactionalAnnotationFix
import com.visualizetransaction.settings.TransactionInspectorSettings

class InvalidTransactionalMethodInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)

                val transactional = method.annotations.firstOrNull {
                    it.qualifiedName == "org.springframework.transaction.annotation.Transactional"
                } ?: return

                val settings = TransactionInspectorSettings.getInstance(holder.project).state

                if (method.hasModifierProperty(PsiModifier.PRIVATE) && settings.enablePrivateMethodDetection) {
                    holder.registerProblem(
                        transactional as PsiElement,
                        "⚠️ @Transactional on private method '${method.name}' has no effect. " +
                                "Spring AOP cannot proxy private methods.",
                        ProblemHighlightType.WARNING,
                        ChangeMethodVisibilityFix(method.name),
                        RemoveTransactionalAnnotationFix(method.name)
                    )
                }

                if (method.hasModifierProperty(PsiModifier.FINAL) && settings.enableFinalMethodDetection) {
                    holder.registerProblem(
                        transactional as PsiElement,
                        "⚠️ @Transactional on final method '${method.name}' has no effect. " +
                                "Spring AOP cannot override final methods.",
                        ProblemHighlightType.WARNING,
                        RemoveFinalModifierFix(method.name),
                        RemoveTransactionalAnnotationFix(method.name)
                    )
                }

                if (method.hasModifierProperty(PsiModifier.STATIC) && settings.enableStaticMethodDetection) {
                    holder.registerProblem(
                        transactional as PsiElement,
                        "⚠️ @Transactional on static method '${method.name}' has no effect.",
                        ProblemHighlightType.WARNING,
                        RemoveStaticModifierFix(method.name),
                        RemoveTransactionalAnnotationFix(method.name)
                    )
                }
            }
        }
    }

    override fun getStaticDescription(): String {
        return """
            Detects @Transactional annotation on methods with invalid modifiers (private, final, static).
            <p>
            <b>Problem:</b>
            </p>
            <p>
            Spring's transaction management uses AOP (Aspect-Oriented Programming) proxies to intercept method calls
            and apply transaction behavior. However, AOP proxies have limitations:
            </p>
            <ul>
                <li><b>Private methods</b> cannot be intercepted by proxies</li>
                <li><b>Final methods</b> cannot be overridden by CGLIB proxies</li>
                <li><b>Static methods</b> belong to the class, not instances, so cannot be proxied</li>
            </ul>
            <p>
            When @Transactional is placed on these methods, Spring silently ignores it, and no transaction will be started.
            </p>
            <p>
            <b>Problem Examples:</b>
            </p>
            <pre>
            @Service
            public class OrderService {
                @Transactional
                private void processOrder(Order order) {  // ❌ Private - AOP cannot intercept!
                    orderRepository.save(order);
                    // No transaction! Data may be inconsistent
                }

                @Transactional
                public final void completeOrder(Long id) {  // ❌ Final - CGLIB cannot override!
                    Order order = orderRepository.findById(id);
                    order.setStatus(OrderStatus.COMPLETED);
                }

                @Transactional
                public static void cleanupOrders() {  // ❌ Static - belongs to class, not instance!
                    // No transaction context available
                }
            }
            </pre>
            <p>
            <b>Solutions:</b>
            </p>
            <ol>
                <li><b>Private methods:</b> Change visibility to <code>public</code> or <code>protected</code>
                    <pre>
            @Transactional
            public void processOrder(Order order) {  // ✅ Public - can be proxied
                orderRepository.save(order);
            }
                    </pre>
                </li>
                <li><b>Final methods:</b> Remove the <code>final</code> modifier
                    <pre>
            @Transactional
            public void completeOrder(Long id) {  // ✅ Non-final - can be overridden
                Order order = orderRepository.findById(id);
                order.setStatus(OrderStatus.COMPLETED);
            }
                    </pre>
                </li>
                <li><b>Static methods:</b> Convert to instance method or remove @Transactional
                    <pre>
            @Transactional
            public void cleanupOrders() {  // ✅ Instance method - has transaction context
                orderRepository.deleteOldOrders();
            }
                    </pre>
                </li>
            </ol>
            <p>
            <b>Quick Fixes Available:</b>
            </p>
            <ul>
                <li>Change method visibility to public/protected</li>
                <li>Remove final modifier</li>
                <li>Remove static modifier</li>
                <li>Remove @Transactional annotation if not needed</li>
            </ul>
            <p>
            <b>Customization:</b>
            </p>
            <p>
            You can enable/disable individual checks in Settings → Tools → Spring Transaction Inspector:
            </p>
            <ul>
                <li>Warn on private methods with @Transactional</li>
                <li>Warn on final methods with @Transactional</li>
                <li>Warn on static methods with @Transactional</li>
            </ul>
        """.trimIndent()
    }
}