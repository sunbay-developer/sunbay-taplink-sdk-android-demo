package com.sunmi.tapro.taplink.demo.service

import com.sunmi.tapro.taplink.sdk.enums.PrintReceipt
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory
import com.sunmi.tapro.taplink.sdk.model.common.SignatureConfig
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo
import com.sunmi.tapro.taplink.sdk.model.common.TipConfig
import java.math.BigDecimal

/**
 * Payment Service Interface
 *
 * Defines the contract for payment-related operations in the Taplink Demo application.
 * This interface abstracts the underlying SDK implementation and provides a clean API
 * for payment operations, connection management, and transaction processing.
 *
 * The interface supports multiple connection modes (App-to-App, Cable, LAN) and
 * various transaction types (SALE, AUTH, REFUND, etc.) while maintaining consistent
 * error handling and callback patterns.
 */
interface PaymentService {

    /**
     * Establish connection to payment terminal using specified configuration
     */
    fun connect(connectionConfig: ConnectionConfig, listener: ConnectionListener)

    /**
     * Auto-discover LAN Taplink services (mDNS) and connect to the first available.
     *
     * Only supported by the local Taplink SDK service. Other implementations report
     * an error through the listener.
     */
    fun autoDiscoverAndConnect(listener: ConnectionListener) {
        listener.onError("UNSUPPORTED", "Auto-discover is not supported in this connection mode")
    }

    /**
     * Discover LAN Taplink services (mDNS) WITHOUT connecting.
     *
     * Returns the resolved host/port list so the caller can auto-fill the address and
     * then connect. Only supported by the local Taplink SDK service.
     *
     * @param onResult Called with the discovered services (may be empty)
     * @param onError Called with (code, message) on failure
     */
    fun discoverLanServices(
        onResult: (List<com.sunmi.tapro.taplink.sdk.model.DiscoveredService>) -> Unit,
        onError: (String, String) -> Unit
    ) {
        onError("UNSUPPORTED", "Discovery is not supported in this connection mode")
    }

    /**
     * Launch the SDK QR scanner and connect using the scanned LAN Taplink QR code.
     *
     * Only supported by the local Taplink SDK service. Other implementations report
     * an error through the listener.
     */
    fun scanAndConnect(listener: ConnectionListener) {
        listener.onError("UNSUPPORTED", "QR scan connect is not supported in this connection mode")
    }

    /**
     * Launch the SDK QR scanner and return the scanned lan:// host/port WITHOUT
     * connecting.
     *
     * Returns the scanned service so the caller can auto-fill the address and then
     * connect. Only supported by the local Taplink SDK service.
     *
     * @param onResult Called with the scanned services (single-element list on success)
     * @param onError Called with (code, message) on cancel / failure
     */
    fun scanLanQrCode(
        onResult: (List<com.sunmi.tapro.taplink.sdk.model.DiscoveredService>) -> Unit,
        onError: (String, String) -> Unit
    ) {
        onError("UNSUPPORTED", "QR scan is not supported in this connection mode")
    }

    /**
     * Disconnect from the payment terminal
     */
    fun disconnect()

    /**
     * Get the identifier of the currently connected device
     */
    fun getConnectedDeviceId(): String?

    /**
     * Get the version of the connected Tapro application
     */
    fun getTaproVersion(): String?

    /**
     * Query the merchant and device information of the currently connected TaPro terminal.
     *
     * This is a read-only control action and does not create a payment transaction.
     */
    fun getTerminalInfo(callback: TerminalInfoCallback) {
        callback.onFailure(
            code = "UNSUPPORTED",
            message = "Profile queries are not supported in this connection mode"
        )
    }

    /**
     * Ask TaPro to open its USB screen player application.
     *
     * 仅在 tapro 与本 App 同机运行时有意义。集成商 App 自己的副屏投放（Presentation）
     * 不应依赖本接口的结果，参见 docs/secondary-display.md。
     * Implementations must never throw so UI flow is not blocked.
     */
    fun ensureUsbScreenPlayerReady()

    /**
     * Configure whether transaction lifecycle callbacks may request the USB screen player.
     *
     * Implementations that do not control a local screen player may ignore this setting.
     */
    fun setSecondaryDisplayEnabled(enabled: Boolean) {}

