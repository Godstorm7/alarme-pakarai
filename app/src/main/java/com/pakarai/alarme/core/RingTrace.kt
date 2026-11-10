package com.pakarai.alarme.core

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diario do toque: o proprio app registra o que fez com o som.
 *
 * Antes a rampa era medida por fora, no `dumpsys audio` (evento de
 * `setStreamVolume`). Agora a rampa e ganho do track, nao gera evento nenhum no
 * sistema — entao a testemunha passou a ser este arquivo: ganho de cada degrau,
 * saida de audio escolhida, inicio e fim do toque.
 *
 * Fica em filesDir/ring_trace.log; rotaciona sozinho para nao crescer sem fim.
 */
object RingTrace {

    private const val FILE = "ring_trace.log"
    private const val MAX_BYTES = 256 * 1024
    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun note(context: Context, msg: String) {
        try {
            val f = File(context.filesDir, FILE)
            if (f.length() > MAX_BYTES) f.delete()
            f.appendText("${fmt.format(Date())} $msg\n")
        } catch (_: Exception) {
        }
    }

    fun read(context: Context, maxChars: Int = 20_000): String = try {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) "" else f.readText().takeLast(maxChars)
    } catch (_: Exception) {
        ""
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE).delete()
        } catch (_: Exception) {
        }
    }

    /** Para onde o som iria agora: e isso que explica um alarme "que nao tocou". */
    fun describeOutput(context: Context): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "audio desconhecido"
        val alarmVol = try {
            "${am.getStreamVolume(AudioManager.STREAM_ALARM)}/${am.getStreamMaxVolume(AudioManager.STREAM_ALARM)}"
        } catch (_: Exception) {
            "?"
        }
        val out = try {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.isSink }
                .joinToString(",") { "${it.typeName()}${if (it.isSink) "" else ""}" }
                .ifEmpty { "sem saida listada" }
        } catch (_: Exception) {
            "erro lendo saidas"
        }
        val bt = try {
            if (am.isBluetoothA2dpOn) "a2dp=on" else "a2dp=off"
        } catch (_: Exception) {
            "a2dp=?"
        }
        return "canalAlarme=$alarmVol $bt saidas=[$out]"
    }

    private fun AudioDeviceInfo.typeName(): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt-a2dp"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt-sco"
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE -> "usb"
        else -> "tipo$type"
    }
}
