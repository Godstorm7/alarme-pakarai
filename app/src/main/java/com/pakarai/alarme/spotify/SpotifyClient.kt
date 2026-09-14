package com.pakarai.alarme.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
)

data class SpotifyDevice(
    val id: String,
    val name: String,
    val type: String,
    val isActive: Boolean,
    val restricted: Boolean,
)

/** Fonte de conteúdo abstrata do Spotify — testável com um fake. */
interface SpotifyClient {
    suspend fun search(query: String): List<SpotifyItem>
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

    override suspend fun search(query: String): List<SpotifyItem> = withContext(Dispatchers.IO) {
        val token = session.accessToken() ?: return@withContext emptyList()
        val url = "https://api.spotify.com/v1/search?q=${Pkce.urlEncode(query)}&type=track,album,playlist,artist&limit=10"
        val req = get(url, token)
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val json = JSONObject(resp.body?.string().orEmpty())
                parseTracks(json.optJSONObject("tracks")) +
                    parseAlbums(json.optJSONObject("albums")) +
                    parsePlaylists(json.optJSONObject("playlists")) +
                    parseArtists(json.optJSONObject("artists"))
            }
        }.getOrDefault(emptyList())
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
            SpotifyItem(t.optString("uri"), t.optString("name"), artistNames.joinToString(", "), "Faixa")
        }

    private fun parseAlbums(o: JSONObject?): List<SpotifyItem> =
        items(o) { a ->
            val artists = a.optJSONArray("artists")
            val artistNames = List(artists?.length() ?: 0) { i -> artists.getJSONObject(i).optString("name") }
            SpotifyItem(a.optString("uri"), a.optString("name"), artistNames.joinToString(", "), "Álbum")
        }

    private fun parsePlaylists(o: JSONObject?): List<SpotifyItem> =
        items(o) { p ->
            val owner = p.optJSONObject("owner")?.optString("display_name").orEmpty()
            SpotifyItem(p.optString("uri"), p.optString("name"), "Playlist · $owner", "Playlist")
        }

    private fun parseArtists(o: JSONObject?): List<SpotifyItem> =
        items(o) { a ->
            SpotifyItem(a.optString("uri"), a.optString("name"), "Artista", "Artista")
        }

    private fun items(o: JSONObject?, map: (JSONObject) -> SpotifyItem): List<SpotifyItem> {
        val arr = o?.optJSONArray("items") ?: return emptyList()
        return List(arr.length()) { i -> map(arr.getJSONObject(i)) }.filter { it.uri.isNotBlank() }
    }
}