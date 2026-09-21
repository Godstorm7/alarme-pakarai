package com.pakarai.alarme.spotify

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SpotifyItem(
    val uri: String,
    val name: String,
    val subtitle: String,
    val kind: String,
    val imageUrl: String = "",
)

data class SpotifyDevice(
    val id: String,
    val name: String,
    val type: String,
    val isActive: Boolean,
    val restricted: Boolean,
    /** O device aceita controle de volume pela Web API? (se não, rampa no canal de mídia) */
    val supportsVolume: Boolean = false,
)

/** Resultado de uma busca: itens (pode ser vazio) ou a falha real (pra UI/logcat). */
sealed interface SearchOutcome {
    data class Ok(val items: List<SpotifyItem>) : SearchOutcome
    /** [code] = HTTP status (null = falha de rede/exceção). */
    data class Failure(val code: Int?, val message: String) : SearchOutcome
}

/** Fonte de conteúdo abstrata do Spotify — testável com um fake. */
interface SpotifyClient {
    suspend fun search(query: String): List<SpotifyItem>

    /**
     * Igual a [search], mas sem esconder o erro. Implementações simples podem herdar
     * o default (embrulha [search]); o cliente HTTP real sobrescreve com o código HTTP.
     */
    suspend fun searchDetailed(query: String): SearchOutcome =
        SearchOutcome.Ok(search(query))

    /** Playlists do usuário (biblioteca). Precisa dos escopos playlist-read-*. */
    suspend fun myPlaylists(): List<SpotifyItem> = emptyList()

    suspend fun devices(): List<SpotifyDevice>
    suspend fun transferTo(deviceId: String): Boolean
    suspend fun setVolume(percent: Int): Boolean
    suspend fun play(uri: String): Boolean
    suspend fun resumePlay(): Boolean
    suspend fun pause(): Boolean
    /** true = tocando agora; false = parado/sem faixa; null = sem resposta (pra fallback). */
    suspend fun isPlaying(): Boolean?
}

/** Payload JSON do play: contexto (playlist/álbum/artista) ou faixa única. Puro e testável. */
fun buildPlayBody(uri: String): String {
    val json = JSONObject()
    if (uri.startsWith("spotify:track:")) {
        json.put("uris", JSONArray().put(uri))
    } else {
        json.put("context_uri", uri)
    }
    return json.toString()
}

