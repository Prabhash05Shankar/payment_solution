package com.prabhash.payments.upi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UpiResponseParserTest {
    @Test fun parsesResponseFields() {
        val values = UpiResponseParser.parseResponseString("status=SUCCESS&txnId=TXN-1")
        assertEquals("SUCCESS", values["status"])
        assertEquals("TXN-1", values["txnId"])
    }
    @Test fun missingResponseIsEmpty() {
        assertEquals(0, UpiResponseParser.parseResponseString(null).size)
    }
    @Test fun responseModelDoesNotClaimVerification() {
        assertFalse(UpiResponse(UpiPaymentStatus.SUCCESS).isAuthoritativelyVerified)
    }
}
