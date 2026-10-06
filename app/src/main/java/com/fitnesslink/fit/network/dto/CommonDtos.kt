package com.fitnesslink.fit.network.dto

import com.google.gson.annotations.SerializedName

/**
 * The platform's `PagedResult<T>` envelope. The array lives under `data`
 * (iOS `PaginatedResponse` accepts `items` as a legacy alias and so does
 * this). Prefer this over `PaginatedResponse` in `SyncDtos.kt`, whose
 * `items` field never matches what the server sends.
 */
data class PagedResponse<T>(
    @SerializedName("data") val data: List<T>? = null,
    @SerializedName("items") private val legacyItems: List<T>? = null,
    val page: Int = 1,
    val pageSize: Int = 0,
    val totalCount: Int = 0,
    val totalPages: Int = 0,
    val hasNextPage: Boolean = false,
    val hasPreviousPage: Boolean = false
) {
    val items: List<T> get() = data ?: legacyItems ?: emptyList()
}

/**
 * `{}` request body for POST endpoints that take no parameters. iOS sends an
 * empty object because some server paths treat a missing body as 400.
 */
object EmptyBody

/**
 * Optimistic-concurrency body for the Nutrition module's mutating DELETE /
 * toggle endpoints (FB-102): the SQL Server rowversion as base64. 409 on a
 * stale token — refetch and retry.
 */
data class RowVersionBody(val rowVersion: String)