class SpotifyHttpClient(
    private val session: SpotifySession,
) : SpotifyClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    // a busca tem payload maior e pode demorar — timeouts folgados (compartilha pool)
    private val searchHttp = http.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    override suspend fun search(query: String): List<SpotifyItem> =
        when (val outcome = searchDetailed(query)) {
            is SearchOutcome.Ok -> outcome.items
            is SearchOutcome.Failure -> emptyList()
        }

    override suspend fun searchDetailed(query: String): SearchOutcome = withContext(Dispatchers.IO) {
        val token = session.accessToken()
            ?: return@withContext SearchOutcome.Failure(401, "Sem sessão do Spotify (não conectado).")
        try {
            // HttpUrl.Builder cuida do encoding (o query pode ter espaço/acento/&)
            val url = "https://api.spotify.com/v1/search".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("q", query)
                ?.addQueryParameter("type", "track,album,playlist,artist")
                ?.addQueryParameter("limit", "10")
                ?.build()
                ?: return@withContext SearchOutcome.Failure(null, "URL de busca inválida")
            searchHttp.newCall(get(url.toString(), token)).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching {
                        JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
                    }.getOrDefault("")
                    Log.w(TAG, "search falhou: HTTP ${resp.code} — $msg")
                    return@use SearchOutcome.Failure(resp.code, msg.ifBlank { "HTTP ${resp.code}" })
                }
                val json = runCatching { JSONObject(body) }.getOrNull()
                    ?: return@use SearchOutcome.Failure(null, "Resposta inválida do Spotify")
                SearchOutcome.Ok(
                    parseTracks(json.optJSONObject("tracks")) +
                        parseAlbums(json.optJSONObject("albums")) +
                        parsePlaylists(json.optJSONObject("playlists")) +
                        parseArtists(json.optJSONObject("artists"))
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "search erro: ${e.javaClass.simpleName}: ${e.message}")
            SearchOutcome.Failure(null, "${e.javaClass.simpleName}: ${e.message ?: "falha"}")
        }
    }

    override suspend fun myPlaylists(): List<SpotifyItem> = withContext(Dispatchers.IO) {
        val token = session.accessToken() ?: return@withContext emptyList()
        val req = get("https://api.spotify.com/v1/me/playlists?limit=50", token)
        try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "myPlaylists falhou: HTTP ${resp.code}")
                    return@use emptyList()
                }
                parsePlaylists(JSONObject(resp.body?.string().orEmpty()))
            }
        } catch (e: Exception) {
            Log.w(TAG, "myPlaylists erro: ${e.message}")
            emptyList()
        }
    }

    override suspend fun devices(): List<SpotifyDevice> = withContext(Dispatchers.IO) {
        val token = session.accessToken() ?: return@withContext emptyList()
        val req = get("https://api.spotify.com/v1/me/player/devices", token)
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("devices") ?: return@use emptyList()
                List(arr.length()) { i ->
                    val d = arr.getJSONObject(i)
                    SpotifyDevice(
                        id = d.optString("id"),
                        name = d.optString("name"),
                        type = d.optString("type"),
                        isActive = d.optBoolean("is_active", false),
                        restricted = d.optBoolean("restricted", false),
                        supportsVolume = d.optBoolean("supports_volume", false),
                    )
                }.filter { it.id.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun transferTo(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        val token = session.accessToken() ?: return@withContext false
        val body = JSONObject().put("device_ids", JSONArray().put(deviceId)).toString()
        put("https://api.spotify.com/v1/me/player", token, body)
    }

    override suspend fun setVolume(percent: Int): Boolean = withContext(Dispatchers.IO) {
        val token = session.accessToken() ?: return@withContext false
        val p = percent.coerceIn(0, 100)
        put("https://api.spotify.com/v1/me/player/volume?volume_percent=$p", token, null)
    }

    override suspend fun play(uri: String): Boolean = withContext(Dispatchers.IO) {
        val token = session.accessToken() ?: return@withContext false
        put("https://api.spotify.com/v1/me/player/play", token, buildPlayBody(uri))
    }

    override suspend fun resumePlay(): Boolean = withContext(Dispatchers.IO) {
        val token = session.accessToken() ?: return@withContext false
        put("https://api.spotify.com/v1/me/player/play", token, "{}")
    }

    override suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        val token = session.accessToken() ?: return@withContext false
        put("https://api.spotify.com/v1/me/player/pause", token, null)
    }

    override suspend fun isPlaying(): Boolean? = withContext(Dispatchers.IO) {
        val token = session.accessToken() ?: return@withContext null
        val req = get("https://api.spotify.com/v1/me/player/currently-playing", token)
        runCatching {
            http.newCall(req).execute().use { resp ->
                when {
                    !resp.isSuccessful -> null
                    resp.code == 204 -> false
                    else -> JSONObject(resp.body?.string().orEmpty()).optBoolean("is_playing", false)
                }
            }
        }.getOrNull()
    }

    private fun get(url: String, token: String): Request =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

    private fun put(url: String, token: String, body: String?): Boolean {
        val reqBody = body?.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .method("PUT", reqBody)
            .build()
        return runCatching {
            http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun parseTracks(o: JSONObject?): List<SpotifyItem> =
        items(o) { t ->
            val artists = t.optJSONArray("artists")
            val artistNames = List(artists?.length() ?: 0) { i -> artists.getJSONObject(i).optString("name") }
            SpotifyItem(
                t.optString("uri"),
                t.optString("name"),
                artistNames.joinToString(", "),
                "Faixa",
                firstImage(t.optJSONObject("album"))
            )
        }

    private fun parseAlbums(o: JSONObject?): List<SpotifyItem> =
        items(o) { a ->
            val artists = a.optJSONArray("artists")
            val artistNames = List(artists?.length() ?: 0) { i -> artists.getJSONObject(i).optString("name") }
            SpotifyItem(a.optString("uri"), a.optString("name"), artistNames.joinToString(", "), "Álbum", firstImage(a))
        }

    private fun parsePlaylists(o: JSONObject?): List<SpotifyItem> =
        items(o) { p ->
            val owner = p.optJSONObject("owner")?.optString("display_name").orEmpty()
            SpotifyItem(p.optString("uri"), p.optString("name"), "Playlist · $owner", "Playlist", firstImage(p))
        }

    private fun parseArtists(o: JSONObject?): List<SpotifyItem> =
        items(o) { a ->
            SpotifyItem(a.optString("uri"), a.optString("name"), "Artista", "Artista", firstImage(a))
        }

    /** Primeira imagem (capa) de um objeto do Spotify, ou "" se não tiver. */
    private fun firstImage(o: JSONObject?): String {
        val arr = o?.optJSONArray("images") ?: return ""
        if (arr.length() == 0) return ""
        return arr.getJSONObject(0).optString("url")
    }

    private fun items(o: JSONObject?, map: (JSONObject) -> SpotifyItem): List<SpotifyItem> {
        val arr = o?.optJSONArray("items") ?: return emptyList()
        return List(arr.length()) { i -> map(arr.getJSONObject(i)) }.filter { it.uri.isNotBlank() }
    }

    private companion object {
        const val TAG = "SpotifySearch"
    }
}