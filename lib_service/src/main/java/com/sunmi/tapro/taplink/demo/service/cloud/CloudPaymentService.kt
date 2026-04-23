package com.sunmi.tapro.taplink.demo.service.cloud

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.JsonObject
import com.sunmi.tapro.taplink.demo.service.util.AmountConverter
import com.sunmi.tapro.taplink.demo.service.ConnectionListener
import com.sunmi.tapro.taplink.demo.service.PaymentCallback
import com.sunmi.tapro.taplink.demo.service.PaymentService
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo
import com.sunmi.tapro.taplink.sdk.model.common.TipConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Cloud Payment Service implementation using OkHttp-based CloudHttpClient.
 *
 * Builds request JSON manually using JsonObject instead of Nexus SDK Lombok POJOs,
 * which cannot be properly serialized by Gson on Android.
 */
class CloudPaymentService : PaymentService {

    companion object {
        private const val TAG = "CloudPaymentService"

        // API paths (previously from ApiConstants)
        private const val PATH_SALE = "/v1/semi-integration/transaction/sale"
        private const val PATH_AUTH = "/v1/semi-integration/transaction/auth"
        private const val PATH_FORCED_AUTH = "/v1/semi-integration/transaction/forced-auth"
        private const val PATH_INCREMENTAL_AUTH = "/v1/semi-integration/transaction/incremental-auth"
        private const val PATH_POST_AUTH = "/v1/semi-integration/transaction/post-auth"
        private const val PATH_REFUND = "/v1/semi-integration/transaction/refund"
        private const val PATH_VOID = "/v1/semi-integration/transaction/void"
        private const val PATH_ABORT = "/v1/semi-integration/transaction/abort"
        private const val PATH_TIP_ADJUST = "/v1/semi-integration/transaction/tip-adjust"
        private const val PATH_QUERY = "/v1/transaction/query"
        private const val PATH_BATCH_QUERY = "/v1/settlement/batch-query"
        private const val PATH_BATCH_CLOSE = "/v1/settlement/batch-close"
    }

    private var httpClient: CloudHttpClient? = null
    private var apiKey: String = ""
    private var baseUrl: String = ""
    private var appId: String = ""
    private var merchantId: String = ""
    private var terminalSn: String = ""
    private var connectionListener: ConnectionListener? = null
    private var configured: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun initialize(apiKey: String, baseUrl: String, appId: String, merchantId: String, terminalSn: String) {
        Log.d(TAG, "Configuring: baseUrl=$baseUrl, appId=$appId, merchantId=$merchantId, terminalSn=$terminalSn")
        this.apiKey = apiKey
        this.baseUrl = baseUrl
        this.appId = appId
        this.merchantId = merchantId
        this.terminalSn = terminalSn
        this.configured = true
        // Reset the existing client so ensureClient() recreates it with the new config.
        // Without this, ensureClient() would keep returning the old client even after
        // apiKey / baseUrl / terminalSn change.
        try { httpClient?.close() } catch (e: Exception) { Log.w(TAG, "Error closing old client on re-init", e) }
        httpClient = null
    }

    @Synchronized
    private fun ensureClient(): CloudHttpClient {
        httpClient?.let { return it }
        if (!configured) throw IllegalStateException("CloudPaymentService not configured")
        Log.d(TAG, "Creating CloudHttpClient: baseUrl=$baseUrl")
        val client = CloudHttpClient(apiKey = apiKey, baseUrl = baseUrl)
        httpClient = client
        Log.d(TAG, "CloudHttpClient created successfully")
        return client
    }

    fun shutdown() {
        Log.d(TAG, "Shutting down CloudPaymentService")
        try { httpClient?.close() } catch (e: Exception) { Log.e(TAG, "Error closing client", e) }
        httpClient = null
        configured = false
    }

    override fun connect(connectionConfig: ConnectionConfig, listener: ConnectionListener) {
        this.connectionListener = listener
        if (!configured) {
            mainHandler.post { listener.onError("CLOUD_NOT_INITIALIZED", "Cloud service not configured") }
            return
        }
        mainHandler.post { listener.onConnected(terminalSn, "Cloud") }
    }

