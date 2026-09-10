package com.pakarai.alarme.core

object Constants {
    const val ACTION_FIRE = "com.pakarai.alarme.action.FIRE"
    const val ACTION_SNOOZE = "com.pakarai.alarme.action.SNOOZE"
    const val ACTION_DISMISS = "com.pakarai.alarme.action.DISMISS"

    const val EXTRA_ALARM_ID = "extra_alarm_id"
    const val EXTRA_ACTION = "extra_action"

    const val REQUEST_CODE_BASE = 42000

    const val NOTIF_ID_RINGING = 1
    const val NOTIF_ID_PENDING = 2
    const val NOTIF_ID_SNOOZE = 3

    const val SERVICE_ID_RINGING = 10
}