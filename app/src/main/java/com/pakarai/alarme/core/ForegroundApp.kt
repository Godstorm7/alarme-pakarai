package com.pakarai.alarme.core

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * Quem está na frente agora, sem serviço de acessibilidade.
 *
 * Usa o "Acesso ao uso" (UsageStats) — um acesso especial que o banco NÃO
 * trata como ameaça (diferente da acessibilidade, que faz o Nubank acusar o
 * app de nocivo). Sem esse acesso, [currentPackage] devolve `null` e o vigia
 * simplesmente não opina: o alarme continua tocando e a notificação em tela
 * cheia segue como plano B.
 */
object ForegroundApp {

    /** Janela de busca: eventos mais velhos que isso não dizem quem está na frente. */
    private const val WINDOW_MS = 15_000L

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
        } catch (_: Exception) {
            return false
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Último pacote que veio pra frente. `null` quando não há evento (tela
     * apagada há muito tempo, acesso negado, ou o app não viu nada).
     */
    fun currentPackage(context: Context, nowMs: Long = System.currentTimeMillis()): String? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        return try {
            val events = usm.queryEvents(nowMs - WINDOW_MS, nowMs)
            val event = UsageEvents.Event()
            var lastForeground: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val type = event.eventType
                // MOVE_TO_FOREGROUND == ACTIVITY_RESUMED (mesmo valor, 1)
                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastForeground = event.packageName
                }
            }
            lastForeground
        } catch (_: Exception) {
            null
        }
    }
}