    fun executeSale(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        paymentCategory: PaymentCategory = PaymentCategory.CARD,
        paymentMethodId: String? = null,
        subPaymentMethodId: String? = null,
        cardNetworkType: String? = null,
        tipAmount: BigDecimal? = null,
        taxAmount: BigDecimal? = null,
        cashbackAmount: BigDecimal? = null,
        serviceFee: BigDecimal? = null,
        surchargeAmount: BigDecimal? = null,
        staffInfo: StaffInfo? = null,
        tipConfig: TipConfig? = null,
        attach: String? = null,
        callback: PaymentCallback
    ) {
        executeSale(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            amount = amount,
            currency = currency,
            description = description,
            paymentCategory = paymentCategory,
            paymentMethodId = paymentMethodId,
            subPaymentMethodId = subPaymentMethodId,
            cardNetworkType = cardNetworkType,
            tipAmount = tipAmount,
            taxAmount = taxAmount,
            cashbackAmount = cashbackAmount,
            serviceFee = serviceFee,
            surchargeAmount = surchargeAmount,
            staffInfo = staffInfo,
            tipConfig = tipConfig,
            attach = attach,
            callback = callback,
            signatureConfig = null
        )
    }

    /**
     * Execute sale transaction with per-transaction signature configuration.
     *
     * @param signatureConfig Optional signature location and amount threshold.
     */
    fun executeSale(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        paymentCategory: PaymentCategory = PaymentCategory.CARD,
        paymentMethodId: String? = null,
        subPaymentMethodId: String? = null,
        cardNetworkType: String? = null,
        tipAmount: BigDecimal? = null,
        taxAmount: BigDecimal? = null,
        cashbackAmount: BigDecimal? = null,
        serviceFee: BigDecimal? = null,
        surchargeAmount: BigDecimal? = null,
        staffInfo: StaffInfo? = null,
        tipConfig: TipConfig? = null,
        attach: String? = null,
        callback: PaymentCallback,
        signatureConfig: SignatureConfig?
    )

    fun executeAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        staffInfo: StaffInfo? = null,
        attach: String? = null,
        callback: PaymentCallback
    ) {
        executeAuth(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            amount = amount,
            currency = currency,
            description = description,
            staffInfo = staffInfo,
            attach = attach,
            callback = callback,
            signatureConfig = null
        )
    }

    /**
     * Execute auth transaction with per-transaction signature configuration.
     *
     * @param signatureConfig Optional signature location and amount threshold.
     */
    fun executeAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        staffInfo: StaffInfo? = null,
        attach: String? = null,
        callback: PaymentCallback,
        signatureConfig: SignatureConfig?
    )

    fun executeForcedAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        tipAmount: BigDecimal? = null,
        taxAmount: BigDecimal? = null,
        staffInfo: StaffInfo? = null,
        attach: String? = null,
        callback: PaymentCallback
    )

    /**
     * Execute refund transaction.
     *
     * Referenced refund: provide originalTransactionId or originalTransactionRequestId (at least one).
     *   referenceOrderId is NOT required — the server auto-associates the original order.
     *
     * Unreferenced refund: both originalTransactionId and originalTransactionRequestId must be empty.
     *   referenceOrderId is REQUIRED.
     *
     * @param paymentCategory Payment category for routing (CARD, QR_MPM, QR_CPM). When null,
     *   defaults to CARD. For referenced refunds of QR transactions, pass QR_MPM or QR_CPM
     *   so the terminal routes to the correct QR refund flow.
     * @param cardNetworkType Optional card network for CARD refunds (e.g. CREDIT/DEBIT).
     *   Used by standalone refund UI to force routing to a specific card network.
     * @param signatureConfig Optional signature configuration. Only accepted for **unreferenced**
     *   refunds, where the cardholder presents a card and can be asked to sign. Passing it for a
     *   referenced refund is rejected by the SDK.
     * @see <a href="https://docs.sunbay.dev/zh/refspec/transaction/refund">Refund API</a>
     */
    fun executeRefund(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        originalTransactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        reason: String?,
        paymentCategory: PaymentCategory? = null,
        cardNetworkType: String? = null,
        staffInfo: StaffInfo? = null,
        signatureConfig: SignatureConfig? = null,
        callback: PaymentCallback
    )

    fun executeVoid(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        description: String,
        reason: String?,
        callback: PaymentCallback
    )

    fun executePostAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        tipAmount: BigDecimal? = null,
        taxAmount: BigDecimal? = null,
        cashbackAmount: BigDecimal? = null,
        serviceFee: BigDecimal? = null,
        staffInfo: StaffInfo? = null,
        tipConfig: TipConfig? = null,
        callback: PaymentCallback
    )

    fun executeIncrementalAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        staffInfo: StaffInfo? = null,
        callback: PaymentCallback
    )

    fun executeTipAdjust(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        originalTransactionRequestId: String,
        tipAmount: BigDecimal,
        description: String,
        callback: PaymentCallback
    )

    fun executeQuery(
        transactionRequestId: String,
        callback: PaymentCallback
    )

    fun executeQueryByTransactionId(
        transactionId: String,
        callback: PaymentCallback
    )

    fun executeBatchClose(
        transactionRequestId: String,
        description: String,
        printReceipt: PrintReceipt = PrintReceipt.AUTO,
        callback: PaymentCallback
    )

    fun executeAbort(
        originalTransactionId: String?,
        originalTransactionRequestId: String?,
        description: String?,
        callback: PaymentCallback
    )

    /**
     * Headless 控制指令：将当前在途交易切换为手输卡号流程。
     *
     * 仅在终端返回 WAITING_CARD 阶段时允许触发，其他阶段由服务端拒绝。
     *
     * @param originalTransactionRequestId 目标交易请求 ID（可选，作为幂等与一致性校验）
     * @param callback 回调结果
     */
    fun executeSwitchToManualEntry(
        originalTransactionRequestId: String?,
        callback: PaymentCallback
    )
}

