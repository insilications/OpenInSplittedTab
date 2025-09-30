package org.insilications.openinsplitted

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.ide.navigation.NavigationOptions
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
val navigationOptionsRequestFocus: NavigationOptions = NavigationOptions.requestFocus()

inline fun Logger.debug(lazyMessage: () -> String) {
    if (isDebugEnabled) {
        debug(lazyMessage())
    }
}
