package com.pakarai.alarme.core

import android.content.Context
import android.content.Intent

/**
 * Abre o app do Spotify. A Web API só toca se houver um **device ativo** —
 * ou seja, o app do Spotify precisa estar rodando. Chamamos isso ao escolher
 * a faixa, ao ouvir a prévia e na hora do alarme.
 *
 * Retorna `false` se o Spotify não estiver instalado.
 */
fun openSpotifyApp(context: Context): Boolean {
    return try {
        val intent = context.packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

private const val SPOTIFY_PACKAGE = "com.spotify.music"
