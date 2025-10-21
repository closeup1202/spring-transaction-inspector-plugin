package com.visualizetransaction.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Test suite for TransactionalMethodCallInspection
 *
 * Tests detection of same-class @Transactional method calls (AOP proxy bypass)
 */
class TransactionalMethodCallInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(TransactionalMethodCallInspection::class.java)
    }

    override fun getTestDataPath(): String {
        return "src/test/resources"
    }

    fun testDetectSameClassTransactionalCall() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                @Transactional
                public void outerMethod() {
                    innerMethod();  // ❌ Calling @Transactional method in same class - AOP proxy bypassed
                }

                @Transactional
                public void innerMethod() {
                    // implementation
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("Same-class") == true && it.description?.contains("@Transactional") == true }) {
            "Expected warning for same-class @Transactional call not found"
        }
    }

    fun testDetectSameClassTransactionalCallViaThis() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                @Transactional
                public void outerMethod() {
                    this.innerMethod();  // ❌ Explicit this reference - AOP proxy bypassed
                }

                @Transactional
                public void innerMethod() {
                    // implementation
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("Same-class") == true && it.description?.contains("@Transactional") == true }) {
            "Expected warning for same-class @Transactional call via 'this' not found"
        }
    }

    fun testDetectNestedTransactionalCallInLoop() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;
            import java.util.List;

            @Service
            public class UserService {
                @Transactional
                public void batchProcessUsers(List<User> users) {
                    for (User user : users) {
                        processUser(user);  // ❌ Same-class @Transactional call in loop
                    }
                }

                @Transactional
                public void processUser(User user) {
                    // implementation
                }
            }

            class User {}
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("Same-class") == true && it.description?.contains("@Transactional") == true }) {
            "Expected warning for same-class @Transactional call in loop not found"
        }
    }

    fun testNoWarningForDifferentClassTransactionalCall() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                private AnotherService anotherService;

                @Transactional
                public void outerMethod() {
                    anotherService.innerMethod();  // ✓ No warning - different class
                }
            }

            @Service
            class AnotherService {
                @Transactional
                public void innerMethod() {
                    // implementation
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none { it.description?.contains("Same-class") == true && it.description?.contains("@Transactional") == true }) {
            "Unexpected warning for different-class @Transactional call"
        }
    }

    fun testNoWarningForNonTransactionalMethodCall() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                @Transactional
                public void outerMethod() {
                    helperMethod();  // ✓ No warning - called method not @Transactional
                }

                public void helperMethod() {
                    // implementation
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none { it.description?.contains("Same-class") == true && it.description?.contains("@Transactional") == true }) {
            "Unexpected warning for non-transactional method call"
        }
    }

    fun testDetectClassLevelTransactionalCall() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            @Transactional  // Class-level annotation
            public class UserService {
                public void outerMethod() {
                    innerMethod();  // ❌ Both methods have @Transactional (via class annotation)
                }

                public void innerMethod() {
                    // implementation
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("Same-class") == true && it.description?.contains("@Transactional") == true }) {
            "Expected warning for class-level @Transactional call not found"
        }
    }
}
