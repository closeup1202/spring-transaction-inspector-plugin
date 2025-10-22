package com.visualizetransaction.utils

import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod

object PsiUtils {

    fun findFieldFromGetter(method: PsiMethod): PsiField? {
        val methodName = method.name

        val fieldName = when {
            methodName.startsWith("get") && methodName.length > 3 -> {
                methodName.substring(3).replaceFirstChar { it.lowercase() }
            }

            methodName.startsWith("is") && methodName.length > 2 -> {
                methodName.substring(2).replaceFirstChar { it.lowercase() }
            }

            else -> return null
        }

        return method.containingClass?.findFieldByName(fieldName, false)
    }
}
