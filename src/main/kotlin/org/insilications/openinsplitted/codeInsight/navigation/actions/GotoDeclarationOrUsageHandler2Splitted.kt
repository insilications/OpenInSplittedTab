package org.insilications.openinsplitted.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
import com.intellij.codeInsight.navigation.impl.LazyTargetWithPresentation
import com.intellij.codeInsight.navigation.impl.NavigationActionResult
import com.intellij.codeInsight.navigation.impl.NavigationActionResult.MultipleTargets
import com.intellij.codeInsight.navigation.impl.NavigationActionResult.SingleTarget
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil.underModalProgress
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.DumbModeBlockedFunctionality
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.list.createTargetPopup
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

class GotoDeclarationOrUsageHandler2Splitted(private val reporter: DataContext?) : CodeInsightActionHandler {
    companion object {
        private val LOG: Logger = Logger.getInstance(GotoDeclarationOrUsageHandler2Splitted::class.java)
    }

    override fun startInWriteAction(): Boolean = false

    fun invokeCustomGTDU(project: Project, editor: Editor, file: PsiFile, offset: Int) {
        try {
            val actionResult = underModalProgress(
                project,
                CodeInsightBundle.message("progress.title.resolving.reference")
            ) {
                // 1) Resolve private companion method: gotoDeclarationOrUsages(Project, Editor, PsiFile, Int)
                val outerK = GotoDeclarationOrUsageHandler2::class
                val companionK = outerK.companionObject ?: return@underModalProgress null
                val companionInstance = outerK.companionObjectInstance ?: return@underModalProgress null

                val funGoto = companionK.declaredFunctions.firstOrNull {
                    it.name == "gotoDeclarationOrUsages" && it.parameters.size == 5 // receiver + 4 args
                } ?: return@underModalProgress null

                funGoto.isAccessible = true
                val actionData: Any = funGoto.call(companionInstance, project, editor, file, offset) ?: return@underModalProgress null

                // 2) actionData.result(): Any?
                val resultFun = actionData::class.declaredFunctions.firstOrNull { it.name == "result" && it.parameters.size == 1 }
                    ?: return@underModalProgress null
                resultFun.isAccessible = true
                val rawResult = resultFun.call(actionData) ?: return@underModalProgress null

                // 3) Distinguish GTD vs SU by probing getters
                val resultK = rawResult::class
                val navGetter = resultK.members.firstOrNull { it.name == "getNavigationActionResult" && it.parameters.size == 1 }
                val tvGetter = resultK.members.firstOrNull { it.name == "getTargetVariants" && it.parameters.size == 1 }

                when {
                    navGetter != null -> {
                        navGetter.isAccessible = true
                        val navigationActionResult = navGetter.call(rawResult)!! as NavigationActionResult
//                        // Delegate to platform GTD behavior (popup/selection/etc.) via public companion
                        gotoDeclarationOnly(project, editor, navigationActionResult)
                        "GTD"
                    }

                    tvGetter != null -> {
//                        tvGetter.isAccessible = true
//                        @Suppress("UNCHECKED_CAST")
//                        val variants = tvGetter.call(rawResult) as List<TargetVariant>
//                        if (variants.isEmpty()) return@underModalProgress "NONE"
//
//                        // You can customize Show Usages here; below mirrors stock popup call
//                        val dataContext = SimpleDataContext.builder()
//                            .add(CommonDataKeys.PSI_FILE, file)
//                            .add(CommonDataKeys.EDITOR, editor)
//                            .add(PlatformCoreDataKeys.CONTEXT_COMPONENT, editor.contentComponent)
//                            .build()
//                        ShowUsagesAction.showUsages(
//                            project,
//                            variants,
//                            JBPopupFactory.getInstance().guessBestPopupLocation(editor),
//                            editor,
//                            FindUsagesOptions.findScopeByName(project, dataContext, FindUsagesSettings.getInstance().defaultScopeName)
//                        )
                        LOG.info("Show usages invoked from GotoDeclarationOrUsageHandler2")
                        "SU"
                    }

                    else -> "NONE"
                }
            }

            if (actionResult == null || actionResult == "NONE") {
                // fall back to stock message
//                com.intellij.codeInsight.navigation.impl.notifyNowhereToGo(project, editor, file, offset)
                LOG.info("notifyNowhereToGo")
            }
        } catch (_: IndexNotReadyException) {
            DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
                CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update"),
                DumbModeBlockedFunctionality.GotoDeclarationOrUsage
            )
        }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (navigateToLookupItem(project)) {
            return
        }

        if (EditorUtil.isCaretInVirtualSpace(editor)) {
            return
        }

        val offset = editor.caretModel.offset
        invokeCustomGTDU(project, editor, file, offset)
    }
    
    private fun gotoDeclarationOnly(
        project: Project,
        editor: Editor,
        actionResult: NavigationActionResult,
//        reporter: GotoDeclarationReporter?
    ) {
        // obtain event data before showing the popup,
        // because showing the popup will finish the GotoDeclarationAction#actionPerformed and clear the data
//        val eventData: List<EventPair<*>> = GotoDeclarationAction.getCurrentEventData()
        when (actionResult) {
            is SingleTarget -> {
//                reporter?.reportDeclarationSearchFinished(GotoDeclarationReporter.DeclarationsFound.SINGLE)
//                actionResult.navigationProvider?.let {
//                    GTDUCollector.recordNavigated(eventData, it.javaClass)
//                }
                navigateRequestLazy(project, actionResult.requestor)
//                reporter?.reportNavigatedToDeclaration(GotoDeclarationReporter.NavigationType.AUTO, actionResult.navigationProvider)
            }

            is MultipleTargets -> {
//                reporter?.reportDeclarationSearchFinished(GotoDeclarationReporter.DeclarationsFound.MULTIPLE)
                val popup = createTargetPopup(
                    CodeInsightBundle.message("declaration.navigation.title"),
                    actionResult.targets, LazyTargetWithPresentation::presentation
                ) { (requestor, _, navigationProvider) ->
//                    navigationProvider?.let {
//                        GTDUCollector.recordNavigated(eventData, navigationProvider.javaClass)
//                    }
                    navigateRequestLazy(project, requestor)
//                    reporter?.reportNavigatedToDeclaration(GotoDeclarationReporter.NavigationType.FROM_POPUP, navigationProvider)
                }
                popup.showInBestPositionFor(editor)
//                reporter?.reportLookupElementsShown()
            }
        }
    }

//    private fun showUsages(project: Project, editor: Editor, file: PsiFile, searchTargets: List<TargetVariant>) {
//        require(searchTargets.isNotEmpty())
//        val dataContext = SimpleDataContext.builder()
//            .add(CommonDataKeys.PSI_FILE, file)
//            .add(CommonDataKeys.EDITOR, editor)
//            .add(PlatformCoreDataKeys.CONTEXT_COMPONENT, editor.contentComponent)
//            .build()
//        try {
//            showUsages(
//                project,
//                searchTargets,
//                JBPopupFactory.getInstance().guessBestPopupLocation(editor),
//                editor,
//                FindUsagesOptions.findScopeByName(project, dataContext, FindUsagesSettings.getInstance().getDefaultScopeName())
//            )
//        } catch (_: IndexNotReadyException) {
//            DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
//                CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update"),
//                DumbModeBlockedFunctionality.GotoDeclarationOrUsage
//            )
//        }
//    }
}
