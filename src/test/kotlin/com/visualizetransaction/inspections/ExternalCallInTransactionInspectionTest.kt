package com.visualizetransaction.inspections

import com.visualizetransaction.settings.TransactionInspectorSettings

class ExternalCallInTransactionInspectionTest : BaseInspectionTest() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(ExternalCallInTransactionInspection::class.java)
        TransactionInspectorSettings.getInstance(project).state.enableExternalCallDetection = true
        addExternalStubs()
    }

    private fun addExternalStubs() {
        myFixture.addFileToProject("org/springframework/web/client/RestTemplate.java", """
            package org.springframework.web.client;
            public class RestTemplate {
                public <T> T getForObject(String url, Class<T> type) { return null; }
            }
        """.trimIndent())

        myFixture.addFileToProject("org/springframework/mail/javamail/JavaMailSender.java", """
            package org.springframework.mail.javamail;
            public interface JavaMailSender {
                void send(Object message);
            }
        """.trimIndent())
    }

    fun testDetectRestTemplateCall() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.web.client.RestTemplate;

            class OrderService {
                private final RestTemplate restTemplate = new RestTemplate();

                @Transactional
                public void process() {
                    String r = restTemplate.getForObject("http://api", String.class);
                }
            }
        """.trimIndent()

        myFixture.configureByText("OrderService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any {
            it.description?.contains("RestTemplate") == true &&
                    it.description?.contains("@Transactional") == true
        }) {
            "Expected warning for RestTemplate call in transaction. Found: ${highlights.map { it.description }}"
        }
    }

    fun testDetectMailSendCall() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;
            import org.springframework.mail.javamail.JavaMailSender;

            class OrderService {
                private final JavaMailSender mailSender;

                OrderService(JavaMailSender mailSender) { this.mailSender = mailSender; }

                @Transactional
                public void process() {
                    mailSender.send(new Object());
                }
            }
        """.trimIndent()

        myFixture.configureByText("OrderService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("email send") == true }) {
            "Expected warning for mail send in transaction. Found: ${highlights.map { it.description }}"
        }
    }

    fun testDetectThreadSleep() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;

            class OrderService {
                @Transactional
                public void process() throws InterruptedException {
                    Thread.sleep(1000);
                }
            }
        """.trimIndent()

        myFixture.configureByText("OrderService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.any { it.description?.contains("Thread.sleep") == true }) {
            "Expected warning for Thread.sleep in transaction. Found: ${highlights.map { it.description }}"
        }
    }

    fun testNoWarningOutsideTransaction() {
        val code = """
            import org.springframework.web.client.RestTemplate;

            class OrderService {
                private final RestTemplate restTemplate = new RestTemplate();

                public void process() {
                    String r = restTemplate.getForObject("http://api", String.class);
                }
            }
        """.trimIndent()

        myFixture.configureByText("OrderService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none { it.description?.contains("RestTemplate") == true }) {
            "Should not warn for external call outside a transaction"
        }
    }

    fun testNoWarningForPlainDbWork() {
        val code = """
            import org.springframework.transaction.annotation.Transactional;

            class OrderService {
                @Transactional
                public void process() {
                    int x = compute();
                }

                int compute() { return 1; }
            }
        """.trimIndent()

        myFixture.configureByText("OrderService.java", code)
        val highlights = myFixture.doHighlighting()

        assert(highlights.none { it.description?.contains("inside a @Transactional method") == true }) {
            "Should not warn for ordinary in-process method calls"
        }
    }
}
