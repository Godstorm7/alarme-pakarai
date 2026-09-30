package com.pakarai.alarme.core

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock

/**
 * Fura o "Não perturbe" só durante o toque.
 *
 * Regras (todas deliberadamente conservadoras):
 * - Nada acontece sem `ACCESS_NOTIFICATION_POLICY` concedida pelo usuário.
 * - Só mexe se o DND estiver LIGADO (silêncio total ou só prioridade): com o
 *   DND já desligado, quem manda é o volume — não precisa mexer em nada.
 * - Guarda o filtro anterior e devolve no cleanup. Se o filtro já não for mais
 *   o nosso (a pessoa mexeu na mão durante o toque), não sobrescreve: um
 *   alarme não pode mudar a preferência que o usuário acabou de escolher.
 * - O filtro anterior também é guardado em disco, não só na memória: o SO pode
 *   matar o processo no meio do toque (e é justamente o caso do redelivery
 *   que o `START_REDELIVER_INTENT` cobre) e, sem isso, o aparelho ficaria
 *   travado em "só alarme" com o DND ligado, sem ninguém pra devolver.
 */
object DndBypass {

    private fun prefs(context: Context) =
        context.getSharedPreferences("pakarai_dnd", Context.MODE_PRIVATE)

    /**
     * O app tem o direito de alterar o filtro de notificação?
     * (minSdk 26 > 23, então a API de acesso ao DND sempre existe aqui.)
     */
    fun hasPolicyAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    /** O DND está ativo agora? (silêncio total ou só prioridade) */
    fun isDndActive(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return when (nm.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            -> true
            else -> false
        }
    }

    /**
     * Token de devolução deixado por um processo que morreu: devolve o filtro
     * anterior à [com.pakarai.alarme.service.AlarmService] sem precisar que a
     * memória do processo anterior sobreviva. Idempotente.
     */
    fun consumePendingRestore(context: Context): Int? {
        val p = prefs(context)
        val filter = p.getInt(KEY_PREVIOUS, 0)
        if (filter == 0) return null
        val at = p.getLong(KEY_PREVIOUS_AT, 0L)
        if (!Token.isUsable(SystemClock.elapsedRealtime() - at)) {
            p.edit().remove(KEY_PREVIOUS).remove(KEY_PREVIOUS_AT).apply()
            return null
        }
        return filter
    }

    /**
     * Liga o bypass se fizer sentido. Devolve o filtro anterior pra ser
     * devolvido em [restore], ou null se nada foi alterado (sem permissão,
     * DND desligado, ou já em modo alarme).
     *
     * Se já existe um token pendente (processo anterior morreu), NÃO grava um
     * novo: o [restore] final tem que devolver ao filtro original, não ao
     * "só alarme" que nós mesmos colocamos.
     */
    fun enter(context: Context): Int? {
        if (consumePendingRestore(context) != null) return null // devolução pendente
        if (!hasPolicyAccess(context)) return null
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return null
        val current = nm.currentInterruptionFilter
        if (current == NotificationManager.INTERRUPTION_FILTER_ALARMS) return null // já é o nosso
        if (!isDndActive(context)) return null // DND desligado: som sai pelo volume mesmo
        return try {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
            prefs(context).edit()
                .putInt(KEY_PREVIOUS, current)
                .putLong(KEY_PREVIOUS_AT, SystemClock.elapsedRealtime())
                .apply()
            current
        } catch (_: SecurityException) {
            // permissão revogada no meio do toque — não é motivo pra derrubar o alarme
            null
        }
    }

    /**
     * Devolve o DND ao estado anterior. Idempotente: seguro chamar várias vezes,
     * e seguro chamar sem argumento (aí usa o token em disco, que sobrevive à
     * morte do processo).
     */
    fun restore(context: Context, previous: Int? = consumePendingRestore(context)) {
        val target = previous ?: return
        if (!hasPolicyAccess(context)) {
            // sem permissão não dá pra mexer: limpa o token pra não ficar
            // tentando devolver um bypass que já não é nosso
            clearToken(context)
            return
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm == null) {
            clearToken(context)
            return
        }
        // se a pessoa mexeu no DND durante o toque, respeito a escolha dela
        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALARMS) {
            clearToken(context)
            return
        }
        try {
            nm.setInterruptionFilter(target)
        } catch (_: SecurityException) {
        }
        clearToken(context)
    }

    private fun clearToken(context: Context) {
        prefs(context).edit().remove(KEY_PREVIOUS).remove(KEY_PREVIOUS_AT).apply()
    }

    /**
     * Regra do token de devolução, isolada e testável (o resto depende do
     * `NotificationManager`, que só roda no aparelho).
     *
     * O token vive no disco enquanto o bypass está ativo. Depois de
     * [MAX_AGE_MS] ele é lixo — o ciclo do alarme já acabou — e aí a gente
     * apaga o registro **sem mexer no filtro**: passado esse ponto já não dá
     * pra saber se o "só alarme" é resíduo nosso ou escolha da pessoa, e
     * nenhum alarme pode sobrescrever a preferência que ela acabou de fazer.
     */
    object Token {
        const val MAX_AGE_MS = 60 * 60 * 1000L

        /** Token com essa idade ainda vale a pena devolver? */
        fun isUsable(ageMs: Long): Boolean = ageMs <= MAX_AGE_MS
    }

    private const val KEY_PREVIOUS = "dnd_previous_filter"
    private const val KEY_PREVIOUS_AT = "dnd_previous_at"
}
