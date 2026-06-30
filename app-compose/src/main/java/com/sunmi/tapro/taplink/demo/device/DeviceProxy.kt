package com.sunmi.tapro.taplink.demo.device

import android.os.Build
import android.text.TextUtils
import java.util.Locale

/**
 * Device proxy that identifies the current device model and provides
 * device-specific configuration (tablet mode, keyboard, printer, reader, etc.).
 *
 * Ported from tapro-app DeviceProxy logic for use in the demo application.
 */
object DeviceProxy {

    val device: BaseDevice = when {
        isP3k() -> P3K()
        isP3MIX() -> P3MIX()
        isP3() -> P3()
        isP2SmartPad() -> P2SmartPad()
        isP3Air() -> P3Air()
        isPayPad3() -> CPadPay3()
        else -> UnknownDevice()
    }

    init {
        android.util.Log.i("DeviceProxy", "model:${Build.MODEL} identified as: ${device.javaClass.simpleName}")
    }
}

/**
 * Base device configuration. Subclasses override properties to match hardware capabilities.
 */
open class BaseDevice(
    val hasPhysicalKeyboard: Boolean = false,
    val hasLocalPrinterSupport: Boolean = true,
    val hasLocalCardReaderSupport: Boolean = true,
    val isTabletDevice: Boolean = false,
    val isHiddenReader: Boolean = false
)

// P3 series base
class P3 : BaseDevice()

// P3K - with physical keyboard
class P3K : BaseDevice(hasPhysicalKeyboard = true)

// P3 Air - no printer, hidden reader
class P3Air : BaseDevice(
    hasPhysicalKeyboard = false,
    hasLocalPrinterSupport = false,
    hasLocalCardReaderSupport = true,
    isHiddenReader = true
)

// CPad Pay3 - tablet with hidden reader
class CPadPay3 : BaseDevice(
    hasPhysicalKeyboard = false,
    hasLocalPrinterSupport = false,
    hasLocalCardReaderSupport = true,
    isTabletDevice = true,
    isHiddenReader = true
)

// P3 MIX - tablet device
class P3MIX : BaseDevice(isTabletDevice = true)

// P2 SmartPad - tablet with physical keyboard, no printer
class P2SmartPad : BaseDevice(
    isTabletDevice = true,
    hasPhysicalKeyboard = true,
    hasLocalPrinterSupport = false
)

// Unknown device - assume no printer, has card reader
class UnknownDevice : BaseDevice(
    hasLocalPrinterSupport = false,
    hasLocalCardReaderSupport = true
)

// ========== Device detection functions ==========

fun isP3(): Boolean {
    return getModel().matches("p3".toRegex())
}

fun isP3Air(): Boolean {
    return getModel().matches("p3_air(-.+)?".toRegex())
}

fun isP3k(): Boolean {
    return getModel().matches("p3k(-.+)?".toRegex())
}

fun isP3MIX(): Boolean {
    return getModel().matches("p3_mix(-.+)?".toRegex())
}

fun isP2SmartPad(): Boolean {
    return getModel().matches("p2_smartpad(-.+)?".toRegex())
}

fun isPayPad3(): Boolean {
    return getModel().matches("paypad3(-.+)?".toRegex())
}

fun isPayPad3SecondaryScreen(): Boolean {
    if (!isPayPad3()) return false
    val pn = getSystemProperty("ro.fac.cfg.PN")
    return pn in listOf(
        "PAYPAD3_0AW_RD5_01GL",
        "PAYPAD3_0AW_RD5_22EU",
        "PAYPAD3_0AW_RD5_27NA",
        "PAYPAD3_0AW_RD5_08GL",
        "PAYPAD3_0AW_RD5_23EU",
        "PAYPAD3_0AW_RD5_29NA",
        "PAYPAD3_0AW_RD5_06GL",
        "PAYPAD3_0AW_RD5_07GL",
        "PAYPAD3_0AW_RD5_20EU",
        "PAYPAD3_0AW_RD5_21EU",
        "PAYPAD3_0AW_RD5_25NA",
        "PAYPAD3_0AW_RD5_14GL"
    )
}

/**
 * Get the device model string (lowercase).
 * Priority: ro.sunmi.hardware > Build.MODEL > "unknown"
 */
fun getModel(): String {
    var model: String? = getSystemProperty("ro.sunmi.hardware")
    if (model.isNullOrEmpty()) {
        model = Build.MODEL
    }
    if (model.isNullOrEmpty()) {
        model = Build.UNKNOWN
    }
    return model?.lowercase(Locale.getDefault()) ?: ""
}

/**
 * Read a system property via reflection.
 */
private fun getSystemProperty(key: String): String {
    return try {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java)
        (method.invoke(null, key) as? String) ?: ""
    } catch (e: Exception) {
        ""
    }
}
