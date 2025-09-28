// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package org.insilications.openinsplitted.find.actions

import com.intellij.find.usages.api.SearchTarget
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.annotations.ApiStatus
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Proxy

private val LOG: Logger = Logger.getInstance("org.insilications.openinsplitted")

private const val PLATFORM_RESOLVER_CLASS = "com.intellij.find.actions.ResolverKt"
private const val PLATFORM_USAGE_VARIANT_HANDLER = "com.intellij.find.actions.UsageVariantHandler"

private val platformUsageVariantHandlerClass: Class<*>? by lazy(LazyThreadSafetyMode.PUBLICATION) {
    try {
        Class.forName(PLATFORM_USAGE_VARIANT_HANDLER)
    } catch (t: Throwable) {
        LOG.warn("Failed to resolve UsageVariantHandler class.", t)
        null
    }
}

private val platformFindShowUsagesInvoker: MethodHandle? by lazy(LazyThreadSafetyMode.PUBLICATION) {
    val handlerClass = platformUsageVariantHandlerClass ?: return@lazy null
    try {
        val resolverClass = Class.forName(PLATFORM_RESOLVER_CLASS)
        MethodHandles.publicLookup().findStatic(
            resolverClass,
            "findShowUsages",
            MethodType.methodType(
                Void.TYPE,
                Project::class.java,
                Editor::class.java,
                RelativePoint::class.java,
                List::class.java,
                String::class.java,
                handlerClass
            )
        )
    } catch (t: Throwable) {
        LOG.warn("Failed to resolve ResolverKt.findShowUsages via reflection.", t)
        null
    }
}

interface UsageVariantHandler {
    @ApiStatus.Internal
    fun handleTarget(target: SearchTarget)
    fun handlePsi(element: PsiElement)
}

fun findShowUsages(
    project: Project,
    editor: Editor?,
    popupPosition: RelativePoint,
    allTargets: List<Any>,
    @PopupTitle popupTitle: String,
    handler: UsageVariantHandler
) {
    val invoker = platformFindShowUsagesInvoker
    val handlerProxy = createPlatformHandlerProxy(handler)

    if (invoker == null || handlerProxy == null) {
        LOG.warn("Falling back – platform findShowUsages unavailable.")
        return
    }

    try {
        invoker.invokeWithArguments(project, editor, popupPosition, allTargets, popupTitle, handlerProxy)
    } catch (t: Throwable) {
        LOG.warn("Failed to delegate to platform findShowUsages.", t)
    }
}

private fun createPlatformHandlerProxy(handler: UsageVariantHandler): Any? {
    val handlerClass = platformUsageVariantHandlerClass ?: return null
    return Proxy.newProxyInstance(handlerClass.classLoader, arrayOf(handlerClass)) { proxy, method, args ->
        when (method.name) {
            "handleTarget" -> {
                val target = args?.getOrNull(0) as? SearchTarget ?: return@newProxyInstance null
                handler.handleTarget(target)
                null
            }

            "handlePsi" -> {
                val element = args?.getOrNull(0) as? PsiElement ?: return@newProxyInstance null
                handler.handlePsi(element)
                null
            }

            "toString" -> "UsageVariantHandlerProxy(${handler::class.java.name})"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.getOrNull(0)
            else -> method.defaultValue
        }
    }
}
