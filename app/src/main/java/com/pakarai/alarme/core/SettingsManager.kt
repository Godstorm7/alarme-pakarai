package com.pakarai.alarme.core

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferências pequenas do app (não é o estado do alarme).
 */
class SettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences("pakarai_prefs", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    var samsungWizardShown: Boolean
        get() = prefs.getBoolean(KEY_SAMSUNG_WIZARD_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_SAMSUNG_WIZARD_SHOWN, value).apply()

    var guardUserEnabled: Boolean
        get() = prefs.getBoolean(KEY_GUARD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_GUARD_ENABLED, value).apply()

    /** Notificação fixa mostrando o próximo alarme. */
    var persistentNotification: Boolean
        get() = prefs.getBoolean(KEY_PERSISTENT_NOTIF, true)
        set(value) = prefs.edit().putBoolean(KEY_PERSISTENT_NOTIF, value).apply()

    /** Cor de acento do app. Reativo: mudar aqui recompõe o tema inteiro na hora. */
    private val _accentId = MutableStateFlow(prefs.getString(KEY_ACCENT, DEFAULT_ACCENT) ?: DEFAULT_ACCENT)
    val accentId: StateFlow<String> = _accentId.asStateFlow()

    fun setAccent(id: String) {
        if (id == _accentId.value) return
        prefs.edit().putString(KEY_ACCENT, id).apply()
        _accentId.value = id
    }

    /**
     * Rate-limit do aviso "Permissão de alarme exato saiu": toca no máximo
     * uma vez a cada [minIntervalMs] (padrão 6h) pra não virar spam de
     * re-agenda a cada abertura do app.
     */
    fun canShowPausedNotification(
        now: Long = System.currentTimeMillis(),
        minIntervalMs: Long = 6 * 60 * 60 * 1000L
    ): Boolean {
        val last = prefs.getLong(KEY_PAUSED_NOTIF_AT, 0L)
        if (now - last < minIntervalMs) return false
        prefs.edit().putLong(KEY_PAUSED_NOTIF_AT, now).apply()
        return true
    }

    /**
     * Rate-limit do AVISO de permissão exata em si (abrir a tela de settings).
     * Antes, o schedule jogava o usuário pra settings a CADA abertura do app —
     * uma armadilha. Agora no máximo 1x/dia, e o banner/notif cobrem o resto.
     */
    fun canNudgeExactPermission(
        now: Long = System.currentTimeMillis(),
        minIntervalMs: Long = 24 * 60 * 60 * 1000L
    ): Boolean {
        val last = prefs.getLong(KEY_EXACT_NUDGE_AT, 0L)
        if (now - last < minIntervalMs) return false
        prefs.edit().putLong(KEY_EXACT_NUDGE_AT, now).apply()
        return true
    }

    fun isGuardActuallyEnabled(context: Context): Boolean {
        if (!guardUserEnabled) return false
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(context.packageName + "/.accessibility.GuardService", true) }
    }

    private companion object {
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_SAMSUNG_WIZARD_SHOWN = "samsung_wizard_shown"
        const val KEY_GUARD_ENABLED = "guard_enabled"
        const val KEY_PERSISTENT_NOTIF = "persistent_notif"
        const val KEY_ACCENT = "accent_id"
        const val KEY_PAUSED_NOTIF_AT = "paused_notif_at"
        const val KEY_EXACT_NUDGE_AT = "exact_nudge_at"
        const val DEFAULT_ACCENT = "amber"
    }
}