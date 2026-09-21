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
import com.sunmi.tapro.taplink.sdk.model.common.SignatureConfig
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo
import com.sunmi.tapro.taplink.sdk.model.common.TipConfig
import com.sunmi.tapro.taplink.sdk.enums.TipMode
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
import com.google.gson.GsonBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener as SdkConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.DiscoveryListener as SdkDiscoveryListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback as SdkPaymentCallback
import com.sunmi.tapro.taplink.sdk.callback.TerminalInfoCallback as SdkTerminalInfoCallback
import com.sunmi.tapro.taplink.sdk.error.ConnectionError as SdkConnectionError
import com.sunmi.tapro.taplink.sdk.error.PaymentError as SdkPaymentError
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent as SdkPaymentEvent
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult as SdkPaymentResult
import com.sunmi.tapro.taplink.demo.service.HeadlessProgressEvent

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

    /** Explicit request timeout for foreground payment requests, in the SDK wire unit. */
    @Volatile
    private var requestTimeout: Long? = null

    /** Whether this app has enabled the secondary display in Settings. */
    @Volatile
    private var secondaryDisplayEnabled = true

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

    fun setRequestTimeoutMillis(timeoutMillis: Long) {
        requestTimeout = timeoutMillis.takeIf { it > 0L }
        Log.d(TAG, "Foreground request timeout configured: ${requestTimeout}ms")
    }

    override fun setSecondaryDisplayEnabled(enabled: Boolean) {
        secondaryDisplayEnabled = enabled
    }

    override fun getTerminalInfo(callback: TerminalInfoCallback) {
        logSdkRequest(
            "GET_TERMINAL_INFO",
            mapOf("action" to "GET_TERMINAL_INFO")
        )
        getClient().getTerminalInfo(object : SdkTerminalInfoCallback {
            override fun onSuccess(info: com.sunmi.tapro.taplink.sdk.model.response.TerminalInfo) {
                logSdkResult(info)
                callback.onSuccess(
                    TerminalInfo(
                        merchantId = info.merchantId,
                        dbaName = info.dbaName,
                        mcc = info.mcc,
                        country = info.country,
                        stateName = info.stateName,
                        cityName = info.cityName,
                        street = info.street,
                        detailAddress = info.detailAddress,
                        zipCode = info.zipCode,
                        midList = info.midList.map { mid ->
                            MerchantMid(
                                channelCode = mid.channelCode,
                                channelName = mid.channelName,
                                mid = mid.mid
                            )
                        },
                        terminal = Terminal(
                            sn = info.terminal.sn,
                            vendor = info.terminal.vendor,
                            model = info.terminal.model,
                            tidList = info.terminal.tidList.map { tid ->
                                TerminalTid(
                                    channelCode = tid.channelCode,
                                    channelName = tid.channelName,
                                    tid = tid.tid
                                )
                            }
                        )
                    )
                )
            }

            override fun onFailure(error: SdkPaymentError) {
                logSdkFailure("GET_TERMINAL_INFO", error)
                callback.onFailure(error.code, error.message)
            }
        })
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
        serviceFee: BigDecimal? = null,
        surchargeAmount: BigDecimal? = null
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
        surchargeAmount?.let { amountInfo = amountInfo.setSurchargeAmount(toCents(it)) }

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

    private fun shouldEmitTipProcessing(tipConfig: TipConfig?): Boolean {
        return tipConfig?.onScreenTip == true && tipConfig.tipMode == TipMode.ON_SALE
    }

    private fun emitTipProcessing(callback: PaymentCallback) {
        callback.onProgress(
            SdkPaymentEvent.TipProcessing.eventCode,
            SdkPaymentEvent.TipProcessing.eventMsg
        )
    }

    private fun forwardProgress(
        callback: PaymentCallback,
        event: SdkPaymentEvent,
        transactionType: String,
        tipConfig: TipConfig? = null
    ) {
        if (event is SdkPaymentEvent.HeadlessEvent) {
            Log.d(
                TAG,
                "HEADLESS_PROGRESS [$transactionType]: code=${event.eventCode}, data=${event.data}"
            )
            callback.onHeadlessProgress(
                HeadlessProgressEvent(
                    eventCode = event.eventCode,
                    eventMessage = event.eventMsg,
                    eventId = event.eventId,
                    transactionRequestId = event.transactionRequestId,
                    referenceOrderId = event.referenceOrderId,
                    cancelable = event.cancelable,
                    timestamp = event.timestamp,
                    data = event.data,
                    rawEventCode = event.rawEventCode
                )
            )
            return
        }

        if (shouldEmitTipProcessing(tipConfig) && event.eventCode == SdkPaymentEvent.Processing.eventCode) {
            callback.onProgress(
                SdkPaymentEvent.TipProcessing.eventCode,
                SdkPaymentEvent.TipProcessing.eventMsg
            )
            return
        }
        callback.onProgress(event.eventCode, getProgressMessage(event, transactionType))
    }

    private val objectMapper by lazy {
        ObjectMapper().registerKotlinModule()
            .enable(SerializationFeature.INDENT_OUTPUT)
    }

    /** Convert any object to JSON string for logging */
    private fun toJson(obj: Any): String {
        return try {
            objectMapper.writeValueAsString(obj)
        } catch (e: Exception) {
            obj.toString()
        }
    }

    /** Log SDK request as JSON */
    private fun logSdkRequest(action: String, req: Any) {
        Log.d(TAG, "SDK_REQ [$action]: ${toJson(req)}")
    }

    /** Log SDK success result as JSON */
    private fun logSdkResult(result: Any) {
        Log.d(TAG, "SDK_RESULT: ${toJson(result)}")
    }

    /** Log SDK failure/error as JSON */
    private fun logSdkFailure(action: String, error: SdkPaymentError) {
        Log.e(TAG, "SDK_FAILURE [$action]: ${toJson(error)}")
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

    override fun autoDiscoverAndConnect(listener: ConnectionListener) {
        this.connectionListener = listener
        Log.d(TAG, "SDK_REQUEST [AUTO_DISCOVER]: starting mDNS auto-discovery")
        try {
            TaplinkSDK.autoDiscoverAndConnect(object : SdkConnectionListener {
                override fun onConnected(deviceId: String, taproVersion: String) {
                    Log.d(TAG, "SDK_RESULT [AUTO_DISCOVER]: success, deviceId=$deviceId, taproVersion=$taproVersion")
                    connecting = false
                    handleConnected(deviceId, taproVersion)
                }
                override fun onDisconnected(reason: String) {
                    Log.d(TAG, "SDK_RESULT [AUTO_DISCOVER]: disconnected, reason=$reason")
                    connecting = false
                    handleDisconnected(reason)
                }
                override fun onError(error: SdkConnectionError) {
                    Log.e(TAG, "SDK_FAILURE [AUTO_DISCOVER]: $error")
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
            Log.e(TAG, "Unexpected error during auto-discovery", e)
            connecting = false
            handleConnectionError(
                "DISCOVER_EXCEPTION",
                "Auto-discovery failed: ${e.message ?: "Unknown error"}"
            )
        }
    }

    override fun discoverLanServices(
        onResult: (List<com.sunmi.tapro.taplink.sdk.model.DiscoveredService>) -> Unit,
        onError: (String, String) -> Unit
    ) {
        Log.d(TAG, "SDK_REQUEST [DISCOVER]: starting mDNS discovery (discover-only)")
        try {
            TaplinkSDK.discoverLanServices(object : SdkDiscoveryListener {
                override fun onDiscovered(services: List<com.sunmi.tapro.taplink.sdk.model.DiscoveredService>) {
                    Log.d(TAG, "SDK_RESULT [DISCOVER]: found ${services.size} services")
                    onResult(services)
                }
                override fun onError(error: SdkConnectionError) {
                    Log.e(TAG, "SDK_FAILURE [DISCOVER]: $error")
                    onError(error.code, error.message)
                }
            })
        } catch (e: NoSuchMethodError) {
            Log.e(TAG, "SDK API incompatibility detected", e)
            onError("SDK_INCOMPATIBLE", "SDK version mismatch. Please check SDK integration: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during discovery", e)
            onError("DISCOVER_EXCEPTION", "Discovery failed: ${e.message ?: "Unknown error"}")
        }
    }

    override fun scanLanQrCode(
        onResult: (List<com.sunmi.tapro.taplink.sdk.model.DiscoveredService>) -> Unit,
        onError: (String, String) -> Unit
    ) {
        Log.d(TAG, "SDK_REQUEST [SCAN]: launching QR scanner (scan-only)")
        try {
            TaplinkSDK.scanLanQrCode(object : SdkDiscoveryListener {
                override fun onDiscovered(services: List<com.sunmi.tapro.taplink.sdk.model.DiscoveredService>) {
                    Log.d(TAG, "SDK_RESULT [SCAN]: scanned ${services.size} service(s)")
                    onResult(services)
                }
                override fun onError(error: SdkConnectionError) {
                    Log.e(TAG, "SDK_FAILURE [SCAN]: $error")
                    onError(error.code, error.message)
                }
            })
        } catch (e: NoSuchMethodError) {
            Log.e(TAG, "SDK API incompatibility detected", e)
            onError("SDK_INCOMPATIBLE", "SDK version mismatch. Please check SDK integration: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during scan", e)
            onError("SCAN_EXCEPTION", "QR scan failed: ${e.message ?: "Unknown error"}")
        }
    }

    override fun scanAndConnect(listener: ConnectionListener) {
        this.connectionListener = listener
        Log.d(TAG, "SDK_REQUEST [SCAN_CONNECT]: launching QR scanner")
        try {
            TaplinkSDK.scanAndConnect(object : SdkConnectionListener {
                override fun onConnected(deviceId: String, taproVersion: String) {
                    Log.d(TAG, "SDK_RESULT [SCAN_CONNECT]: success, deviceId=$deviceId, taproVersion=$taproVersion")
                    connecting = false
                    handleConnected(deviceId, taproVersion)
                }
                override fun onDisconnected(reason: String) {
                    Log.d(TAG, "SDK_RESULT [SCAN_CONNECT]: disconnected, reason=$reason")
                    connecting = false
                    handleDisconnected(reason)
                }
                override fun onError(error: SdkConnectionError) {
                    Log.e(TAG, "SDK_FAILURE [SCAN_CONNECT]: $error")
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
            Log.e(TAG, "Unexpected error during scan connect", e)
            connecting = false
            handleConnectionError(
                "SCAN_EXCEPTION",
                "QR scan connect failed: ${e.message ?: "Unknown error"}"
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
        // 交易失败也视作交易结束，按需求在结束时再次请求副屏播放器就绪。
        if (secondaryDisplayEnabled) {
            ensureUsbScreenPlayerReady()
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

    private fun handlePaymentResult(
        sdkResult: SdkPaymentResult,
        callback: PaymentCallback,
        triggerScreenReady: Boolean = true
    ) {
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
            transactionStatus = sdkResult.transactionStatus,
            transactionBatchStatus = sdkResult.transactionBatchStatus,
            relatedTransactionStatus = sdkResult.relatedTransactionStatus,
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
            receiptJson = sdkResult.receiptJson,
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
                    totalSurchargeAmount = toDollars(bci.totalSurchargeAmount) ?: BigDecimal.ZERO,
                    totalServiceFee = toDollars(bci.totalServiceFee) ?: BigDecimal.ZERO,
                    cashDiscount = toDollars(bci.cashDiscount) ?: BigDecimal.ZERO,
                    closeTime = bci.closeTime ?: ""
                )
            }
        )
        // 仅在终态（成功/失败）时触发“交易结束”时机的副屏播放器就绪请求。
        // abort 响应不触发，避免干扰 Tapro 侧仍在进行中的交易。
        if (secondaryDisplayEnabled && triggerScreenReady && result.isTerminal()) {
            ensureUsbScreenPlayerReady()
        }
        callback.onSuccess(result)
    }

    override fun disconnect() {
        TaplinkSDK.disconnect()
        handleDisconnected("User initiated disconnection")
    }

    override fun getConnectedDeviceId(): String? = connectedDeviceId
    override fun getTaproVersion(): String? = taproVersion

    /**
     * Notify tapro to pull up its secondary screen player through the OPEN_USB_SCREEN_PLAYER control command.
     *
     * This method is an optional feature: if the SDK does not support it or fails during runtime, only warning logs will be recorded without interrupting the core business process.
     */
    override fun ensureUsbScreenPlayerReady() {
        if (!secondaryDisplayEnabled) {
            Log.d(TAG, "USB screen player request skipped: secondary display is disabled")
            return
        }
        val callback = object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) {
                Log.d(
                    TAG,
                    "SDK_RESULT [OPEN_USB_SCREEN_PLAYER]: code=${result.code}, msg=${result.message}"
                )
            }

            override fun onFailure(error: SdkPaymentError) {
                Log.w(
                    TAG,
                    "SDK_FAILURE [OPEN_USB_SCREEN_PLAYER]: code=${error.code}, msg=${error.message}"
                )
            }

            override fun onProgress(event: SdkPaymentEvent) {
                Log.d(TAG, "SDK_PROGRESS [OPEN_USB_SCREEN_PLAYER]: ${event.eventCode}")
            }
        }

        try {
            TaplinkSDK.openUsbScreenPlayer(callback)
        } catch (e: NoSuchMethodError) {
            // SDK 版本不支持此功能 - 仅记录，不阻断业务流程
            Log.w(
                TAG,
                "openUsbScreenPlayer not supported in this SDK version: ${e.message}"
            )
        } catch (e: LinkageError) {
            // SDK 内部链接错误 - 仅记录，不阻断业务流程
            Log.w(
                TAG,
                "openUsbScreenPlayer linkage error (SDK incompatibility): ${e.message}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to invoke openUsbScreenPlayer: ${e.message}")
        }
    }

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
        surchargeAmount: BigDecimal?,
        staffInfo: StaffInfo?,
        tipConfig: TipConfig?,
        attach: String?,
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
        surchargeAmount: BigDecimal?,
        staffInfo: StaffInfo?,
        tipConfig: TipConfig?,
        attach: String?,
        callback: PaymentCallback,
        signatureConfig: SignatureConfig?
    ) {
        // Convert String? to SDK types for Taplink SDK
        val sdkPaymentMethodId = paymentMethodId?.let { PaymentMethodId.valueOf(it) }
        val sdkSubPaymentMethodId = subPaymentMethodId?.let { PaymentMethodSubId.valueOf(it) }
        val sdkCardNetworkType = cardNetworkType?.let { CardNetworkType.fromValue(it) }
        val amountInfo = buildAmountInfo(amount, currency, tipAmount, taxAmount, cashbackAmount, serviceFee, surchargeAmount)
        if (tipConfig != null) {
            Log.d(TAG, "TIP_CONFIG [SALE]: applied tipConfig=$tipConfig")
        } else {
            Log.d(TAG, "TIP_CONFIG [SALE]: tipConfig disabled, not included in request")
        }
        if (shouldEmitTipProcessing(tipConfig)) {
            emitTipProcessing(callback)
        }
        val saleRequest = SaleRequest(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            amount = amountInfo,
            description = description,
            paymentMethod = buildPaymentMethodInfo(
                paymentCategory,
                sdkPaymentMethodId,
                sdkSubPaymentMethodId
            ),
            cardNetworkType = sdkCardNetworkType,
            attach = attach,
            notifyUrl = null,
            requestTimeout = requestTimeout,
            staffInfo = staffInfo,
            tipConfig = tipConfig,
            printReceipt = printReceipt,
            signatureConfig = signatureConfig
        )
        logSdkRequest("SALE", saleRequest)
        getClient().sale(saleRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("SALE", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { forwardProgress(callback, event, "SALE", tipConfig) }
        })
    }

    override fun executeAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        staffInfo: StaffInfo?,
        attach: String?,
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

    override fun executeAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        staffInfo: StaffInfo?,
        attach: String?,
        callback: PaymentCallback,
        signatureConfig: SignatureConfig?
    ) {
        val authRequest = AuthRequest(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            amount = buildAuthAmountInfo(amount, currency),
            description = description,
            staffInfo = staffInfo,
            attach = attach,
            printReceipt = printReceipt,
            signatureConfig = signatureConfig,
            requestTimeout = requestTimeout
        )
        logSdkRequest("AUTH", authRequest)
        getClient().auth(authRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("AUTH", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { forwardProgress(callback, event, "AUTH") }
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
        staffInfo: StaffInfo?,
        attach: String?,
        callback: PaymentCallback
    ) {
        val forcedAuthRequest = ForcedAuthRequest(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            amount = buildAuthAmountInfo(amount, currency),
            description = description,
            staffInfo = staffInfo,
            attach = attach,
            printReceipt = printReceipt,
            requestTimeout = requestTimeout
        )
        logSdkRequest("FORCED_AUTH", forcedAuthRequest)
        getClient().forcedAuth(forcedAuthRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("FORCED_AUTH", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { forwardProgress(callback, event, "FORCED_AUTH") }
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
        paymentCategory: PaymentCategory?,
        cardNetworkType: String?,
        staffInfo: StaffInfo?,
        signatureConfig: SignatureConfig?,
        callback: PaymentCallback
    ) {
        val finalDescription = if (reason != null) {
            if (description.isNotEmpty()) "$description (Reason: $reason)" else "Reason: $reason"
        } else description

        // Referenced refund: provide originalTransactionId or originalTransactionRequestId.
        //   referenceOrderId is NOT passed — server auto-associates the original order.
        // Unreferenced refund: both original IDs empty, referenceOrderId is REQUIRED.
        val isReferenced = originalTransactionId.isNotEmpty() || originalTransactionRequestId.isNotEmpty()

        // Build paymentMethod from category when specified (needed for QR/EBT refund routing)
        val paymentMethodInfo = paymentCategory?.let { category ->
            if (category == PaymentCategory.EBT) {
                buildPaymentMethodInfo(
                    category = category,
                    id = PaymentMethodId.EBT,
                    subId = PaymentMethodSubId.SNAP
                )
            } else {
                buildPaymentMethodInfo(category = category)
            }
        }
        val explicitCardNetworkType = cardNetworkType?.let { CardNetworkType.fromValue(it) }
        val refundCardNetworkType = when (paymentCategory) {
            null, PaymentCategory.CARD -> explicitCardNetworkType
            else -> null
        }

        val refundRequest = RefundRequest(
            transactionRequestId = transactionRequestId,
            amount = buildAmountInfo(amount, currency),
            description = finalDescription,
            originalTransactionId = originalTransactionId.takeIf { it.isNotEmpty() },
            originalTransactionRequestId = originalTransactionRequestId.takeIf { it.isNotEmpty() && originalTransactionId.isEmpty() },
            referenceOrderId = if (!isReferenced) referenceOrderId else null,
            paymentMethod = paymentMethodInfo,
            cardNetworkType = refundCardNetworkType,
            staffInfo = staffInfo,
            printReceipt = printReceipt,
            // Only an unreferenced refund can capture a signature.
            signatureConfig = signatureConfig.takeIf { !isReferenced }
        )
        logSdkRequest("REFUND", refundRequest)
        getClient().refund(refundRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("REFUND", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { forwardProgress(callback, event, "REFUND") }
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
            override fun onProgress(event: SdkPaymentEvent) { forwardProgress(callback, event, "VOID") }
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
        staffInfo: StaffInfo?,
        tipConfig: TipConfig?,
        callback: PaymentCallback
    ) {
        val amountInfo = buildAmountInfo(amount, currency, tipAmount, taxAmount, cashbackAmount, serviceFee)
        if (tipConfig != null) {
            Log.d(TAG, "TIP_CONFIG [POST_AUTH]: applied tipConfig=$tipConfig")
        } else {
            Log.d(TAG, "TIP_CONFIG [POST_AUTH]: tipConfig disabled, not included in request")
        }
        val postAuthRequest = PostAuthRequest(
            originalTransactionId = originalTransactionId,
            transactionRequestId = transactionRequestId,
            amount = amountInfo,
            description = description,
            staffInfo = staffInfo,
            tipConfig = tipConfig,
            printReceipt = printReceipt
        )
        logSdkRequest("POST_AUTH", postAuthRequest)
        getClient().postAuth(postAuthRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("POST_AUTH", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { forwardProgress(callback, event, "POST_AUTH") }
        })
    }

    override fun executeIncrementalAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        staffInfo: StaffInfo?,
        callback: PaymentCallback
    ) {
        val incrementalAuthRequest = IncrementalAuthRequest(
            originalTransactionId = originalTransactionId,
            transactionRequestId = transactionRequestId,
            amount = buildAuthAmountInfo(amount, currency),
            description = description,
            staffInfo = staffInfo,
            printReceipt = printReceipt
        )
        logSdkRequest("INCREMENTAL_AUTH", incrementalAuthRequest)
        getClient().incrementalAuth(incrementalAuthRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("INCREMENT_AUTH", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { forwardProgress(callback, event, "INCREMENT_AUTH") }
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
            override fun onProgress(event: SdkPaymentEvent) { forwardProgress(callback, event, "TIP_ADJUST") }
        })
    }

    override fun executeQuery(transactionRequestId: String, callback: PaymentCallback) {
        val queryRequest = QueryRequest().setTransactionRequestId(transactionRequestId)
        logSdkRequest("QUERY", queryRequest)
        getClient().query(queryRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("QUERY", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { forwardProgress(callback, event, "QUERY") }
        })
    }

    override fun executeQueryByTransactionId(transactionId: String, callback: PaymentCallback) {
        val queryRequest = QueryRequest().setTransactionId(transactionId)
        logSdkRequest("QUERY_BY_TXN_ID", queryRequest)
        getClient().query(queryRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("QUERY", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { forwardProgress(callback, event, "QUERY") }
        })
    }

    override fun executeBatchClose(
        transactionRequestId: String,
        description: String,
        printReceipt: PrintReceipt,
        callback: PaymentCallback
    ) {
        val batchCloseRequest = BatchCloseRequest(
            transactionRequestId = transactionRequestId,
            description = description,
            printReceipt = printReceipt
        )
        logSdkRequest("BATCH_CLOSE", batchCloseRequest)
        getClient().batchClose(batchCloseRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) { handlePaymentFailure("BATCH_CLOSE", error, callback) }
            override fun onProgress(event: SdkPaymentEvent) { forwardProgress(callback, event, "BATCH_CLOSE") }
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
            override fun onSuccess(result: SdkPaymentResult) {
                Log.d(TAG, "SDK_RESULT [ABORT]: code=${result.code}, msg=${result.message}, " +
                    "transactionRequestId=${result.transactionRequestId}, transactionStatus=${result.transactionStatus}")
                handlePaymentResult(result, callback, triggerScreenReady = false)
            }

            override fun onFailure(error: SdkPaymentError) {
                Log.e(TAG, "SDK_FAILURE [ABORT]: code=${error.code}, msg=${error.message}")
                handlePaymentFailure("ABORT", error, callback)
            }

            override fun onProgress(event: SdkPaymentEvent) {
                Log.d(TAG, "SDK_PROGRESS [ABORT]: code=${event.eventCode}, msg=${event.eventMsg}")
                forwardProgress(callback, event, "ABORT")
            }
        })
    }

    /**
     * Headless 控制指令：请求切换到手输卡号。
     *
     * 说明：
     * 1. 这里只负责把控制指令透传给 SDK；
     * 2. 是否允许切换由 Tapro 根据当前交易阶段（必须 WAITING_CARD）做最终判定。
     */
    override fun executeSwitchToManualEntry(
        originalTransactionRequestId: String?,
        callback: PaymentCallback
    ) {
        Log.d(
            TAG,
            "SWITCH_TO_MANUAL_ENTRY [SDK]: originalTransactionRequestId=$originalTransactionRequestId"
        )

        // Use the dedicated SDK API so Tapro can keep the original request-id
        // consistency check and the WAITING_CARD gate in one place.
        TaplinkSDK.switchToManualEntry(originalTransactionRequestId, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) { handlePaymentResult(result, callback) }
            override fun onFailure(error: SdkPaymentError) {
                handlePaymentFailure("SWITCH_TO_MANUAL_ENTRY", error, callback)
            }
            override fun onProgress(event: SdkPaymentEvent) {
                forwardProgress(callback, event, "SWITCH_TO_MANUAL_ENTRY")
            }
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
