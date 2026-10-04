package com.prabhash.payments.upi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class UpiPaymentRequestTest {
    @Test fun acceptsValidRequest() {
        val request = UpiPaymentRequest(
            payeeAddress = "merchant@upi",
            payeeName = "Example Merchant",
            amount = "10.50",
            transactionRef = "ORDER-123",
            transactionNote = "Order payment"
        )
        assertTrue(request.validate().isEmpty())
        assertTrue(request.fieldErrors().isEmpty())
    }

    @Test fun rejectsMissingPayeeAddress() {
        val errors = UpiPaymentRequest("  ", "Merchant", "10.00", "ORDER-1").fieldErrors()
        assertEquals("payeeAddress", errors.single().field)
        assertEquals("required", errors.single().code)
    }

    @Test fun rejectsMalformedPayeeAddress() {
        val errors = UpiPaymentRequest("merchant", "Merchant", "10.00", "ORDER-1").fieldErrors()
        assertEquals("invalid", errors.single().code)
        assertTrue(errors.single().message.contains("UPI ID"))
    }

    @Test fun rejectsMissingPayeeName() {
        val errors = UpiPaymentRequest("merchant@upi", " ", "10.00", "ORDER-1").fieldErrors()
        assertEquals("payeeName", errors.single().field)
        assertEquals("required", errors.single().code)
    }

    @Test fun rejectsZeroAmount() {
        assertAmountCode("0", "not_positive")
        assertAmountCode("0.00", "not_positive")
        assertAmountCode("000", "not_positive")
    }

    @Test fun rejectsNegativeAmount() {
        assertAmountCode("-1", "not_positive")
        assertAmountCode("-0.01", "not_positive")
    }

    @Test fun rejectsInvalidAmountFormat() {
        assertAmountCode("10,50", "invalid")
        assertAmountCode("10.5.1", "invalid")
        assertAmountCode("1e2", "invalid")
        assertAmountCode("+10", "invalid")
        assertAmountCode(".50", "invalid")
        assertAmountCode("10.", "invalid")
    }

    @Test fun rejectsExcessiveDecimalPrecision() {
        assertAmountCode("10.999", "precision")
        assertAmountCode("10.500", "precision")
    }

    @Test fun rejectsMissingTransactionReference() {
        val errors = UpiPaymentRequest("merchant@upi", "Merchant", "10.00", "  ").fieldErrors()
        assertEquals("transactionRef", errors.single().field)
        assertEquals("required", errors.single().code)
    }

    @Test fun rejectsUnsafeTransactionReference() {
        val errors = UpiPaymentRequest("merchant@upi", "Merchant", "10.00", "ORDER 1").fieldErrors()
        assertEquals("invalid", errors.single().code)
    }

    @Test fun allowsSpecialCharactersInTransactionNote() {
        val request = UpiPaymentRequest(
            payeeAddress = "merchant@upi",
            payeeName = "Merchant",
            amount = "10.00",
            transactionRef = "ORDER-1",
            transactionNote = "Café & sons / #1"
        )
        assertTrue(request.validate().isEmpty())
    }

    @Test fun amountValidationDoesNotDependOnLocale() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            assertTrue(UpiPaymentRequest("merchant@upi", "Merchant", "10.50", "ORDER-1").validate().isEmpty())
            assertAmountCode("10,50", "invalid")
            assertEquals("10.50", canonicalAmount("10.5"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun reportsEveryIndependentError() {
        val fields = UpiPaymentRequest("", "", "0", "").fieldErrors().map { it.field }
        assertEquals(listOf("payeeAddress", "payeeName", "amount", "transactionRef"), fields)
    }

    private fun assertAmountCode(amount: String, code: String) {
        val errors = UpiPaymentRequest("merchant@upi", "Merchant", amount, "ORDER-1").fieldErrors()
        assertEquals(amount, code, errors.single { it.field == "amount" }.code)
    }
}
