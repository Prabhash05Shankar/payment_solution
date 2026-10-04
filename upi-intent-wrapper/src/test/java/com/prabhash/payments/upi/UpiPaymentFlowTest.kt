package com.prabhash.payments.upi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiPaymentFlowTest {
    private val validRequest = UpiPaymentRequest(
        payeeAddress = "merchant@upi",
        payeeName = "Example Merchant",
        amount = "10.00",
        transactionRef = "ORDER-123",
        transactionNote = "Order payment"
    )

    @Test fun noCompatibleApplicationDoesNotLaunch() {
        val decision = UpiLaunchPlanner.decide(validRequest, handlerCount = 0)
        assertEquals(UpiLaunchDecision.NoApplication, decision)
        val negative = UpiLaunchPlanner.decide(validRequest, handlerCount = -1)
        assertEquals(UpiLaunchDecision.NoApplication, negative)
    }

    @Test fun singleApplicationLaunchesDirectly() {
        val decision = UpiLaunchPlanner.decide(validRequest, handlerCount = 1) as UpiLaunchDecision.Ready
        assertFalse(decision.useChooser)
        assertTrue(decision.uri.startsWith("upi://pay?pa=merchant@upi"))
    }

    @Test fun severalApplicationsUseTheChooser() {
        val decision = UpiLaunchPlanner.decide(validRequest, handlerCount = 3) as UpiLaunchDecision.Ready
        assertTrue(decision.useChooser)
    }

    @Test fun invalidRequestIsNotLaunchedEvenWhenAnAppExists() {
        val request = validRequest.copy(amount = "0")
        val decision = UpiLaunchPlanner.decide(request, handlerCount = 2)
        val invalid = decision as UpiLaunchDecision.Invalid
        assertEquals("amount", invalid.errors.single().field)
    }

    @Test fun activityNotFoundIsAMissingAppError() {
        val failure = MissingActivityException("missing")
        assertNull(UpiLaunchErrors.classify(failure))
        val classified = UpiLaunchErrors.classify(
            failure,
            activityNotFoundClassName = failure.javaClass.name
        )
        assertEquals(UpiErrorCode.NO_UPI_APP, classified?.code)
        assertSame(failure, classified?.cause)
        assertTrue(classified!!.message!!.contains("No compatible UPI application"))
    }

    @Test fun launcherNotRegisteredIsALaunchFailure() {
        val classified = UpiLaunchErrors.classify(IllegalStateException("not registered"))
        assertEquals(UpiErrorCode.LAUNCH_FAILED, classified?.code)
        assertTrue(classified!!.message!!.contains("registerForActivityResult"))
    }

    @Test fun unexpectedLaunchErrorsAreNotSwallowed() {
        assertNull(UpiLaunchErrors.classify(RuntimeException("boom")))
    }

    @Test fun typedLaunchErrorIsPreserved() {
        val error = UpiPaymentException(UpiErrorCode.NO_UPI_APP, "No compatible UPI application is installed.")
        assertSame(error, UpiLaunchErrors.classify(error))
    }

    @Test fun missingActivityResultIsUnknown() {
        val response = UpiResponseParser.parse(resultCode = 0, rawResponse = null)
        assertEquals(UpiPaymentStatus.UNKNOWN, response.status)
        assertTrue(response.message!!.contains("not proof of cancellation"))
    }

    @Test fun unexpectedResultDataIsUnknown() {
        val response = UpiResponseParser.parse(-1, "###not-a-upi-response###")
        assertEquals(UpiPaymentStatus.UNKNOWN, response.status)
        assertEquals("###not-a-upi-response###", response.rawResponse)
        assertFalse(response.isAuthoritativelyVerified)
    }

}

private class MissingActivityException(message: String) : RuntimeException(message)
