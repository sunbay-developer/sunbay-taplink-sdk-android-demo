package com.sunmi.tapro.taplink.demo.service.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Utility object for converting amounts between dollars (BigDecimal) and cents (Int).
 *
 * The Nexus SDK uses Integer (cents) for amount representation,
 * while the app uses BigDecimal (dollars). This converter bridges the two formats.
 */
object AmountConverter {

    private const val CENTS_MULTIPLIER = 100
    private const val DECIMAL_PLACES = 2

    /**
     * Convert dollar amount (BigDecimal) to cents (Int).
     * Multiplies by 100 and rounds half-up.
     */
    fun toCents(dollars: BigDecimal): Int {
        return dollars.multiply(BigDecimal(CENTS_MULTIPLIER))
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()
    }

    /**
     * Convert cents (Int) to dollar amount (BigDecimal).
     * Divides by 100 with 2 decimal places.
     * Returns null if input is null.
     */
    fun toDollars(cents: Int?): BigDecimal? {
        if (cents == null) return null
        return BigDecimal(cents)
            .divide(BigDecimal(CENTS_MULTIPLIER), DECIMAL_PLACES, RoundingMode.HALF_UP)
    }
}
