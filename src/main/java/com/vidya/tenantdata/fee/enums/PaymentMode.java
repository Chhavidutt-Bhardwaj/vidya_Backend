package com.vidya.tenantdata.fee.enums;

/** How the money was received: at the counter, by auto-debit (NACH) or through the gateway (ONLINE). */
public enum PaymentMode {
    CASH, UPI, CARD, CHEQUE, BANK_TRANSFER, DD,
    /** Auto-debit through a mandate (eNACH / UPI Autopay); pending until the bank reports. */
    NACH,
    /** Paid through the school's payment gateway (link or checkout). */
    ONLINE
}
