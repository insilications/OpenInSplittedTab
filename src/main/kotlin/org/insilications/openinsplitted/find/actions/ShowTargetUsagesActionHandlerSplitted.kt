package org.insilications.openinsplitted.find.actions

import com.intellij.find.actions.ShowUsagesAction
import com.intellij.find.actions.ShowUsagesParameters
import com.intellij.find.usages.api.SearchTarget
import com.intellij.openapi.project.Project
import com.intellij.psi.search.SearchScope
import org.jetbrains.annotations.ApiStatus

internal class ShowTargetUsagesActionHandlerSplitted {

    companion object {
        @ApiStatus.Experimental
        @JvmStatic
        fun showUsages(project: Project, searchScope: SearchScope, target: SearchTarget, parameters: ShowUsagesParameters) {
//            val showTargetUsagesActionHandler: ShowUsagesActionHandler
//            val showTargetUsagesActionHandler: ShowUsagesActionHandler = ShowTargetUsagesActionHandlerSplitted(
//                project,
//                target = target,
//                allOptions = getSearchOptions(SearchVariant.SHOW_USAGES, target, searchScope)
//            )
//            val kk = ShowTargetUsagesActionHandler(
//                project,
//                target = target,
//                allOptions = getSearchOptions(SearchVariant.SHOW_USAGES, target, searchScope)
//            )
            val kk = ShowUsagesAction.createActionHandler(project, searchScope, target)
//            ShowUsagesActionSplitted.showElementUsages(parameters, showTargetUsagesActionHandler)
        }
    }
}