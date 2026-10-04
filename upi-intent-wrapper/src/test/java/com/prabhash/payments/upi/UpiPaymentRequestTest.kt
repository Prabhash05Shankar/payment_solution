package com.prabhash.payments.upi

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class UpiPaymentRequestTest {
    @Test fun acceptsValidRequest() {
        assertTrue(UpiPaymentRequest("merchant@upi", "Merchant", "10.50", "ORDER-1").validate().isEmpty())
    }
    @Test fun rejectsInvalidAmountAndMissingReference() {
        assertFalse(UpiPaymentRequest("merchant@upi", "Merchant", "0", "").validate().isEmpty())
    }
}
