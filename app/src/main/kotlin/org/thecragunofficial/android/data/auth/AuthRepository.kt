package org.thecragunofficial.android.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.thecragunofficial.android.data.remote.KeyValueStore
import org.thecragunofficial.android.data.remote.PersistentCookieJar
import org.thecragunofficial.android.data.remote.dto.long
import org.thecragunofficial.android.data.remote.dto.string

@Serializable
data class Account(val id: Long, val name: String, val loggedInAt: Long)

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
        val account = Account(id, name, clock())
        store.put(KEY, json.encodeToString(Account.serializer(), account))
        _state.value = account
        return account
    }

    fun logout() {
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
