package com.android.systemui.prodx

import java.util.concurrent.ConcurrentHashMap
import java.security.MessageDigest

/** Tracks synthetic operations for trusted status UI; it cannot execute an operation. */
class ProdXIndicatorController(private val emergencyStop: () -> Boolean = { false }) {
    private val active = ConcurrentHashMap.newKeySet<String>()

    fun operationStarted(operationId: String): Boolean =
        operationId.isNotBlank() && active.add(operationId)

    fun operationFinished(operationId: String): Boolean = active.remove(operationId)
    fun activeOperationCount(): Int = active.size
    fun hasActiveOperations(): Boolean = active.isNotEmpty()

    fun confirmationStarted(canonicalChallenge: ByteArray): String {
        val operationId = MessageDigest.getInstance("SHA-256")
            .digest(canonicalChallenge)
            .take(8)
            .joinToString("") { "%02x".format(it) }
        operationStarted(operationId)
        return operationId
    }

    fun requestEmergencyStop(): Boolean {
        val stopped = emergencyStop()
        if (stopped) active.clear()
        return stopped
    }
}
