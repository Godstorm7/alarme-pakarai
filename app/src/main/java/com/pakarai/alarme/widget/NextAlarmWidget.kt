package com.pakarai.alarme.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.R
import com.pakarai.alarme.scheduler.computeNextTrigger
import com.pakarai.alarme.service.NextAlarmNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Widget "PRÓXIMO ALARME": mostra o horário do próximo disparo marcado.
 * Atualiza a cada 30min (mínimo do sistema), a cada boot e imediatamente
 * quando um alarme é salvo/apagado/desligado/respondido.
 */
class NextAlarmWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            refresh(context)
        } else {
            super.onReceive(context, intent)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.pakarai.alarme.widget.REFRESH"

        /** Dispara uma atualização assíncrona — seguro chamar de qualquer lugar. */
        fun refresh(context: Context) {
            // hook único de "o próximo alarme mudou": atualiza também a notificação fixa
            NextAlarmNotifier.refresh(context)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NextAlarmWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.Main).launch {
            try {
                val next = withContext(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    AppScope.repository.getAll()
                        .asSequence()
                        .filter { it.enabled }
                        .mapNotNull { runCatching { computeNextTrigger(it, now) to it }.getOrNull() }
                        .minByOrNull { it.first }
                }
                val views = RemoteViews(context.packageName, R.layout.widget_next_alarm)
                if (next == null) {
                    views.setTextViewText(R.id.widget_time, "--:--")
                    views.setTextViewText(R.id.widget_desc, context.getString(R.string.widget_no_alarm))
                } else {
                    val cal = Calendar.getInstance().apply { timeInMillis = next.first }
                    val hh = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
                    val mm = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
                    views.setTextViewText(R.id.widget_time, "$hh:$mm")
                    val day = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val whenLabel = when {
                        next.first < day + 24 * 60 * 60 * 1000L -> context.getString(R.string.widget_today)
                        else -> context.getString(R.string.widget_tomorrow)
                    }
                    val label = next.second.label.ifBlank { "Alarme" }
                    views.setTextViewText(R.id.widget_desc, "$whenLabel · $label")
                }
                manager.updateAppWidget(ids, views)
            } catch (_: Exception) {
            }
        }
        }
    }
}