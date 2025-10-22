package com.visualizetransaction.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethodCallExpression

class ChangePropagationToRequiresNewFix : LocalQuickFix {

    override fun getFamilyName(): String = "Change propagation to REQUIRES_NEW"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val methodCall = descriptor.psiElement.parent as? PsiMethodCallExpression ?: return
        val calledMethod = methodCall.resolveMethod() ?: return

        val transactional = calledMethod.annotations.firstOrNull {
            it.qualifiedName == "org.springframework.transaction.annotation.Transactional"
        } ?: return

        val factory = JavaPsiFacade.getElementFactory(project)

        val existingAttributes = transactional.parameterList.attributes
            .filter { it.name != "propagation" }
            .joinToString(", ") { attr ->
                val name = attr.name ?: "value"
                val value = attr.value?.text ?: ""
                "$name = $value"
            }

        val newAnnotationText = if (existingAttributes.isEmpty()) {
            "@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)"
        } else {
            "@org.springframework.transaction.annotation.Transactional($existingAttributes, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)"
        }

        val newAnnotation = factory.createAnnotationFromText(newAnnotationText, transactional.context)
        transactional.replace(newAnnotation)
    }
}
