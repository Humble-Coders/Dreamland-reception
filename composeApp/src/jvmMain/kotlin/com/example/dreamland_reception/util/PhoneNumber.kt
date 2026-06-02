package com.example.dreamland_reception.util

/**
 * Normalizes a receptionist-entered phone number to canonical E.164 form (`+91XXXXXXXXXX`).
 *
 * Reception always types a bare 10-digit Indian mobile number — the `+91` country code is
 * attached here automatically. This is the match key stored in `users.phoneNumber`, so the
 * format must be identical to what the mobile app's `linkOrFetchMyUser` produces.
 *
 *  - non-digits (spaces, dashes, etc.) are stripped defensively
 *  - 10 digits                → `+91XXXXXXXXXX`
 *  - 11 digits with leading `0`→ drop the `0`, then `+91XXXXXXXXXX`
 *  - 12 digits starting `91`   → treated as already carrying the country code → `+91XXXXXXXXXX`
 *  - anything else returns `null`, which callers treat as "no phone provided"
 */
fun normalizePhoneE164(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val digits = raw.filter { it.isDigit() }
    val national = when {
        digits.length == 10 -> digits
        digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
        digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
        else -> return null
    }
    return "+91$national"
}
