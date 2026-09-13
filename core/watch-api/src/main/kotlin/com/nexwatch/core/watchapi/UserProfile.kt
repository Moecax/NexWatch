package com.nexwatch.core.watchapi

/**
 * Feeds on-watch calculations (§2); passed to both bind() and login(), and re-sending it
 * on every login updates the watch's copy.
 */
data class UserProfile(
    val sex: Sex,
    val age: Int,
    val heightCm: Int,
    val weightKg: Int,
) {
    enum class Sex { MALE, FEMALE }
}
