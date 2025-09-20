// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.insilications.openinsplitted.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.find.FindUsagesSettings
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil.underModalProgress
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.DumbModeBlockedFunctionality
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiFile
import org.insilications.openinsplitted.codeInsight.navigation.actions.GotoDeclarationOnlyHandler2.Companion.gotoDeclaration
import org.insilications.openinsplitted.codeInsight.navigation.impl.GTDUActionData
import org.insilications.openinsplitted.codeInsight.navigation.impl.GTDUActionResult
import org.insilications.openinsplitted.codeInsight.navigation.impl.fromGTDProviders
import org.insilications.openinsplitted.codeInsight.navigation.impl.gotoDeclarationOrUsages
import org.insilications.openinsplitted.codeInsight.navigation.impl.toGTDUActionData
import org.insilications.openinsplitted.find.actions.ShowUsagesAction.showUsages
import org.insilications.openinsplitted.find.actions.TargetVariant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
class GotoDeclarationOrUsageHandler2(private val reporter: GotoDeclarationReporter?) : CodeInsightActionHandler {

    companion object {

        private fun gotoDeclarationOrUsages(project: Project, editor: Editor, file: PsiFile, offset: Int): GTDUActionData? {
            return fromGTDProviders(project, editor, offset)?.toGTDUActionData()
                ?: gotoDeclarationOrUsages(file, offset)
        }
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
            when (actionResult) {
                null -> {
                    reporter?.reportDeclarationSearchFinished(GotoDeclarationReporter.DeclarationsFound.NONE)
                    notifyNowhereToGo(project, editor, file, offset)
                }

                is GTDUActionResult.GTD -> {
                    GTDUCollector.recordPerformed(GTDUCollector.GTDUChoice.GTD)
                    gotoDeclaration(project, editor, actionResult.navigationActionResult, reporter)
                }

                is GTDUActionResult.SU -> {
                    reporter?.reportDeclarationSearchFinished(GotoDeclarationReporter.DeclarationsFound.NONE)
                    GTDUCollector.recordPerformed(GTDUCollector.GTDUChoice.SU)
                    showUsages(project, editor, file, actionResult.targetVariants)
                }
            }
        } catch (_: IndexNotReadyException) {
            DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
                CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update"),
                DumbModeBlockedFunctionality.GotoDeclarationOrUsage
            )
        }
    }

    private fun showUsages(project: Project, editor: Editor, file: PsiFile, searchTargets: List<TargetVariant>) {
        require(searchTargets.isNotEmpty())
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PSI_FILE, file)
            .add(CommonDataKeys.EDITOR, editor)
            .add(PlatformCoreDataKeys.CONTEXT_COMPONENT, editor.contentComponent)
            .build()
        try {
            showUsages(
                project,
                searchTargets,
                JBPopupFactory.getInstance().guessBestPopupLocation(editor),
                editor,
                FindUsagesOptions.findScopeByName(project, dataContext, FindUsagesSettings.getInstance().getDefaultScopeName())
            )
        } catch (_: IndexNotReadyException) {
            DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
                CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update"),
                DumbModeBlockedFunctionality.GotoDeclarationOrUsage
            )
        }
    }

    @TestOnly
    enum class GTDUOutcome {
        GTD,
        SU,
        ;
    }
}