/**
 * Connection status listener
 */
interface ConnectionListener {
    fun onConnected(deviceId: String, taproVersion: String)
    fun onDisconnected(reason: String)
    fun onError(code: String, message: String)
}

interface TerminalInfoCallback {
    fun onSuccess(info: TerminalInfo)
    fun onFailure(code: String, message: String)
}

data class TerminalInfo(
    val merchantId: String,
    val dbaName: String,
    val mcc: String?,
    val logo: String?,
    val country: String?,
    val stateName: String?,
    val cityName: String?,
    val street: String?,
    val detailAddress: String?,
    val zipCode: String?,
    val midList: List<MerchantMid>,
    val terminal: Terminal
)

data class MerchantMid(
    val channelCode: String,
    val channelName: String,
    val mid: String
)

data class Terminal(
    val sn: String,
    val vendor: String?,
    val model: String?,
    val tidList: List<TerminalTid>
)

data class TerminalTid(
    val channelCode: String,
    val channelName: String,
    val tid: String
)

/**
 * Payment callback
 */
interface PaymentCallback {
    fun onSuccess(result: PaymentResult)
    fun onFailure(code: String, message: String)
    fun onProgress(status: String, message: String) {}

    /**
     * Headless mode structured progress event.
     *
     * This callback is only used when Taplink emits the richer Headless event payload.
     * Existing implementations can ignore it and continue using [onProgress].
     */
    fun onHeadlessProgress(event: HeadlessProgressEvent) {
        onProgress(event.eventCode, event.eventMessage)
    }
}

/**
 * Headless progress payload.
 *
 * Keeps the full metadata required by the design doc so POS apps can recover
 * state, de-duplicate events, and gate UI actions with the server-provided
 * cancelable flag.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class HeadlessProgressEvent(
    val eventCode: String,
    val eventMessage: String,
    val eventId: String? = null,
    val transactionRequestId: String? = null,
    val referenceOrderId: String? = null,
    val cancelable: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val data: Map<String, String>? = null,
    val rawEventCode: String? = null
) {
    /**
     * Card types currently enabled by Tapro while WAITING_CARD is active.
     */
    @get:JsonIgnore
    val waitingCardTypes: List<String>
        get() = data?.get(KEY_WAITING_CARD_TYPES)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    /**
     * True when this event indicates Tapro removed the surcharge from a debit-card
     * transaction (and thus the displayed amount should be refreshed).
     *
     * Matches both the normalized code ("SURCHARGE_REMOVED" from newer SDKs) and the
     * raw engine code ("SurchargeRemoved"), so it works regardless of SDK AAR version.
     */
    @get:JsonIgnore
    val isSurchargeRemoved: Boolean
        get() = listOfNotNull(rawEventCode, eventCode)
            .any { it.replace("_", "").equals(RAW_EVENT_SURCHARGE_REMOVED, ignoreCase = true) }

    /**
     * Updated transaction total carried by a surcharge-removed event, in the smallest
     * currency unit (cents). Null when absent or unparsable.
     */
    @get:JsonIgnore
    val surchargeRemovedTotalAmountCents: java.math.BigDecimal?
        get() = data?.get(KEY_SURCHARGE_REMOVED_TOTAL_AMOUNT)?.let {
            runCatching { java.math.BigDecimal(it) }.getOrNull()
        }

    companion object {
        /**
         * Headless WAITING_CARD data key for the currently enabled card types.
         */
        const val KEY_WAITING_CARD_TYPES = "cardTypes"

        /**
         * Headless SURCHARGE_REMOVED data key for the updated transaction total (cents).
         */
        const val KEY_SURCHARGE_REMOVED_TOTAL_AMOUNT = "totalAmount"

        /**
         * Raw engine event code (PascalCase) emitted by Tapro when the surcharge is removed.
         */
        const val RAW_EVENT_SURCHARGE_REMOVED = "SurchargeRemoved"
    }
}

