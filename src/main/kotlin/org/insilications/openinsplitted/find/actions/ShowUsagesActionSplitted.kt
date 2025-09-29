package org.insilications.openinsplitted.find.actions

import com.intellij.find.actions.ShowUsagesAction
import com.intellij.find.actions.ShowUsagesActionHandler
import com.intellij.find.usages.api.SearchTarget
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.ui.awt.RelativePoint
import org.insilications.openinsplitted.debug
import org.insilications.openinsplitted.find.actions.ShowUsagesAction.startFindUsages
import org.jetbrains.annotations.ApiStatus
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method

class ShowUsagesActionSplitted {
    companion object {
        private val LOG: Logger = Logger.getInstance("org.insilications.openinsplitted")
//        private const val PLATFORM_SHOW_USAGES_TABLE_CELL_RENDERER = "com.intellij.find.actions.ShowUsagesTableCellRenderer"


//        private val showUsagesTableCellRendererCachedConstructor: ((Predicate<in Usage>, AtomicInteger, SearchScope) -> TableCellRenderer)? by lazy(
//            LazyThreadSafetyMode.PUBLICATION
//        ) {
//            try {
//                val klass: Class<*> = Class.forName(PLATFORM_SHOW_USAGES_TABLE_CELL_RENDERER)
//                val constructorMethod = klass.getConstructor(Predicate::class.java, AtomicInteger::class.java, SearchScope::class.java).apply {
//                    isAccessible = true
//                }
//
//                val constructorHandle: MethodHandle = MethodHandles.lookup().unreflectConstructor(constructorMethod).asType(
//                    MethodType.methodType(
//                        klass,
//                        Predicate::class.java,
//                        AtomicInteger::class.java,
//                        SearchScope::class.java
//                    )
//                );
//
//                { originUsageCheck: Predicate<in Usage>, outOfScopeUsages: AtomicInteger, searchScope: SearchScope ->
//                    constructorHandle.invokeWithArguments(originUsageCheck, outOfScopeUsages, searchScope) as TableCellRenderer
//                }
//            } catch (t: Throwable) {
//                LOG.warn("Failed to resolve ShowUsagesTableCellRenderer(Predicate<? super Usage>, AtomicInteger, SearchScope)  class.", t)
//                null
//            }
//        }

        private val createShowTargetUsagesActionHandlerCachedInvoker: ((Project, SearchScope, SearchTarget) -> ShowUsagesActionHandler)?
                by lazy(LazyThreadSafetyMode.PUBLICATION) {
                    try {
                        // 1. Get the private method from the ShowUsagesAction class.
                        // We use `getDeclaredMethod` because the method is not public.
                        val method: Method = ShowUsagesAction::class.java.getDeclaredMethod(
                            "createActionHandler",
                            Project::class.java,
                            SearchScope::class.java,
                            SearchTarget::class.java
                        ).apply {
                            // 2. Make it accessible. This is crucial for private members.
                            isAccessible = true
                        }

                        // 2. Create a `MethodHandle` with a specific type using `.asType(...)`.
                        // This provides a runtime guarantee and acts as an assertion during initialization.
                        // Additionally, the method is static, we do not need to bind it to an instance.
                        // By creating an adapted handle with a specific type, you provide more information
                        // to the JVM's JIT compiler, which can lead to better performance optimizations
                        // compared to a completely generic handle.
                        val handle: MethodHandle = MethodHandles.lookup().unreflect(method).asType(
                            MethodType.methodType(
                                ShowUsagesActionHandler::class.java,
                                Project::class.java,
                                SearchScope::class.java,
                                SearchTarget::class.java
                            )
                        );

                        // 3. Return a strongly-typed lambda that wraps the handle.
                        // The result is cast to the known return type for type safety.
                        { project: Project, searchScope: SearchScope, target: SearchTarget ->
                            // Call the handle. Because `invokeWithArguments` is statically typed to
                            // return `Object`, we still need a cast to satisfy the Kotlin compiler.
                            // This cast is safe because of the `.asType(...)` guarantee above.
                            handle.invokeWithArguments(project, searchScope, target) as ShowUsagesActionHandler
                        }
                    } catch (t: Throwable) {
                        LOG.warn("Failed to resolve ShowUsagesAction.createActionHandler via reflection.", t)
                        // If reflection fails for any reason, the invoker will be null.
                        null
                    }
                }

//        private val createActionHandlerCachedInvoker: ((FindUsagesHandlerBase, FindUsagesOptions, String) -> ShowUsagesActionHandler)?
//                by lazy(LazyThreadSafetyMode.PUBLICATION) {
//                    try {
//                        // 1. Get the private method from the ShowUsagesAction class.
//                        // We use `getDeclaredMethod` because the method is not public.
//                        val method = ShowUsagesAction::class.java.getDeclaredMethod(
//                            "createActionHandler",
//                            FindUsagesHandlerBase::class.java,
//                            FindUsagesOptions::class.java,
//                            String::class.java
//                        ).apply {
//                            // 2. Make it accessible. This is crucial for private members.
//                            isAccessible = true
//                        }
//
//                        // 2. Create a `MethodHandle` with a specific type using `.asType(...)`.
//                        // This provides a runtime guarantee and acts as an assertion during initialization.
//                        // Additionally, the method is static, we do not need to bind it to an instance.
//                        // By creating an adapted handle with a specific type, you provide more information
//                        // to the JVM's JIT compiler, which can lead to better performance optimizations
//                        // compared to a completely generic handle.
//                        val handle: MethodHandle = MethodHandles.lookup().unreflect(method).asType(
//                            MethodType.methodType(
//                                ShowUsagesActionHandler::class.java,
//                                FindUsagesHandlerBase::class.java,
//                                FindUsagesOptions::class.java,
//                                String::class.java
//                            )
//                        );
//
//                        // 3. Return a strongly-typed lambda that wraps the handle.
//                        // The result is cast to the known return type for type safety.
//                        { handler, options, title ->
//                            // Call the handle. Because `invokeWithArguments` is statically typed to
//                            // return `Object`, we still need a cast to satisfy the Kotlin compiler.
//                            // This cast is safe because of the `.asType(...)` guarantee above.
//                            handle.invokeWithArguments(handler, options, title) as ShowUsagesActionHandler
//                        }
//                    } catch (t: Throwable) {
//                        LOG.warn("Failed to resolve ShowUsagesAction.createActionHandler(FindUsagesHandlerBase, FindUsagesOptions, String) via reflection.", t)
//                        // If reflection fails for any reason, the invoker will be null.
//                        null
//                    }
//                }

//        @ApiStatus.Internal
//        fun showUsages(
//            project: Project,
//            targetVariants: List<Any>,
//            popupPosition: RelativePoint,
//            editor: Editor,
//            searchScope: SearchScope
//        ) {
//            LOG.debug { "ShowUsagesActionSplitted - showUsages" }
//            findShowUsages(
//                project, editor, popupPosition, targetVariants, FindBundle.message("show.usages.ambiguous.title"),
//                createVariantHandler(project, editor, popupPosition, searchScope)
//            )
//        }

        fun createVariantHandler(
            project: Project,
            editor: Editor,
            popupPosition: RelativePoint,
            searchScope: SearchScope
        ): UsageVariantHandler {
            return object : UsageVariantHandler {
                override fun handleTarget(target: SearchTarget) {
                    showElementUsages(
                        project, searchScope, target,
                        ShowUsagesParameters.initial(project, editor, popupPosition)
                    )
                }

                override fun handlePsi(element: PsiElement) {
                    startFindUsages(element, popupPosition, editor)
                }
            }
        }

        @ApiStatus.Experimental
        fun showElementUsages(project: Project, searchScope: SearchScope, target: SearchTarget, parameters: ShowUsagesParameters) {
            val createShowTargetUsagesActionHandlerInvoker: (Project, SearchScope, SearchTarget) -> ShowUsagesActionHandler =
                createShowTargetUsagesActionHandlerCachedInvoker ?: return
            val showTargetUsagesActionHandler: ShowUsagesActionHandler = try {
                createShowTargetUsagesActionHandlerInvoker(project, searchScope, target)
            } catch (t: Throwable) {
                LOG.warn("Failed to invoke gotoDeclarationOrUsages", t)
                return
            }
            LOG.debug { "ShowUsagesActionSplitted - showElementUsages" }
            // TODO: Implement my custom `showElementUsagesWithResult` functionality here.
            // showElementUsagesWithResult(parameters, actionHandler)
        }
    }
}
