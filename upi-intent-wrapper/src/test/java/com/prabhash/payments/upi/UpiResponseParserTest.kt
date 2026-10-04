package com.prabhash.payments.upi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiResponseParserTest {
    @Test fun parsesSuccessfulResponse() {
        val response = UpiResponseParser.parse(
            resultCode = -1,
            rawResponse = "status=SUCCESS&txnId=TXN-1&txnRef=ORDER-1&ApprovalRefNo=APP-9&responseCode=00"
        )
        assertEquals(UpiPaymentStatus.SUCCESS, response.status)
        assertEquals("TXN-1", response.transactionId)
        assertEquals("ORDER-1", response.transactionRef)
        assertEquals("APP-9", response.approvalReference)
        assertEquals("00", response.responseCode)
        assertFalse(response.isAuthoritativelyVerified)
        assertTrue(response.message!!.contains("not proof"))
    }

    @Test fun parsesFailedResponse() {
        val response = UpiResponseParser.parse(0, "Status=FAILED&responseCode=ZM")
        assertEquals(UpiPaymentStatus.FAILURE, response.status)
        assertEquals("ZM", response.responseCode)
        assertFalse(response.isAuthoritativelyVerified)
    }

    @Test fun parsesPendingSubmittedAndDeemedAsPending() {
        assertEquals(UpiPaymentStatus.PENDING, UpiResponseParser.parse(-1, "status=pending").status)
        assertEquals(UpiPaymentStatus.PENDING, UpiResponseParser.parse(-1, "status=SUBMITTED").status)
        assertEquals(UpiPaymentStatus.PENDING, UpiResponseParser.parse(-1, "status=deemed").status)
    }

    @Test fun parsesExplicitCancellationOnlyFromThePayload() {
        val response = UpiResponseParser.parse(0, "status=CANCELLED")
        assertEquals(UpiPaymentStatus.CANCELLED_OR_UNKNOWN, response.status)
        assertTrue(response.message!!.contains("not proof"))
        assertEquals(
            UpiPaymentStatus.CANCELLED_OR_UNKNOWN,
            UpiResponseParser.parse(-1, "status=canceled").status
        )
    }

    @Test fun nullResponseIsInconclusive() {
        val response = UpiResponseParser.parse(resultCode = 0, rawResponse = null)
        assertEquals(UpiPaymentStatus.UNKNOWN, response.status)
        assertNull(response.rawResponse)
        assertTrue(response.message!!.contains("activity result 0"))
        assertTrue(response.message!!.contains("not proof of cancellation"))
        assertFalse(response.isAuthoritativelyVerified)
    }

    @Test fun emptyResponseIsInconclusive() {
        val response = UpiResponseParser.parse(-1, "   ")
        assertEquals(UpiPaymentStatus.UNKNOWN, response.status)
        assertFalse(response.message!!.contains("success"))
    }

    @Test fun malformedResponseDoesNotCrash() {
        listOf("&&&", "====", "status", "not a response", "%", "txnId=bad%").forEach { raw ->
            val response = UpiResponseParser.parse(-1, raw)
            assertEquals(raw, UpiPaymentStatus.UNKNOWN, response.status)
        }
        assertEquals("bad%", UpiResponseParser.parse(-1, "txnId=bad%").transactionId)
    }

    @Test fun decodesUrlEncodedValues() {
        val response = UpiResponseParser.parse(
            -1,
            "status=SUCCESS&txnId=TXN%2D1&message=Paid%20%26%20done"
        )
        assertEquals("TXN-1", response.transactionId)
        assertEquals("Paid & done", response.message)
        assertEquals(UpiPaymentStatus.SUCCESS, response.status)
    }

    @Test fun acceptsUpperAndLowerCaseKeys() {
        val response = UpiResponseParser.parse(
            -1,
            "STATUS=success&TXNID=T1&TXNREF=R1&APPROVALREFNO=A1&RESPONSECODE=00"
        )
        assertEquals(UpiPaymentStatus.SUCCESS, response.status)
        assertEquals("T1", response.transactionId)
        assertEquals("R1", response.transactionRef)
        assertEquals("A1", response.approvalReference)
        assertEquals("00", response.responseCode)
    }

    @Test fun successWithoutTransactionIdStaysUnverified() {
        val response = UpiResponseParser.parse(-1, "status=SUCCESS")
        assertEquals(UpiPaymentStatus.SUCCESS, response.status)
        assertNull(response.transactionId)
        assertFalse(response.isAuthoritativelyVerified)
    }

    @Test fun readsAlternateTransactionReferenceFields() {
        assertEquals("REF-A", UpiResponseParser.parse(-1, "status=SUCCESS&txnRef=REF-A").transactionRef)
        assertEquals("REF-B", UpiResponseParser.parse(-1, "status=SUCCESS&refId=REF-B").transactionRef)
        assertEquals("REF-C", UpiResponseParser.parse(-1, "status=SUCCESS&txn_ref=REF-C").transactionRef)
    }

    @Test fun unknownStatusIsNotSuccess() {
        val response = UpiResponseParser.parse(-1, "status=ok&responseCode=00&txnId=T1")
        assertEquals(UpiPaymentStatus.UNKNOWN, response.status)
        assertEquals("00", response.responseCode)
        assertEquals("T1", response.transactionId)
        assertFalse(response.isAuthoritativelyVerified)
    }

    @Test fun responseCodeAloneIsNotSuccess() {
        val response = UpiResponseParser.parse(-1, "responseCode=00")
        assertEquals(UpiPaymentStatus.UNKNOWN, response.status)
    }

    @Test fun duplicateParametersKeepTheFirstNonBlankValue() {
        val values = UpiResponseParser.parseResponseString("status=SUCCESS&status=FAILURE&txnId=&txnId=TWO&foo=bar")
        assertEquals("SUCCESS", values["status"])
        assertEquals("TWO", values["txnid"])
        assertEquals("bar", values["foo"])
        val response = UpiResponseParser.parse(-1, "status=FAILURE&status=SUCCESS&extra=1")
        assertEquals(UpiPaymentStatus.FAILURE, response.status)
    }

    @Test fun unexpectedParametersAreIgnored() {
        val response = UpiResponseParser.parse(-1, "foo=bar&status=SUCCESS&unknown=1")
        assertEquals(UpiPaymentStatus.SUCCESS, response.status)
        assertTrue(response.rawResponse!!.contains("foo=bar"))
    }

    @Test fun parsesUriAndLeadingQuestionMark() {
        assertEquals(
            UpiPaymentStatus.SUCCESS,
            UpiResponseParser.parse(-1, "upi://pay?status=SUCCESS&txnId=T1").status
        )
        assertEquals("T9", UpiResponseParser.parse(-1, "?status=FAILURE&txnId=T9").transactionId)
    }

    @Test fun parsesBareStatusToken() {
        assertEquals(UpiPaymentStatus.FAILURE, UpiResponseParser.parse(0, "FAILURE").status)
        assertEquals(UpiPaymentStatus.UNKNOWN, UpiResponseParser.parse(-1, "MAYBE").status)
    }

    @Test fun responseModelCannotClaimVerification() {
        assertFalse(UpiResponse(UpiPaymentStatus.SUCCESS).isAuthoritativelyVerified)
        assertFalse(UpiResponse(UpiPaymentStatus.SUCCESS).copy(transactionId = "T1").isAuthoritativelyVerified)
    }

    @Test fun missingResponseStringIsEmpty() {
        assertEquals(0, UpiResponseParser.parseResponseString(null).size)
        assertEquals(0, UpiResponseParser.parseResponseString("").size)
    }
}