    override fun disconnect() {
        try { httpClient?.close() } catch (e: Exception) { Log.e(TAG, "Error during disconnect", e) }
        httpClient = null
        mainHandler.post { connectionListener?.onDisconnected("User initiated disconnection") }
    }

    override fun getConnectedDeviceId(): String? = terminalSn.takeIf { it.isNotEmpty() }
    override fun getTaproVersion(): String = "Cloud"

    // --- Helper: build base JSON with common fields ---

    private fun baseJson(): JsonObject = JsonObject().apply {
        addProperty("appId", appId)
        addProperty("merchantId", merchantId)
        addProperty("terminalSn", terminalSn)
    }

    private fun amountJson(orderAmount: Int, currency: String): JsonObject = JsonObject().apply {
        addProperty("orderAmount", orderAmount)
        addProperty("priceCurrency", currency)
    }

    private fun paymentMethodJson(
        category: PaymentCategory,
        id: String? = null,
        subId: String? = null
    ): JsonObject = JsonObject().apply {
        addProperty("category", category.name)
        id?.let { addProperty("id", it) }
        subId?.let { addProperty("subId", it) }
    }

    /** Build tipConfig JSON object from SDK TipConfig for cloud requests */
    private fun buildTipConfigJson(tipConfig: TipConfig): JsonObject = JsonObject().apply {
        addProperty("onScreenTip", tipConfig.onScreenTip)
        addProperty("tipMode", tipConfig.tipMode.name)
        addProperty("tipWithTax", tipConfig.tipWithTax)
        tipConfig.suggestions?.let { suggestions ->
            add("suggestions", JsonObject().apply {
                addProperty("feeMode", suggestions.feeMode.name)
                val valuesArray = com.google.gson.JsonArray()
                suggestions.values.forEach { valuesArray.add(it) }
                add("values", valuesArray)
            })
        }
    }

    /** Print receipt mode for cloud transactions. Set from app layer when loading preferences. */
    private var printReceipt: String = "BOTH"

    /** Notify URL for transaction result callbacks. Set from app layer when loading preferences. */
    private var notifyUrl: String = ""

    /** Whether to push transaction to terminal device. Set from app layer settings toggle. */
    private var pushToTerminal: Boolean = true

    fun setPrintReceipt(receipt: String) {
        this.printReceipt = receipt
        Log.d(TAG, "Print receipt mode set to: $receipt")
    }

    fun setNotifyUrl(url: String) {
        this.notifyUrl = url
        Log.d(TAG, "Notify URL set to: $url")
    }

    fun setPushToTerminal(enabled: Boolean) {
        this.pushToTerminal = enabled
        Log.d(TAG, "Push to terminal set to: $enabled")
    }

    /**
     * Apply notifyUrl to a request body if configured.
     * Supported by: SALE, AUTH, FORCED_AUTH, REFUND, VOID, POST_AUTH, INCREMENTAL_AUTH
     */
    private fun JsonObject.applyNotifyUrl() {
        if (notifyUrl.isNotBlank()) {
            addProperty("notifyUrl", notifyUrl)
        }
    }

    /**
     * Apply pushToTerminal flag to a request body when enabled.
     * Supported for: VOID, TIP_ADJUST, INCREMENTAL_AUTH, POST_AUTH,
     * and REFUND with originalTransactionId.
     */
    private fun JsonObject.applyPushToTerminal() {
        addProperty("pushToTerminal", pushToTerminal)
    }

    // --- Execute cloud transaction with error handling ---

