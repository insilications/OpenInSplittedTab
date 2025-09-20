package org.insilications.openinsplitted.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.CodeInsightBundle
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
import org.insilications.openinsplitted.codeInsight.navigation.impl.GTDUActionData
import org.insilications.openinsplitted.codeInsight.navigation.impl.GTDUActionResult
import org.insilications.openinsplitted.codeInsight.navigation.impl.fromGTDProviders
import org.insilications.openinsplitted.codeInsight.navigation.impl.gotoDeclarationOrUsages
import org.insilications.openinsplitted.codeInsight.navigation.impl.toGTDUActionData

class GotoDeclarationOrUsageHandler2Splitted(private val reporter: DataContext?) : CodeInsightActionHandler {
    companion object {
        private val LOG: Logger = Logger.getInstance(GotoDeclarationOrUsageHandler2Splitted::class.java)

        private fun gotoDeclarationOrUsages(project: Project, editor: Editor, file: PsiFile, offset: Int): GTDUActionData? {
//            GotoDeclarationOrUsageHandler2.gotoDeclarationOrUsages()
            return fromGTDProviders(project, editor, offset)?.toGTDUActionData()
                ?: gotoDeclarationOrUsages(file, offset)
        }

//        @JvmStatic
//        fun getCtrlMouseData(editor: Editor, file: PsiFile, offset: Int): CtrlMouseData? {
//            return gotoDeclarationOrUsages(file.project, editor, file, offset)?.ctrlMouseData()
//        }

    }

    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (navigateToLookupItem(project)) {
            return
        }
        if (EditorUtil.isCaretInVirtualSpace(editor)) {
            return
        }

        val offset = editor.caretModel.offset
        try {
            val actionResult: GTDUActionResult? = underModalProgress(
                project,
                CodeInsightBundle.message("progress.title.resolving.reference")
            ) {
                gotoDeclarationOrUsages(project, editor, file, offset)?.result()
            }
            LOG.info("invoke")
            when (actionResult) {
                null -> {
//                    reporter?.reportDeclarationSearchFinished(GotoDeclarationReporter.DeclarationsFound.NONE)
                    LOG.info("notifyNowhereToGo")
                    notifyNowhereToGo(project, editor, file, offset)
                }

                is GTDUActionResult.GTD -> {
//                    GTDUCollector.recordPerformed(GTDUCollector.GTDUChoice.GTD)
//                    gotoDeclarationOnly(project, editor, actionResult.navigationActionResult, reporter)
                    LOG.info("gotoDeclarationOnly")
                    gotoDeclarationOnly(project, editor, actionResult.navigationActionResult)
                }

                is GTDUActionResult.SU -> {
//                    reporter?.reportDeclarationSearchFinished(GotoDeclarationReporter.DeclarationsFound.NONE)
//                    GTDUCollector.recordPerformed(GTDUCollector.GTDUChoice.SU)
//                    showUsages(project, editor, file, actionResult.targetVariants)
                    LOG.info("Show usages invoked from GotoDeclarationOrUsageHandler2")
                }
            }
        } catch (_: IndexNotReadyException) {
            DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
                CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update"),
                DumbModeBlockedFunctionality.GotoDeclarationOrUsage
            )
        }
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
