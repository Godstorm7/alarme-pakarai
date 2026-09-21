package com.pakarai.alarme.service

import com.pakarai.alarme.data.AlarmEntity

/** Opção de som exibida no editor e na home. */
data class SoundOption(
    val id: String,
    val label: String,
    val caption: String,
    /** `true` = arquivo de áudio real empacotado no APK (AOSP DeskClock, Apache-2.0). */
    val audio: Boolean = false,
)

/** Kinds que tocam um arquivo OGG empacotado em res/raw (sons reais, limpos). */
val RAW_AUDIO_KINDS = setOf(
    "alarm_classic", "alarm_beep", "alarm_buzzer",
    "alarm_rooster", "alarm_helium", "alarm_oxygen",
)

/**
 * Catálogo completo dos sons: os 3 clássicos (síntese legada do [SirenSink])
 * + os 6 harmônicos novos ([HarmonicSink]) + 6 áudios reais (AOSP) + o som
 * do sistema.
 */
val SOUND_OPTIONS = listOf(
    SoundOption("siren", "SIRENE", "wail clássico"),
    SoundOption("airhorn", "BUZINA", "grave com tremolo"),
    SoundOption("tone", "BIP", "bip agudo"),
    SoundOption("boom", "BOM", "impacto grave 440-520Hz"),
    SoundOption("surto", "SURTO", "arpejo 105 BPM"),
    SoundOption("galvaniza", "GALVANIZA", "cascata de sinos"),
    SoundOption("pulso", "PULSO", "pilha rica que respira"),
    SoundOption("alvorada", "ALVORADA", "amanhecer em camadas"),
    SoundOption("drone", "DRONADA", "rumor grave com batida"),
    SoundOption("alarm_classic", "CLÁSSICO", "áudio real · suave"),
    SoundOption("alarm_beep", "TRIPLE BEEP", "áudio real · bi-bi-bi"),
    SoundOption("alarm_buzzer", "BUZZER", "áudio real · insistente"),
    SoundOption("alarm_rooster", "GALO", "áudio real · cocoricó"),
    SoundOption("alarm_helium", "HÉLIO", "áudio real · campainha"),
    SoundOption("alarm_oxygen", "OXIGÊNIO", "áudio real · sereno"),
    SoundOption("ringtone", "MÚSICA", "som do sistema"),
)

/** Rótulo curto pro chip/card de um som. */
fun soundLabel(kind: String): String =
    SOUND_OPTIONS.firstOrNull { it.id == kind }?.label ?: "MÚSICA"

/** Rótulo do som de um alarme (fonte do Spotify incluída) — usada na Home e no editor. */
fun alarmSoundLabel(alarm: AlarmEntity): String = when (alarm.soundKind) {
    "spotify" -> alarm.spotifyLabel.ifBlank { "SPOTIFY" }
    "ringtone" -> "MÚSICA"
    else -> soundLabel(alarm.soundKind)
}

/** Se o som é síntese local (SirenSink ou HarmonicSink), não um arquivo. */
fun isSynthSound(kind: String): Boolean = kind != "ringtone" && kind !in RAW_AUDIO_KINDS

/** Fábrica dos sinks de som local conforme o `kind` (áudio real ou síntese). */
fun createSynthSink(context: android.content.Context, kind: String): SoundSink {
    if (kind in RAW_AUDIO_KINDS) {
        return RingtoneSink(context, "android.resource://${context.packageName}/raw/$kind")
    }
    return HARMONIC_PRESETS[kind]?.let { HarmonicSink(context, it) } ?: SirenSink(context, kind)
}

/**
 * Sink do som de reserva (o som local que o alarme tinha antes de virar
 * Spotify). Toca quando o Spotify não funciona. Ringtone → [RingtoneSink];
 * qualquer outra coisa → síntese local.
 */
fun fallbackSinkFor(context: android.content.Context, kind: String, uri: String): SoundSink {
    if (kind == "ringtone" && uri.isNotBlank()) {
        return try {
            RingtoneSink(context, uri)
        } catch (_: Exception) {
            SirenSink(context, "siren")
        }
    }
    return createSynthSink(context, kind)
}

/** Rótulo curto do som de reserva pra mostrar no editor. */
fun fallbackLabel(kind: String, uri: String): String = when (kind) {
    "ringtone" -> if (uri.isNotBlank()) "MÚSICA" else "MÚSICA"
    else -> soundLabel(kind)
}