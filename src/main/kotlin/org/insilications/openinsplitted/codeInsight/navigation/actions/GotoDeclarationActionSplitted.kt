package org.insilications.openinsplitted.codeInsight.navigation.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.actionSystem.DataContext

// import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
class GotoDeclarationActionSplitted : GotoDeclarationAction() {
    protected override fun getHandler(): CodeInsightActionHandler {
//        val kk = super.getHandler() as GotoDeclarationOrUsageHandler2
//        val oo = kk::class.java::getDeclaredMethodOrNull("gotoDeclarationOrUsages")
//        GotoDeclarationOrUsageHandler2(null)
        // EditorWindow::class.declaredMemberProperties.find { it.name == "component" }?.get(editorWindow) as JComponent
        // EditorWindow::class.GotoDeclarationOrUsageHandler2.Companion.find { it.name == "gotoDeclarationOrUsages" }

    }

    protected override fun getHandler(dataContext: DataContext): CodeInsightActionHandler {
        return GotoDeclarationOrUsageHandler2Splitted(dataContext)
    }
}

//fun GotoDeclarationOrUsageHandler2.Companion.gotoDeclarationOrUsages(project: Project, editor: Editor, file: PsiFile, offset: Int): GTDUActionData? {
//    this.gotoDeclarationOrUsages()
//}