package com.visualizetransaction.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.utils.PsiUtils

class ChangePropagationToRequiresNewFix : LocalQuickFix {

    override fun getFamilyName(): String = "Change propagation to REQUIRES_NEW"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val methodCall = PsiTreeUtil.getParentOfType(
            descriptor.psiElement,
            PsiMethodCallExpression::class.java
        ) ?: return

        val calledMethod = methodCall.resolveMethod() ?: return
        val transactional = calledMethod.annotations.firstOrNull {
            it.qualifiedName == PsiUtils.SPRING_TRANSACTIONAL
        } ?: return

        val factory = JavaPsiFacade.getElementFactory(project)
        val annotationFqn = transactional.qualifiedName ?: return

        val attributesText = transactional.parameterList.attributes
            .filter { it.name != "propagation" }
            .joinToString(", ") { attr ->
                "${attr.name ?: "value"} = ${attr.value?.text ?: ""}"
            }

        val newAnnotationText = if (attributesText.isEmpty()) {
            "@$annotationFqn(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)"
        } else {
            "@$annotationFqn($attributesText, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)"
        }

        val newAnnotation = factory.createAnnotationFromText(newAnnotationText, transactional.context)
        transactional.replace(newAnnotation)
    }
}