    private fun executeCloud(action: String, callback: PaymentCallback, block: (CloudHttpClient) -> CloudResponse) {
        if (!configured) {
            mainHandler.post { callback.onFailure("CLOUD_NOT_INITIALIZED", "Cloud service not configured") }
            return
        }
        scope.launch {
            try {
                val client = ensureClient()
                val response = block(client)
                Log.d(TAG, "SDK_RESULT [$action]: $response")
                val result = CloudResponseMapper.mapToPaymentResult(response, action)
                mainHandler.post { callback.onSuccess(result) }
            } catch (e: CloudNetworkException) {
                Log.e(TAG, "Network error during $action", e)
                mainHandler.post { callback.onFailure("NETWORK_ERROR", e.message ?: "Network error") }
            } catch (e: CloudBusinessException) {
                Log.e(TAG, "Business error during $action: code=${e.code}, message=${e.message}", e)
                mainHandler.post { callback.onFailure(e.code, e.message ?: "Business error") }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during $action", e)
                mainHandler.post { callback.onFailure("CLOUD_ERROR", e.message ?: "Unknown error") }
            }
        }
    }

    // --- Transaction implementations ---

    override fun executeSale(
        referenceOrderId: String, transactionRequestId: String,
        amount: BigDecimal, currency: String, description: String,
        paymentCategory: PaymentCategory,
        paymentMethodId: String?,
        subPaymentMethodId: String?,
        cardNetworkType: String?,
        tipAmount: BigDecimal?, taxAmount: BigDecimal?,
        cashbackAmount: BigDecimal?, serviceFee: BigDecimal?,
        staffInfo: StaffInfo?, tipConfig: TipConfig?, callback: PaymentCallback
    ) {
        val body = baseJson().apply {
            addProperty("referenceOrderId", referenceOrderId)
            addProperty("transactionRequestId", transactionRequestId)
            addProperty("description", description)
            addProperty("printReceipt", printReceipt)
            applyNotifyUrl()
            add("paymentMethod", paymentMethodJson(paymentCategory, paymentMethodId, subPaymentMethodId))
            cardNetworkType?.let { addProperty("cardNetworkType", it) }
            add("amount", amountJson(AmountConverter.toCents(amount), currency).apply {
                tipAmount?.let { addProperty("tipAmount", AmountConverter.toCents(it)) }
                taxAmount?.let { addProperty("taxAmount", AmountConverter.toCents(it)) }
                cashbackAmount?.let { addProperty("cashbackAmount", AmountConverter.toCents(it)) }
                serviceFee?.let { addProperty("surchargeAmount", AmountConverter.toCents(it)) }
                tipConfig?.let { tc -> add("tipConfig", buildTipConfigJson(tc)) }
            })
        }
        if (tipConfig != null) {
            Log.d(TAG, "TIP_CONFIG [SALE]: applied tipConfig=${buildTipConfigJson(tipConfig)}")
        } else {
            Log.d(TAG, "TIP_CONFIG [SALE]: tipConfig disabled, not included in request")
        }
        Log.d(TAG, "SDK_REQ [SALE]: $body")
        executeCloud("SALE", callback) { it.post(PATH_SALE, body) }
    }

    override fun executeAuth(
        referenceOrderId: String, transactionRequestId: String,
        amount: BigDecimal, currency: String, description: String,
        callback: PaymentCallback
    ) {
        val body = baseJson().apply {
            addProperty("referenceOrderId", referenceOrderId)
            addProperty("transactionRequestId", transactionRequestId)
            addProperty("description", description)
            addProperty("printReceipt", printReceipt)
            applyNotifyUrl()
            add("amount", amountJson(AmountConverter.toCents(amount), currency))
        }
        Log.d(TAG, "SDK_REQ [AUTH]: $body")
        executeCloud("AUTH", callback) { it.post(PATH_AUTH, body) }
    }

    override fun executeForcedAuth(
        referenceOrderId: String, transactionRequestId: String,
        amount: BigDecimal, currency: String, description: String,
        tipAmount: BigDecimal?, taxAmount: BigDecimal?,
        callback: PaymentCallback
    ) {
        val body = baseJson().apply {
            addProperty("referenceOrderId", referenceOrderId)
            addProperty("transactionRequestId", transactionRequestId)
            addProperty("description", description)
            addProperty("printReceipt", printReceipt)
            applyNotifyUrl()
            add("amount", amountJson(AmountConverter.toCents(amount), currency))
        }
        Log.d(TAG, "SDK_REQ [FORCED_AUTH]: $body")
        executeCloud("FORCED_AUTH", callback) { it.post(PATH_FORCED_AUTH, body) }
    }

