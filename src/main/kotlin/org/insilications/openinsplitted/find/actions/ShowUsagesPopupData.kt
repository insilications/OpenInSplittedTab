// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.insilications.openinsplitted.find.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.popup.AbstractPopup
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JTable

internal class ShowUsagesPopupData(
    @JvmField val parameters: ShowUsagesParameters, @JvmField val table: JTable,
    @JvmField val actionHandler: ShowUsagesActionHandler,
) {

    @JvmField
    val popupRef: AtomicReference<AbstractPopup> = AtomicReference<AbstractPopup>()

    @JvmField
    val pinGroup: DefaultActionGroup = DefaultActionGroup()

    @JvmField
    val header: ShowUsagesHeader = ShowUsagesHeader(
        createPinButton(parameters.project, popupRef, pinGroup, table, actionHandler::findUsages), actionHandler.presentation.searchTargetString
    )

    private fun createPinButton(
        project: Project,
        popupRef: AtomicReference<AbstractPopup>,
        pinGroup: DefaultActionGroup,
        table: JTable,
        findUsagesRunnable: Runnable,
    ): JComponent {
        val icon = ToolWindowManager.getInstance(project).getShowInFindToolWindowIcon()
        val pinAction: AnAction = object : AnAction(
            IdeBundle.messagePointer("show.in.find.window.button.name"), IdeBundle.messagePointer("show.in.find.window.button.pin.description"), icon
        ) {
            init {
                val action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES)
                shortcutSet = action.shortcutSet
            }

            override fun actionPerformed(e: AnActionEvent) {
                ShowUsagesAction.hideHints()
                ShowUsagesAction.cancel(popupRef.get())
                findUsagesRunnable.run()
            }
        }

        pinGroup.add(ActionManager.getInstance().getAction("ShowUsagesPinGroup"))
        pinGroup.add(pinAction)

        val pinToolbar = ShowUsagesAction.createActionToolbar(table, pinGroup)
        val result = pinToolbar.component
        result.border = null
        result.isOpaque = false
        return result
    }
}
