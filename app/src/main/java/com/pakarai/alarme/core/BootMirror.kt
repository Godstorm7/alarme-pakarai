package com.pakarai.alarme.core

import android.content.Context
import com.pakarai.alarme.data.AlarmEntity

/**
 * Cópia mínima de um alarme para o espelho de direct boot.
 * Só carrega o necessário pra reagendar pelo AlarmManager sem abrir o Room
 * (que vive em armazenamento criptografado — inacessível antes do 1º desbloqueio).
 */
data class MirrorEntry(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val repeatMask: Int,
    val enabled: Boolean
) {
    fun isRepeating(): Boolean = repeatMask != 0

    fun toAlarm(): AlarmEntity =
        AlarmEntity(id = id, hour = hour, minute = minute, repeatDaysMask = repeatMask, enabled = true)

    fun encoded(): String = "$id|$hour|$minute|$repeatMask|$enabled"

    companion object {
        fun from(alarm: AlarmEntity): MirrorEntry =
            MirrorEntry(alarm.id, alarm.hour, alarm.minute, alarm.repeatDaysMask, alarm.enabled)

        fun decode(raw: String): MirrorEntry? {
            val parts = raw.split('|')
            if (parts.size != 5) return null
            return try {
                MirrorEntry(parts[0].toLong(), parts[1].toInt(), parts[2].toInt(), parts[3].toInt(), parts[4].toBoolean())
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** Persistência do espelho. Desacoplada pra testar a lógica sem Android. */
interface BootMirror {
    fun save(entry: MirrorEntry)
    fun remove(id: Long)
    fun list(): List<MirrorEntry>
    /** Registra um disparo que chegou enquanto o telefone ainda estava bloqueado. */
    fun recordMissedDue(id: Long, dueMs: Long)
    /** Devolve e apaga os disparos perdidos (recuperação ao desbloquear). */
    fun takeMissedDue(): List<Pair<Long, Long>>
}

/**
 * Espelho em Device Protected Storage: sobrevive ao reboot e à criptografia
 * do 1º desbloqueio, sendo legível pelo LOCKED_BOOT_COMPLETED.
 */
class PrefsBootMirror(context: Context) : BootMirror {

    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("pakarai_boot", Context.MODE_PRIVATE)

    override fun save(entry: MirrorEntry) {
        if (!entry.enabled) {
            remove(entry.id)
            return
        }
        prefs.edit().putString(KEY_ENTRY + entry.id, entry.encoded()).commit()
    }

    override fun remove(id: Long) {
        prefs.edit().remove(KEY_ENTRY + id).remove(KEY_MISSED + id).commit()
    }

    override fun list(): List<MirrorEntry> =
        prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(KEY_ENTRY) && value is String) MirrorEntry.decode(value) else null
        }

    override fun recordMissedDue(id: Long, dueMs: Long) {
        prefs.edit().putLong(KEY_MISSED + id, dueMs).commit()
    }

    override fun takeMissedDue(): List<Pair<Long, Long>> {
        val out = prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(KEY_MISSED) && value is Long) {
                key.substring(KEY_MISSED.length).toLongOrNull()?.let { it to value }
            } else {
                null
            }
        }
        if (out.isNotEmpty()) {
            val editor = prefs.edit()
            out.forEach { (id, _) -> editor.remove(KEY_MISSED + id) }
            editor.commit()
        }
        return out
    }

    private companion object {
        const val KEY_ENTRY = "entry_"
        const val KEY_MISSED = "missed_"
    }
}