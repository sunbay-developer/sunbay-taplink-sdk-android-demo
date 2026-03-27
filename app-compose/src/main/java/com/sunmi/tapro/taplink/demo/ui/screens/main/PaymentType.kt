package com.sunmi.tapro.taplink.demo.ui.screens.main

import com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory

/**
 * Unified payment option combining transaction type and payment method.
 *
 * Card options support Sale/Auth/Forced Auth transaction types.
 * CREDIT/DEBIT options use Sale with cardNetworkType.
 * EBT options always use Sale transaction type with sub-payment method selection.
 *
 * @property label Display text on the Pay button
 * @property category SDK PaymentCategory
 * @property transactionType Which transaction type to execute
 * @property paymentMethodId Payment method id (e.g. "EBT")
 * @property subPaymentMethodId Sub payment method id (e.g. "SNAP")
 * @property cardNetworkType Card network type (e.g. "CREDIT", "DEBIT"), only for CARD category
 * @property group Group label for dropdown section headers
 */
enum class PaymentOption(
    val label: String,
    val category: PaymentCategory,
    val transactionType: TransactionType,
    val paymentMethodId: String? = null,
    val subPaymentMethodId: String? = null,
    val cardNetworkType: String? = null,
    val group: String
) {
    // Card payment options
    CARD_SALE("Card - Sale", PaymentCategory.CARD, TransactionType.SALE, group = "Card"),
    CREDIT_SALE("Credit - Sale", PaymentCategory.CARD, TransactionType.SALE, cardNetworkType = "CREDIT", group = "Card"),
    DEBIT_SALE("Debit - Sale", PaymentCategory.CARD, TransactionType.SALE, cardNetworkType = "DEBIT", group = "Card"),
    CARD_AUTH("Card - Auth", PaymentCategory.CARD, TransactionType.AUTH, group = "Card"),
    CARD_FORCED_AUTH("Card - Forced Auth", PaymentCategory.CARD, TransactionType.FORCED_AUTH, group = "Card"),

    // EBT payment options (always SALE)
    EBT_SNAP("EBT - SNAP", PaymentCategory.EBT, TransactionType.SALE, "EBT", "SNAP", group = "EBT"),
    EBT_VOUCHER("EBT - VOUCHER", PaymentCategory.EBT, TransactionType.SALE, "EBT", "VOUCHER", group = "EBT"),
    EBT_BENEFIT("EBT - BENEFIT", PaymentCategory.EBT, TransactionType.SALE, "EBT", "BENEFIT", group = "EBT");

    /**
     * Internal transaction type mapping (reuses the name to avoid conflict with model.TransactionType)
     */
    enum class TransactionType {
        SALE, AUTH, FORCED_AUTH
    }
}
