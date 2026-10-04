package com.prabhash.payments.upi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiAttemptLifecycleTest {
    @Test fun openAttemptRejectsASecondStart() {
        val dispatcher = dispatcher()
        val first = start(dispatcher, "ORDER-1")
        dispatcher.markWaiting(first.id)
        val rejected = dispatcher.tryStart("ORDER-2") as UpiAttemptStart.Rejected

        assertEquals(first.id, rejected.active.id)
        assertEquals(UpiAttemptState.WAITING_FOR_RESULT, dispatcher.state)
        assertEquals("ORDER-1", dispatcher.attempt?.transactionRef)
    }

    @Test fun launchingAttemptAlsoRejectsASecondStart() {
        val dispatcher = dispatcher()
        val first = start(dispatcher, "ORDER-1")
        val rejected = dispatcher.tryStart("ORDER-2") as UpiAttemptStart.Rejected
        assertEquals(UpiAttemptState.LAUNCHING, rejected.active.state)
        assertEquals(first.id, dispatcher.attempt?.id)
    }

    @Test fun duplicateCallbackDoesNotReplaceTheBoundResult() {
        val dispatcher = waiting("ORDER-1")
        val seen = mutableListOf<UpiPaymentStatus>()
        val first = UpiResponse(UpiPaymentStatus.UNKNOWN, transactionRef = "ORDER-1")
        val duplicate = UpiResponse(UpiPaymentStatus.SUCCESS, transactionRef = "ORDER-1")

        assertTrue(dispatcher.dispatch(first) { seen += it.clientResponse!!.status })
        assertFalse(dispatcher.dispatch(duplicate) { seen += it.clientResponse!!.status })

        assertEquals(listOf(UpiPaymentStatus.UNKNOWN), seen)
        assertEquals(UpiPaymentStatus.UNKNOWN, dispatcher.attempt?.clientResponse?.status)
        assertEquals(UpiAttemptState.RESULT_RECEIVED, dispatcher.state)
    }

    @Test fun callbackThatThrowsStillConsumesTheAttempt() {
        val dispatcher = waiting("ORDER-1")
        var calls = 0
        try {
            dispatcher.dispatch(UpiResponse(UpiPaymentStatus.PENDING, transactionRef = "ORDER-1")) {
                calls += 1
                throw IllegalStateException("ui failed")
            }
        } catch (error: IllegalStateException) {
            assertEquals("ui failed", error.message)
        }
        assertFalse(dispatcher.dispatch(UpiResponse(UpiPaymentStatus.SUCCESS, transactionRef = "ORDER-1")) { calls += 1 })
        assertEquals(1, calls)
        assertEquals(UpiAttemptState.RESULT_RECEIVED, dispatcher.state)
    }

    @Test fun cancellationPayloadIsDeliveredOnceAndIsNotSettlement() {
        val dispatcher = waiting("ORDER-1")
        var delivered: UpiPaymentAttempt? = null
        val response = UpiResponseParser.parse(0, "status=cancelled&txnRef=ORDER-1")

        assertTrue(dispatcher.dispatch(response) { delivered = it })
        assertFalse(dispatcher.dispatch(response) { delivered = it })

        assertEquals(UpiPaymentStatus.CANCELLED_OR_UNKNOWN, delivered?.clientResponse?.status)
        assertFalse(delivered!!.clientResponse!!.isAuthoritativelyVerified)
        assertEquals(UpiAttemptState.RESULT_RECEIVED, dispatcher.state)
    }

    @Test fun missingCallbackIsInconclusiveRatherThanFailed() {
        val dispatcher = waiting("ORDER-1")
        val attempt = dispatcher.markInconclusive(dispatcher.attempt!!.id)

        assertEquals(UpiAttemptState.RESULT_UNKNOWN, attempt?.state)
        assertNull(attempt?.clientResponse)
        assertEquals(UpiAttemptState.RESULT_UNKNOWN, dispatcher.state)
        assertNull(dispatcher.markInconclusive("someone-else"))
    }

    @Test fun lateResultAfterInconclusiveCanStillBindToTheSameAttempt() {
        val dispatcher = waiting("ORDER-1")
        dispatcher.markInconclusive(dispatcher.attempt!!.id)
        val binding = dispatcher.onActivityResult(UpiResponse(UpiPaymentStatus.PENDING))

        val accepted = binding as UpiResultBinding.Accepted
        assertEquals("a1", accepted.attempt.id)
        assertEquals(UpiAttemptState.RESULT_RECEIVED, accepted.attempt.state)
        assertEquals(UpiPaymentStatus.PENDING, accepted.attempt.clientResponse?.status)
        assertFalse(accepted.attempt.clientResponse!!.isAuthoritativelyVerified)
    }

    @Test fun staleReferenceDoesNotCompleteANewerAttempt() {
        val dispatcher = unresolvedThenWaiting(firstRef = "ORDER-1", secondRef = "ORDER-2")
        val binding = dispatcher.onActivityResult(
            UpiResponse(UpiPaymentStatus.SUCCESS, transactionRef = "ORDER-1", transactionId = "OLD")
        )

        assertEquals(UpiIgnoreReason.STALE_REFERENCE, (binding as UpiResultBinding.Ignored).reason)
        assertEquals(UpiAttemptState.WAITING_FOR_RESULT, dispatcher.state)
        assertEquals("ORDER-2", dispatcher.attempt?.transactionRef)
        assertNull(dispatcher.attempt?.clientResponse)
    }

    @Test fun uncorrelatedPayloadDoesNotCompleteANewerAttempt() {
        val dispatcher = unresolvedThenWaiting(firstRef = "ORDER-1", secondRef = "ORDER-2")
        val binding = dispatcher.onActivityResult(UpiResponse(UpiPaymentStatus.SUCCESS, transactionId = "TXN"))

        assertEquals(UpiIgnoreReason.UNCORRELATED, (binding as UpiResultBinding.Ignored).reason)
        assertEquals(UpiAttemptState.WAITING_FOR_RESULT, dispatcher.state)
        assertEquals("a2", dispatcher.attempt?.id)
        assertNull(dispatcher.attempt?.clientResponse)
    }

    @Test fun matchingReferenceCanCompleteTheNewerAttempt() {
        val dispatcher = unresolvedThenWaiting(firstRef = "ORDER-1", secondRef = "ORDER-2")
        val binding = dispatcher.onActivityResult(
            UpiResponse(UpiPaymentStatus.SUCCESS, transactionRef = "ORDER-2")
        )

        val accepted = binding as UpiResultBinding.Accepted
        assertEquals("a2", accepted.attempt.id)
        assertEquals(UpiPaymentStatus.SUCCESS, accepted.attempt.clientResponse?.status)
        assertFalse(accepted.attempt.clientResponse!!.isAuthoritativelyVerified)
    }

    @Test fun singleOutstandingAttemptAcceptsAPayloadWithoutAReference() {
        val dispatcher = waiting("ORDER-1")
        val binding = dispatcher.onActivityResult(UpiResponse(UpiPaymentStatus.FAILURE))

        assertEquals(UpiPaymentStatus.FAILURE, (binding as UpiResultBinding.Accepted).attempt.clientResponse?.status)
        assertFalse(binding.attempt.requiresReferenceMatch)
    }

    @Test fun launchFailureAllowsAnotherAttemptAndIgnoresAStrayCallback() {
        val dispatcher = dispatcher()
        val first = start(dispatcher, "ORDER-1")
        val failed = dispatcher.markLaunchFailed(
            first.id,
            UpiPaymentException(UpiErrorCode.NO_UPI_APP, "No compatible UPI application is installed.")
        )

        assertEquals(UpiAttemptState.LAUNCH_FAILED, failed?.state)
        val stray = dispatcher.onActivityResult(UpiResponse(UpiPaymentStatus.SUCCESS, transactionRef = "ORDER-1"))
        assertEquals(UpiIgnoreReason.ATTEMPT_NOT_WAITING, (stray as UpiResultBinding.Ignored).reason)

        val second = dispatcher.tryStart("ORDER-2") as UpiAttemptStart.Started
        assertEquals(UpiAttemptState.LAUNCHING, second.attempt.state)
        assertEquals("a2", second.attempt.id)
        assertFalse(second.attempt.requiresReferenceMatch)
    }

    @Test fun resultWithNoAttemptIsIgnored() {
        val binding = dispatcher().onActivityResult(UpiResponse(UpiPaymentStatus.SUCCESS))
        assertEquals(UpiIgnoreReason.NO_ACTIVE_ATTEMPT, (binding as UpiResultBinding.Ignored).reason)
    }

    @Test fun restoredWaitingAttemptReceivesThePendingResult() {
        val original = waiting("ORDER-1")
        val restored = UpiResultDispatcher(nextAttemptId = { "restored" })
        restored.restore(original.snapshot())

        assertEquals(UpiAttemptState.WAITING_FOR_RESULT, restored.state)
        assertEquals("a1", restored.attempt?.id)
        val binding = restored.onActivityResult(
            UpiResponse(UpiPaymentStatus.PENDING, transactionRef = "ORDER-1")
        )
        assertEquals(UpiAttemptState.RESULT_RECEIVED, (binding as UpiResultBinding.Accepted).attempt.state)
    }

    @Test fun restoredReceivedAttemptIgnoresARepeatedCallback() {
        val original = waiting("ORDER-1")
        original.onActivityResult(UpiResponse(UpiPaymentStatus.SUCCESS, transactionRef = "ORDER-1"))
        val restored = UpiResultDispatcher()
        restored.restore(original.snapshot())

        val binding = restored.onActivityResult(UpiResponse(UpiPaymentStatus.FAILURE, transactionRef = "ORDER-1"))
        assertEquals(UpiIgnoreReason.ATTEMPT_NOT_WAITING, (binding as UpiResultBinding.Ignored).reason)
        assertEquals(UpiPaymentStatus.SUCCESS, restored.attempt?.clientResponse?.status)
        assertFalse(restored.attempt!!.clientResponse!!.isAuthoritativelyVerified)
    }

    @Test fun restoredUnresolvedAttemptStillRefusesAnUncorrelatedResult() {
        val original = unresolvedThenWaiting("ORDER-1", "ORDER-2")
        val restored = UpiResultDispatcher()
        restored.restore(original.snapshot())

        val binding = restored.onActivityResult(UpiResponse(UpiPaymentStatus.SUCCESS))
        assertEquals(UpiIgnoreReason.UNCORRELATED, (binding as UpiResultBinding.Ignored).reason)
        assertEquals("a2", restored.attempt?.id)
        assertEquals(UpiAttemptState.WAITING_FOR_RESULT, restored.state)
    }

    private fun dispatcher(): UpiResultDispatcher {
        val ids = ArrayDeque(listOf("a1", "a2", "a3"))
        return UpiResultDispatcher(nextAttemptId = { ids.removeFirst() })
    }

    private fun start(dispatcher: UpiResultDispatcher, transactionRef: String): UpiPaymentAttempt {
        return (dispatcher.tryStart(transactionRef) as UpiAttemptStart.Started).attempt
    }

    private fun waiting(transactionRef: String): UpiResultDispatcher {
        val dispatcher = dispatcher()
        val attempt = start(dispatcher, transactionRef)
        dispatcher.markWaiting(attempt.id)
        return dispatcher
    }

    private fun unresolvedThenWaiting(firstRef: String, secondRef: String): UpiResultDispatcher {
        val dispatcher = waiting(firstRef)
        dispatcher.markInconclusive(dispatcher.attempt!!.id)
        val second = start(dispatcher, secondRef)
        dispatcher.markWaiting(second.id)
        assertTrue(dispatcher.attempt!!.requiresReferenceMatch)
        return dispatcher
    }
}
