package com.prabhash.payments.upi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class UpiPayUriTest {
    @Test fun buildsCanonicalUri() {
        val uri = UpiPayUri.build(
            UpiPaymentRequest(
                payeeAddress = " merchant@upi ",
                payeeName = "Example Merchant",
                amount = "10.5",
                transactionRef = "ORDER-123",
                transactionNote = "A & B"
            )
        )
        assertEquals(
            "upi://pay?pa=merchant@upi&pn=Example%20Merchant&am=10.50&cu=INR&tr=ORDER-123&tn=A%20%26%20B",
            uri
        )
    }

    @Test fun leavesAtSignUnencodedAndRoundTripsValues() {
        val uri = UpiPayUri.build(
            UpiPaymentRequest(
                payeeAddress = "store.name@okaxis",
                payeeName = "A + B",
                amount = "2",
                transactionRef = "REF_1",
                transactionNote = "100% off"
            )
        )
        assertTrue(uri.contains("pa=store.name@okaxis"))
        assertFalse(uri.contains("%40"))
        assertFalse(uri.contains("+"))
        val values = queryValues(uri)
        assertEquals("store.name@okaxis", values["pa"])
        assertEquals("A + B", values["pn"])
        assertEquals("2.00", values["am"])
        assertEquals("INR", values["cu"])
        assertEquals("REF_1", values["tr"])
        assertEquals("100% off", values["tn"])
    }

    @Test fun omitsBlankNote() {
        val uri = UpiPayUri.build(
            UpiPaymentRequest("merchant@upi", "Merchant", "10.00", "ORDER-1", transactionNote = "   ")
        )
        assertFalse(uri.contains("tn="))
    }

    @Test fun invalidRequestDoesNotProduceUri() {
        val error = assertThrows(UpiPaymentException::class.java) {
            UpiPayUri.build(UpiPaymentRequest("merchant@upi", "Merchant", "-5", "ORDER-1"))
        }
        assertEquals(UpiErrorCode.INVALID_REQUEST, error.code)
        assertTrue(error.message!!.contains("greater than zero"))
    }

    private fun queryValues(uri: String): Map<String, String> {
        return uri.substringAfter('?').split('&').associate { part ->
            val key = part.substringBefore('=')
            val value = URLDecoder.decode(part.substringAfter('='), StandardCharsets.UTF_8)
            key to value
        }
    }
}
