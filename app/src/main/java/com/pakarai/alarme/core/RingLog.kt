package com.pakarai.alarme.core

import android.os.SystemClock
import android.util.Log
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.data.RingLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Registro dos toques — "as últimas vezes que o alarme tocou".
 *
 * Grava DUAS pontas: quando o som começa ([start]) e quando o toque sai de cena
 * ([end]). O meio (desligou/soneca/checagem/estourou a janela) vem de
 * [outcomeFor], decisão pura e testável.
 *
 * Tudo escreve no Room (não só no logcat): o histórico sobrevive a reboot, a
 * fechar o app e a "limpar dados de log" — e é o que a tela HISTÓRICO lê. O
 * logcat ganha a mesma informação com a tag [TAG], pra debug ao vivo.
 *
 * Nada aqui pode derrubar o alarme: falha de banco vira um `Log.w` e o toque
 * segue. Histórico é diagnóstico; o despertar é o produto.
 */
object RingLog {

    /** Filtro pronto: `adb logcat -s PakaraiRing`. */
    const val TAG = "PakaraiRing"

    /** Abre a linha do toque. Chamar só quando o toque REALMENTE começa. */
    suspend fun start(alarmId: Long, label: String, snoozes: Int) {
        val now = System.currentTimeMillis()
        Log.i(TAG, "TOCOU alarme=$alarmId \"$label\" sonecas=$snoozes")
        try {
            val dao = AppScope.ringLog
            dao.insert(
                RingLogEntity(
                    alarmId = alarmId,
                    label = label,
                    startedAt = now,
                    startedElapsed = SystemClock.elapsedRealtime(),
                    snoozes = snoozes
                )
            )
            // histórico é diagnóstico, não arquivo: poda o que passou de 200
            dao.prune()
        } catch (t: Throwable) {
            Log.w(TAG, "não gravou o início do toque", t)
        }
    }

    /**
     * Fecha a linha do toque. É BLOQUEANTE de propósito: `cleanup()` logo
     * depois chama `stopSelf()`, e um write em coroutine solta perderia a
     * corrida com a morte do processo — o histórico ficaria cheio de "tocando"
     * eternos. A escrita é um UPDATE de uma linha só; o custo é irrelevante
     * perto de perder o registro justamente nos casos ruins (que são os que a
     * pessoa quer investigar).
     */
    fun end(alarmId: Long, outcome: String, snoozes: Int) {
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        try {
            runBlocking(Dispatchers.IO) {
                AppScope.ringLog.closeOpen(alarmId, nowWall, nowElapsed, outcome, snoozes)
            }
            Log.i(TAG, "FIM alarme=$alarmId resultado=$outcome sonecas=$snoozes")
        } catch (t: Throwable) {
            Log.w(TAG, "não fechou o toque (fica como 'tocando')", t)
        }
    }

    /**
     * Traduz o estado em que o toque morreu para o rótulo do histórico.
     *
     * Só o watchdog sabe que ninguém resolveu — ele passa [RingLogEntity.Outcome.MISSED]
     * na mão, não dá pra deduzir de `Idle` (desligar também termina em `Idle`).
     */
    fun outcomeFor(state: AlarmStateManager.State?): String = when (state) {
        is AlarmStateManager.State.Snoozing -> RingLogEntity.Outcome.SNOOZE
        is AlarmStateManager.State.Checking -> RingLogEntity.Outcome.CHECK
        is AlarmStateManager.State.Ringing -> RingLogEntity.Outcome.RINGING
        else -> RingLogEntity.Outcome.DISMISSED
    }
}
