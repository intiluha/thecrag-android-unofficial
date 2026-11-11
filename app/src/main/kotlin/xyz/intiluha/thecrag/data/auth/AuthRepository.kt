package xyz.intiluha.thecrag.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import xyz.intiluha.thecrag.data.remote.KeyValueStore
import xyz.intiluha.thecrag.data.remote.PersistentCookieJar
import xyz.intiluha.thecrag.data.remote.dto.long
import xyz.intiluha.thecrag.data.remote.dto.string

@Serializable
data class Account(
    val id: Long,
    /** Display name as theCrag shows it (`name` in the account reply), not what one logs in with. */
    val name: String,
    val loggedInAt: Long,
    /** What the user typed to log in; used to prefill the form when the session expires. Absent on records from older builds. */
    val login: String? = null,
)

/**
 * Website login driven natively: GET the form (session cookie + CSRF), POST the
 * credentials, then one `me` call to confirm and learn the account id. Three
 * requests, the password is never stored. Logout is local only.
 */
class AuthRepository(
    private val api: AuthApi,
    private val cookies: PersistentCookieJar,
    private val store: KeyValueStore,
    private val json: Json,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    class LoginFailed(message: String) : Exception(message)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Account?> = _state
    val account: Account? get() = _state.value

    /**
     * True once theCrag answered a call with the login cookie as if there were none: the
     * server-side session is gone while [account] is still remembered. The Account screen
     * then shows the login form again; a successful login or a logout clears it.
     */
    val sessionExpired = MutableStateFlow(false)

    fun markExpired() { sessionExpired.value = true }

    suspend fun login(username: String, password: String): Account {
        cookies.clear()
        val formHtml = api.loginForm().let { it.body()?.string() ?: it.errorBody()?.string() ?: "" }
        val csrf = CSRF.find(formHtml)?.groupValues?.get(1)
            ?: throw LoginFailed("Login form not recognised: no CSRF token in theCrag's reply.")
        val fields = mapOf(
            "D:Login" to username,
            "D:Password" to password,
            "State:Login" to "Login",
            "C:ResetContinueURL" to "1",
            "D:LoginTarget" to LOGIN_TARGET,
            "C:Portal" to "Default Licensee",
            "D:CSRF" to csrf,
        )
        val res = api.login(fields)
        if (res.code() !in 300..399) {
            val body = res.body()?.string() ?: res.errorBody()?.string() ?: ""
            val alert = ALERT.find(body)?.groupValues?.get(1)?.replace(TAGS, " ")?.replace(Regex("\\s+"), " ")?.trim()
            throw LoginFailed(alert?.takeIf { it.isNotBlank() } ?: "Login rejected (HTTP ${res.code()}).")
        }
        val me = api.me().data
        val id = me.long("id") ?: me.long("accountID")
            ?: throw LoginFailed("Logged in, but theCrag's account reply has no id: ${me.keys.joinToString()}")
        val name = me.string("name") ?: me.string("label") ?: me.string("login") ?: username
        val account = Account(id, name, clock(), login = username.trim())
        store.put(KEY, json.encodeToString(Account.serializer(), account))
        _state.value = account
        sessionExpired.value = false
        return account
    }

    /**
     * Asks theCrag whether the stored session is still accepted and updates [sessionExpired].
     * Returns true when it is; a network failure leaves the flag as it was and returns its inverse.
     */
    suspend fun checkSession(): Boolean {
        if (account == null) return false
        return try {
            api.me()
            sessionExpired.value = false
            true
        } catch (e: HttpException) {
            val body = runCatching { e.response()?.errorBody()?.source()?.peek()?.readUtf8() }.getOrNull().orEmpty()
            if (body.contains("insufficient-api-permissions") || body.contains("authweb")) markExpired()
            !sessionExpired.value
        } catch (e: Exception) {
            !sessionExpired.value
        }
    }

    fun logout() {
        sessionExpired.value = false
        cookies.clear()
        store.put(KEY, "")
        _state.value = null
    }

    private fun load(): Account? =
        store.get(KEY)?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(Account.serializer(), it) }.getOrNull() }

    companion object {
        const val KEY = "thecrag.account"
        private val CSRF = Regex("""name="D:CSRF"\s+value="([^"]+)"""")
        private val ALERT = Regex("""<div class="alert alert-danger"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
        private val TAGS = Regex("<[^>]*>")
    }
}
