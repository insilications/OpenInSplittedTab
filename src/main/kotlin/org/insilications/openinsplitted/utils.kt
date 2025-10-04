@file:Suppress("NOTHING_TO_INLINE")

package org.insilications.openinsplitted

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger

val LOG: Logger = Logger.getInstance("org.insilications.openinsplitted")

inline fun Logger.debug(lazyMessage: () -> String) {
    if (isDebugEnabled) {
        debug(lazyMessage())
    }
}

inline fun printCurrentThreadContext(context: String) {
    val app: Application = ApplicationManager.getApplication()
    val currentThread: Thread = Thread.currentThread()

    // Checks if the read access is currently allowed
    // `true` if the read access is currently allowed, `false` otherwise
    val isReadAccessAllowed: Boolean = app.isReadAccessAllowed

    // Checks if the write access is currently allowed
    // `true` if the write access is currently allowed, `false` otherwise
    val isWriteAccessAllowed: Boolean = app.isWriteAccessAllowed

    // Checks if the current thread has IW lock acquired, which grants read access and the ability to run write actions
    // `true` if the current thread has IW lock acquired, `false` otherwise
    val isWriteIntentLockAcquired: Boolean = app.isWriteIntentLockAcquired

    // Checks if the current thread is the event dispatch thread and has IW lock acquired
    // `true` if the current thread is the Swing dispatch thread with IW lock, `false` otherwise
    val isEDTandWriteIntentLocked: Boolean = app.isDispatchThread

    LOG.debug {
        "$context - isReadAccessAllowed=$isReadAccessAllowed - isWriteAccessAllowed=$isWriteAccessAllowed - " +
                "isWriteIntentLockAcquired=$isWriteIntentLockAcquired - isEDTandWriteIntentLocked=$isEDTandWriteIntentLocked - " +
                "thread=${currentThread.name}"
    }
}

inline fun printCurrentThreadContext() {
    val app: Application = ApplicationManager.getApplication()
    val currentThread: Thread = Thread.currentThread()

    // Checks if the read access is currently allowed
    // `true` if the read access is currently allowed, `false` otherwise
    val isReadAccessAllowed: Boolean = app.isReadAccessAllowed

    // Checks if the write access is currently allowed
    // `true` if the write access is currently allowed, `false` otherwise
    val isWriteAccessAllowed: Boolean = app.isWriteAccessAllowed

    // Checks if the current thread has IW lock acquired, which grants read access and the ability to run write actions
    // `true` if the current thread has IW lock acquired, `false` otherwise
    val isWriteIntentLockAcquired: Boolean = app.isWriteIntentLockAcquired

    // Checks if the current thread is the event dispatch thread and has IW lock acquired
    // `true` if the current thread is the Swing dispatch thread with IW lock, `false` otherwise
    val isEDTandWriteIntentLocked: Boolean = app.isDispatchThread

    val stackTrace = currentThread.stackTrace
    // The element at index 2 typically refers to the caller of this utility function.
    // Index 0 is Thread.getStackTrace(), Index 1 is logCurrentLocation() itself.
    if (stackTrace.size > 2) {
        val caller = stackTrace[2]
        LOG.debug {
            "${caller.methodName}:${caller.lineNumber} - isReadAccessAllowed=$isReadAccessAllowed - isWriteAccessAllowed=$isWriteAccessAllowed - " +
                    "isWriteIntentLockAcquired=$isWriteIntentLockAcquired - isEDTandWriteIntentLocked=$isEDTandWriteIntentLocked - " +
                    "thread=${currentThread.name}"
        }
    } else {
        LOG.debug {
            "UNKNOWN - isReadAccessAllowed=$isReadAccessAllowed - isWriteAccessAllowed=$isWriteAccessAllowed - " +
                    "isWriteIntentLockAcquired=$isWriteIntentLockAcquired - isEDTandWriteIntentLocked=$isEDTandWriteIntentLocked - " +
                    "thread=${currentThread.name}"
        }
    }
}


