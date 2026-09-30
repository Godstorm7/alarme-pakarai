package com.pakarai.alarme.core

import android.content.Context
import com.pakarai.alarme.AppScope

/**
 * Memória do que a pessoa já viu.
 *
 * O banner da Home mostra UM ajuste por vez (o mais grave) e para de insistir
 * quando ela dispensa. Mas o guia continua listando tudo — dispensar não
 * esconde ajuste nenhum, só o aviso.
 */
class SetupWatch(context: Context) {

    private val prefs = context.getSharedPreferences("pakarai_setup", Context.MODE_PRIVATE)

    fun isDismissed(item: SetupItem): Boolean =
        prefs.getBoolean(key(item), false)

    fun dismiss(item: SetupItem) {
        prefs.edit().putBoolean(key(item), true).apply()
    }

    fun reset(item: SetupItem) {
        prefs.edit().remove(key(item)).apply()
    }

    /**
     * Estado atual de tudo que importa pro alarme, mais o primeiro item que
     * merece banner. Null = nada pendente (ou tudo já vistado).
     */
    fun snapshot(guardEnabled: Boolean = AppScope.settings.guardUserEnabled): SetupSnapshot {
        val all = SetupStatusReader.read(AppScope.appContext, guardEnabled)
        val pending = all
            .filter { it.state == SetupState.ACTION || it.state == SetupState.MANUAL }
            .sortedByDescending { it.item.severity }
        val banner = pending.firstOrNull { !isDismissed(it.item) }
        return SetupSnapshot(all, pending, banner)
    }

    private fun key(item: SetupItem) = "dismissed_${item.name}"
}

data class SetupSnapshot(
    val all: List<SetupStatus>,
    /** Tudo que está fora do OK, do mais grave pro menos. */
    val pending: List<SetupStatus>,
    /** O único que o banner da Home mostra (null = nada a mostrar). */
    val banner: SetupStatus?,
) {
    val pendingCount: Int get() = pending.size
}
