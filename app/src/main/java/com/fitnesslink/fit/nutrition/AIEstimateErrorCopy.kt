package com.fitnesslink.fit.nutrition

/** User-facing copy for the AI endpoints' `errorReasonCode` values (iOS `AIEstimateErrorCopy`). */
object AIEstimateErrorCopy {
    fun message(reasonCode: String?, detail: String?): String = when (reasonCode) {
        "implausible_estimate" -> "Couldn't estimate this one reliably — try again or fill it in by hand."
        "prompt_empty" -> "Enter a food name to estimate."
        "gemini_unavailable", "timeout" -> "The estimate service is unavailable right now. Try again shortly."
        "rate_limited" -> "You've used AI a lot in the last hour — try again in a little while."
        "no_items" -> "Couldn't pick out any foods from that. Try naming the items."
        // FTNSSLNK-488/489: Snap a Meal.
        "no_food_detected" -> "Couldn't find any food in that photo. Try another shot, or describe the meal instead."
        "image_unreadable" -> "Couldn't make out the food — try better light or a closer shot."
        "image_invalid" -> "That photo couldn't be used. Try taking it again."
        "hint_too_long" -> "Keep the hint under 200 characters."
        "source_generation_invalid" -> "That photo result has expired. Snap it again or describe the meal."
        else -> detail ?: "Couldn't estimate this food. Try again."
    }
}

/** Compile-time gates for nutrition entry points. */
object NutritionFeatureFlags {
    /**
     * FTNSSLNK-494: the Snap a Meal entry point. iOS ships with
     * `SnapMealFeature.isEnabled = true`; flip to false if the
     * identify-meal-photo backend is not deployed for this build.
     */
    const val snapMealEnabled: Boolean = true
}
