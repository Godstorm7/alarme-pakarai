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
        val snoozeChannel = NotificationChannel(
            context.getString(R.string.channel_snooze),
            context.getString(R.string.channel_snooze),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_snooze_desc)
        }
        nm.createNotificationChannel(alarmChannel)
        nm.createNotificationChannel(serviceChannel)
        nm.createNotificationChannel(snoozeChannel)
    }

    /**
     * Notificação com fullScreenIntent: o sistema abre o ChallengeActivity
     * POR CIMA DE TUDO (até da lockscreen), igual chamada de telefone.
     */
    fun ringing(context: Context, alarmId: Long): Notification {
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
        return NotificationCompat.Builder(context, context.getString(R.string.channel_alarm))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(context.getString(R.string.notif_foreground_title))
            .setContentText(context.getString(R.string.notif_foreground_text))
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
}