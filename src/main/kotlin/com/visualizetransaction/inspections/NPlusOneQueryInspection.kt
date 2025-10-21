package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.settings.TransactionVisualizerSettings

class NPlusOneQueryInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {

            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)

                val settings = TransactionVisualizerSettings.getInstance(holder.project).state
                if (!settings.enableN1Detection) {
                    return  // N+1 감지 비활성화되어 있으면 체크 안 함
                }

                // @Transactional이 있는 메서드만 검사
                if (!hasTransactionalAnnotation(method)) {
                    return
                }

                // 메서드 내부의 모든 표현식 검사
                method.body?.let { body ->
                    checkForNPlusOne(body, holder, settings)
                }
            }
        }
    }

    private fun checkForNPlusOne(
        body: PsiCodeBlock, holder: ProblemsHolder,
        settings: TransactionVisualizerSettings.State
    ) {
        val methodCalls = PsiTreeUtil.findChildrenOfType(body, PsiMethodCallExpression::class.java)

        for (call in methodCalls) {
            val method = call.resolveMethod() ?: continue

            if (isRepositoryQueryMethod(method)) {
                checkLazyLoadingAfterQuery(call, holder, settings)
            }
        }

        if (settings.checkInLoops) {  // 설정에 따라 체크!
            checkLazyLoadingInLoops(body, holder, settings)
        }
    }

    private fun checkLazyLoadingAfterQuery(
        queryCall: PsiMethodCallExpression,
        holder: ProblemsHolder,
        settings: TransactionVisualizerSettings.State
    ) {
        val parent = PsiTreeUtil.getParentOfType(queryCall, PsiStatement::class.java) ?: return
        val nextStatements = getFollowingStatements(parent)



        for (statement in nextStatements) {
            // stream().map() 패턴 찾기
            if (settings.checkInStreamOperations
                && statement.text.contains(".stream()")
                && statement.text.contains(".map(")) {
                val lambdas = PsiTreeUtil.findChildrenOfType(statement, PsiLambdaExpression::class.java)

                for (lambda in lambdas) {
                    val lazyAccess = findLazyFieldAccess(lambda)
                    if (lazyAccess != null) {
                        holder.registerProblem(
                            lazyAccess as PsiElement,
                            "⚠️ Potential N+1 query: Lazy-loaded collection accessed in stream. " +
                                    "Consider using fetch join or @EntityGraph.",
                            ProblemHighlightType.WARNING
                        )
                    }
                }
            }

            // for-each 루프 찾기
            if (settings.checkInLoops && statement is PsiForeachStatement) {
                val lazyAccess = findLazyFieldAccess(statement.body)
                if (lazyAccess != null) {
                    holder.registerProblem(
                        lazyAccess as PsiElement,
                        "⚠️ Potential N+1 query: Lazy-loaded collection accessed in loop. " +
                                "Consider using fetch join or @EntityGraph.",
                        ProblemHighlightType.WARNING
                    )
                }
            }
        }
    }

    private fun checkLazyLoadingInLoops(
        body: PsiCodeBlock,
        holder: ProblemsHolder,
        settings: TransactionVisualizerSettings.State
    ) {
        if (settings.checkInLoops) {
            // for-each 루프 찾기
            val foreachStatements = PsiTreeUtil.findChildrenOfType(body, PsiForeachStatement::class.java)

            for (loop in foreachStatements) {
                val loopBody = loop.body ?: continue

                // 루프 내부에서 컬렉션 필드 접근 찾기
                val fieldAccesses = PsiTreeUtil.findChildrenOfType(loopBody, PsiReferenceExpression::class.java)

                for (access in fieldAccesses) {
                    if (isLazyCollection(access)) {
                        holder.registerProblem(
                            access as PsiElement,
                            "⚠️ Potential N+1 query: Accessing lazy collection inside loop. " +
                                    "Each iteration may trigger a separate query.",
                            ProblemHighlightType.WARNING
                        )
                    }
                }
            }
        }

        if (settings.checkInStreamOperations) {
            // stream().map() 패턴 찾기
            val streamCalls = PsiTreeUtil.findChildrenOfType(body, PsiMethodCallExpression::class.java)

            for (call in streamCalls) {
                if (call.methodExpression.referenceName == "map") {
                    // map 안의 람다 표현식 검사
                    val lambda = call.argumentList.expressions.firstOrNull() as? PsiLambdaExpression
                    lambda?.let {
                        val lazyAccess = findLazyFieldAccess(it)
                        if (lazyAccess != null) {
                            holder.registerProblem(
                                lazyAccess as PsiElement,
                                "⚠️ Potential N+1 query: Lazy collection accessed in stream.map(). " +
                                        "Consider using fetch join.",
                                ProblemHighlightType.WARNING
                            )
                        }
                    }
                }
            }
        }
    }

    private fun findLazyFieldAccess(element: PsiElement?): PsiReferenceExpression? {
        if (element == null) return null

        val references = PsiTreeUtil.findChildrenOfType(element, PsiReferenceExpression::class.java)

        for (ref in references) {
            if (isLazyCollection(ref)) {
                return ref
            }
        }

        return null
    }

    private fun isLazyCollection(expression: PsiReferenceExpression): Boolean {
        val resolved = expression.resolve()

        // 1. 필드로 직접 resolve된 경우
        if (resolved is PsiField) {
            return checkFieldAnnotations(resolved)
        }

        // 2. getter 메서드로 resolve된 경우
        if (resolved is PsiMethod) {
            // getter 메서드에서 필드 찾기
            val field = findFieldFromGetter(resolved)
            if (field != null) {
                return checkFieldAnnotations(field)
            }
        }

        return false
    }

    private fun checkFieldAnnotations(field: PsiField): Boolean {
        val hasLazyRelation = field.annotations.any { annotation ->
            val qualifiedName = annotation.qualifiedName ?: return@any false

            when {
                qualifiedName == "javax.persistence.OneToMany" ||
                        qualifiedName == "jakarta.persistence.OneToMany" ||
                        qualifiedName == "javax.persistence.ManyToMany" ||
                        qualifiedName == "jakarta.persistence.ManyToMany" -> {
                    // fetch 타입 확인
                    val fetchValue = annotation.findAttributeValue("fetch")?.text
                    // LAZY가 명시되어 있거나, 명시 안 됐으면 기본값이 LAZY
                    fetchValue?.contains("LAZY") != false
                }

                else -> false
            }
        }

        return hasLazyRelation
    }

    private fun findFieldFromGetter(method: PsiMethod): PsiField? {
        val methodName = method.name

        // getter 패턴 확인 (getPosts, isSomething 등)
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

    private fun isRepositoryQueryMethod(method: PsiMethod): Boolean {
        val containingClass = method.containingClass ?: return false

        // Spring Data Repository 인터페이스 확인
        val isRepository = containingClass.interfaces.any {
            it.qualifiedName?.contains("Repository") == true
        }

        if (!isRepository) return false

        // findAll, findBy*, getBy* 등 쿼리 메서드
        val methodName = method.name
        return methodName.startsWith("findAll") ||
                methodName.startsWith("findBy") ||
                methodName.startsWith("getBy") ||
                methodName.startsWith("queryBy")
    }

    private fun hasTransactionalAnnotation(method: PsiMethod): Boolean {
        // 메서드 레벨 체크
        val methodHasIt = method.annotations.any {
            it.qualifiedName == "org.springframework.transaction.annotation.Transactional"
        }

        if (methodHasIt) return true

        // 클래스 레벨 체크
        return method.containingClass?.annotations?.any {
            it.qualifiedName == "org.springframework.transaction.annotation.Transactional"
        } ?: false
    }

    private fun getFollowingStatements(statement: PsiStatement): List<PsiStatement> {
        val parent = statement.parent as? PsiCodeBlock ?: return emptyList()
        val statements = parent.statements
        val index = statements.indexOf(statement)

        return if (index >= 0 && index < statements.size - 1) {
            statements.toList().subList(index + 1, statements.size)
        } else {
            emptyList()
        }
    }
}