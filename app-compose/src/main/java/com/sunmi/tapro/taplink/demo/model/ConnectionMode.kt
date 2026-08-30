package com.sunmi.tapro.taplink.demo.model

/**
 * ConnectionMode enum representing different connection types supported by Taplink SDK
 */
enum class ConnectionMode {
    /**
     * On-device integration mode (recommended over [APP_TO_APP]).
     *
     * The merchant app and Tapro run on the SAME terminal; communication happens via
     * Android IPC. This is the SDK's business-facing integration mode name for what
     * was previously exposed as [APP_TO_APP] — its current underlying implementation
     * is still App-to-App under the hood.
     */
    ON_DEVICE,

    /**
     * PinPad integration mode (recommended over [LAN] / [CABLE]).
     *
     * Tapro acts as a standalone payment PIN Pad reached over a connection channel.
     * The concrete transport (AUTO / LAN / CABLE) is a configuration detail selected
     * via `PinpadStrategy`, mirroring how [CABLE] configures its protocol.
     */
    PINPAD,

    /**
     * App-to-App connection mode
     * Communication between merchant app and Tapro app via Android IPC
     */
    @Deprecated("Use ON_DEVICE instead.", ReplaceWith("ConnectionMode.ON_DEVICE"))
    APP_TO_APP,

    /**
     * Cable connection mode
     * Direct USB/serial cable connection to payment terminal
     */
    @Deprecated("Use PINPAD with PinpadStrategy.CABLE instead.")
    CABLE,

    /**
     * LAN connection mode
     * Network connection to payment terminal via local area network
     */
    @Deprecated("Use PINPAD with PinpadStrategy.LAN instead.")
    LAN,

    /**
     * Cloud connection mode
     * Direct HTTP API connection to Sunbay cloud service via Nexus SDK
     */
    CLOUD,

    /**
     * Secondary Display connection mode
     * Connects via USB VSP (Virtual Serial Port) and automatically opens the
     * secondary display on the payment terminal for customer-facing content.
     */
    SECONDARY_DISPLAY
}
