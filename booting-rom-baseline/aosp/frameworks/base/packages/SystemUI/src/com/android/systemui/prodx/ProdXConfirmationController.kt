package com.android.systemui.prodx

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** Security state machine behind the future trusted confirmation surface. */
class ProdXConfirmationController(
    private val clock: () -> Long = System::currentTimeMillis,
    private val currentUser: () -> Int,
    private val timeoutMillis: Long = 30_000L,
) {
    enum class Choice { ALLOW, DENY }
    enum class State { IDLE, PENDING, COMPLETED, CANCELLED }

    data class Challenge(
        val id: String,
        val capability: String,
        val provider: String,
        val requester: String,
        val purpose: String,
        val consequence: String,
        val risk: Int,
        val userId: Int,
        val expiresAtMillis: Long,
        val binding: ByteArray,
        val untrustedModelContext: String? = null,
    )

    data class Result(val challengeId: String, val choice: Choice, val proof: ByteArray)

    private var pending: Challenge? = null
    var state: State = State.IDLE
        private set

    fun present(challenge: Challenge): Boolean {
        if (state == State.PENDING || challenge.id.isBlank() || challenge.binding.isEmpty()) return false
        val now = clock()
        if (challenge.userId != currentUser() || challenge.expiresAtMillis <= now ||
            challenge.expiresAtMillis - now > timeoutMillis) return false
        pending = challenge
        state = State.PENDING
        return true
    }

    fun complete(choice: Choice, authResultReference: ByteArray, obscuredTouch: Boolean): Result? {
        val challenge = pending ?: return null
        if (obscuredTouch || authResultReference.isEmpty() || !isStillValid(challenge)) {
            cancel()
            return null
        }
        val proof = hash(canonicalResult(challenge, choice, authResultReference))
        pending = null
        state = State.COMPLETED
        return Result(challenge.id, choice, proof)
    }

    fun onScreenOff() = cancel()
    fun onAuthorityDied() = cancel()
    fun onUserChanged() = cancel()

    fun cancel() {
        if (state == State.PENDING) state = State.CANCELLED
        pending = null
    }

    private fun isStillValid(challenge: Challenge): Boolean =
        challenge.userId == currentUser() && clock() < challenge.expiresAtMillis

    private fun canonicalResult(
        challenge: Challenge,
        choice: Choice,
        authResultReference: ByteArray,
    ): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeUTF(challenge.id)
            output.writeUTF(challenge.capability)
            output.writeUTF(challenge.provider)
            output.writeUTF(challenge.requester)
            output.writeUTF(challenge.purpose)
            output.writeUTF(challenge.consequence)
            output.writeInt(challenge.risk)
            output.writeInt(challenge.userId)
            output.writeLong(challenge.expiresAtMillis)
            output.writeInt(challenge.binding.size)
            output.write(challenge.binding)
            output.writeUTF(choice.name)
            output.writeInt(authResultReference.size)
            output.write(authResultReference)
        }
        bytes.toByteArray()
    }

    private fun hash(value: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value)
}