    override fun executeRefund(
        referenceOrderId: String, transactionRequestId: String,
        originalTransactionId: String, originalTransactionRequestId: String,
        amount: BigDecimal, currency: String,
        description: String, reason: String?, callback: PaymentCallback
    ) {
        // Referenced refund: provide originalTransactionId or originalTransactionRequestId.
        //   referenceOrderId is NOT passed — server auto-associates the original order.
        // Unreferenced refund: both original IDs empty, referenceOrderId is REQUIRED.
        val isReferenced = originalTransactionId.isNotBlank() || originalTransactionRequestId.isNotBlank()
        val body = baseJson().apply {
            addProperty("transactionRequestId", transactionRequestId)
            if (originalTransactionId.isNotBlank()) {
                addProperty("originalTransactionId", originalTransactionId)
            } else if (originalTransactionRequestId.isNotBlank()) {
                addProperty("originalTransactionRequestId", originalTransactionRequestId)
            }
            if (!isReferenced && referenceOrderId.isNotBlank()) {
                addProperty("referenceOrderId", referenceOrderId)
            }
            addProperty("description", description)
            addProperty("printReceipt", printReceipt)
            applyNotifyUrl()
            if (isReferenced) {
                applyPushToTerminal()
            }
            add("amount", amountJson(AmountConverter.toCents(amount), currency))
        }
        Log.d(TAG, "SDK_REQ [REFUND]: $body")
        executeCloud("REFUND", callback) { it.post(PATH_REFUND, body) }
    }

    override fun executeVoid(
        referenceOrderId: String, transactionRequestId: String,
        originalTransactionId: String, description: String,
        reason: String?, callback: PaymentCallback
    ) {
        val body = baseJson().apply {
            addProperty("transactionRequestId", transactionRequestId)
            addProperty("originalTransactionId", originalTransactionId)
            addProperty("description", description)
            addProperty("printReceipt", printReceipt)
            applyPushToTerminal()
            applyNotifyUrl()
        }
        Log.d(TAG, "SDK_REQ [VOID]: $body")
        executeCloud("VOID", callback) { it.post(PATH_VOID, body) }
    }

    override fun executePostAuth(
        referenceOrderId: String, transactionRequestId: String,
        originalTransactionId: String, amount: BigDecimal, currency: String,
        description: String, tipAmount: BigDecimal?, taxAmount: BigDecimal?,
        cashbackAmount: BigDecimal?, serviceFee: BigDecimal?,
        tipConfig: TipConfig?,
        callback: PaymentCallback
    ) {
        val body = baseJson().apply {
            addProperty("transactionRequestId", transactionRequestId)
            addProperty("originalTransactionId", originalTransactionId)
            addProperty("description", description)
            addProperty("printReceipt", printReceipt)
            applyNotifyUrl()
            applyPushToTerminal()
            add("amount", amountJson(AmountConverter.toCents(amount), currency).apply {
                tipAmount?.let { addProperty("tipAmount", AmountConverter.toCents(it)) }
                taxAmount?.let { addProperty("taxAmount", AmountConverter.toCents(it)) }
                serviceFee?.let { addProperty("surchargeAmount", AmountConverter.toCents(it)) }
                tipConfig?.let { tc -> add("tipConfig", buildTipConfigJson(tc)) }
            })
        }
        if (tipConfig != null) {
            Log.d(TAG, "TIP_CONFIG [POST_AUTH]: applied tipConfig=${buildTipConfigJson(tipConfig)}")
        } else {
            Log.d(TAG, "TIP_CONFIG [POST_AUTH]: tipConfig disabled, not included in request")
        }
        Log.d(TAG, "SDK_REQ [POST_AUTH]: $body")
        executeCloud("POST_AUTH", callback) { it.post(PATH_POST_AUTH, body) }
    }

