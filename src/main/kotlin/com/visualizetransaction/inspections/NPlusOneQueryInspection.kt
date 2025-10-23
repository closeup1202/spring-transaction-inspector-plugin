package com.visualizetransaction.inspections

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.visualizetransaction.settings.TransactionInspectorSettings
import com.visualizetransaction.utils.PsiUtils

class NPlusOneQueryInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {

            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)

                val settings = TransactionInspectorSettings.getInstance(holder.project).state
                if (!settings.enableN1Detection) {
                    return
                }

                if (!hasTransactionalAnnotation(method)) {
                    return
                }

                method.body?.let { body ->
                    checkForNPlusOne(body, holder, settings)
                }
            }
        }
    }

    private fun checkForNPlusOne(
        body: PsiCodeBlock, holder: ProblemsHolder,
        settings: TransactionInspectorSettings.State
    ) {
        // Check for lazy loading in loops and stream operations
        if (settings.checkInLoops) {
            checkLazyLoadingInLoops(body, holder, settings)
        }
    }

    private fun checkLazyLoadingInLoops(
        body: PsiCodeBlock,
        holder: ProblemsHolder,
        settings: TransactionInspectorSettings.State
    ) {
        if (settings.checkInLoops) {
            val foreachStatements = PsiTreeUtil.findChildrenOfType(body, PsiForeachStatement::class.java)

            for (loop in foreachStatements) {
                val loopBody = loop.body ?: continue

                val fieldAccesses = PsiTreeUtil.findChildrenOfType(loopBody, PsiReferenceExpression::class.java)

                for (access in fieldAccesses) {
                    if (isLazyCollection(access)) {
                        holder.registerProblem(
                            access as PsiElement,
                            "⚠️ Potential N+1 query: Accessing lazy collection inside loop. " +
                                    "Each iteration may trigger a separate query. Consider using @EntityGraph or fetch join.",
                            ProblemHighlightType.WARNING
                        )
                    }
                }
            }
        }

        if (settings.checkInStreamOperations) {
            val streamCalls = PsiTreeUtil.findChildrenOfType(body, PsiMethodCallExpression::class.java)

            for (call in streamCalls) {
                val methodName = call.methodExpression.referenceName

                // Detect: .map(), .flatMap(), .forEach()
                if (methodName in listOf("map", "flatMap", "forEach", "filter")) {
                    val lambda = call.argumentList.expressions.firstOrNull() as? PsiLambdaExpression
                    lambda?.let {
                        val lazyAccess = findLazyFieldAccess(it)
                        if (lazyAccess != null) {
                            val message = when (methodName) {
                                "map" -> "⚠️ Potential N+1 query: Lazy collection accessed in stream.map(). Consider using @EntityGraph or fetch join."
                                "flatMap" -> "⚠️ Potential N+1 query: Lazy collection accessed in stream.flatMap(). Consider using @EntityGraph or fetch join."
                                "forEach" -> "⚠️ Potential N+1 query: Lazy collection accessed in stream.forEach(). Consider using @EntityGraph or fetch join."
                                "filter" -> "⚠️ Potential N+1 query: Lazy collection accessed in stream.filter(). Consider using @EntityGraph or fetch join."
                                else -> "⚠️ Potential N+1 query: Lazy collection accessed in stream operation. Consider using @EntityGraph or fetch join."
                            }
                            holder.registerProblem(
                                lazyAccess as PsiElement,
                                message,
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

        if (resolved is PsiField) {
            return checkFieldAnnotations(resolved)
        }

        if (resolved is PsiMethod) {
            val field = PsiUtils.findFieldFromGetter(resolved)
            if (field != null) {
                return checkFieldAnnotations(field)
            }
        }

        return false
    }

    private fun checkFieldAnnotations(field: PsiField): Boolean {
        return field.annotations.any { annotation ->
            if (!PsiUtils.isLazyJpaRelationshipAnnotation(annotation)) {
                return@any false
            }

            // Check if fetch is explicitly set to LAZY (or default)
            val fetchValue = annotation.findAttributeValue("fetch")?.text
            fetchValue?.contains("LAZY") != false
        }
    }


    private fun hasTransactionalAnnotation(method: PsiMethod): Boolean {
        return PsiUtils.hasTransactionalAnnotation(method)
    }

    override fun getStaticDescription(): String {
        return """
            Detects potential N+1 query problems when lazy-loaded collections are accessed in loops or stream operations.
            <p>
            <b>Problem:</b>
            </p>
            <p>
            When you load entities with lazy-loaded relationships and then access those relationships in a loop or stream,
            each iteration triggers a separate database query. This is known as the N+1 query problem:
            1 query to fetch N entities + N queries to fetch their relationships = N+1 queries total.
            </p>
            <p>
            <b>Problem Example:</b>
            </p>
            <pre>
            @Transactional
            public List&lt;UserDTO&gt; getUsers() {
                List&lt;User&gt; users = userRepository.findAll();  // 1 query

                return users.stream()
                    .map(user -> new UserDTO(
                        user.getName(),
                        user.getPosts().size()  // ❌ N queries! (1 per user)
                    ))
                    .collect(Collectors.toList());
            }
            </pre>
            <p>
            If there are 100 users, this will execute 101 queries (1 for users + 100 for posts).
            </p>
            <p>
            <b>Solutions:</b>
            </p>
            <ol>
                <li><b>Use JOIN FETCH:</b> Fetch the relationship eagerly in a single query
                    <pre>
            @Query("SELECT u FROM User u LEFT JOIN FETCH u.posts")
            List&lt;User&gt; findAllWithPosts();  // ✅ Single query
                    </pre>
                </li>
                <li><b>Use @EntityGraph:</b> Define which relationships to fetch
                    <pre>
            @EntityGraph(attributePaths = {"posts"})
            List&lt;User&gt; findAll();  // ✅ Single query with entity graph
                    </pre>
                </li>
                <li><b>Use batch fetching:</b> Fetch relationships in batches
                    <pre>
            @BatchSize(size = 10)  // On the relationship field
            @OneToMany(fetch = FetchType.LAZY)
            private List&lt;Post&gt; posts;
                    </pre>
                </li>
            </ol>
            <p>
            <b>Detection Scope:</b>
            </p>
            <ul>
                <li>Lazy collections accessed in <code>for-each</code> loops</li>
                <li>Lazy collections accessed in stream operations (<code>.map()</code>, <code>.flatMap()</code>, <code>.forEach()</code>)</li>
                <li>Collections with <code>@OneToMany</code> or <code>@ManyToMany</code> annotations</li>
                <li>Default fetch type is LAZY for these annotations</li>
            </ul>
            <p>
            <b>Customization:</b>
            </p>
            <p>
            You can configure which patterns to check in Settings → Tools → Spring Transaction Inspector:
            </p>
            <ul>
                <li>Enable/disable N+1 query detection</li>
                <li>Check in stream operations (.map, .flatMap)</li>
                <li>Check in for-each loops</li>
            </ul>
        """.trimIndent()
    }
}