package org.thecragunofficial.android.data.remote

import okhttp3.ResponseBody
import org.thecragunofficial.android.data.remote.dto.DataList
import org.thecragunofficial.android.data.remote.dto.DataObj
import org.thecragunofficial.android.data.remote.dto.LookupDto
import org.thecragunofficial.android.data.remote.dto.NodeDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The JSON endpoints the thecrag.com web UI uses. They answer at the "anonweb"
 * access level when the request carries a thecrag Referer and the anonymous
 * session cookie; [SessionInterceptor] adds both. See docs/research/api.md.
 */
interface TheCragApi {
    @GET("api/node/id/{id}")
    suspend fun node(
        @Path("id") id: Long,
        @Query("show") show: List<String> = listOf("ancestors"),
        @Query("markupType") markupType: String? = null,
    ): DataObj<NodeDto>

    @GET("api/node/id/{id}/children/area")
    suspend fun childAreas(@Path("id") id: Long): DataList<NodeDto>

    @GET("api/node/id/{id}/children/route")
    suspend fun childRoutes(@Path("id") id: Long): DataList<NodeDto>

    @GET("api/lookup/node")
    suspend fun lookup(
        @Query("search") search: String,
        @Query("ancestor") ancestor: Long,
        @Query("page-size") pageSize: Int = 25,
    ): DataList<LookupDto>

    /** Plain page load; its only purpose is to receive the anonymous session cookie. */
    @GET("home")
    suspend fun home(): Response<ResponseBody>
}
