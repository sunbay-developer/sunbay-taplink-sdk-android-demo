package com.sunmi.tapro.taplink.demo.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.enums.CardNetworkType
import com.sunmi.tapro.taplink.sdk.enums.PrintReceipt
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory
import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodId
import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodInfo
import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodSubId
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.AbortRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.AuthAmountInfo
import com.sunmi.tapro.taplink.sdk.model.request.transaction.AuthRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.ForcedAuthRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.IncrementalAuthRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.PostAuthRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.RefundRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.SaleRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.TipAdjustRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.VoidRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.settlement.BatchCloseRequest
import java.math.BigDecimal
import java.math.RoundingMode
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener as SdkConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback as SdkPaymentCallback
import com.sunmi.tapro.taplink.sdk.error.ConnectionError as SdkConnectionError
import com.sunmi.tapro.taplink.sdk.error.PaymentError as SdkPaymentError
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent as SdkPaymentEvent
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult as SdkPaymentResult


/**
 * Unified payment service implementation supporting multiple connection modes.
 * Implements PaymentService interface, encapsulates Taplink SDK calling logic.
 * Supports App-to-App, Cable, and LAN connection modes.
 *
 * Print receipt behaviour: call [setPrintReceipt] from app/app-compose when loading settings;
 * defaults to [PrintReceipt.NONE] if not set.
 */
class TaplinkPaymentService : PaymentService {

    companion object {
        private const val TAG = "TaplinkPaymentService"
        private const val CENTS_TO_DOLLARS_MULTIPLIER = 100
        private const val AMOUNT_DECIMAL_PLACES = 2

        private object ConnectionErrorCodes {
            const val TARGET_APP_CRASHED = "C36"
            const val CONNECTION_TIMEOUT = "C01"
            const val CONNECTION_FAILED = "C02"
            const val CONNECTION_LOST = "C03"
            const val SERVICE_DISCONNECTED = "C04"
            const val SERVICE_BINDING_FAILED = "C05"
        }

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: TaplinkPaymentService? = null

        fun getInstance(): TaplinkPaymentService {
            return instance ?: synchronized(this) {
                instance ?: TaplinkPaymentService().also { instance = it }
            }
        }
    }

    private var connected = false
    private var connecting = false
    private var connectedDeviceId: String? = null
    private var taproVersion: String? = null
    private var connectionListener: ConnectionListener? = null
    private var context: Context? = null

    /** SDK print receipt option. Set from app/app-compose when loading preferences; default NONE. */
    private var printReceipt: PrintReceipt = PrintReceipt.NONE

    /**
     * Optional: initialize with application context (e.g. for future use).
     * Not required for basic payment operations.
     */
    fun initialize(context: Context) {
        this.context = context.applicationContext
        Log.d(TAG, "TaplinkPaymentService initialized with context")
    }

    /**
     * Set print receipt for transaction requests.
     * Call from app/app-compose when loading connection/settings preferences.
     */
    fun setPrintReceipt(receipt: PrintReceipt) {
        this.printReceipt = receipt
    }

    private fun getClient(): com.sunmi.tapro.taplink.sdk.TaplinkClient {
        return TaplinkSDK.getClient()
    }

    private fun buildAmountInfo(
        amount: BigDecimal,
        currency: String,
        tipAmount: BigDecimal? = null,
        taxAmount: BigDecimal? = null,
        cashbackAmount: BigDecimal? = null,
        serviceFee: BigDecimal? = null
    ): AmountInfo {
        fun toCents(dollarAmount: BigDecimal): BigDecimal {
            return (dollarAmount * BigDecimal(CENTS_TO_DOLLARS_MULTIPLIER)).setScale(0, RoundingMode.HALF_UP)
        }
        var amountInfo = AmountInfo(
            orderAmount = toCents(amount),
            pricingCurrency = currency
        )
        tipAmount?.let { amountInfo = amountInfo.setTipAmount(toCents(it)) }
        taxAmount?.let { amountInfo = amountInfo.setTaxAmount(toCents(it)) }
        cashbackAmount?.let { amountInfo = amountInfo.setCashbackAmount(toCents(it)) }
        serviceFee?.let { amountInfo = amountInfo.setServiceFee(toCents(it)) }
        return amountInfo
    }

