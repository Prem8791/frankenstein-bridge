package com.android.systemui.prodx

import android.app.prodx.IProdXConfirmationCallback
import android.app.prodx.ProdXManager
import java.util.concurrent.Executor

/** Binder bridge only. Rendering/authentication remains behind the trusted Renderer interface. */
class ProdXConfirmationBridge(
    private val manager: ProdXManager,
    private val mainExecutor: Executor,
    private val renderer: Renderer,
    private val indicator: ProdXIndicatorController = ProdXIndicatorController(),
    private val stateChanged: () -> Unit = {},
) {
    interface Renderer {
        fun show(canonicalChallenge: ByteArray, completion: (approved: Boolean, proof: ByteArray?) -> Unit)
        fun dismiss()
    }

    private var pending: ByteArray? = null
    private var pendingOperationId: String? = null
    private val callback = object : IProdXConfirmationCallback.Stub() {
        override fun onChallengeReady(challenge: ByteArray?) {
            val bounded = challenge?.takeIf { it.isNotEmpty() && it.size <= 64 * 1024 }?.copyOf()
            if (bounded == null) return
            mainExecutor.execute {
                if (pending != null) {
                    manager.cancelConfirmation(bounded)
                    return@execute
                }
                pending = bounded
                pendingOperationId = indicator.confirmationStarted(bounded)
                stateChanged()
                renderer.show(bounded.copyOf()) { approved, proof ->
                    val current = pending ?: return@show
                    pending = null
                    finishIndicator()
                    if (proof == null || proof.size != 32) {
                        manager.cancelConfirmation(current)
                    } else {
                        manager.submitConfirmationResult(current, proof, approved)
                    }
                }
            }
        }

        override fun onProofVerified(success: Boolean) {
            mainExecutor.execute { renderer.dismiss() }
        }

        override fun onCancelled() {
            mainExecutor.execute {
                pending = null
                finishIndicator()
                renderer.dismiss()
            }
        }
    }

    fun start(): Boolean {
        val registered = manager.registerConfirmationCallback(callback)
        if (!registered) securityCancel(notifyAuthority = false)
        return registered
    }

    fun securityCancel(notifyAuthority: Boolean = true) {
        val current = pending
        pending = null
        finishIndicator()
        renderer.dismiss()
        if (notifyAuthority && current != null) manager.cancelConfirmation(current)
    }

    fun stop() {
        securityCancel()
        manager.unregisterConfirmationCallback(callback)
    }

    private fun finishIndicator() {
        pendingOperationId?.let(indicator::operationFinished)
        pendingOperationId = null
        stateChanged()
    }
}
