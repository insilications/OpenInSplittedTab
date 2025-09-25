package org.insilications.openinsplitted.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.actionSystem.DataContext

/**
 * Opens the declaration/usage of the currently selected symbol in the next available splitted tab.
 * If a splitted tab is already open, it uses that tab to navigate to the target symbol. Otherwise, a new splitted tab is opened.
 * If there are multiple declarations/usages, a popup will appear, allowing you to select one.
 */
class GotoDeclarationActionSplitted : GotoDeclarationAction() {
    // Reuse a single handler instance to avoid per‑invocation allocations.
    private val sharedHandler: CodeInsightActionHandler by lazy(LazyThreadSafetyMode.PUBLICATION) {
        GotoDeclarationOrUsageHandler2Splitted()
    }

    protected override fun getHandler(): CodeInsightActionHandler = sharedHandler

    protected override fun getHandler(dataContext: DataContext): CodeInsightActionHandler = sharedHandler
}
