package org.insilications.openinsplitted.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.actionSystem.DataContext

class GotoDeclarationActionSplitted : GotoDeclarationAction() {
    protected override fun getHandler(): CodeInsightActionHandler {
        return GotoDeclarationOrUsageHandler2Splitted(null)

    }

    protected override fun getHandler(dataContext: DataContext): CodeInsightActionHandler {
        return GotoDeclarationOrUsageHandler2Splitted(null)
    }
}
