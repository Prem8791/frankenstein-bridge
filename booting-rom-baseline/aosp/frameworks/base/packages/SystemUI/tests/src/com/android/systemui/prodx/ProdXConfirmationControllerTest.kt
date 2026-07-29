package com.android.systemui.prodx

import com.android.systemui.prodx.ProdXConfirmationController.Choice
import com.android.systemui.prodx.ProdXConfirmationController.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProdXConfirmationControllerTest {
    private var now = 1_000L
    private var user = 10
    private val controller = ProdXConfirmationController({ now }, { user })

    @Test
    fun exactChallenge_canCompleteOnlyOnce() {
        assertTrue(controller.present(challenge()))
        assertNotNull(controller.complete(Choice.ALLOW, byteArrayOf(7), false))
        assertNull(controller.complete(Choice.ALLOW, byteArrayOf(7), false))
        assertEquals(State.COMPLETED, controller.state)
    }

    @Test
    fun obscuredTouch_cancelsWithoutProof() {
        assertTrue(controller.present(challenge()))
        assertNull(controller.complete(Choice.ALLOW, byteArrayOf(7), true))
        assertEquals(State.CANCELLED, controller.state)
    }

    @Test
    fun expiryAndUserSwitch_failClosed() {
        assertTrue(controller.present(challenge()))
        now = 2_001L
        assertNull(controller.complete(Choice.ALLOW, byteArrayOf(7), false))

        val another = ProdXConfirmationController({ 1_000L }, { 11 })
        assertFalse(another.present(challenge()))
    }

    @Test
    fun screenOff_cancelsPendingChallenge() {
        assertTrue(controller.present(challenge()))
        controller.onScreenOff()
        assertEquals(State.CANCELLED, controller.state)
        assertNull(controller.complete(Choice.DENY, byteArrayOf(1), false))
    }

    @Test
    fun explicitCancel_isFinalAndProducesNoProof() {
        assertTrue(controller.present(challenge()))
        controller.cancel()
        assertEquals(State.CANCELLED, controller.state)
        assertNull(controller.complete(Choice.ALLOW, byteArrayOf(7), false))
    }

    @Test
    fun authorityDeath_cancelsPendingChallenge() {
        assertTrue(controller.present(challenge()))
        controller.onAuthorityDied()
        assertEquals(State.CANCELLED, controller.state)
        assertNull(controller.complete(Choice.ALLOW, byteArrayOf(7), false))
    }

    @Test
    fun userChangeWhilePending_cancelsPendingChallenge() {
        assertTrue(controller.present(challenge()))
        user = 11
        controller.onUserChanged()
        assertEquals(State.CANCELLED, controller.state)
        assertNull(controller.complete(Choice.ALLOW, byteArrayOf(7), false))
    }

    @Test
    fun emptyAuthenticationReference_failsClosed() {
        assertTrue(controller.present(challenge()))
        assertNull(controller.complete(Choice.ALLOW, byteArrayOf(), false))
        assertEquals(State.CANCELLED, controller.state)
    }

    @Test
    fun malformedOrOutOfWindowChallenge_isRejected() {
        assertFalse(controller.present(challenge(id = "")))
        assertFalse(controller.present(challenge(binding = byteArrayOf())))
        assertFalse(controller.present(challenge(expiresAtMillis = now)))
        assertFalse(controller.present(challenge(expiresAtMillis = now + 30_001L)))
        assertEquals(State.IDLE, controller.state)
    }

    @Test
    fun concurrentPresentation_isRejectedWithoutReplacingPendingChallenge() {
        assertTrue(controller.present(challenge(id = "first")))
        assertFalse(controller.present(challenge(id = "second")))
        val result = controller.complete(Choice.DENY, byteArrayOf(9), false)
        assertEquals("first", result?.challengeId)
    }

    @Test
    fun proofBindsChoiceAuthenticationAndChallengeBytes() {
        val allow = completedProof(challenge(binding = byteArrayOf(1, 2, 3)), Choice.ALLOW, byteArrayOf(7))
        val deny = completedProof(challenge(binding = byteArrayOf(1, 2, 3)), Choice.DENY, byteArrayOf(7))
        val otherAuth = completedProof(challenge(binding = byteArrayOf(1, 2, 3)), Choice.ALLOW, byteArrayOf(8))
        val otherBinding = completedProof(challenge(binding = byteArrayOf(3, 2, 1)), Choice.ALLOW, byteArrayOf(7))

        assertFalse(allow.contentEquals(deny))
        assertFalse(allow.contentEquals(otherAuth))
        assertFalse(allow.contentEquals(otherBinding))
    }

    private fun completedProof(
        challenge: ProdXConfirmationController.Challenge,
        choice: Choice,
        authentication: ByteArray,
    ): ByteArray {
        val isolated = ProdXConfirmationController({ now }, { user })
        assertTrue(isolated.present(challenge))
        return requireNotNull(isolated.complete(choice, authentication, false)).proof
    }

    private fun challenge(
        id: String = "challenge-1",
        expiresAtMillis: Long = 2_000L,
        binding: ByteArray = byteArrayOf(1, 2, 3),
    ) = ProdXConfirmationController.Challenge(
        id = id,
        capability = "prodx.test.noop.echo",
        provider = "prodx.test",
        requester = "android",
        purpose = "synthetic test",
        consequence = "no external effect",
        risk = 1,
        userId = 10,
        expiresAtMillis = expiresAtMillis,
        binding = binding,
        untrustedModelContext = "untrusted explanation",
    )
}
