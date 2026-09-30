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

/**
 * Deep links OEM: tentam o componente Samsung; se não achar, caem em telas genéricas.
 */
fun openSmartManager(context: Context) {
    val candidates = listOf(
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
    )
    for (c in candidates) {
        if (tryStart(context, Intent().apply {
            component = c
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })) return
    }
    openAppDetails(context)
}

/**
 * Acha um componente sem abrir tela errada: resolveActivity() ANTES de
 * startActivity() (Samsung lança build onde o componente não existe e o
 * startActivity jogaria o usuário em crash/erro genérico), e engole o
 * ActivityNotFoundException se ainda assim falhar.
 */
fun tryStart(context: Context, intent: Intent): Boolean {
    return try {
        if (context.packageManager.resolveActivity(intent, 0) == null) false
        else {
            context.startActivity(intent)
            true
        }
    } catch (_: Exception) {
        false
    }
}

/** Copia texto pra área de transferência (usado nos caminhos manuais). */
fun copyToClipboard(context: Context, label: String, text: String): Boolean = try {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    true
} catch (_: Exception) {
    false
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

/**
 * Estado do AppOp "pin_window". Separate do bool antigo de propósito:
 * MODE_DEFAULT é HERDADO, e é exatamente ele que mente — no AOSP o pinning vem
 * ligado, na OneUI vem desligado, e o app não tem como saber qual é o caso.
 * Por isso HERDEDADO vira MANUAL na UI, nunca "ATIVO".
 */
enum class PinningAppOp { ALLOWED, DENIED, INHERITED, UNKNOWN }

fun pinningAppOpState(context: Context): PinningAppOp = try {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val result = if (Build.VERSION.SDK_INT >= 29) {
        appOps.unsafeCheckOpNoThrow("android:pin_window", Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow("android:pin_window", Process.myUid(), context.packageName)
    }
    when (result) {
        AppOpsManager.MODE_ALLOWED -> PinningAppOp.ALLOWED
        AppOpsManager.MODE_IGNORED -> PinningAppOp.DENIED
        AppOpsManager.MODE_DEFAULT -> PinningAppOp.INHERITED
        else -> PinningAppOp.UNKNOWN
    }
} catch (_: Exception) {
    PinningAppOp.UNKNOWN
}

/**
 * Telas de Fixar janelas. OneUI 6+ movou pra "Outras configurações de
 * segurança" e o componente exato varia por build, então tentamos os
 * candidatos e caímos no caminho genérico.
 */
fun openPinningSettings(context: Context) {
    val candidates = listOf(
        ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.pinning.LockTaskActivity"),
        ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm_cn.ui.pinning.LockTaskActivity"),
        ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.settings.security.SecuritySettings"),
    )
    for (c in candidates) {
        if (tryStart(context, Intent().apply {
            component = c
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })) return
    }
    // AOSP: a tela de segurança é o único ponto de entrada confiável
    tryStart(
        context,
        Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/**
 * "Aparecer por cima" (SYSTEM_ALERT_WINDOW): sem ela o Android bloqueia a
 * abertura do popup do "AINDA ACORDADO?" quando o celular está em uso.
 */
fun isOverlayAllowed(context: Context): Boolean = try {
    Settings.canDrawOverlays(context)
} catch (_: Exception) {
    false
}

fun openOverlaySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
        openAppDetails(context)
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

fun isBackgroundRestricted(context: Context): Boolean? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    return try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.isBackgroundRestricted
    } catch (_: Exception) {
        null
    }
}
