package com.pakarai.alarme.core

import kotlinx.coroutines.flow.StateFlow

/**
 * Porta de leitura/limpeza do estado do alarme. O fluxo lógico (AlarmFlowCoordinator)
 * depende SÓ dela — nada de SharedPreferences/Android — pra rodar testável em JVM.
 */
interface AlarmStateStore {
    val state: StateFlow<AlarmStateManager.State>

    /** Zera estados vencidos (Ringing além do teto), usando o relógio injetado. */
    fun checkExpired(nowMs: Long)

    fun clear()
}