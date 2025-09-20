package org.insilications.openinsplitted.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
import com.intellij.openapi.actionSystem.DataContext
import org.jetbrains.kotlin.idea.gradleTooling.getDeclaredMethodOrNull


class GotoDeclarationActionSplitted : GotoDeclarationAction() {
    protected override fun getHandler(): CodeInsightActionHandler {
        val kk = super.getHandler() as GotoDeclarationOrUsageHandler2
        val oo = kk::class.java::getDeclaredMethodOrNull("gotoDeclarationOrUsages")
    }

    protected override fun getHandler(dataContext: DataContext): CodeInsightActionHandler {
        return GotoDeclarationOrUsageHandler2Splitted(dataContext)
    }
}
