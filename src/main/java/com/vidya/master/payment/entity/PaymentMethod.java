package com.vidya.master.payment.entity;

/**
 * Payment method or channel used to complete the transaction.
 */
public enum PaymentMethod {
    UPI,
    CARD,
    NETBANKING,
    WALLET,
    NEFT,
    RTGS,
    CHEQUE,
    OTHER
}
