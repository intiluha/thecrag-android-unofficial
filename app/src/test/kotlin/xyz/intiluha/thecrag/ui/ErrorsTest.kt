package xyz.intiluha.thecrag.ui

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import xyz.intiluha.thecrag.ui.common.describe
import xyz.intiluha.thecrag.ui.common.needsLogin

class ErrorsTest {
    private fun apiError(json: String) =
        HttpException(Response.error<Any>(400, json.toResponseBody("application/json".toMediaType())))

    @Test
    fun insufficientAccessAsksForLoginEvenAfterTheMessageWasRead() {
        val e = apiError("""{"error":"You have insufficient access for the api endpoint '/api/node/id/\\d+'","errorLabel":"insufficient-api-permissions"}""")
        assertTrue(e.describe().contains("logged-in accounts"))
        // The body must survive the first read: both calls happen for one screen.
        assertTrue(e.needsLogin())
        assertTrue(e.needsLogin())
    }

    @Test
    fun otherApiErrorsKeepRetry() {
        val e = apiError("""{"error":"Node not found","errorLabel":"not-found"}""")
        assertEquals("theCrag: Node not found", e.describe())
        assertFalse(e.needsLogin())
    }
}
