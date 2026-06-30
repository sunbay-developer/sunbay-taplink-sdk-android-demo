package com.sunmi.tapro.taplink.demo.device

import androidx.annotation.RawRes
import com.sunmi.tapro.taplink.demo.R

/**
 * Card type enum matching the Tapro Headless event cardTypes values.
 * Values correspond to card reading capabilities.
 */
enum class CardType {
    CONTACTLESS,
    CONTACT,
    MAGSTRIPE;

    companion object {
        /**
         * Parse card type from Headless event string (case-insensitive).
         * Accepted values: CONTACTLESS, CONTACT, MAGSTRIPE
         */
        fun fromString(value: String): CardType? {
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
        }
    }
}

/**
 * Returns the animated WebP resource ID for the card reading animation,
 * based on the current device model and supported card types.
 *
 * Logic:
 * - If only MAGSTRIPE is supported -> swipe animation
 * - Otherwise -> all card read animation (tap/insert/swipe)
 *
 * @param cardTypes List of card types currently enabled. If empty, defaults to all-card animation.
 * @return Raw resource ID for the animated WebP
 */
@RawRes
fun getCardAnimationResId(cardTypes: List<CardType> = emptyList()): Int {
    val isSwipeOnly = cardTypes.size == 1 && cardTypes.contains(CardType.MAGSTRIPE)
    return if (isSwipeOnly) {
        getSwipeAnimationRes()
    } else {
        getAllCardReadAnimationRes()
    }
}

/**
 * Returns the animated WebP resource ID for card animation based on
 * raw string card type list from HeadlessProgressEvent.
 *
 * @param cardTypeStrings List of card type strings (e.g. ["CONTACTLESS", "CONTACT", "MAGSTRIPE"])
 * @return Raw resource ID for the animated WebP
 */
@RawRes
fun getCardAnimationResIdFromStrings(cardTypeStrings: List<String>): Int {
    val cardTypes = cardTypeStrings.mapNotNull { CardType.fromString(it) }
    return getCardAnimationResId(cardTypes)
}

/**
 * Swipe-only animation resource, selected by device model.
 */
@RawRes
private fun getSwipeAnimationRes(): Int {
    return when {
        isP3MIX() -> R.raw.p3_mix_swipe_webp
        isP3k() -> R.raw.p3k_swipe_card_webp
        isP2SmartPad() -> R.raw.sp_swipe_card_webp
        isP3Air() -> R.raw.p3air_swipe_card_webp
        isPayPad3SecondaryScreen() -> R.raw.cpad_pay_desktop_swipe_card_webp
        isPayPad3() -> R.raw.cpad_pay_mobile_swipe_card_webp
        else -> R.raw.p3_swipe_webp
    }
}

/**
 * All card read animation resource (tap/insert/swipe), selected by device model.
 */
@RawRes
private fun getAllCardReadAnimationRes(): Int {
    return when {
        isP3MIX() -> R.raw.p3_mix_all_card_read_webp
        isP3k() -> R.raw.p3k_all_card_read_webp
        isP2SmartPad() -> R.raw.sp_all_card_read_webp
        isP3Air() -> R.raw.p3air_all_card_read_webp
        isPayPad3SecondaryScreen() -> R.raw.cpad_pay_desktop_all_card_read_webp
        isPayPad3() -> R.raw.cpad_pay_mobile_all_card_read_webp
        else -> R.raw.p3_all_card_read_webp
    }
}
