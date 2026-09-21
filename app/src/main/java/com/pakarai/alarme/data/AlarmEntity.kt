package com.pakarai.alarme.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Alarme persistido. Todos os campos de configuração moram aqui:
 * volume progressivo, snooze, som, desafio, anti-fuga.
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String = "Alarme",
    val hour: Int = 7,
    val minute: Int = 0,
    val enabled: Boolean = true,
    /** Bit 0=Segunda ... 6=Domingo. 0 = alarme único. */
    val repeatDaysMask: Int = 0,
    val vibrate: Boolean = true,

    /** "siren" | "airhorn" | "tone" | "ringtone" | "spotify" */
    val soundKind: String = "siren",
    /** Uri de toque do sistema, vazio se não usado. */
    val ringtoneUri: String = "",

    /** URI do Spotify (track/álbum/artista/playlist) quando soundKind == "spotify". */
    val spotifyUri: String = "",
    /** Nome amigável da fonte escolhida (mostrado no editor). */
    val spotifyLabel: String = "",
    /** Som local memorizado pra tocar quando o Spotify falhar (o som que o
     *  alarme tinha antes de ser trocado pra Spotify). */
    val fallbackKind: String = "siren",
    /** Uri do ringtone do fallback, se fallbackKind == "ringtone". */
    val fallbackUri: String = "",

    /** Volume inicial (fração do canal de alarme). */
    val volumeInitial: Float = 0.15f,
    /** Teto do volume progressivo. */
    val volumePeak: Float = 1.0f,
    /** Tempo (ms) até atingir o teto. */
    val rampMs: Int = 300_000,
    /** "linear" | "exp" | "step" */
    val rampCurve: String = "exp",
    /** Re-sobe o volume toda vez que alguém tentar abaixar. */
    val policeVolume: Boolean = true,

    /** 0 = sem soneca (modo radical). */
    val snoozeLimit: Int = 2,
    val snoozeMinutes: Int = 3,

    val mathEnabled: Boolean = true,
    /** 0=fácil 1=médio 2=difícil */
    val mathDifficulty: Int = 1,
    /** Tipo de desafio: challengeMode de ChallengeMode. "math" | "memory" | "tiles" | "shake" | "steps" | "qr" | "type" | "spin" | "object" */
    val challengeMode: String = "math",
    /** Fila de rodadas na ordem de execução (keys separadas por "|"). Repetir o mesmo desafio = duplicar a key ("math|math|math|memory"). */
    val challengeModes: String = "",
    /** Legado: nº de rodadas global dos alarmes antigos. Hoje a repetição é na própria [challengeModes]. */
    val challengeRounds: Int = 1,
    /** Conteúdo do QR Code que desliga (modo "qr"). */
    val challengeQrSecret: String = "",

    /** Foto de referência do objeto cadastrado (modo "object" — caminho no filesDir). */
    val objectRefPath: String = "",
    /** Nome do objeto cadastrado ("minha escova") — vira a dica no desafio. */
    val objectRefLabel: String = "",

    /** Agitações a fazer (modo "shake"). */
    val shakeCount: Int = 10,
    /** Passos a andar (modo "steps"). */
    val stepCount: Int = 20,
    /** Graus a girar (modo "spin"). */
    val spinCount: Int = 90,

    /** Modo "tiles" (memória estilo Alarmy): quantos tiles acendem (3..7 = Very Easy..Very Hard). */
    val memoryDifficulty: Int = 5,
    /** Modo "tiles": tempo que os tiles ficam acesos pra memorizar, em ms. */
    val memorySpeedMs: Int = 3000,

    /** Cadeado: impede desligar ou apagar este alarme pela Home/editor. */
    val locked: Boolean = false,
    /** Exige confirmar "AINDA ACORDADO?" de tempos em tempos; sem resposta, volta a tocar. */
    val ackRequired: Boolean = false,
    /** Intervalo (s) entre cada "AINDA ACORDADO?" quando ackRequired. Janela fixa: 30s. */
    val ackSeconds: Int = 300,

    /** Trava a tela do desafio com screen pinning. */
    val screenPin: Boolean = true
) {
    fun isRepeating(): Boolean = repeatDaysMask != 0

    fun matchesDayOfWeek(): Boolean {
        if (!isRepeating()) return true
        val calendarDay = java.util.Calendar.getInstance()
        // Calendar.MONDAY=2..SUNDAY=8 -> bit 0=Segunda
        var dayBit = calendarDay.get(java.util.Calendar.DAY_OF_WEEK) - 2
        if (dayBit < 0) dayBit += 7
        return repeatDaysMask and (1 shl dayBit) != 0
    }

    companion object {
        const val BIT_MON = 1 shl 0
        const val BIT_TUE = 1 shl 1
        const val BIT_WED = 1 shl 2
        const val BIT_THU = 1 shl 3
        const val BIT_FRI = 1 shl 4
        const val BIT_SAT = 1 shl 5
        const val BIT_SUN = 1 shl 6
    }
}