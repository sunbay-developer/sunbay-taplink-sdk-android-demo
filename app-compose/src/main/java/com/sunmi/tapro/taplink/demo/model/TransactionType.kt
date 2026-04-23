package com.sunmi.tapro.taplink.demo.model

/**
 * Transaction type enumeration
 * 
 * Defines all transaction types supported by Taplink SDK
 */
enum class TransactionType {
    /**
     * Sale transaction - Direct deduction
     */
    SALE,
    
    /**
     * Pre-authorization - Freeze funds without deduction
     */
    AUTH,

    /**
     * Forced authorization - Offline authorization
     */
    FORCED_AUTH,
    
    /**
     * Incremental authorization - Increase amount based on original pre-authorization
     */
    INCREMENT_AUTH,
    
    /**
     * Pre-authorization completion - Complete pre-authorization and deduct funds
     */
    POST_AUTH,
    
    /**
     * Refund - Refund the paid amount
     */
    REFUND,
    
    /**
     * Void - Cancel the same-day transaction
     */
    VOID,
    
    /**
     * Tip adjustment - Adjust the transaction tip amount
     */
    TIP_ADJUST,
    
    /**
     * Query - Query transaction status
     */
    QUERY,
    
    /**
     * Batch close - Settle all transactions of the day
     */
    BATCH_CLOSE;

    /**
     * User-facing display name (e.g. "Capture" for POST_AUTH, "Tip adjust" for TIP_ADJUST).
     * Use this for UI only; request/API logic still uses the enum name.
     */
    fun displayName(): String = when (this) {
        SALE -> "Sale"
        AUTH -> "Auth"
        INCREMENT_AUTH -> "Increment"
        FORCED_AUTH -> "Forced"
        POST_AUTH -> "Capture"
        REFUND -> "Refund"
        VOID -> "Void"
        TIP_ADJUST -> "Tip Adjust"
        QUERY -> "Query"
        BATCH_CLOSE -> "Batch Close"
    }
}
