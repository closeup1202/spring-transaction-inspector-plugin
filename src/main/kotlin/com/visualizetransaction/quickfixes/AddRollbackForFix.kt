package com.visualizetransaction.quickfixes

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.*

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

        val rollbackForValue = if (exceptionTypes.size == 1) {
            "${exceptionTypes[0]}.class"
        } else {
            "{${exceptionTypes.joinToString(", ") { "$it.class" }}}"
        }

        val existingRollbackFor = annotation.findDeclaredAttributeValue("rollbackFor")
        val newAnnotationText = if (existingRollbackFor != null) {
            val mergedValue = mergeRollbackForValues(existingRollbackFor, exceptionTypes)
            val existingAttributes = annotation.parameterList.attributes
                .filter { it.name != "rollbackFor" }
                .joinToString(", ") { attr ->
                    "${attr.name ?: "value"} = ${attr.value?.text ?: ""}"
                }

            if (existingAttributes.isEmpty()) {
                "@org.springframework.transaction.annotation.Transactional(rollbackFor = $mergedValue)"
            } else {
                "@org.springframework.transaction.annotation.Transactional($existingAttributes, rollbackFor = $mergedValue)"
            }
        } else if (annotation.parameterList.attributes.isEmpty()) {
            "@org.springframework.transaction.annotation.Transactional(rollbackFor = $rollbackForValue)"
        } else {
            val existingAttributes = annotation.parameterList.attributes
                .joinToString(", ") { attr ->
                    "${attr.name ?: "value"} = ${attr.value?.text ?: ""}"
                }
            "@org.springframework.transaction.annotation.Transactional($existingAttributes, rollbackFor = $rollbackForValue)"
        }

        val newAnnotation = factory.createAnnotationFromText(newAnnotationText, annotation.context)
        annotation.replace(newAnnotation)
    }

    private fun mergeRollbackForValues(existingValue: PsiAnnotationMemberValue, newTypes: List<String>): String {
        val existingTypes = mutableSetOf<String>()

        when (existingValue) {
            is PsiArrayInitializerMemberValue -> existingValue.initializers.forEach {
                extractExceptionType(it)?.let(existingTypes::add)
            }

            else -> extractExceptionType(existingValue)?.let(existingTypes::add)
        }

        existingTypes.addAll(newTypes)

        return if (existingTypes.size == 1) {
            "${existingTypes.first()}.class"
        } else {
            "{${existingTypes.joinToString(", ") { "$it.class" }}}"
        }
    }

    private fun extractExceptionType(value: PsiAnnotationMemberValue): String? {
        return value.text.replace(".class", "").trim()
    }

    companion object {
        fun create(annotation: PsiAnnotation, exceptionTypes: List<String>): AddRollbackForFix {
            return AddRollbackForFix(annotation, exceptionTypes)
        }
    }
}
