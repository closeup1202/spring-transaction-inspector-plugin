package com.visualizetransaction.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Test suite for TransactionalPropagationConflictInspection
 *
 * Tests detection of transaction propagation conflicts:
 * - MANDATORY: Must be called within transaction
 * - NEVER: Must NOT be called within transaction
 * - REQUIRES_NEW: Creates independent transaction
 */
class TransactionalPropagationConflictInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(TransactionalPropagationConflictInspection::class.java)
    }

    override fun getTestDataPath(): String {
        return "src/test/resources"
    }

    // ========== MANDATORY Tests ==========

    fun testMandatory_ErrorWhenCalledWithoutTransaction() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.transaction.annotation.Propagation;
            import org.springframework.stereotype.Service;

            @Service
            public class InventoryService {
                // ❌ No @Transactional
                public void updateInventory(Long productId) {
                    decreaseStock(productId, 10);  // Should ERROR
                }

                @Transactional(propagation = Propagation.MANDATORY)
                public void decreaseStock(Long productId, int quantity) {
                    // This MUST run within a transaction
                }
            }
        """.trimIndent()

        myFixture.configureByText("InventoryService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any {
            it.description?.contains("MANDATORY") == true &&
            it.description?.contains("requires active transaction") == true
        }) {
            "Expected ERROR for MANDATORY called without transaction"
        }
    }

    fun testMandatory_NoErrorWhenCalledWithTransaction() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.transaction.annotation.Propagation;
            import org.springframework.stereotype.Service;

            @Service
            public class InventoryService {
                @Transactional  // ✅ Has @Transactional
                public void updateInventory(Long productId) {
                    decreaseStock(productId, 10);  // Should be OK
                }

                @Transactional(propagation = Propagation.MANDATORY)
                public void decreaseStock(Long productId, int quantity) {
                    // This runs within a transaction - OK
                }
            }
        """.trimIndent()

        myFixture.configureByText("InventoryService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("MANDATORY") == true &&
            it.description?.contains("requires active transaction") == true
        }) {
            "Should NOT show error when MANDATORY is called with transaction"
        }
    }

    fun testMandatory_NoErrorWhenCallerHasClassLevelTransactional() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.transaction.annotation.Propagation;
            import org.springframework.stereotype.Service;

            @Service
            @Transactional  // ✅ Class-level @Transactional
            public class InventoryService {
                public void updateInventory(Long productId) {
                    decreaseStock(productId, 10);  // Should be OK
                }

                @Transactional(propagation = Propagation.MANDATORY)
                public void decreaseStock(Long productId, int quantity) {
                    // This runs within a transaction - OK
                }
            }
        """.trimIndent()

        myFixture.configureByText("InventoryService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("MANDATORY") == true &&
            it.description?.contains("requires active transaction") == true
        }) {
            "Should NOT show error when caller has class-level @Transactional"
        }
    }

    // ========== NEVER Tests ==========

    fun testNever_ErrorWhenCalledWithinTransaction() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.transaction.annotation.Propagation;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                @Transactional  // ❌ Has transaction
                public void registerUser(User user) {
                    emailService.sendWelcomeEmail(user);  // Should ERROR
                }
            }

            @Service
            class EmailService {
                @Transactional(propagation = Propagation.NEVER)
                public void sendWelcomeEmail(User user) {
                    // This must NOT run in a transaction
                }
            }

            class User {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any {
            it.description?.contains("NEVER") == true &&
            it.description?.contains("transactional context") == true
        }) {
            "Expected ERROR for NEVER called within transaction"
        }
    }

    fun testNever_NoErrorWhenCalledWithoutTransaction() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.transaction.annotation.Propagation;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                // ✅ No @Transactional
                public void sendEmail(User user) {
                    emailService.sendWelcomeEmail(user);  // Should be OK
                }
            }

            @Service
            class EmailService {
                @Transactional(propagation = Propagation.NEVER)
                public void sendWelcomeEmail(User user) {
                    // This runs without transaction - OK
                }
            }

            class User {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("NEVER") == true &&
            it.description?.contains("transactional context") == true
        }) {
            "Should NOT show error when NEVER is called without transaction"
        }
    }

    // ========== REQUIRES_NEW Tests ==========

    fun testRequiresNew_WarningWhenCalledWithinTransaction() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.transaction.annotation.Propagation;
            import org.springframework.stereotype.Service;

            @Service
            public class OrderService {
                @Transactional  // Has transaction
                public void createOrder(Order order) {
                    paymentService.processPayment(order);  // Should WARN
                }
            }

            @Service
            class PaymentService {
                @Transactional(propagation = Propagation.REQUIRES_NEW)
                public void processPayment(Order order) {
                    // Creates independent transaction
                }
            }

            class Order {}
        """.trimIndent()

        myFixture.configureByText("OrderService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any {
            it.description?.contains("REQUIRES_NEW") == true &&
            it.description?.contains("independent transaction") == true
        }) {
            "Expected WARNING for REQUIRES_NEW creating independent transaction"
        }
    }

    fun testRequiresNew_NoWarningWhenCalledWithoutTransaction() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.transaction.annotation.Propagation;
            import org.springframework.stereotype.Service;

            @Service
            public class OrderService {
                // ✅ No @Transactional
                public void processOrder(Order order) {
                    paymentService.processPayment(order);  // Should be OK
                }
            }

            @Service
            class PaymentService {
                @Transactional(propagation = Propagation.REQUIRES_NEW)
                public void processPayment(Order order) {
                    // No parent transaction, so no issue
                }
            }

            class Order {}
        """.trimIndent()

        myFixture.configureByText("OrderService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("REQUIRES_NEW") == true &&
            it.description?.contains("independent transaction") == true
        }) {
            "Should NOT show warning when REQUIRES_NEW is called without parent transaction"
        }
    }

    // ========== Cross-class vs Same-class ==========

    fun testCrossClass_MandatoryDetected() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.transaction.annotation.Propagation;
            import org.springframework.stereotype.Service;

            @Service
            public class ServiceA {
                public void method1() {
                    serviceB.method2();  // Cross-class call - should detect
                }
            }

            @Service
            class ServiceB {
                @Transactional(propagation = Propagation.MANDATORY)
                public void method2() {
                }
            }
        """.trimIndent()

        myFixture.configureByText("ServiceA.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any {
            it.description?.contains("MANDATORY") == true
        }) {
            "Should detect MANDATORY violation in cross-class call"
        }
    }

    fun testSameClass_MandatoryAlsoDetected() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.transaction.annotation.Propagation;
            import org.springframework.stereotype.Service;

            @Service
            public class ServiceA {
                public void method1() {
                    method2();  // Same-class call - should also detect
                }

                @Transactional(propagation = Propagation.MANDATORY)
                public void method2() {
                }
            }
        """.trimIndent()

        myFixture.configureByText("ServiceA.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any {
            it.description?.contains("MANDATORY") == true
        }) {
            "Should detect MANDATORY violation even in same-class call"
        }
    }

    // ========== No false positives ==========

    fun testNoWarning_ForRegularTransactional() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                @Transactional
                public void method1() {
                    method2();  // Regular @Transactional - no warning
                }

                @Transactional
                public void method2() {
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none {
            it.description?.contains("MANDATORY") == true ||
            it.description?.contains("NEVER") == true ||
            it.description?.contains("REQUIRES_NEW") == true
        }) {
            "Should NOT show propagation warnings for regular @Transactional"
        }
    }
}
