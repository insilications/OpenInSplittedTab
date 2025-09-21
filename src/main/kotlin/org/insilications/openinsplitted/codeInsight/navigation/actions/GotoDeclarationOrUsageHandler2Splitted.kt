package org.insilications.openinsplitted.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
import com.intellij.codeInsight.navigation.impl.LazyTargetWithPresentation
import com.intellij.codeInsight.navigation.impl.NavigationActionResult
import com.intellij.codeInsight.navigation.impl.NavigationActionResult.MultipleTargets
import com.intellij.codeInsight.navigation.impl.NavigationActionResult.SingleTarget
import com.intellij.find.FindUsagesSettings
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil.underModalProgress
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.DumbModeBlockedFunctionality
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiFile
import com.intellij.ui.list.createTargetPopup
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

/**
 * "Go To Declaration Or Usages" action result
 * Mirror of the internal `GTDUActionResult` from `src/com/intellij/codeInsight/navigation/impl/gtdu.kt` in the Intellij Platform API
 */
sealed class GTDUActionResultMirror {
    /**
     * Go To Declaration
     */

    @Internal
    class GTD(val navigationActionResult: NavigationActionResult) : GTDUActionResultMirror()

    /**
     * Show Usages
     */
    class SU(val targetVariants: List<*>) : GTDUActionResultMirror() {

        init {
            require(targetVariants.isNotEmpty())
        }
    }
}

class GotoDeclarationOrUsageHandler2Splitted() : CodeInsightActionHandler {
    companion object {
        private val LOG: Logger = Logger.getInstance(GotoDeclarationOrUsageHandler2Splitted::class.java)

        /**
         * Reflectively call `com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2.Companion.gotoDeclarationOrUsages`
         * and parse its result, returning a `GTDUActionResultMirror`. The code path behind `gotoDeclarationOrUsages` relies
         * on a web of internal/private functions and types, so reimplementing it is expensive. We are forced to use reflection.
         */
        private fun gotoDeclarationOrUsages_HACK(project: Project, editor: Editor, file: PsiFile, offset: Int): GTDUActionResultMirror? {
            val actionResult: GTDUActionResultMirror? = underModalProgress(
                project,
                CodeInsightBundle.message("progress.title.resolving.reference")
            ) {
                // 1) Resolve private companion method: gotoDeclarationOrUsages(Project, Editor, PsiFile, Int)
                val outerK: KClass<GotoDeclarationOrUsageHandler2> = GotoDeclarationOrUsageHandler2::class
                val companionK: KClass<*> = outerK.companionObject ?: return@underModalProgress null
                val companionInstance = outerK.companionObjectInstance ?: return@underModalProgress null

                val funGoto: KFunction<*> = companionK.declaredFunctions.firstOrNull {
                    it.name == "gotoDeclarationOrUsages" && it.parameters.size == 5 // receiver + 4 args
                } ?: return@underModalProgress null

                funGoto.isAccessible = true
                val actionData: Any = funGoto.call(companionInstance, project, editor, file, offset) ?: return@underModalProgress null

                // 2) actionData.result(): Any?
                val resultMethod: Method = actionData.javaClass.methods.firstOrNull { it.name == "result" && it.parameterCount == 0 }
                    ?: return@underModalProgress null
                resultMethod.isAccessible = true
                val rawResult = resultMethod.invoke(actionData) ?: return@underModalProgress null

                // 3) Distinguish GTD vs SU via Java-style getters
                val resultClass: Class<Any> = rawResult.javaClass

                val navMethod: Method? =
                    resultClass.methods.firstOrNull { it.name == "getNavigationActionResult" && it.parameterCount == 0 }
                val tvMethod: Method? = resultClass.methods.firstOrNull { it.name == "getTargetVariants" && it.parameterCount == 0 }

                when {
                    navMethod != null -> {
                        val navigationActionResult = navMethod.invoke(rawResult)!! as NavigationActionResult
                        GTDUActionResultMirror.GTD(navigationActionResult)
                    }

                    tvMethod != null -> {
                        val variants: List<*> =
                            tvMethod.invoke(rawResult) as? List<*> ?: return@underModalProgress null
                        if (variants.isEmpty()) return@underModalProgress null

                        GTDUActionResultMirror.SU(variants) // non-empty
                    }

                    else -> null
                }
            }

            return actionResult
        }
    }

