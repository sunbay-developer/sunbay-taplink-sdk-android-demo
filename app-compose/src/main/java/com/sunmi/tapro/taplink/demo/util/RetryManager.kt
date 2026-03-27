package com.sunmi.tapro.taplink.demo.util

/**
 * Retry Manager
 * 
 * Manages retry logic for failed operations, working in conjunction with ErrorHandler
 * to determine if operations should be retried and tracking retry attempts.
 * 
 * This class provides:
 * - Retry count tracking
 * - Maximum retry limit enforcement
 * - Integration with ErrorHandler for retry eligibility
 * - Exponential backoff delay calculation
 */
class RetryManager(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 1000L
) {
    private var retryCount = 0
    
    /**
     * Check if an operation should be retried based on error code and current retry count
     * 
     * @param errorCode Error code from the failed operation
     * @param errorMessage Error message for classification
     * @return true if the operation should be retried
     */
    fun shouldRetry(errorCode: String, errorMessage: String = ""): Boolean {
        if (retryCount >= maxRetries) {
            return false
        }
        
        val category = ErrorHandler.classifyError(errorCode, errorMessage)
        return ErrorHandler.isRetryable(errorCode, category)
    }
    
    /**
     * Increment the retry counter
     * Call this after each retry attempt
     */
    fun incrementRetry() {
        retryCount++
    }
    
    /**
     * Reset the retry counter
     * Call this when starting a new operation or after a successful operation
     */
    fun reset() {
        retryCount = 0
    }
    
    /**
     * Get the current retry count
     * 
     * @return Current number of retry attempts
     */
    fun getRetryCount(): Int = retryCount
    
    /**
     * Get the maximum number of retries allowed
     * 
     * @return Maximum retry limit
     */
    fun getMaxRetries(): Int = maxRetries
    
    /**
     * Check if maximum retries have been reached
     * 
     * @return true if no more retries are allowed
     */
    fun hasReachedMaxRetries(): Boolean = retryCount >= maxRetries
    
    /**
     * Calculate delay before next retry using exponential backoff
     * 
     * @return Delay in milliseconds before next retry attempt
     */
    fun getRetryDelay(): Long {
        // Exponential backoff: baseDelay * 2^retryCount
        // For example: 1s, 2s, 4s, 8s...
        return baseDelayMs * (1 shl retryCount.coerceAtMost(5)) // Cap at 2^5 = 32x
    }
    
    /**
     * Get a human-readable status message
     * 
     * @return Status message describing current retry state
     */
    fun getStatusMessage(): String {
        return when {
            retryCount == 0 -> "No retries attempted"
            retryCount < maxRetries -> "Retry attempt $retryCount of $maxRetries"
            else -> "Maximum retries ($maxRetries) reached"
        }
    }
}

/**
 * Retry Manager Factory
 * 
 * Provides pre-configured RetryManager instances for different use cases
 */
object RetryManagerFactory {
    
    /**
     * Create a RetryManager for connection operations
     * Connection operations typically need more retries with longer delays
     */
    fun forConnection(): RetryManager {
        return RetryManager(maxRetries = 5, baseDelayMs = 2000L)
    }
    
    /**
     * Create a RetryManager for payment operations
     * Payment operations need fewer retries with shorter delays
     */
    fun forPayment(): RetryManager {
        return RetryManager(maxRetries = 3, baseDelayMs = 1000L)
    }
    
    /**
     * Create a RetryManager for network operations
     * Network operations need moderate retries with moderate delays
     */
    fun forNetwork(): RetryManager {
        return RetryManager(maxRetries = 4, baseDelayMs = 1500L)
    }
    
    /**
     * Create a RetryManager with custom configuration
     * 
     * @param maxRetries Maximum number of retry attempts
     * @param baseDelayMs Base delay in milliseconds for exponential backoff
     */
    fun custom(maxRetries: Int, baseDelayMs: Long): RetryManager {
        return RetryManager(maxRetries, baseDelayMs)
    }
}
