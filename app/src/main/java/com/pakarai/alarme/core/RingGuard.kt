package com.pakarai.alarme.core

/** Estado leve pro GuardService saber se deve bloquear o desligamento durante o toque. */
object RingGuard {
    @Volatile
    var preventOff: Boolean = false
}
