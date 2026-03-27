package com.sunmi.tapro.taplink.demo.model

/**
 * ConnectionMode enum representing different connection types supported by Taplink SDK
 */
enum class ConnectionMode {
    /**
     * App-to-App connection mode
     * Communication between merchant app and Tapro app via Android IPC
     */
    APP_TO_APP,

    /**
     * Cable connection mode
     * Direct USB/serial cable connection to payment terminal
     */
    CABLE,

    /**
     * LAN connection mode
     * Network connection to payment terminal via local area network
     */
    LAN,

    /**
     * Cloud connection mode
     * Direct HTTP API connection to Sunbay cloud service via Nexus SDK
     */
    CLOUD
}