    override fun executeIncrementalAuth(
        referenceOrderId: String, transactionRequestId: String,
        originalTransactionId: String, amount: BigDecimal, currency: String,
        description: String, callback: PaymentCallback
    ) {
        val body = baseJson().apply {
            addProperty("transactionRequestId", transactionRequestId)
            addProperty("originalTransactionId", originalTransactionId)
            addProperty("description", description)
            addProperty("printReceipt", printReceipt)
            applyNotifyUrl()
            applyPushToTerminal()
            add("amount", amountJson(AmountConverter.toCents(amount), currency))
        }
        Log.d(TAG, "SDK_REQ [INCREMENTAL_AUTH]: $body")
        executeCloud("INCREMENTAL_AUTH", callback) { it.post(PATH_INCREMENTAL_AUTH, body) }
    }

    override fun executeTipAdjust(
        referenceOrderId: String, transactionRequestId: String,
        originalTransactionId: String, originalTransactionRequestId: String,
        tipAmount: BigDecimal, description: String, callback: PaymentCallback
    ) {
        val body = baseJson().apply {
            addProperty("originalTransactionId", originalTransactionId)
            addProperty("tipAmount", AmountConverter.toCents(tipAmount))
            applyPushToTerminal()
        }
        Log.d(TAG, "SDK_REQ [TIP_ADJUST]: $body")
        executeCloud("TIP_ADJUST", callback) { it.post(PATH_TIP_ADJUST, body) }
    }

    override fun executeQuery(transactionRequestId: String, callback: PaymentCallback) {
        val params = JsonObject().apply {
            addProperty("appId", appId)
            addProperty("merchantId", merchantId)
            addProperty("transactionRequestId", transactionRequestId)
        }
        Log.d(TAG, "SDK_REQ [QUERY]: $params")
        executeCloud("QUERY", callback) { it.get(PATH_QUERY, params) }
    }

    override fun executeQueryByTransactionId(transactionId: String, callback: PaymentCallback) {
        val params = JsonObject().apply {
            addProperty("appId", appId)
            addProperty("merchantId", merchantId)
            addProperty("transactionId", transactionId)
        }
        Log.d(TAG, "SDK_REQ [QUERY_BY_TXN_ID]: $params")
        executeCloud("QUERY", callback) { it.get(PATH_QUERY, params) }
    }

