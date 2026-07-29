package com.android.systemui.prodx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProdXIndicatorControllerTest {
    @Test
    fun emergencyStop_clearsOnlyAfterAuthorityAcknowledges() {
        var stopAllowed = false
        val controller = ProdXIndicatorController { stopAllowed }
        assertTrue(controller.operationStarted("operation"))
        assertFalse(controller.requestEmergencyStop())
        assertEquals(1, controller.activeOperationCount())

        stopAllowed = true
        assertTrue(controller.requestEmergencyStop())
        assertEquals(0, controller.activeOperationCount())
    }

    @Test
    fun duplicateOperation_doesNotInflateIndicator() {
        val controller = ProdXIndicatorController()
        assertTrue(controller.operationStarted("operation"))
        assertFalse(controller.operationStarted("operation"))
        assertEquals(1, controller.activeOperationCount())
    }

    @Test
    fun blankOperation_isRejected() {
        val controller = ProdXIndicatorController()
        assertFalse(controller.operationStarted(""))
        assertFalse(controller.hasActiveOperations())
    }

    @Test
    fun finishingUnknownOperation_doesNotAffectActiveOperation() {
        val controller = ProdXIndicatorController()
        assertTrue(controller.operationStarted("active"))
        assertFalse(controller.operationFinished("unknown"))
        assertEquals(1, controller.activeOperationCount())
        assertTrue(controller.operationFinished("active"))
        assertFalse(controller.hasActiveOperations())
    }

    @Test
    fun rejectedEmergencyStop_preservesAllActiveOperations() {
        val controller = ProdXIndicatorController { false }
        assertTrue(controller.operationStarted("one"))
        assertTrue(controller.operationStarted("two"))
        assertFalse(controller.requestEmergencyStop())
        assertEquals(2, controller.activeOperationCount())
    }
}
