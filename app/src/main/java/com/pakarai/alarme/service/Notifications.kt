package com.pakarai.alarme.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pakarai.alarme.R
import com.pakarai.alarme.core.Constants
import com.pakarai.alarme.ui.challenge.ChallengeActivity
import com.pakarai.alarme.ui.check.CheckActivity

object Notifications {

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alarmChannel = NotificationChannel(
            context.getString(R.string.channel_alarm),
            context.getString(R.string.channel_alarm),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alarm_desc)
            setSound(null, null) // som vem do nosso engine, não da notificação
        }
        val serviceChannel = NotificationChannel(
            context.getString(R.string.channel_service),
            context.getString(R.string.channel_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_service_desc)
            setSound(null, null)
        }
        nm.createNotificationChannel(alarmChannel)
        nm.createNotificationChannel(serviceChannel)
        val snoozeChannel = NotificationChannel(
            context.getString(R.string.channel_snooze),
            context.getString(R.string.channel_snooze),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_snooze_desc)
        }
        nm.createNotificationChannel(snoozeChannel)
        val pausedChannel = NotificationChannel(
            context.getString(R.string.channel_paused),
            context.getString(R.string.channel_paused),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_paused_desc)
            setSound(null, null)
        }
        nm.createNotificationChannel(pausedChannel)
        val nextChannel = NotificationChannel(
            CHANNEL_NEXT_ALARM,
            "Próximo alarme",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Mostra o próximo alarme na barra de notificações."
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(nextChannel)
    }

    fun notify(context: Context, id: Int, notification: Notification) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(id, notification)
        } catch (_: Exception) {
        }
    }

    /**
     * Aviso de que a permissão de alarme exato foi embora: sem ela NENHUM
     * alarme dispara, e o app calado nessa situação parece um alarme quebrado.
     */
    fun paused(context: Context): Notification {
        val settingsIntent = Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM").apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val contentPi = PendingIntent.getActivity(
            context,
            3,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, context.getString(R.string.channel_paused))
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.notif_paused_title))
            .setContentText(context.getString(R.string.notif_paused_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()
    }

    /**
     * Notificação com fullScreenIntent: o sistema abre o ChallengeActivity
     * POR CIMA DE TUDO (até da lockscreen), igual chamada de telefone.
     * [hasChallenge]: null = ainda não sabe (texto neutro); true/false ajusta a dica.
     */
    fun ringing(context: Context, alarmId: Long, hasChallenge: Boolean? = null): Notification {
        val fullScreenIntent = Intent(context, ChallengeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(Constants.EXTRA_ALARM_ID, alarmId)
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = when (hasChallenge) {
            true -> context.getString(R.string.notif_foreground_text)
            false -> context.getString(R.string.notif_foreground_text_off)
            null -> context.getString(R.string.notif_foreground_text_generic)
        }
        return NotificationCompat.Builder(context, context.getString(R.string.channel_alarm))
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.notif_foreground_title))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(false)
            .build()
    }

    /**
     * Notificação do "AINDA ACORDADO?": fullScreenIntent joga o CheckActivity
     * por cima de tudo (inclusive lockscreen) quando acaba a janela de resposta.
     */
    fun checking(context: Context, alarmId: Long): Notification {
        val fullScreenIntent = Intent(context, CheckActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(Constants.EXTRA_ALARM_ID, alarmId)
        }
        val pi = PendingIntent.getActivity(
            context,
            1,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, context.getString(R.string.channel_alarm))
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.notif_check_title))
            .setContentText(context.getString(R.string.notif_check_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(false)
            .build()
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Notificação fixa do próximo alarme (silenciosa, atualizada quando muda). */
    fun nextAlarm(context: Context, title: String, text: String): Notification {
        val openApp = Intent(context, com.pakarai.alarme.ui.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            context,
            4,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_NEXT_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(pi)
            .build()
    }

    const val CHANNEL_NEXT_ALARM = "next_alarm"
}