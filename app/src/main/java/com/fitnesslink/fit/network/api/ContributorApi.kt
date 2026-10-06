package com.fitnesslink.fit.network.api

import com.fitnesslink.fit.network.dto.ContributorSummaryDto
import com.fitnesslink.fit.network.dto.PagedResponse
import retrofit2.http.GET
import retrofit2.http.Query

/** Mirrors iOS `ContributorService`. */
interface ContributorApi {
    /**
     * The contributor roster for the filter pickers. `pageSize` is generous
     * so one call returns everyone (the roster is small and slow-changing).
     * Approved-only is not a server param — filter client-side if needed.
     */
    @GET("contributors")
    suspend fun list(@Query("pageSize") pageSize: Int = 200): PagedResponse<ContributorSummaryDto>
}
