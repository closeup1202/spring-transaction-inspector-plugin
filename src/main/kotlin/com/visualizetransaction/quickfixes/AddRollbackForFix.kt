package com.visualizetransaction.quickfixes

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class AddRollbackForFix private constructor(
    annotation: PsiAnnotation,
    @FileModifier.SafeFieldForPreview
    private val exceptionTypes: List<String>
) : LocalQuickFixOnPsiElement(annotation) {

    override fun getFamilyName(): String {
        return if (exceptionTypes.size == 1) {
            "Add rollbackFor = ${exceptionTypes[0]}.class"
        } else {
            "Add rollbackFor = {${exceptionTypes.joinToString(", ") { "$it.class" }}}"
        }
    }

    override fun getText(): String = familyName

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val annotation = startElement as? PsiAnnotation ?: return
        val factory = JavaPsiFacade.getElementFactory(project)
        val annotationFqn = annotation.qualifiedName ?: return

        val existingRollbackFor = annotation.findDeclaredAttributeValue("rollbackFor")
        val rollbackForValue = if (existingRollbackFor != null) {
            mergeRollbackForValues(existingRollbackFor, exceptionTypes)
        } else {
            renderTypes(exceptionTypes)
        }

        val attributesText = annotation.parameterList.attributes
            .filter { it.name != "rollbackFor" }
            .joinToString(", ") { attr ->
                "${attr.name ?: "value"} = ${attr.value?.text ?: ""}"
            }

        val newAnnotationText = if (attributesText.isEmpty()) {
            "@$annotationFqn(rollbackFor = $rollbackForValue)"
        } else {
            "@$annotationFqn($attributesText, rollbackFor = $rollbackForValue)"
        }

        val newAnnotation = factory.createAnnotationFromText(newAnnotationText, annotation.context)
        annotation.replace(newAnnotation)
    }

    private fun renderTypes(types: List<String>): String =
        if (types.size == 1) "${types[0]}.class"
        else "{${types.joinToString(", ") { "$it.class" }}}"

    private fun mergeRollbackForValues(existingValue: PsiAnnotationMemberValue, newTypes: List<String>): String {
        val existingTypes = linkedSetOf<String>()
        when (existingValue) {
            is PsiArrayInitializerMemberValue -> existingValue.initializers.forEach {
                extractExceptionType(it)?.let(existingTypes::add)
            }
            else -> extractExceptionType(existingValue)?.let(existingTypes::add)
        }
        existingTypes.addAll(newTypes)
        return renderTypes(existingTypes.toList())
    }

    private fun extractExceptionType(value: PsiAnnotationMemberValue): String? {
        return value.text.replace(".class", "").trim().takeIf { it.isNotEmpty() }
    }

    companion object {
        fun create(annotation: PsiAnnotation, exceptionTypes: List<String>): AddRollbackForFix {
            return AddRollbackForFix(annotation, exceptionTypes)
        }
    }
}