    private fun buildAuthAmountInfo(amount: BigDecimal, currency: String): AuthAmountInfo {
        fun toCents(dollarAmount: BigDecimal): BigDecimal {
            return (dollarAmount * BigDecimal(CENTS_TO_DOLLARS_MULTIPLIER)).setScale(0, RoundingMode.HALF_UP)
        }
        return AuthAmountInfo(
            orderAmount = toCents(amount),
            pricingCurrency = currency
        )
    }

    private fun buildPaymentMethodInfo(
        category: PaymentCategory = PaymentCategory.CARD,
        id: PaymentMethodId? = null,
        subId: PaymentMethodSubId? = null
    ): PaymentMethodInfo {
        return PaymentMethodInfo(
            category = category,
            id = id,
            subId = subId
        )
    }

    private fun getProgressMessage(event: SdkPaymentEvent, transactionType: String): String {
        return event.eventMsg.takeIf { it.isNotBlank() }
            ?: "$transactionType transaction processing..."
    }

    /** 统一打印发往 SDK 的请求对象 */
    private fun logSdkRequest(action: String, req: Any) {
        Log.d(TAG, "SDK_REQ [$action]: $req")
    }

    /** 统一打印 SDK 返回的成功结果对象 */
    private fun logSdkResult(result: Any) {
        Log.d(TAG, "SDK_RESULT: $result")
    }

    /** 统一打印 SDK 返回的失败/错误对象，输出具体参数便于排查 */
    private fun logSdkFailure(action: String, error: SdkPaymentError) {
        val parts = mutableListOf<String>()
        parts.add("code=${error.code}")
        parts.add("message=${error.message}")
        for (prop in listOf("suggestion", "traceId", "referenceOrderId", "transactionId", "transactionRequestId", "canRetryWithSameId")) {
            runCatching {
                val getterName = "get" + prop.replaceFirstChar { it.uppercaseChar() }
                val getter = error.javaClass.getMethod(getterName)
                val value = getter.invoke(error)
                parts.add("$prop=$value")
            }
        }
        runCatching {
            val detail = error.javaClass.getMethod("getDetail").invoke(error)
            if (detail != null) parts.add("detail=$detail")
        }
        Log.e(TAG, "SDK_FAILURE [$action]: ${parts.joinToString(", ")}")
    }

    override fun connect(connectionConfig: ConnectionConfig, listener: ConnectionListener) {
        this.connectionListener = listener
        logSdkRequest("CONNECT", connectionConfig)
        Log.d(TAG, "Current connection status: connected=${TaplinkSDK.isConnected()}, connecting=$connecting")
        try {
            TaplinkSDK.connect(connectionConfig, object : SdkConnectionListener {
                override fun onConnected(deviceId: String, taproVersion: String) {
                    Log.d(TAG, "SDK_RESULT [CONNECT]: success, deviceId=$deviceId, taproVersion=$taproVersion")
                    connecting = false
                    handleConnected(deviceId, taproVersion)
                }
                override fun onDisconnected(reason: String) {
                    Log.d(TAG, "SDK_RESULT [CONNECT]: disconnected, reason=$reason")
                    connecting = false
                    handleDisconnected(reason)
                }
                override fun onError(error: SdkConnectionError) {
                    Log.e(TAG, "SDK_FAILURE [CONNECT]: $error")
                    connecting = false
                    handleConnectionError(error.code, error.message)
                }
            })
        } catch (e: NoSuchMethodError) {
            Log.e(TAG, "SDK API incompatibility detected", e)
            connecting = false
            handleConnectionError(
                "SDK_INCOMPATIBLE",
                "SDK version mismatch. Please check SDK integration: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during connection", e)
            connecting = false
            handleConnectionError(
                "CONNECTION_EXCEPTION",
                "Connection failed: ${e.message ?: "Unknown error"}"
            )
        }
    }

