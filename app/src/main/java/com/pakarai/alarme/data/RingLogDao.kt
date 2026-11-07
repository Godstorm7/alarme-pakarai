package com.pakarai.alarme.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RingLogDao {

    /** Os toques mais recentes primeiro — é o que a tela mostra. */
    @Query("SELECT * FROM ring_log ORDER BY startedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<RingLogEntity>>

    @Query("SELECT * FROM ring_log ORDER BY startedAt DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<RingLogEntity>

    @Insert
    suspend fun insert(entry: RingLogEntity): Long

    @Query(
        """
        UPDATE ring_log
        SET endedAt = :endedAt, durationMs = :durationMs, outcome = :outcome, snoozes = :snoozes
        WHERE id = :id
        """
    )
    suspend fun finish(id: Long, endedAt: Long, durationMs: Long, outcome: String, snoozes: Int)

    /**
     * O toque ainda aberto desse alarme. "tocando" é o valor de
     * [RingLogEntity.Outcome.RINGING] gravado no início; um toque sem fim é o
     * retrato honesto de "o processo morreu no meio" (aparece assim na tela).
     */
    @Query("SELECT * FROM ring_log WHERE alarmId = :alarmId AND outcome = 'tocando' ORDER BY id DESC LIMIT 1")
    suspend fun findOpen(alarmId: Long): RingLogEntity?

    /**
     * Fecha o toque aberto desse alarme. A duração usa `startedElapsed`, não o
     * relógio de parede: mexer na hora/fuso no meio do toque não inventa uma
     * duração negativa de 3 horas.
     */
    suspend fun closeOpen(alarmId: Long, endedAt: Long, nowElapsed: Long, outcome: String, snoozes: Int) {
        val open = findOpen(alarmId) ?: return
        finish(open.id, endedAt, (nowElapsed - open.startedElapsed).coerceAtLeast(0L), outcome, snoozes)
    }

    @Query("DELETE FROM ring_log")
    suspend fun clearAll()

    /**
     * Mantém só as [keep] linhas mais recentes. O histórico é diagnóstico, não
     * arquivo: sem poda ele cresceria pra sempre num alarme diário.
     */
    @Query(
        """
        DELETE FROM ring_log
        WHERE id NOT IN (SELECT id FROM ring_log ORDER BY startedAt DESC, id DESC LIMIT :keep)
        """
    )
    suspend fun prune(keep: Int = 200)
}
