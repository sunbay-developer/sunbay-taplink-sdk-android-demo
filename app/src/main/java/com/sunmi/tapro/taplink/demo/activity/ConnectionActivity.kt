package com.sunmi.tapro.taplink.demo.activity

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.sunmi.tapro.taplink.demo.R
import com.sunmi.tapro.taplink.demo.service.ConnectionListener
import com.sunmi.tapro.taplink.demo.service.TaplinkPaymentService
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.demo.util.NetworkUtils
import kotlinx.coroutines.launch

/**
 * Connection Mode Selection Activity
 * 
 * Functions:
 * 1. Select connection mode (App-to-App, Cable, LAN)
 * 2. Configure connection parameters (LAN requires IP and port)
 * 3. Validate configuration integrity
 * 4. Save configuration and reconnect
 */
class ConnectionActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ConnectionActivity"
        const val RESULT_CONNECTION_CHANGED = 100
    }
    
    // UI components
    private lateinit var rgConnectionMode: RadioGroup
    private lateinit var rbAppToApp: RadioButton
    private lateinit var rbCable: RadioButton
    private lateinit var rbLan: RadioButton
    private lateinit var rbCloud: RadioButton
    
    // Configuration areas
    private lateinit var layoutAppToAppConfig: CardView
    private lateinit var layoutCableConfig: CardView
    private lateinit var layoutLanConfig: CardView
    private lateinit var layoutCloudConfig: CardView
    
    // LAN configuration inputs
    private lateinit var etLanIp: EditText
    private lateinit var etLanPort: EditText
    private lateinit var switchTls: Switch
    
    // Cable configuration inputs
    private lateinit var spinnerCableProtocol: Spinner
    
    // Error prompts
    private lateinit var cardConfigError: CardView
    private lateinit var tvConfigError: TextView

    private lateinit var btnConfirm: Button
    private lateinit var btnExitApp: Button
    
    // Currently selected connection mode
    private var selectedMode: ConnectionPreferences.ConnectionMode = ConnectionPreferences.ConnectionMode.APP_TO_APP
    
    // Payment service
    private val paymentService = TaplinkPaymentService.getInstance()
    
    // Anti-duplicate click protection
    private var lastClickTime: Long = 0
    private val CLICK_INTERVAL: Long = 500 // 500ms interval
    
    // Current alert dialog reference for proper cleanup
    private var currentAlertDialog: android.app.AlertDialog? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)
        initViews()
        loadCurrentConfig()
        setupListeners()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up validation runnables to prevent memory leaks
        etLanIp.removeCallbacks(ipValidationRunnable)
        etLanPort.removeCallbacks(portValidationRunnable)
        
        // Clear any other pending callbacks
        etLanIp.removeCallbacks(null)
        etLanPort.removeCallbacks(null)
        
        // Dismiss any current alert dialog
        currentAlertDialog?.dismiss()
        currentAlertDialog = null
    }
    

    
    /**
     * Check if button can be clicked (prevent duplicate clicks)
     */
    private fun canClick(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > CLICK_INTERVAL) {
            lastClickTime = currentTime
            return true
        }
        return false
    }
    
    /**
     * Initialize view components
     */
    private fun initViews() {
        // Connection mode selection
        rgConnectionMode = findViewById(R.id.rg_connection_mode)
        rbAppToApp = findViewById(R.id.rb_app_to_app)
        rbCable = findViewById(R.id.rb_cable)
        rbLan = findViewById(R.id.rb_lan)
        rbCloud = findViewById(R.id.rb_cloud)
        
        // Configuration areas
        layoutAppToAppConfig = findViewById(R.id.layout_app_to_app_config)
        layoutCableConfig = findViewById(R.id.layout_cable_config)
        layoutLanConfig = findViewById(R.id.layout_lan_config)
        layoutCloudConfig = findViewById(R.id.layout_cloud_config)
        
        // LAN configuration inputs
        etLanIp = findViewById(R.id.et_lan_ip)
        etLanPort = findViewById(R.id.et_lan_port)
        
        // Cable configuration inputs
        spinnerCableProtocol = findViewById(R.id.spinner_cable_protocol)
        
        // Error prompts
        cardConfigError = findViewById(R.id.card_config_error)
        tvConfigError = findViewById(R.id.tv_config_error)
        
        // Buttons
        btnConfirm = findViewById(R.id.btn_confirm)
        btnExitApp = findViewById(R.id.btn_exit_app)
    }
    
    /**
     * Load current configuration
     */
    private fun loadCurrentConfig() {
        // Load saved connection mode
        val currentMode = ConnectionPreferences.getConnectionMode(this)
        selectedMode = currentMode
        
        // Set corresponding RadioButton checked
        when (currentMode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                rbAppToApp.isChecked = true
                showConfigArea(ConnectionPreferences.ConnectionMode.APP_TO_APP)
            }
            ConnectionPreferences.ConnectionMode.CABLE -> {
                rbCable.isChecked = true
                showConfigArea(ConnectionPreferences.ConnectionMode.CABLE)
                setupCableProtocolSpinner()
            }
            ConnectionPreferences.ConnectionMode.LAN -> {
                rbLan.isChecked = true
                showConfigArea(ConnectionPreferences.ConnectionMode.LAN)
                loadLanConfig()
            }
        }
        
        Log.d(TAG, "Load current configuration - Connection mode: $currentMode")
    }
    
    /**
     * Load LAN configuration
     */
    private fun loadLanConfig() {
        val lanConfig = ConnectionPreferences.getLanConfig(this)
        val ip = lanConfig.first
        val port = lanConfig.second
        
        ip?.let { etLanIp.setText(it) }
        etLanPort.setText(port.toString())
//        switchTls.isChecked = false // LAN mode defaults to TLS disabled
        
        Log.d(TAG, "Load LAN configuration - IP: $ip, Port: $port")
    }
    
    /**
     * Setup cable protocol spinner
     */
    private fun setupCableProtocolSpinner() {
        // Create protocol display names
        val protocolNames = arrayOf(
            "AUTO (Auto-detect)",
            "USB_AOA (USB Android Open Accessory)",
            "USB_VSP (USB Virtual Serial Port)",
            "RS232 (Standard RS232 Serial)"
        )
        
        // Create adapter for spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, protocolNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        // Set adapter to spinner
        spinnerCableProtocol.adapter = adapter
        
        // Load saved protocol or set default to AUTO
        val savedProtocol = ConnectionPreferences.getCableProtocol(this)
        spinnerCableProtocol.setSelection(savedProtocol.ordinal)
        
        Log.d(TAG, "Cable protocol spinner setup with saved protocol: $savedProtocol")
    }
    
    /**
     * Set up event listeners
     */
    private fun setupListeners() {
        // Connection mode selection listener
        rgConnectionMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_app_to_app -> {
                    selectedMode = ConnectionPreferences.ConnectionMode.APP_TO_APP
                    showConfigArea(ConnectionPreferences.ConnectionMode.APP_TO_APP)
                }
                R.id.rb_cable -> {
                    selectedMode = ConnectionPreferences.ConnectionMode.CABLE
                    showConfigArea(ConnectionPreferences.ConnectionMode.CABLE)
                    setupCableProtocolSpinner() // Initialize cable protocol spinner
                }
                R.id.rb_lan -> {
                    selectedMode = ConnectionPreferences.ConnectionMode.LAN
                    showConfigArea(ConnectionPreferences.ConnectionMode.LAN)
                    loadLanConfig() // Reload LAN configuration
                }
            }
            
            // Hide error prompt
            hideConfigError()
        }

        
        // Confirm button click listener
        btnConfirm.setOnClickListener {
            if (canClick()) {
                handleConfirm()
            }
        }
        
        // Exit app button click listener
        btnExitApp.setOnClickListener {
            if (canClick()) {
                handleExitApp()
            }
        }
        
        // LAN configuration real-time validation listeners
        setupLanConfigValidation()
    }
    
    /**
     * Set up real-time validation for LAN configuration inputs
     */
    private fun setupLanConfigValidation() {
        // IP address real-time validation
        etLanIp.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                validateLanIpInput()
            } else {
                hideConfigError()
            }
        }
        
        // Port number real-time validation
        etLanPort.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                validateLanPortInput()
            } else {
                hideConfigError()
            }
        }
        
        // Add text change listeners for immediate feedback
        etLanIp.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // Only validate if user has finished typing (after a short delay)
                etLanIp.removeCallbacks(ipValidationRunnable)
                etLanIp.postDelayed(ipValidationRunnable, 500)
            }
        })
        
        etLanPort.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // Only validate if user has finished typing (after a short delay)
                etLanPort.removeCallbacks(portValidationRunnable)
                etLanPort.postDelayed(portValidationRunnable, 500)
            }
        })
    }
    
    // Validation runnables for delayed validation
    private val ipValidationRunnable = Runnable {
        if (selectedMode == ConnectionPreferences.ConnectionMode.LAN) {
            validateLanIpInput()
        }
    }
    
    private val portValidationRunnable = Runnable {
        if (selectedMode == ConnectionPreferences.ConnectionMode.LAN) {
            validateLanPortInput()
        }
    }
    
    /**
     * Validate LAN IP address input
     */
    private fun validateLanIpInput() {
        val ip = etLanIp.text.toString().trim()
        
        if (ip.isNotEmpty() && !NetworkUtils.isValidIpAddress(ip)) {
            showConfigError("IP address format is incorrect. Please enter a valid IPv4 address (e.g., 192.168.1.100)")
        } else {
            hideConfigError()
        }
    }
    
    /**
     * Validate LAN port number input
     */
    private fun validateLanPortInput() {
        val portStr = etLanPort.text.toString().trim()
        
        if (portStr.isNotEmpty()) {
            try {
                val port = portStr.toInt()
                if (!NetworkUtils.isPortValid(port)) {
                    showConfigError("Port number must be between 1-65535. Recommended range: 8443-8453")
                } else {
                    hideConfigError()
                }
            } catch (e: NumberFormatException) {
                showConfigError("Port number format is incorrect. Please enter a valid number")
            }
        } else {
            hideConfigError()
        }
    }

    /**
     * Show corresponding configuration area
     */
    private fun showConfigArea(mode: ConnectionPreferences.ConnectionMode) {
        // Hide all configuration areas
        layoutAppToAppConfig.visibility = View.GONE
        layoutCableConfig.visibility = View.GONE
        layoutLanConfig.visibility = View.GONE
        layoutCloudConfig.visibility = View.GONE
        
        // Show corresponding configuration area
        when (mode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                layoutAppToAppConfig.visibility = View.VISIBLE
            }
            ConnectionPreferences.ConnectionMode.CABLE -> {
                layoutCableConfig.visibility = View.VISIBLE
            }
            ConnectionPreferences.ConnectionMode.LAN -> {
                layoutLanConfig.visibility = View.VISIBLE
            }
        }
        
        Log.d(TAG, "Show configuration area: $mode")
    }
    
    /**
     * Handle confirm button click
     */
    private fun handleConfirm() {
        Log.d(TAG, "User clicks confirm - Selected mode: $selectedMode")
        
        // Validate configuration
        val validationResult = validateConfig()
        if (!validationResult.isValid) {
            showConfigError(validationResult.errorMessage)
            return
        }
        
        // Save configuration
        saveConfig()
        
        // Reconnect with new mode (includes SDK re-initialization)
        reconnectWithNewMode()
    }
    
    /**
     * Validate configuration
     */
    private fun validateConfig(): ValidationResult {
        when (selectedMode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                // App-to-App mode requires no additional configuration
                return ValidationResult(true, "")
            }
            
            ConnectionPreferences.ConnectionMode.LAN -> {
                // Check network connectivity first
                if (!NetworkUtils.isNetworkConnected(this)) {
                    return ValidationResult(false, "No network connection available. Please check your network settings.")
                }
                
                // Validate LAN configuration
                val ip = etLanIp.text.toString().trim()
                val portStr = etLanPort.text.toString().trim()
                
                if (TextUtils.isEmpty(ip)) {
                    return ValidationResult(false, "Please enter IP address")
                }
                
                if (!NetworkUtils.isValidIpAddress(ip)) {
                    return ValidationResult(false, "IP address format is incorrect. Please enter a valid IPv4 address (e.g., 192.168.1.100)")
                }
                
                if (TextUtils.isEmpty(portStr)) {
                    return ValidationResult(false, "Please enter port number")
                }
                
                val port = try {
                    portStr.toInt()
                } catch (e: NumberFormatException) {
                    return ValidationResult(false, "Port number format is incorrect. Please enter a valid number")
                }
                
                if (!NetworkUtils.isPortValid(port)) {
                    return ValidationResult(false, "Port number must be between 1-65535. Recommended range: 8443-8453")
                }
                
                // Check if target IP is in same subnet (warning, not error)
                if (!NetworkUtils.isInSameSubnet(this, ip)) {
                    val networkType = NetworkUtils.getNetworkType(this)
                    val localIp = NetworkUtils.getLocalIpAddress(this)
                    Log.w(TAG, "Target IP $ip may not be in same subnet as local IP $localIp (Network: $networkType)")
                }
                
                return ValidationResult(true, "")
            }
            
            ConnectionPreferences.ConnectionMode.CABLE -> {
                // Cable mode requires no additional configuration (auto-detection)
                return ValidationResult(true, "")
            }
        }
    }
    

    /**
     * Save configuration
     */
    private fun saveConfig() {
        // Save connection mode
        ConnectionPreferences.saveConnectionMode(this, selectedMode)
        
        // Save mode-specific configuration
        when (selectedMode) {
            ConnectionPreferences.ConnectionMode.LAN -> {
                val ip = etLanIp.text.toString().trim()
                val port = etLanPort.text.toString().trim().toInt()
                ConnectionPreferences.saveLanConfig(this, ip, port)
                Log.d(TAG, "Save LAN configuration - IP: $ip, Port: $port")
            }
            
            ConnectionPreferences.ConnectionMode.CABLE -> {
                // Save selected cable protocol
                val selectedProtocolIndex = spinnerCableProtocol.selectedItemPosition
                val selectedProtocol = ConnectionPreferences.CableProtocol.values()[selectedProtocolIndex]
                ConnectionPreferences.saveCableProtocol(this, selectedProtocol)
                Log.d(TAG, "Save Cable configuration - Protocol: $selectedProtocol")
            }
            
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                // No additional configuration needed for App-to-App mode
                Log.d(TAG, "App-to-App mode - no additional configuration to save")
            }
        }
        
        Log.d(TAG, "Configuration saved successfully - Mode: $selectedMode")
    }
    
    /**
     * Reconnect with new mode
     */
    private fun reconnectWithNewMode() {
        Log.d(TAG, "Start reconnecting with mode: $selectedMode")
        
        // Show connecting status with detailed progress
        updateConnectionProgress("Initializing SDK...")
        btnConfirm.isEnabled = false

        // Re-initialize and connect
        reinitializeSDKAndConnect()
    }
    
    /**
     * Update connection progress display
     */
    private fun updateConnectionProgress(message: String) {
        btnConfirm.text = message
        Log.d(TAG, "Connection progress: $message")
    }
    
    /**
     * Re-initialize SDK and connect for mode switching
     */
    private fun reinitializeSDKAndConnect() {
        Log.d(TAG, "Starting connection with mode: $selectedMode")
        
        // Step 1: Disconnect current connection
        updateConnectionProgress("Disconnecting...")
        paymentService.disconnect()
        
        // Step 2: Re-initialize SDK for the new connection mode
        updateConnectionProgress("Initializing SDK...")
        val reinitSuccess = paymentService.initialize(
            context = this,
            appId = "", // Will be read from resources in initialize method
            merchantId = "", // Will be read from resources in initialize method
            secretKey = "" // Will be read from resources in initialize method
        )
        
        if (!reinitSuccess) {
            Log.e(TAG, "SDK re-initialization failed for mode: $selectedMode")
            showConnectionResult(false, "SDK initialization failed for $selectedMode mode")
            return
        }
        
        Log.d(TAG, "SDK initialized successfully for mode: $selectedMode")
        
        // Step 3: Start connection process with pre-check
        when (selectedMode) {
            ConnectionPreferences.ConnectionMode.LAN -> {
                val lanConfig = ConnectionPreferences.getLanConfig(this)
                val ip = lanConfig.first ?: "unknown"
                val port = lanConfig.second
                updateConnectionProgress("Testing connectivity to $ip:$port...")
                
                // Pre-check network connectivity
                lifecycleScope.launch {
                    val ipAddress = lanConfig.first ?: return@launch
                    val portNumber = lanConfig.second
                    val isReachable = NetworkUtils.testConnection(ipAddress, portNumber, 3000)
                    if (!isReachable) {
                        Log.w(TAG, "Pre-check failed: Cannot reach $ipAddress:$portNumber")
                        runOnUiThread {
                            updateConnectionProgress("Host unreachable, trying SDK connection...")
                        }
                    } else {
                        Log.d(TAG, "Pre-check successful: $ipAddress:$portNumber is reachable")
                        runOnUiThread {
                            updateConnectionProgress("Host reachable, establishing connection...")
                        }
                    }
                    
                    // Continue with SDK connection regardless of pre-check result
                    runOnUiThread {
                        startSDKConnection()
                    }
                }
                return
            }
            ConnectionPreferences.ConnectionMode.CABLE -> {
                updateConnectionProgress("Connecting via Cable...")
            }
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                updateConnectionProgress("Connecting to Tapro App...")
            }
        }
        
        // For non-LAN modes, start connection immediately
        startSDKConnection()
    }
    
    /**
     * Start SDK connection process
     */
    private fun startSDKConnection() {
        Log.d(TAG, "Starting SDK connection for mode: $selectedMode")
        
        // Connect to payment terminal with new mode
        paymentService.connect(object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.d(TAG, "Connection successful - DeviceId: $deviceId, Version: $taproVersion")
                runOnUiThread {
                    showConnectionResult(true, "Connected to $deviceId (v$taproVersion)")
                }
            }
            
            override fun onDisconnected(reason: String) {
                Log.d(TAG, "Connection disconnected - Reason: $reason")
                runOnUiThread {
                    showConnectionResult(false, "Connection disconnected: $reason")
                }
            }
            
            override fun onError(code: String, message: String) {
                Log.e(TAG, "Connection failed - Code: $code, Message: $message")
                runOnUiThread {
                    val errorMsg = mapConnectionError(code, message)
                    showConnectionResult(false, errorMsg)
                }
            }
        })
    }
    
    /**
     * Map connection error to user-friendly message
     */
    private fun mapConnectionError(code: String, message: String): String {
        // Provide user-friendly error messages based on common connection issues
        return when {
            message.contains("ETIMEDOUT") || message.contains("Connection timed out") -> {
                when (selectedMode) {
                    ConnectionPreferences.ConnectionMode.LAN -> {
                        val lanConfig = ConnectionPreferences.getLanConfig(this)
                        val ip = lanConfig.first ?: "unknown"
                        val port = lanConfig.second
                        "Unable to connect to $ip:$port\n\n" +
                        "Possible solutions:\n" +
                        "• Check if the target device is powered on\n" +
                        "• Verify the IP address and port are correct\n" +
                        "• Ensure both devices are on the same network\n" +
                        "• Check firewall settings\n\n" +
                        "Error Code: $code"
                    }
                    else -> "Connection timeout. Please check network connectivity.\n\nError Code: $code"
                }
            }
            message.contains("failed to connect") -> {
                "Connection failed. Please check network settings and try again.\n\nError Code: $code"
            }
            message.contains("UnknownHostException") -> {
                "Cannot resolve host address. Please check IP address.\n\nError Code: $code"
            }
            message.contains("ConnectException") -> {
                "Connection refused. Please check if the service is running.\n\nError Code: $code"
            }
            message.isNotEmpty() -> {
                "$message\n\nError Code: $code"
            }
            else -> {
                "Connection failed. Please check your settings and try again.\n\nError Code: $code"
            }
        }
    }
    
    /**
     * Show connection result
     */
    private fun showConnectionResult(success: Boolean, message: String) {
        if (success) {
            // Connection successful, return result
            Log.d(TAG, "Connection configuration completed - $message")
            
            val resultIntent = Intent()
            resultIntent.putExtra("connection_mode", selectedMode.name)
            resultIntent.putExtra("connection_message", message)
            setResult(RESULT_CONNECTION_CHANGED, resultIntent)
            finish()
        } else {
            // Connection failed, show simple error dialog
            Log.e(TAG, "Connection failed - $message")
            
            showSimpleConnectionError(message)
            
            btnConfirm.text = getString(R.string.btn_confirm)
            btnConfirm.isEnabled = true
        }
    }
    
    /**
     * Show simple connection error dialog
     */
    private fun showSimpleConnectionError(message: String) {
        // Dismiss any existing dialog first
        currentAlertDialog?.dismiss()
        
        currentAlertDialog = android.app.AlertDialog.Builder(this)
            .setTitle("Connection Failed")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                currentAlertDialog = null
            }
            .setNeutralButton("Retry") { dialog, _ -> 
                dialog.dismiss()
                currentAlertDialog = null
                // Retry connection
                handleConfirm()
            }
            .setOnDismissListener {
                currentAlertDialog = null
            }
            .show()
    }
    
    /**
     * Show configuration error with simple message
     */
    private fun showConfigError(message: String) {
        tvConfigError.text = message
        cardConfigError.visibility = View.VISIBLE
        
        Log.w(TAG, "Configuration error displayed - Mode: $selectedMode, Message: $message")
    }
    
    /**
     * Hide configuration error
     */
    private fun hideConfigError() {
        cardConfigError.visibility = View.GONE
    }
    
    /**
     * Handle exit app button click
     */
    private fun handleExitApp() {
        Log.d(TAG, "User clicks exit app")
        
        // Dismiss any existing dialog first
        currentAlertDialog?.dismiss()
        
        // Show confirmation dialog
        currentAlertDialog = android.app.AlertDialog.Builder(this)
            .setTitle("Exit Application")
            .setMessage("Are you sure you want to exit the application?")
            .setPositiveButton("Exit") { dialog, _ ->
                Log.d(TAG, "User confirms exit")
                dialog.dismiss()
                currentAlertDialog = null
                
                // Disconnect payment service
                try {
                    paymentService.disconnect()
                } catch (e: Exception) {
                    Log.e(TAG, "Error disconnecting payment service", e)
                }
                
                // Finish all activities and exit app
                finishAffinity()
                
                // Force exit the process
//                android.os.Process.killProcess(android.os.Process.myPid())
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                Log.d(TAG, "User cancels exit")
                dialog.dismiss()
                currentAlertDialog = null
            }
            .setOnDismissListener {
                currentAlertDialog = null
            }
            .setCancelable(true)
            .show()
    }
    
    /**
     * Configuration validation result
     */
    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String
    )
}