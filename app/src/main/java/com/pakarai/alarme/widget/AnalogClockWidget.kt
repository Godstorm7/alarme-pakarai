package com.pakarai.alarme.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.RemoteViews
import com.pakarai.alarme.R
import java.util.Calendar

/**
 * Widget "RELÓGIO ANALÓGICO": desenha o relógio num bitmap (RemoteViews não
 * desenha livre) e se auto-reagenda a cada minuto pra os ponteiros andarem.
 */
class AnalogClockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
        scheduleTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TICK) return
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, AnalogClockWidget::class.java))
        if (ids.isEmpty()) {
            cancelTick(context)
            return
        }
        render(context, manager, ids)
        scheduleTick(context)
    }

    override fun onDisabled(context: Context) {
        cancelTick(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val manager = AppWidgetManager.getInstance(context)
        if (manager.getAppWidgetIds(ComponentName(context, AnalogClockWidget::class.java)).isEmpty()) {
            cancelTick(context)
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val options = manager.getAppWidgetOptions(id)
            val sizeDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).coerceIn(90, 260)
            val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt().coerceIn(180, 720)
            val bitmap = drawClock(sizePx)
            val views = RemoteViews(context.packageName, R.layout.widget_analog_clock)
            views.setImageViewBitmap(R.id.widget_clock, bitmap)
            manager.updateAppWidget(id, views)
        }
    }

    private fun drawClock(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = size / 2f
        val cy = size / 2f
        val r = size / 2f - size * 0.04f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD; style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy, r, fill)

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT
            style = Paint.Style.STROKE
            strokeWidth = size * 0.03f
        }
        canvas.drawCircle(cx, cy, r - ring.strokeWidth / 2f, ring)

        // marcas das horas
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; strokeWidth = size * 0.012f }
        for (i in 0 until 12) {
            val a = Math.toRadians((i * 30).toDouble())
            val sx = cx + (r * 0.82f) * kotlin.math.sin(a).toFloat()
            val sy = cy - (r * 0.82f) * kotlin.math.cos(a).toFloat()
            val ex = cx + (r * 0.92f) * kotlin.math.sin(a).toFloat()
            val ey = cy - (r * 0.92f) * kotlin.math.cos(a).toFloat()
            canvas.drawLine(sx, sy, ex, ey, tick)
        }

        val now = Calendar.getInstance()
        val hourAngle = (now.get(Calendar.HOUR) % 12 + now.get(Calendar.MINUTE) / 60f) * 30f
        val minAngle = (now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f) * 6f

        drawHand(canvas, cx, cy, hourAngle, r * 0.5f, size * 0.045f, FG)
        drawHand(canvas, cx, cy, minAngle, r * 0.72f, size * 0.03f, ACCENT)

        val center = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
        canvas.drawCircle(cx, cy, size * 0.03f, center)
        return bmp
    }

    private fun drawHand(canvas: Canvas, cx: Float, cy: Float, angleDeg: Float, length: Float, width: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
        }
        val a = Math.toRadians(angleDeg.toDouble())
        val ex = cx + length * kotlin.math.sin(a).toFloat()
        val ey = cy - length * kotlin.math.cos(a).toFloat()
        canvas.drawLine(cx, cy, ex, ey, paint)
    }

    private fun scheduleTick(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val next = System.currentTimeMillis() / 60_000 * 60_000 + 60_000
        try {
            am.setExact(AlarmManager.RTC, next, tickIntent(context))
        } catch (_: Exception) {
        }
    }

    private fun cancelTick(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            am.cancel(tickIntent(context))
        } catch (_: Exception) {
        }
    }

    private fun tickIntent(context: Context): PendingIntent {
        val intent = Intent(context, AnalogClockWidget::class.java).apply { action = ACTION_TICK }
        return PendingIntent.getBroadcast(
            context,
            77,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val ACTION_TICK = "com.pakarai.alarme.widget.CLOCK_TICK"
        const val CARD = 0xFF192134.toInt()
        const val FG = 0xFFE8EDF4.toInt()
        const val ACCENT = 0xFF8B7CF6.toInt()
        const val MUTED = 0xFF6B7280.toInt()
    }
}
