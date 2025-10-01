package org.insilications.openinsplitted.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.navigation.actions.GotoImplementationAction

/**
 * Opens the implementation of the currently selected symbol in the next available splitted tab.
 * If a splitted tab is already open, it uses that tab to navigate to the target symbol. Otherwise, a new splitted tab is opened.
 * If there are multiple implementations, a popup will appear, allowing you to select one.
 */
class GotoImplementationActionSplitted : GotoImplementationAction() {
    // Reuse a single handler instance to avoid per‑invocation allocations.
    private val gotoImplementationHandlerSplittedShared: CodeInsightActionHandler by lazy(LazyThreadSafetyMode.PUBLICATION) {
        GotoImplementationHandlerSplitted()
    }

    protected override fun getHandler(): CodeInsightActionHandler = gotoImplementationHandlerSplittedShared
}