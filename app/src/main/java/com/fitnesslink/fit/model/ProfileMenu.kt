package com.fitnesslink.fit.model

data class ProfileMenu(
    val id: Int = 0,
    val text: String = "",
    /** Row glyph key (iOS `systemImage`); mapped to a Material icon by the profile menu. Empty = none. */
    val icon: String = ""
)
