package com.pakarai.alarme.ui.wizard

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Deep links e checagens de status dos ajustes especiais do sistema.
 * Compartilhado entre o wizard (CONFIG SAMSUNG) e a tela "ONDE FICA CADA AJUSTE".
 */

fun isBatteryIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return try {
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Exception) {
        false
    }
}

fun openBatteryExemption(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

/** Deep links OEM: tentam o componente Samsung; se não achar, caem em telas genéricas. */
fun openSmartManager(context: Context) {
    val candidates = listOf(
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
    )
    for (c in candidates) {
        try {
            val intent = Intent().apply {
                component = c
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {
        }
    }
    openAppDetails(context)
}

fun openAppDetails(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
    }
}

/** Detecta se a Fixação de tela está HABILITADA (Android 5+; muitas OneUI trazem desligada). */
fun isPinningAllowed(context: Context): Boolean {
    return try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val result = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow("android:pin_window", Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow("android:pin_window", Process.myUid(), context.packageName)
        }
        result == AppOpsManager.MODE_ALLOWED || result == AppOpsManager.MODE_DEFAULT
    } catch (_: Exception) {
        false
    }
}

/** Tela da OneUI: Segmentos de fixação de tela. Fallback pro Android genérico. */
fun openPinningSettings(context: Context) {
    val candidates = listOf(
        ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.pinning.LockTaskActivity"),
        ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm_cn.ui.pinning.LockTaskActivity"),
    )
    for (c in candidates) {
        try {
            val intent = Intent().apply {
                component = c
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {
        }
    }
    try {
        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
    }
}

/** Tela de acesso especial do full-screen intent (Android 14+). */
fun openFullScreenIntentSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        openAppDetails(context)
    }
}

fun openAccessibilitySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
    }
}

fun openNotificationSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
        openAppDetails(context)
    }
}

fun openAppPermissionSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
    }
}

fun openDndSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
        openAppDetails(context)
    }
}

fun openExactAlarmSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
        openAppDetails(context)
    }
}

// ── status ──────────────────────────────────────────────────────────────────

fun isDeviceAdminActive(context: Context): Boolean {
    return try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        dpm.isAdminActive(ComponentName(context, com.pakarai.alarme.admin.PakaraiDeviceAdmin::class.java))
    } catch (_: Exception) {
        false
    }
}

fun requestDeviceAdmin(context: Context) {
    try {
        val cn = ComponentName(context, com.pakarai.alarme.admin.PakaraiDeviceAdmin::class.java)
        val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
            putExtra(
                android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Dificulta desinstalar o alarme por engano. Pra remover depois, desative em Segurança → Apps de administração."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

fun areNotificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

fun canUseFullScreenIntent(context: Context): Boolean? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
    return try {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.canUseFullScreenIntent()
    } catch (_: Exception) {
        null
    }
}

fun isDndOn(context: Context): Boolean? {
    return try {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    } catch (_: Exception) {
        null
    }
}

fun isBackgroundRestricted(context: Context): Boolean? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    return try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.isBackgroundRestricted
    } catch (_: Exception) {
        null
    }
}