    private fun handleConnected(deviceId: String, version: String) {
        connected = true
        connectedDeviceId = deviceId
        taproVersion = version
        try {
            Handler(Looper.getMainLooper()).post {
                try {
                    connectionListener?.onConnected(deviceId, version)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in connectionListener.onConnected", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post to main thread", e)
            if (Looper.myLooper() == Looper.getMainLooper()) {
                try { connectionListener?.onConnected(deviceId, version) } catch (ex: Exception) { Log.e(TAG, "Direct call also failed", ex) }
            }
        }
    }

    private fun handleDisconnected(reason: String) {
        connected = false
        connectedDeviceId = null
        taproVersion = null
        try {
            Handler(Looper.getMainLooper()).post {
                try { connectionListener?.onDisconnected(reason) } catch (e: Exception) { Log.e(TAG, "Exception in connectionListener.onDisconnected", e) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post to main thread", e)
            if (Looper.myLooper() == Looper.getMainLooper()) {
                try { connectionListener?.onDisconnected(reason) } catch (ex: Exception) { Log.e(TAG, "Direct call also failed", ex) }
            }
        }
    }

    private fun handleConnectionError(code: String, message: String) {
        connected = false
        connectedDeviceId = null
        taproVersion = null
        try {
            Handler(Looper.getMainLooper()).post {
                try { connectionListener?.onError(code, message) } catch (e: Exception) { Log.e(TAG, "Exception in connectionListener.onError", e) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post to main thread", e)
            if (Looper.myLooper() == Looper.getMainLooper()) {
                try { connectionListener?.onError(code, message) } catch (ex: Exception) { Log.e(TAG, "Direct call also failed", ex) }
            }
        }
    }

    private fun handlePaymentFailure(
        transactionType: String,
        error: SdkPaymentError,
        callback: PaymentCallback
    ) {
        logSdkFailure(transactionType, error)
        if (isConnectionRelatedError(error.code)) {
            handleConnectionLost("Payment error: ${error.message}")
        }
        callback.onFailure(error.code, error.message)
    }

    private fun handleConnectionLost(reason: String) {
        if (connected) {
            connected = false
            connectedDeviceId = null
            taproVersion = null
            try {
                Handler(Looper.getMainLooper()).post {
                    try { connectionListener?.onDisconnected(reason) } catch (e: Exception) { Log.e(TAG, "Exception in connectionListener.onDisconnected", e) }
                }
            } catch (e: Exception) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    try { connectionListener?.onDisconnected(reason) } catch (ex: Exception) { }
                }
            }
        }
    }

    private fun isConnectionRelatedError(code: String): Boolean {
        if (code.startsWith("21") || code.startsWith("22") || code.startsWith("23") || code.startsWith("24") || code.startsWith("25")) return true
        return when (code) {
            ConnectionErrorCodes.TARGET_APP_CRASHED,
            ConnectionErrorCodes.CONNECTION_TIMEOUT,
            ConnectionErrorCodes.CONNECTION_FAILED,
            ConnectionErrorCodes.CONNECTION_LOST,
            ConnectionErrorCodes.SERVICE_DISCONNECTED,
            ConnectionErrorCodes.SERVICE_BINDING_FAILED -> true
            else -> code.startsWith("C")
        }
    }

    private fun handlePaymentResult(sdkResult: SdkPaymentResult, callback: PaymentCallback) {
        logSdkResult(sdkResult)
        fun toDollars(centsAmount: BigDecimal?): BigDecimal? {
            return centsAmount?.divide(BigDecimal(CENTS_TO_DOLLARS_MULTIPLIER), AMOUNT_DECIMAL_PLACES, RoundingMode.HALF_UP)
        }
        val result = PaymentResult(
            code = sdkResult.code,
            message = sdkResult.message ?: "Success",
            traceId = sdkResult.traceId,
            transactionId = sdkResult.transactionId,
            referenceOrderId = sdkResult.referenceOrderId,
            transactionRequestId = sdkResult.transactionRequestId,
            transactionStatus = "SUCCESS",
            transactionType = sdkResult.transactionType,
            amount = sdkResult.amount?.let { amt ->
                TransactionAmount(
                    priceCurrency = amt.priceCurrency,
                    transAmount = toDollars(amt.transAmount),
                    orderAmount = toDollars(amt.orderAmount),
                    taxAmount = toDollars(amt.taxAmount),
                    serviceFee = toDollars(amt.serviceFee),
                    tipAmount = toDollars(amt.tipAmount),
                    cashbackAmount = toDollars(amt.cashbackAmount)
                )
            },
            createTime = sdkResult.createTime,
            completeTime = sdkResult.completeTime,
            cardInfo = sdkResult.cardInfo?.let { card ->
                CardInfo(
                    maskedPan = card.maskedPan,
                    cardNetworkType = card.cardNetworkType,
                    paymentMethodId = card.paymentMethodId,
                    subPaymentMethodId = card.subPaymentMethodId,
                    entryMode = card.entryMode,
                    authenticationMethod = card.authenticationMethod,
                    cardholderName = card.cardholderName,
                    expiryDate = card.expiryDate,
                    issuerBank = card.issuerBank,
                    cardBrand = card.cardBrand
                )
            },
            batchNo = sdkResult.batchNo,
            voucherNo = sdkResult.voucherNo,
            stan = sdkResult.stan,
            rrn = sdkResult.rrn,
            authCode = sdkResult.authCode,
            transactionResultCode = sdkResult.transactionResultCode,
            transactionResultMsg = sdkResult.transactionResultMsg,
            description = sdkResult.description,
            attach = sdkResult.attach,
            tipAmount = toDollars(sdkResult.tipAmount),
            totalAuthorizedAmount = toDollars(sdkResult.totalAuthorizedAmount),
            merchantRefundNo = sdkResult.merchantRefundNo,
            originalTransactionId = sdkResult.originalTransactionId,
            originalTransactionRequestId = sdkResult.originalTransactionRequestId,
            batchCloseInfo = sdkResult.batchCloseInfo?.let { bci ->
                BatchCloseInfo(
                    totalCount = bci.totalCount ?: 0,
                    totalAmount = toDollars(bci.totalAmount) ?: BigDecimal.ZERO,
                    totalTip = toDollars(bci.totalTip) ?: BigDecimal.ZERO,
                    totalTax = toDollars(bci.totalTax) ?: BigDecimal.ZERO,
                    totalServiceFee = toDollars(bci.totalServiceFee) ?: BigDecimal.ZERO,
                    cashDiscount = toDollars(bci.cashDiscount) ?: BigDecimal.ZERO,
                    closeTime = bci.closeTime ?: ""
                )
            }
        )
        callback.onSuccess(result)
    }

    override fun disconnect() {
        TaplinkSDK.disconnect()
        handleDisconnected("User initiated disconnection")
    }

    override fun getConnectedDeviceId(): String? = connectedDeviceId
    override fun getTaproVersion(): String? = taproVersion

    override fun executeSale(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        paymentCategory: PaymentCategory,
        paymentMethodId: String?,
        subPaymentMethodId: String?,
        cardNetworkType: String?,
        tipAmount: BigDecimal?,
        taxAmount: BigDecimal?,
        cashbackAmount: BigDecimal?,
        serviceFee: BigDecimal?,
        staffInfo: StaffInfo?,
        callback: PaymentCallback
    ) {
        // Convert String? to SDK types for Taplink SDK
        val sdkPaymentMethodId = paymentMethodId?.let { PaymentMethodId.valueOf(it) }
        val sdkSubPaymentMethodId = subPaymentMethodId?.let { PaymentMethodSubId.valueOf(it) }
        val sdkCardNetworkType = cardNetworkType?.let { CardNetworkType.fromValue(it) }
        val amountInfo = buildAmountInfo(amount, currency, tipAmount, taxAmount, cashbackAmount, serviceFee)
        val saleRequest = SaleRequest(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            amount = amountInfo,
            description = description,
            paymentMethod = buildPaymentMethodInfo(paymentCategory, sdkPaymentMethodId, sdkSubPaymentMethodId),
            cardNetworkType = sdkCardNetworkType,
            printReceipt = printReceipt
        )
        logSdkRequest("SALE", saleRequest)
        getClient().sale(saleRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("SALE", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { callback.onProgress("PROCESSING", getProgressMessage(event, "SALE")) }
        })
    }

    override fun executeAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        callback: PaymentCallback
    ) {
        val authRequest = AuthRequest(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            amount = buildAuthAmountInfo(amount, currency),
            description = description,
            printReceipt = printReceipt
        )
        logSdkRequest("AUTH", authRequest)
        getClient().auth(authRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("AUTH", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { callback.onProgress("PROCESSING", getProgressMessage(event, "AUTH")) }
        })
    }

    override fun executeForcedAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        tipAmount: BigDecimal?,
        taxAmount: BigDecimal?,
        callback: PaymentCallback
    ) {
        val forcedAuthRequest = ForcedAuthRequest(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            amount = buildAuthAmountInfo(amount, currency),
            description = description,
            printReceipt = printReceipt
        )
        logSdkRequest("FORCED_AUTH", forcedAuthRequest)
        getClient().forcedAuth(forcedAuthRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("FORCED_AUTH", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { callback.onProgress("PROCESSING", getProgressMessage(event, "FORCED_AUTH")) }
        })
    }

    override fun executeRefund(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        originalTransactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        reason: String?,
        callback: PaymentCallback
    ) {
        val finalDescription = if (reason != null) {
            if (description.isNotEmpty()) "$description (Reason: $reason)" else "Reason: $reason"
        } else description

        // Referenced refund: provide originalTransactionId or originalTransactionRequestId.
        //   referenceOrderId is NOT passed — server auto-associates the original order.
        // Unreferenced refund: both original IDs empty, referenceOrderId is REQUIRED.
        val isReferenced = originalTransactionId.isNotEmpty() || originalTransactionRequestId.isNotEmpty()
        val refundRequest = RefundRequest(
            transactionRequestId = transactionRequestId,
            amount = buildAmountInfo(amount, currency),
            description = finalDescription,
            originalTransactionId = originalTransactionId.takeIf { it.isNotEmpty() },
            originalTransactionRequestId = originalTransactionRequestId.takeIf { it.isNotEmpty() && originalTransactionId.isEmpty() },
            referenceOrderId = if (!isReferenced) referenceOrderId else null,
            printReceipt = printReceipt
        )
        logSdkRequest("REFUND", refundRequest)
        getClient().refund(refundRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("REFUND", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { callback.onProgress("PROCESSING", getProgressMessage(event, "REFUND")) }
        })
    }

    override fun executeVoid(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        description: String,
        reason: String?,
        callback: PaymentCallback
    ) {
        val finalDescription = if (reason != null) {
            if (description.isNotEmpty()) "$description (Reason: $reason)" else "Reason: $reason"
        } else description
        val voidRequest = VoidRequest(
            originalTransactionId = originalTransactionId.takeIf { it.isNotEmpty() },
            originalTransactionRequestId = null,
            transactionRequestId = transactionRequestId,
            description = finalDescription.takeIf { it.isNotEmpty() },
            attach = null,
            notifyUrl = null,
            printReceipt = printReceipt
        )
        logSdkRequest("VOID", voidRequest)
        getClient().void(voidRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("VOID", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { callback.onProgress("PROCESSING", getProgressMessage(event, "VOID")) }
        })
    }

    override fun executePostAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        tipAmount: BigDecimal?,
        taxAmount: BigDecimal?,
        cashbackAmount: BigDecimal?,
        serviceFee: BigDecimal?,
        callback: PaymentCallback
    ) {
        val amountInfo = buildAmountInfo(amount, currency, tipAmount, taxAmount, cashbackAmount, serviceFee)
        val postAuthRequest = PostAuthRequest(
            originalTransactionId = originalTransactionId,
            transactionRequestId = transactionRequestId,
            amount = amountInfo,
            description = description,
            printReceipt = printReceipt
        )
        logSdkRequest("POST_AUTH", postAuthRequest)
        getClient().postAuth(postAuthRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("POST_AUTH", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { callback.onProgress("PROCESSING", getProgressMessage(event, "POST_AUTH")) }
        })
    }

