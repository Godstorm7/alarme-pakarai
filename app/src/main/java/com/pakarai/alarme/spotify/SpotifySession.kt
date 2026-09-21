package com.pakarai.alarme.spotify

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class SpotifyStatus {
    /** Client ID não configurado no build (SPOTIFY_CLIENT_ID ausente/placeholder). */
    data object NotConfigured : SpotifyStatus()

    /** Sem conta conectada — o usuário precisa autorizar (PKCE). */
    data object Disconnected : SpotifyStatus()

    /** Conta conectada e com refresh token válido. */
    data object Connected : SpotifyStatus()
}

/**
 * Sessão OAuth do Spotify: gera o PKCE, troca o authorization code por tokens,
 * faz refresh automático e expõe o estado da conexão. Tokens ficam em prefs privadas.
 */
class SpotifySession(
    context: Context,
    clientId: String,
    private val redirectUri: String,
) {

    private val prefs = context.getSharedPreferences("spotify_session", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    // Client ID pode vir do build (BuildConfig. SPOTIFY_CLIENT_ID) ou ser colado
    // no app em runtime (campo no editor) — o valor runtime vence e fica em prefs.
    @Volatile
    private var clientId: String = prefs.getString(KEY_CLIENT_ID, null) ?: clientId

    // code_verifier do fluxo em andamento (defensivo: dura só até o retorno do browser)
    @Volatile
    private var pendingVerifier: String? = null

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<SpotifyStatus> = _status.asStateFlow()

    /** Último erro de login (negado na tela do Spotify, troca de token falhou…). */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val configured: Boolean
        get() = clientId.isNotBlank() && clientId != "REPLACE_ME"

    /** Configura o Client ID em runtime e persiste (útil quando o build veio sem ele). */
    fun configure(newClientId: String) {
        val clean = newClientId.trim()
        if (clean.isBlank()) return
        clientId = clean
        prefs.edit().putString(KEY_CLIENT_ID, clean).apply()
        _lastError.value = null
        _status.value = readStatus()
    }

    /** URL de autorização; null se o client_id não foi configurado. */
    fun authorizationUrl(): String? {
        if (!configured) return null
        _lastError.value = null
        val verifier = Pkce.verifier()
        pendingVerifier = verifier
        return "https://accounts.spotify.com/authorize" +
            "?client_id=$clientId" +
            "&response_type=code" +
            "&redirect_uri=${Pkce.urlEncode(redirectUri)}" +
            "&scope=${Pkce.urlEncode(SCOPE_PLAYBACK)}" +
            "&code_challenge_method=S256" +
            "&code_challenge=${Pkce.challenge(verifier)}" +
            "&show_dialog=false"
    }

    /** Troca o authorization code (vencido no deep link) por access+refresh token. */
    suspend fun exchangeCode(code: String): Boolean = withContext(Dispatchers.IO) {
        val verifier = pendingVerifier ?: return@withContext false
        pendingVerifier = null
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("client_id", clientId)
            .add("code_verifier", verifier)
            .build()
        val ok = postToken(body)
        if (ok) _status.value = SpotifyStatus.Connected
        else _lastError.value = "Não conseguiu autenticar no Spotify. Tente de novo."
        ok
    }

    /** Loga a falha de autorização vinda do browser (usuário negou / expirou). */
    fun reportAuthError(description: String?) {
        pendingVerifier = null
        _lastError.value = description?.takeIf { it.isNotBlank() } ?: "Login no Spotify negado."
    }

    /** Access token atual, fazendo refresh antes de expirar. Bloqueia rede (chamar fora da Main). */
    fun accessToken(): String? {
        val access = prefs.getString(KEY_ACCESS, null) ?: let {
            _status.value = readStatus()
            return null
        }
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)
        if (System.currentTimeMillis() < expiresAt - REFRESH_SKEW_MS) return access
        return refreshAccessToken()
    }

    /** Desconecta: limpa só os tokens (mantém o Client ID runtime, se colado). */
    fun clear() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_EXPIRES_AT)
            .apply()
        _lastError.value = null
        _status.value = readStatus()
    }

    private fun refreshAccessToken(): String? {
        val refresh = prefs.getString(KEY_REFRESH, null) ?: run {
            _status.value = readStatus()
            return null
        }
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", clientId)
            .build()
        val ok = postToken(body)
        return if (ok && prefs.contains(KEY_ACCESS)) {
            prefs.getString(KEY_ACCESS, null)
        } else {
            _status.value = readStatus()
            null
        }
    }

    private fun postToken(body: FormBody): Boolean {
        val req = Request.Builder()
            .url(TOKEN_URL)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(body)
            .build()
        return try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val json = JSONObject(resp.body?.string().orEmpty())
                val access = json.optString("access_token").takeIf { it.isNotBlank() } ?: return@use false
                val newRefresh = json.optString("refresh_token").takeIf { it.isNotBlank() }
                prefs.edit()
                    .putString(KEY_ACCESS, access)
                    .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000)
                    .apply {
                        if (newRefresh != null) putString(KEY_REFRESH, newRefresh)
                    }
                    .apply()
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun readStatus(): SpotifyStatus {
        if (!configured) return SpotifyStatus.NotConfigured
        return if (prefs.contains(KEY_REFRESH)) SpotifyStatus.Connected else SpotifyStatus.Disconnected
    }

    companion object {
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val SCOPE_PLAYBACK = "user-modify-playback-state user-read-playback-state"
        private const val REFRESH_SKEW_MS = 60_000L
    }
}