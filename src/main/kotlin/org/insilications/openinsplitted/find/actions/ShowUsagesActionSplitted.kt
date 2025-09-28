package org.insilications.openinsplitted.find.actions

import com.intellij.find.FindBundle
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.find.actions.ShowUsagesActionHandler
import com.intellij.find.actions.ShowUsagesParameters
import com.intellij.find.usages.api.SearchTarget
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.ui.awt.RelativePoint
import org.insilications.openinsplitted.debug
import org.jetbrains.annotations.ApiStatus
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

class ShowUsagesActionSplitted {
    companion object {
        private val LOG: Logger = Logger.getInstance("org.insilications.openinsplitted")

        private val createActionHandlerCachedInvoker: ((Project, SearchScope, SearchTarget) -> ShowUsagesActionHandler)?
                by lazy(LazyThreadSafetyMode.PUBLICATION) {
                    try {
                        // 1. Get the private method from the ShowUsagesAction class.
                        // We use `getDeclaredMethod` because the method is not public.
                        val method = ShowUsagesAction::class.java.getDeclaredMethod(
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
                        { project, searchScope, target ->
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

        @ApiStatus.Internal
        fun showUsages(
            project: Project,
            targetVariants: List<TargetVariant>,
            popupPosition: RelativePoint,
            editor: Editor,
            searchScope: SearchScope
        ) {
            LOG.debug { "ShowUsagesActionSplitted - showUsages" }
//            SlowOperations.startSection(SlowOperations.ACTION_PERFORM).use { ignored ->
            findShowUsages(
                project, editor, popupPosition, targetVariants, FindBundle.message("show.usages.ambiguous.title"),
                createVariantHandler(project, editor, popupPosition, searchScope)
            )
//            }
        }

        private fun createVariantHandler(
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

        fun startFindUsages(element: PsiElement, popupPosition: RelativePoint, editor: Editor) {
            LOG.debug { "ShowUsagesActionSplitted - startFindUsages" }
        }

        @ApiStatus.Experimental
        fun showElementUsages(project: Project, searchScope: SearchScope, target: SearchTarget, parameters: ShowUsagesParameters) {
            val createActionHandlerInvoker = createActionHandlerCachedInvoker ?: return
            val showTargetUsagesActionHandler = try {
                createActionHandlerInvoker(project, searchScope, target)
            } catch (t: Throwable) {
                LOG.warn("Failed to invoke gotoDeclarationOrUsages", t)
                return
            }
            LOG.debug { "ShowUsagesActionSplitted - showElementUsages" }
//            showElementUsagesWithResult(parameters, actionHandler)
        }
    }
}