/**
 * Payment result
 */
data class PaymentResult(
    val code: String,
    val message: String,
    val traceId: String?,
    val transactionId: String?,
    val referenceOrderId: String?,
    val transactionRequestId: String?,
    val transactionStatus: String?,
    val transactionBatchStatus: String?,
    val relatedTransactionStatus: String?,
    val transactionType: String?,
    val amount: TransactionAmount?,
    val createTime: String?,
    val completeTime: String?,
    val cardInfo: CardInfo?,
    val batchNo: Int?,
    val voucherNo: String?,
    val stan: String?,
    val rrn: String?,
    val authCode: String?,
    val transactionResultCode: String?,
    val transactionResultMsg: String?,
    val description: String?,
    val attach: String?,
    val receiptJson: String?,
    val batchCloseInfo: BatchCloseInfo?,
    val tipAmount: BigDecimal?,
    val totalAuthorizedAmount: BigDecimal?,
    val merchantRefundNo: String?,
    val originalTransactionId: String?,
    val originalTransactionRequestId: String?
) {
    /**
     * Check whether request-level result code indicates transport/gateway success.
     *
     * Cloud API commonly returns code="100" for accepted requests. Some SDK pathways may
     * return other transport codes (for example "50") while still reporting a terminal
     * transaction status like SUCCESS/FAILED. Status fields must take precedence when present.
     */
    private fun isRequestAccepted(): Boolean = code == "100"

    private fun normalizedStatus(): String? = transactionStatus?.uppercase()

    /**
     * Cloud API returns single-letter status codes: I, P, S, F, C
     * TaplinkPaymentService (App-to-App) uses full names: SUCCESS, PROCESSING, FAILED
     * These helpers support both formats.
     *
     * API status reference (https://docs.sunbay.dev/zh/resources/reference/transaction-status):
     *   I = Initial (created, not yet processing) - non-terminal
     *   P = Processing - non-terminal
     *   S = Success - terminal
     *   F = Failed - terminal (includes cancelled/aborted transactions)
     *   C = Cancelled - terminal (transaction was cancelled)
     */
    fun isSuccess(): Boolean {
        return when (normalizedStatus()) {
            "S", "SUCCESS" -> true
            // BATCH_CLOSE cloud response has no transactionStatus field;
            // treat as success when request is accepted and batchCloseInfo is present.
            null, "" -> isRequestAccepted() && batchCloseInfo != null
            else -> false
        }
    }

    fun isProcessing(): Boolean {
        return when (normalizedStatus()) {
            "P", "I", "PROCESSING" -> true
            else -> false
        }
    }

    fun isFailed(): Boolean {
        return when (normalizedStatus()) {
            "F", "FAILED" -> true
            "S", "SUCCESS", "P", "I", "PROCESSING", "C", "CANCELLED", "CANCELED" -> false
            null, "" -> !isRequestAccepted()
            else -> !isRequestAccepted()
        }
    }

    fun isCancelled(): Boolean {
        return when (normalizedStatus()) {
            "C", "CANCELLED", "CANCELED" -> true
            else -> false
        }
    }

    fun isTerminal(): Boolean = isSuccess() || isFailed() || isCancelled()
}

data class TransactionAmount(
    val priceCurrency: String?,
    val transAmount: BigDecimal?,
    val orderAmount: BigDecimal?,
    val taxAmount: BigDecimal?,
    val serviceFee: BigDecimal?,
    val tipAmount: BigDecimal?,
    val cashbackAmount: BigDecimal?
)

data class CardInfo(
    val maskedPan: String?,
    val cardNetworkType: String?,
    val paymentMethodId: String?,
    val subPaymentMethodId: String?,
    val entryMode: String?,
    val authenticationMethod: String?,
    val cardholderName: String?,
    val expiryDate: String?,
    val issuerBank: String?,
    val cardBrand: String?
)

data class BatchCloseInfo(
    val totalCount: Int,
    val totalAmount: BigDecimal,
    val totalTip: BigDecimal,
    val totalTax: BigDecimal,
    val totalSurchargeAmount: BigDecimal,
    val totalServiceFee: BigDecimal,
    val cashDiscount: BigDecimal,
    val closeTime: String
)
