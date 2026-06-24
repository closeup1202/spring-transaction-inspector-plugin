package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.settings.TransactionInspectorSettings
import com.visualizetransaction.utils.PsiUtils

/**
 * Detects blocking external I/O performed inside a @Transactional method.
 *
 * A @Transactional method holds its database connection for the ENTIRE method body, not just while
 * queries run. Calling an HTTP API, sending an email, performing file I/O or sleeping inside the
 * transaction keeps the connection checked out for the whole (slow, network-bound) duration. Under load
 * the connection pool drains and the application stalls.
 *
 * Keep transactions short: do the external work before/after the transaction, not inside it.
 */
class ExternalCallInTransactionInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val settings = TransactionInspectorSettings.getInstance(holder.project).state
        if (!settings.enableExternalCallDetection) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)

                // Only methods that declare @Transactional themselves. Class-level @Transactional is
                // intentionally excluded to avoid flagging unrelated methods of a transactional service.
                method.annotations.firstOrNull { PsiUtils.isTransactionalAnnotation(it) } ?: return

                val body = method.body ?: return

                PsiTreeUtil.findChildrenOfType(body, PsiMethodCallExpression::class.java).forEach { call ->
                    // Ignore calls nested in a different method scope (lambda / anonymous class).
                    if (PsiTreeUtil.getParentOfType(call, PsiMethod::class.java) != method) {
                        return@forEach
                    }

                    val resource = describeExternalCall(call) ?: return@forEach

                    holder.registerProblem(
                        call.methodExpression as PsiElement,
                        "⚠️ $resource inside a @Transactional method. The DB connection is held for the entire " +
                                "method, so slow external work can exhaust the connection pool under load. " +
                                "Move it outside the transaction boundary.",
                        ProblemHighlightType.WARNING
                    )
                }
            }
        }
    }

    private fun describeExternalCall(call: PsiMethodCallExpression): String? {
        val method = call.resolveMethod() ?: return null
        val containingClass = method.containingClass ?: return null
        val qualifiedName = containingClass.qualifiedName

        // Blocking sleep.
        if (qualifiedName == "java.lang.Thread" && method.name == "sleep") {
            return "a blocking Thread.sleep()"
        }

        qualifiedName?.let { EXTERNAL_TYPES[it] }?.let { return it }

        // Feign clients are user-defined interfaces marked with @FeignClient.
        if (hasFeignClientAnnotation(containingClass)) {
            return "an external HTTP call (Feign client)"
        }

        return null
    }

    private fun hasFeignClientAnnotation(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any {
            it.qualifiedName == "org.springframework.cloud.openfeign.FeignClient"
        }
    }

    companion object {
        private val EXTERNAL_TYPES = mapOf(
            "org.springframework.web.client.RestTemplate" to "an external HTTP call (RestTemplate)",
            "org.springframework.web.client.RestOperations" to "an external HTTP call (RestTemplate)",
            "org.springframework.web.reactive.function.client.WebClient" to "an external HTTP call (WebClient)",
            "org.springframework.web.client.RestClient" to "an external HTTP call (RestClient)",
            "java.net.http.HttpClient" to "an external HTTP call (HttpClient)",
            "okhttp3.OkHttpClient" to "an external HTTP call (OkHttp)",
            "okhttp3.Call" to "an external HTTP call (OkHttp)",
            "org.apache.http.client.HttpClient" to "an external HTTP call (Apache HttpClient)",
            "org.springframework.mail.MailSender" to "an email send (MailSender)",
            "org.springframework.mail.javamail.JavaMailSender" to "an email send (JavaMailSender)",
            "java.nio.file.Files" to "file I/O (java.nio.file.Files)"
        )
    }

    override fun getStaticDescription(): String {
        return """
            Detects blocking external I/O (HTTP calls, email, file I/O, Thread.sleep) inside a
            @Transactional method.
            <p>
            A transactional method holds its database connection for the whole method body. Performing slow,
            network-bound work inside the transaction keeps the connection checked out far longer than needed
            and can exhaust the connection pool under load, freezing the application.
            </p>
            <p>Keep transactions short — move external calls outside the transaction boundary.</p>
            <p><b>Scope:</b> only methods annotated with @Transactional directly are checked; class-level
            @Transactional is intentionally not considered, to avoid noise on unrelated methods.</p>
        """.trimIndent()
    }
}