    override fun executeBatchClose(
        transactionRequestId: String, description: String, callback: PaymentCallback
    ) {
        if (!configured) {
            mainHandler.post { callback.onFailure("CLOUD_NOT_INITIALIZED", "Cloud service not configured") }
            return
        }
        scope.launch {
            try {
                val client = ensureClient()

                // Step 1: Query batch summary to get channelCode list
                val queryParams = JsonObject().apply {
                    addProperty("appId", appId)
                    addProperty("merchantId", merchantId)
                    addProperty("terminalSn", terminalSn)
                }
                Log.d(TAG, "SDK_REQ [BATCH_QUERY]: $queryParams")
                mainHandler.post { callback.onProgress("PROCESSING", "Querying batch summary...") }

                val queryResponse = client.get(PATH_BATCH_QUERY, queryParams)
                Log.d(TAG, "SDK_RESULT [BATCH_QUERY]: $queryResponse")

                val data = queryResponse.data
                val channelList = data?.getAsJsonArray("batchList")
                if (channelList == null || channelList.size() == 0) {
                    Log.w(TAG, "Batch query returned empty list, no channels to close")
                    mainHandler.post { callback.onFailure("BATCH_EMPTY", "No batch data found. There may be no transactions to settle.") }
                    return@launch
                }

                // Extract unique channelCodes from batch query result
                val channelCodes = mutableListOf<String>()
                for (i in 0 until channelList.size()) {
                    val item = channelList.get(i).asJsonObject
                    val code = item.get("channelCode")?.asString
                    if (code != null && code !in channelCodes) {
                        channelCodes.add(code)
                    }
                }
                Log.d(TAG, "Batch query found ${channelCodes.size} channel(s): $channelCodes")

                if (channelCodes.isEmpty()) {
                    mainHandler.post { callback.onFailure("BATCH_NO_CHANNEL", "No channel codes found in batch query result.") }
                    return@launch
                }

                // Step 2: Execute batch-close for each channelCode
                var lastSuccessResponse: CloudResponse? = null
                val failedChannels = mutableListOf<String>()

                for ((index, channelCode) in channelCodes.withIndex()) {
                    // Each batch-close needs a unique transactionRequestId
                    val closeRequestId = if (channelCodes.size == 1) {
                        transactionRequestId
                    } else {
                        "${transactionRequestId}_${channelCode}"
                    }

                    val body = baseJson().apply {
                        addProperty("transactionRequestId", closeRequestId)
                        addProperty("channelCode", channelCode)
                        addProperty("description", description)
                    }
                    Log.d(TAG, "SDK_REQ [BATCH_CLOSE ${index + 1}/${channelCodes.size}] channelCode=$channelCode: $body")
                    mainHandler.post {
                        callback.onProgress("PROCESSING", "Closing batch for channel $channelCode (${index + 1}/${channelCodes.size})...")
                    }

                    try {
                        val closeResponse = client.post(PATH_BATCH_CLOSE, body)
                        Log.d(TAG, "SDK_RESULT [BATCH_CLOSE] channelCode=$channelCode: $closeResponse")
                        lastSuccessResponse = closeResponse
                    } catch (e: CloudBusinessException) {
                        Log.e(TAG, "Batch close failed for channelCode=$channelCode: ${e.code} - ${e.message}")
                        failedChannels.add("$channelCode(${e.code}:${e.message})")
                    }
                }

                // Step 3: Report result
                if (lastSuccessResponse != null && failedChannels.isEmpty()) {
                    // All channels closed successfully — return last response as result
                    val result = CloudResponseMapper.mapToPaymentResult(lastSuccessResponse, "BATCH_CLOSE")
                    mainHandler.post { callback.onSuccess(result) }
                } else if (lastSuccessResponse != null && failedChannels.isNotEmpty()) {
                    // Partial success
                    val result = CloudResponseMapper.mapToPaymentResult(lastSuccessResponse, "BATCH_CLOSE")
                    mainHandler.post { callback.onSuccess(result) }
                    Log.w(TAG, "Batch close partially failed. Failed channels: $failedChannels")
                } else {
                    // All failed
                    mainHandler.post {
                        callback.onFailure("BATCH_CLOSE_FAILED", "All batch close requests failed: $failedChannels")
                    }
                }
            } catch (e: CloudNetworkException) {
                Log.e(TAG, "Network error during BATCH_CLOSE", e)
                mainHandler.post { callback.onFailure("NETWORK_ERROR", e.message ?: "Network error") }
            } catch (e: CloudBusinessException) {
                Log.e(TAG, "Business error during BATCH_CLOSE: code=${e.code}, message=${e.message}", e)
                mainHandler.post { callback.onFailure(e.code, e.message ?: "Business error") }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during BATCH_CLOSE", e)
                mainHandler.post { callback.onFailure("CLOUD_ERROR", e.message ?: "Unknown error") }
            }
        }
    }

    override fun executeAbort(
        originalTransactionId: String?, originalTransactionRequestId: String?,
        description: String?, callback: PaymentCallback
    ) {
        val body = baseJson().apply {
            originalTransactionId?.let { addProperty("originalTransactionId", it) }
            originalTransactionRequestId?.let { addProperty("originalTransactionRequestId", it) }
            description?.let { addProperty("description", it) }
        }
        Log.d(TAG, "SDK_REQ [ABORT]: $body")
        executeCloud("ABORT", callback) { it.post(PATH_ABORT, body) }
    }
}