    override fun startInWriteAction(): Boolean = false


    /**
     * This is where the main work of the "GotoDeclarationActionSplitted" action happens, via our current
     * `GotoDeclarationOrUsageHandler2Splitted` handler class.
     *
     * This function is eventually called by `GotoDeclarationAction` and its ancestors.
     * It tries to navigate to the declaration or show usages of the symbol at the caret position
     */
    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (navigateToLookupItem(project, editor)) {
            LOG.info("navigateToLookupItem")
            return
        }

        if (EditorUtil.isCaretInVirtualSpace(editor)) {
            LOG.info("EditorUtil.isCaretInVirtualSpace")
            return
        }

        val offset = editor.caretModel.offset
        try {
            when (val actionResult: GTDUActionResultMirror? = gotoDeclarationOrUsages_HACK(project, editor, file, offset)) {
                // The result of our action must be of type "Go To Declaration"
                is GTDUActionResultMirror.GTD -> {
                    LOG.info("GTDUActionResultMirror.GTD - gotoDeclarationOnly")
                    gotoDeclarationOnly(project, editor, actionResult.navigationActionResult)
                }

                // The result of our action must be of type "Show Usages"
                is GTDUActionResultMirror.SU -> {
                    LOG.info("GTDUActionResultMirror.SU - showUsages")
                    showUsages_HACK(project, editor, file, actionResult.targetVariants)
                }

                // No viable result. Nowhere to go.
                null -> {
                    LOG.info("notifyNowhereToGo")
                    notifyNowhereToGo(project, editor, file, offset)
                }
            }
        } catch (_: IndexNotReadyException) {
            DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
                CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update"),
                DumbModeBlockedFunctionality.GotoDeclarationOrUsage
            )
        }
    }

    private inline fun gotoDeclarationOnly(
        project: Project,
        editor: Editor,
        actionResult: NavigationActionResult,
    ) {
        when (actionResult) {
            is SingleTarget -> {
                // Just navigate to the single target
                navigateRequestLazy(project, actionResult.requestor, editor)
            }

            is MultipleTargets -> {
                val popup: JBPopup = createTargetPopup(
                    CodeInsightBundle.message("declaration.navigation.title"),
                    actionResult.targets, LazyTargetWithPresentation::presentation
                ) { (requestor, _, _) ->
                    // This is our processor. It is called when the user selects an item from the popup.
                    navigateRequestLazy(project, requestor, editor)
                }
                popup.showInBestPositionFor(editor)
            }
        }
    }

    /**
     * Reflectively call the `com.intellij.find.actions.ShowUsagesAction.showUsages` static method
     * We are forced to use reflection to avoid referencing the internal `com.intellij.find.actions.TargetVariant`
     */
    private inline fun showUsages_HACK(
        project: Project,
        editor: Editor,
        file: PsiFile,
        searchTargets: List<*>
    ) {
        require(searchTargets.isNotEmpty())
        // Build DataContext for scope resolution (public API)
        val dataContext: DataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PSI_FILE, file)
            .add(CommonDataKeys.EDITOR, editor)
            .add(PlatformCoreDataKeys.CONTEXT_COMPONENT, editor.contentComponent)
            .build()

        // Resolve `showUsages` from `com.intellij.find.actions.ShowUsagesAction`
        // showUsages(Project, List<? extends @NotNull TargetVariant>, RelativePoint, Editor, SearchScope)
        val showUsages = ShowUsagesAction::class.java.methods.firstOrNull {
            it.name == "showUsages" && it.parameterCount == 5
        }?.apply { isAccessible = true } ?: return

        // We use this to preemptively set the current window to the next splitted tab or a new splitted tab.
        // This forces `showUsages` to reuse that tab. This workaround might be fragile, but it works perfectly.
        receiveNextWindowPane(project, null)

        try {
            showUsages.invoke(
                null, project, searchTargets,
                JBPopupFactory.getInstance().guessBestPopupLocation(editor), editor,
                FindUsagesOptions.findScopeByName(project, dataContext, FindUsagesSettings.getInstance().defaultScopeName)
            )
        } catch (_: IndexNotReadyException) {
            DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
                CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update"),
                DumbModeBlockedFunctionality.GotoDeclarationOrUsage
            )
        }
    }
}


