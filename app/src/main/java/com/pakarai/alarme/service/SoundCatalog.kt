package com.pakarai.alarme.service

/** Opção de som exibida no editor e na home. */
data class SoundOption(
    val id: String,
    val label: String,
    val caption: String,
)

/**
 * Catálogo completo dos sons: os 3 clássicos (síntese legada do [SirenSink])
 * + os 6 harmônicos novos ([HarmonicSink]) + o som do sistema.
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
    SoundOption("ringtone", "MÚSICA", "som do sistema"),
)

/** Rótulo curto pro chip/card de um som. */
fun soundLabel(kind: String): String =
    SOUND_OPTIONS.firstOrNull { it.id == kind }?.label ?: "MÚSICA"

/** Se o som é síntese local (SirenSink ou HarmonicSink), não um arquivo. */
fun isSynthSound(kind: String): Boolean = kind != "ringtone"

/** Fábrica dos sinks de síntese local conforme o `kind`. */
fun createSynthSink(context: android.content.Context, kind: String): SoundSink {
    return HARMONIC_PRESETS[kind]?.let { HarmonicSink(context, it) } ?: SirenSink(context, kind)
}