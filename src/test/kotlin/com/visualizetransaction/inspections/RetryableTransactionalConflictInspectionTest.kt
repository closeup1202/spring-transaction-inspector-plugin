package com.visualizetransaction.inspections

import com.visualizetransaction.settings.TransactionInspectorSettings

class RetryableTransactionalConflictInspectionTest : BaseInspectionTest() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(RetryableTransactionalConflictInspection::class.java)
        TransactionInspectorSettings.getInstance(project).state.enableRetryableTransactionalDetection = true
    }

    fun testDetectRetryableWithTransactionalOnSameMethod() {
        val code = """
            import org.springframework.retry.annotation.Retryable;
            import org.springframework.transaction.annotation.Transactional;

            class PaymentService {
                @Retryable
                @Transactional
                public void charge() {
                    // retry re-runs a fresh transaction -> double charge
                }
            }
        """.trimIndent()

        myFixture.configureByText("PaymentService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any {
            it.description?.contains("@Retryable") == true &&
                    it.description?.contains("@Transactional") == true
        }) {
            "Expected warning for @Retryable + @Transactional. Found: ${highlights.map { it.description }}"
        }
    }

    fun testDetectRetryableWithClassLevelTransactional() {
        val code = """
            import org.springframework.retry.annotation.Retryable;
            import org.springframework.transaction.annotation.Transactional;

            @Transactional
            class PaymentService {
                @Retryable
                public void charge() {
                }
            }
        """.trimIndent()

        myFixture.configureByText("PaymentService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any {
            it.description?.contains("@Retryable") == true
        }) {
            "Expected warning for @Retryable with class-level @Transactional. Found: ${highlights.map { it.description }}"
        }
    }

    fun testNoWarningForRetryableAlone() {
        val code = """
            import org.springframework.retry.annotation.Retryable;

            class PaymentService {
                @Retryable
                public void charge() {
                }
            }
        """.trimIndent()

        myFixture.configureByText("PaymentService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none { it.description?.contains("@Retryable") == true }) {
            "Should not warn for @Retryable without @Transactional"
        }
    }

    fun testNoWarningForTransactionalAlone() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;

            class PaymentService {
                @Transactional
                public void charge() {
                }
            }
        """.trimIndent()

        myFixture.configureByText("PaymentService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none { it.description?.contains("@Retryable") == true }) {
            "Should not warn for @Transactional alone"
        }
    }
}
