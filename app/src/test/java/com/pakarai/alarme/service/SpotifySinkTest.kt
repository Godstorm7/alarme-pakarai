package com.pakarai.alarme.service

import com.pakarai.alarme.spotify.SpotifyClient
import com.pakarai.alarme.spotify.SpotifyDevice
import com.pakarai.alarme.spotify.SpotifyItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake do SpotifyClient que grava as chamadas e responde conforme o cenário. */
private class FakeSpotifyClient : SpotifyClient {
    var devicesResult: List<SpotifyDevice> = emptyList()
    var playResult: Boolean = true
    var isPlayingResult: Boolean? = true
    var failTransfer: Boolean = false

    val playCalls = mutableListOf<String>()
    val transferCalls = mutableListOf<String>()

    override suspend fun search(query: String): List<SpotifyItem> = emptyList()
    override suspend fun devices(): List<SpotifyDevice> = devicesResult

    override suspend fun transferTo(deviceId: String): Boolean {
        if (failTransfer) return false
        transferCalls += deviceId
        return true
    }

    override suspend fun setVolume(percent: Int): Boolean = true

    override suspend fun play(uri: String): Boolean {
        playCalls += uri
        return playResult
    }

    override suspend fun resumePlay(): Boolean = true
    override suspend fun pause(): Boolean = true
    override suspend fun isPlaying(): Boolean? = isPlayingResult
}

private class RecordingSink : SoundSink {
    @Volatile var playCount = 0
        private set
    @Volatile var stopCount = 0
        private set

    override fun play(previewVolume: Float?) {
        playCount++
    }

    override fun pause() {}
    override fun resume() {}
    override fun stop() {
        stopCount++
    }

    override fun release() {}
}

private fun activeDevices() = listOf(
    SpotifyDevice(id = "d1", name = "Fone", type = "device", isActive = true, restricted = false),
    SpotifyDevice(id = "d2", name = "TV", type = "speaker", isActive = false, restricted = false),
)

private suspend fun awaitTrue(timeoutMs: Long = 2000, predicate: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (predicate()) return
        delay(10)
    }
    throw AssertionError("condição não atingida em ${timeoutMs}ms")
}

class SpotifySinkTest {

    @Test
    fun `play feliz toca o uri no device ativo e NÃO cai no fallback`() = runBlocking {
        val client = FakeSpotifyClient().apply { devicesResult = activeDevices() }
        val fallback = RecordingSink()
        val sink = SpotifySink(client, "spotify:track:abc", fallback, confirmMs = 100)

        sink.play()

        awaitTrue { client.playCalls == listOf("spotify:track:abc") }
        awaitTrue { client.transferCalls == listOf("d1") }
        awaitTrue { fallback.playCount == 0 }
        // isPlaying acusou true depois dos 100ms de confirmação
        delay(400)
        assertEquals(0, fallback.playCount)
        sink.release()
    }

    @Test
    fun `play aceito mas nada tocando em 15s dispara a sirene`() = runBlocking {
        val client = FakeSpotifyClient().apply {
            devicesResult = activeDevices()
            isPlayingResult = false
        }
        val fallback = RecordingSink()
        val sink = SpotifySink(client, "spotify:album:x", fallback, confirmMs = 100)

        sink.play()

        awaitTrue { client.playCalls.isNotEmpty() }
        awaitTrue { fallback.playCount == 1 }
        sink.release()
    }

    @Test
    fun `play recusado cai no fallback imediato`() = runBlocking {
        val client = FakeSpotifyClient().apply {
            devicesResult = activeDevices()
            playResult = false
        }
        val fallback = RecordingSink()
        val sink = SpotifySink(client, "spotify:track:abc", fallback, confirmMs = 10_000)

        sink.play()

        awaitTrue { fallback.playCount == 1 }
        assertTrue(client.playCalls.contains("spotify:track:abc"))
        sink.release()
    }

    @Test
    fun `sem device ativo e transfer falho vai direto pro fallback`() = runBlocking {
        val client = FakeSpotifyClient().apply {
            devicesResult = listOf(
                SpotifyDevice(id = "d1", name = "Fone", type = "device", isActive = false, restricted = false)
            )
            failTransfer = true
        }
        val fallback = RecordingSink()
        val sink = SpotifySink(client, "spotify:track:abc", fallback, confirmMs = 10_000)

        sink.play()

        awaitTrue { fallback.playCount == 1 }
        assertTrue(client.playCalls.isEmpty())
        sink.release()
    }

    @Test
    fun `status desconhecido (null) também é tratado como não-tocando`() = runBlocking {
        val client = FakeSpotifyClient().apply {
            devicesResult = activeDevices()
            isPlayingResult = null
        }
        val fallback = RecordingSink()
        val sink = SpotifySink(client, "spotify:playlist:x", fallback, confirmMs = 100)

        sink.play()

        awaitTrue { client.playCalls.isNotEmpty() }
        awaitTrue { fallback.playCount == 1 }
        sink.release()
    }

    @Test
    fun `release cancela a verificação pendente e não dispara fallback depois`() = runBlocking {
        val client = FakeSpotifyClient().apply {
            devicesResult = activeDevices()
            isPlayingResult = false
        }
        val fallback = RecordingSink()
        val sink = SpotifySink(client, "spotify:track:abc", fallback, confirmMs = 300)

        sink.play()
        awaitTrue { client.playCalls.isNotEmpty() }
        sink.release()
        delay(700)

        assertEquals(0, fallback.playCount)
    }
}