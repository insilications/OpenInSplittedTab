package org.insilications.openinsplitted

import com.intellij.openapi.diagnostic.Logger

inline fun Logger.debug(lazyMessage: () -> String) {
    if (isDebugEnabled) {
        debug(lazyMessage())
    }
}
