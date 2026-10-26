package xyz.intiluha.thecrag.data.auth

import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import xyz.intiluha.thecrag.data.remote.THECRAG_BASE
import xyz.intiluha.thecrag.data.remote.dto.DataObj
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Where the website sends the browser after login; we never follow it. */
const val LOGIN_TARGET = THECRAG_BASE + "home"

/**
 * Calls that carry the logged-in session cookie. They go through a client of
 * their own (separate cookie jar, no redirects, no cache) so the account is
 * disclosed only for login, identity, the own logbook and ticks. Everything is
 * `authweb` level: the session cookie plus `cookieAuth=1`, as the website's
 * own JavaScript does.
 */
interface AuthApi {
    /** The login form; its HTML carries the per-session CSRF token and sets the session cookie. */
    @GET("CIDS/cgi-bin/cids.cgi?C:HideNavigation=1&C:State=13216")
    suspend fun loginForm(@Query("D:LoginTarget") target: String = LOGIN_TARGET): Response<ResponseBody>

    /** 302 to the target on success; 200 with the form and an alert on failure. */
    @FormUrlEncoded
    @POST("CIDS/cgi-bin/cids.cgi")
    suspend fun login(@FieldMap fields: Map<String, String>): Response<ResponseBody>

    @GET("api/account/id/me?cookieAuth=1")
    suspend fun me(): DataObj<JsonObject>

    @GET("api/climber/id/me/ascents?cookieAuth=1")
    suspend fun ascents(@Query("page") page: Int, @Query("page-size") pageSize: Int): JsonObject

    @POST("api/ascent/create?cookieAuth=1")
    suspend fun createAscent(@Body body: JsonObject): JsonObject

    /** `{"data":{"ascent":id}}` -> 202 `{"ok":{}}`. */
    @POST("api/ascent/delete?cookieAuth=1")
    suspend fun deleteAscent(@Body body: JsonObject): JsonObject

    /** Same checks as create without writing anything: the "dry run". Answers 202 with the data echoed. */
    @POST("api/ascent/create/validate?cookieAuth=1")
    suspend fun validateAscent(@Body body: JsonObject): JsonObject
}
