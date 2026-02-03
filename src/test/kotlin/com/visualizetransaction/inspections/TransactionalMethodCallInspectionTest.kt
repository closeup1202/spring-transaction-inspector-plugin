package com.visualizetransaction.inspections
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Test suite for TransactionalMethodCallInspection
 *
 * Tests detection of same-class @Transactional method calls (AOP proxy bypass)
 */
class TransactionalMethodCallInspectionTest : BaseInspectionTest() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(TransactionalMethodCallInspection::class.java)
    }

    fun testDetectSameClassTransactionalCall() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                @Transactional
                public void outerMethod() {
                    innerMethod();  // ℹ️ Both methods have @Transactional, annotation is redundant but joins transaction
                }

                @Transactional
                public void innerMethod() {
                    // implementation
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        // Should detect with WEAK_WARNING (ℹ️) since caller has @Transactional and no special propagation
        assert(highlights.any {
            it.description?.contains("Same-class") == true &&
            it.description?.contains("@Transactional") == true &&
            it.description?.contains("redundant") == true
        }) {
            "Expected info-level warning for same-class @Transactional call not found. Found: ${highlights.map { it.description }}"
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
                    this.innerMethod();  // ℹ️ Both methods have @Transactional, annotation is redundant but joins transaction
                }

                @Transactional
                public void innerMethod() {
                    // implementation
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        // Should detect with WEAK_WARNING (ℹ️) since caller has @Transactional and no special propagation
        assert(highlights.any {
            it.description?.contains("Same-class") == true &&
            it.description?.contains("@Transactional") == true &&
            it.description?.contains("redundant") == true
        }) {
            "Expected info-level warning for same-class @Transactional call via 'this' not found. Found: ${highlights.map { it.description }}"
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
                        processUser(user);  // ℹ️ Both methods have @Transactional, annotation is redundant but joins transaction
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

        // Should detect with WEAK_WARNING (ℹ️) since caller has @Transactional and no special propagation
        assert(highlights.any {
            it.description?.contains("Same-class") == true &&
            it.description?.contains("@Transactional") == true &&
            it.description?.contains("redundant") == true
        }) {
            "Expected info-level warning for same-class @Transactional call in loop not found. Found: ${highlights.map { it.description }}"
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
                    innerMethod();  // ℹ️ Both methods have @Transactional (via class annotation), joins transaction
                }

                public void innerMethod() {
                    // implementation
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        // Should detect with WEAK_WARNING (ℹ️) since caller has @Transactional (class-level) and no special propagation
        assert(highlights.any {
            it.description?.contains("Same-class") == true &&
            it.description?.contains("@Transactional") == true &&
            it.description?.contains("redundant") == true
        }) {
            "Expected info-level warning for class-level @Transactional call not found. Found: ${highlights.map { it.description }}"
        }
    }

    fun testDetectSpecialPropagationCall() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.transaction.annotation.Propagation;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                @Transactional
                public void outerMethod() {
                    innerMethod();  // ⚠️ REQUIRES_NEW won't work in same-class call
                }

                @Transactional(propagation = Propagation.REQUIRES_NEW)
                public void innerMethod() {
                    // implementation
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        // Should detect with WARNING (⚠️) since called method has special propagation
        assert(highlights.any {
            it.description?.contains("Same-class") == true &&
            it.description?.contains("propagation=REQUIRES_NEW") == true &&
            it.description?.contains("will be ignored") == true
        }) {
            "Expected warning for special propagation in same-class call not found. Found: ${highlights.map { it.description }}"
        }
    }

    fun testDetectCallWithoutCallerTransactional() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                public void outerMethod() {
                    innerMethod();  // ⚠️ Caller has no @Transactional, annotation will be ignored
                }

                @Transactional
                public void innerMethod() {
                    // implementation
                }
            }
        """.trimIndent()

        myFixture.configureByText("UserService.java", code)
        val highlights = myFixture.doHighlighting()

        // Should detect with WARNING (⚠️) since caller has no @Transactional
        assert(highlights.any {
            it.description?.contains("Same-class") == true &&
            it.description?.contains("@Transactional") == true &&
            it.description?.contains("will be ignored") == true
        }) {
            "Expected warning for same-class call without caller @Transactional not found. Found: ${highlights.map { it.description }}"
        }
    }
}
