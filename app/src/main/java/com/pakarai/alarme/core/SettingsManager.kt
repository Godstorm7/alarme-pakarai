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

    /** Cor de acento do app. Reativo: mudar aqui recompõe o tema inteiro na hora. */
    private val _accentId = MutableStateFlow(prefs.getString(KEY_ACCENT, DEFAULT_ACCENT) ?: DEFAULT_ACCENT)
    val accentId: StateFlow<String> = _accentId.asStateFlow()

    fun setAccent(id: String) {
        if (id == _accentId.value) return
        prefs.edit().putString(KEY_ACCENT, id).apply()
        _accentId.value = id
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
        const val KEY_ACCENT = "accent_id"
        const val DEFAULT_ACCENT = "amber"
    }
}