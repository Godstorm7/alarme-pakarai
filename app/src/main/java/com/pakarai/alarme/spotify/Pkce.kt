package com.pakarai.alarme.spotify

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE (RFC 7636) para o fluxo Authorization Code sem client secret
 * — o padrão obrigatório pra apps mobile públicos do Spotify.
 */
object Pkce {

    private const val VERIFIER_CHARS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    /** Code verifier aleatório (43-128 chars, sempre dentro do alfabeto RFC 7636). */
    fun verifier(length: Int = 64): String {
        require(length in 43..128) { "verifier deve ter entre 43 e 128 chars" }
        val rnd = SecureRandom()
        val sb = StringBuilder(length)
        repeat(length) { sb.append(VERIFIER_CHARS[rnd.nextInt(VERIFIER_CHARS.length)]) }
        return sb.toString()
    }

    /** Code challenge S256: SHA-256 base64url sem padding. */
    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /** Form-url encoding de query param. */
    fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}