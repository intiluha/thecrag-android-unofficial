package xyz.intiluha.thecrag.data.remote

import xyz.intiluha.thecrag.data.remote.dto.DataList
import xyz.intiluha.thecrag.data.remote.dto.DataObj
import xyz.intiluha.thecrag.data.remote.dto.LookupDto
import xyz.intiluha.thecrag.data.remote.dto.MapBboxDto
import xyz.intiluha.thecrag.data.remote.dto.NodeDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The JSON endpoints the thecrag.com web UI uses. They answer at the "anonweb"
 * access level when the request carries a thecrag Referer and the anonymous
 * session cookie; [SessionInterceptor] adds both. See docs/research/api.md.
 *
 * `cacheControl` is null for normal reads (served from the disk cache when
 * fresh) and [NO_CACHE] for an explicit refresh.
 */
interface TheCragApi {
    @GET("api/node/id/{id}")
    suspend fun node(
        @Path("id") id: Long,
        @Query("show") show: List<String> = listOf("ancestors"),
        @Query("markupType") markupType: String? = null,
        @Header("Cache-Control") cacheControl: String? = null,
    ): DataObj<NodeDto>

    @GET("api/node/id/{id}/children/area")
    suspend fun childAreas(@Path("id") id: Long, @Header("Cache-Control") cacheControl: String? = null): DataList<NodeDto>

    @GET("api/node/id/{id}/children/route")
    suspend fun childRoutes(@Path("id") id: Long, @Header("Cache-Control") cacheControl: String? = null): DataList<NodeDto>

    @GET("api/lookup/node")
    suspend fun lookup(
        @Query("search") search: String,
        @Query("ancestor") ancestor: Long,
        @Query("page-size") pageSize: Int = 25,
    ): DataList<LookupDto>

    @GET("api/map/bbox/heirachy")
    suspend fun mapBbox(
        @Query("s") s: String,
        @Query("v") v: String,
        @Query("f") f: String = "geometry,numberRoutes",
    ): DataObj<MapBboxDto>


    /** Plain page load; its only purpose is to receive the anonymous session cookie. */
    @GET("home")
    suspend fun home(): Response<ResponseBody>
}