    override fun executeIncrementalAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        callback: PaymentCallback
    ) {
        val incrementalAuthRequest = IncrementalAuthRequest(
            originalTransactionId = originalTransactionId,
            transactionRequestId = transactionRequestId,
            amount = buildAuthAmountInfo(amount, currency),
            description = description,
            printReceipt = printReceipt
        )
        logSdkRequest("INCREMENTAL_AUTH", incrementalAuthRequest)
        getClient().incrementalAuth(incrementalAuthRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("INCREMENT_AUTH", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { callback.onProgress("PROCESSING", getProgressMessage(event, "INCREMENT_AUTH")) }
        })
    }

    override fun executeTipAdjust(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        originalTransactionRequestId: String,
        tipAmount: BigDecimal,
        description: String,
        callback: PaymentCallback
    ) {
        val tipAmountInCents = (tipAmount * BigDecimal(CENTS_TO_DOLLARS_MULTIPLIER)).setScale(0, RoundingMode.HALF_UP)
        val tipAdjustRequest = TipAdjustRequest(
            transactionRequestId = transactionRequestId,
            originalTransactionId = originalTransactionId.takeIf { it.isNotEmpty() },
            originalTransactionRequestId = originalTransactionRequestId.takeIf { it.isNotEmpty() },
            tipAmount = tipAmountInCents,
            attach = description.takeIf { it.isNotEmpty() }
        )
        logSdkRequest("TIP_ADJUST", tipAdjustRequest)
        getClient().tipAdjust(tipAdjustRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("TIP_ADJUST", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { callback.onProgress("PROCESSING", getProgressMessage(event, "TIP_ADJUST")) }
        })
    }

    override fun executeQuery(transactionRequestId: String, callback: PaymentCallback) {
        val queryRequest = QueryRequest().setTransactionRequestId(transactionRequestId)
        logSdkRequest("QUERY", queryRequest)
        getClient().query(queryRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("QUERY", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { callback.onProgress("PROCESSING", getProgressMessage(event, "QUERY")) }
        })
    }

    override fun executeQueryByTransactionId(transactionId: String, callback: PaymentCallback) {
        val queryRequest = QueryRequest().setTransactionId(transactionId)
        logSdkRequest("QUERY_BY_TXN_ID", queryRequest)
        getClient().query(queryRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("QUERY", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { callback.onProgress("PROCESSING", getProgressMessage(event, "QUERY")) }
        })
    }

    override fun executeBatchClose(
        transactionRequestId: String,
        description: String,
        callback: PaymentCallback
    ) {
        val batchCloseRequest = BatchCloseRequest(transactionRequestId = transactionRequestId, description = description)
        logSdkRequest("BATCH_CLOSE", batchCloseRequest)
        getClient().batchClose(batchCloseRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("BATCH_CLOSE", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { callback.onProgress("PROCESSING", getProgressMessage(event, "BATCH_CLOSE")) }
        })
    }

    override fun executeAbort(
        originalTransactionId: String?,
        originalTransactionRequestId: String?,
        description: String?,
        callback: PaymentCallback
    ) {
        val abortRequest = AbortRequest(
            originalTransactionRequestId = originalTransactionRequestId,
            description = description,
            attach = null
        )
        logSdkRequest("ABORT", abortRequest)
        getClient().abort(abortRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("ABORT", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { callback.onProgress("PROCESSING", getProgressMessage(event, "ABORT")) }
        })
    }

//    override fun executeAbort(
//        originalTransactionId: String?,
//        originalTransactionRequestId: String?,
//        description: String?,
//        callback: PaymentCallback
//    ) {
//        val refOrderId = originalTransactionId?.takeIf { it.isNotEmpty() }
//            ?: "ABORT_${System.currentTimeMillis()}"
//        val request = PaymentRequest.builder()
//            .setAction("ABORT")
//            .setReferenceOrderId(refOrderId)
//            .setOriginalTransactionRequestId(originalTransactionRequestId ?: "")
//            .setTransactionRequestId(refOrderId)
//            .setDescription(description ?: "")
//            .setReason("reason")
//            .build()
//
//        logSdkRequest("ABORT", request)
//        TaplinkSDK.execute(request, object : SdkPaymentCallback {
//            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
//            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("ABORT", error, callback) }
//            override fun onProgress(event: SdkPaymentEvent) { callback.onProgress("PROCESSING", getProgressMessage(event, "ABORT")) }
//        })
//    }
}
