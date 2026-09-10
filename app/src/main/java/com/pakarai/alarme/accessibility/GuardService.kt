package com.pakarai.alarme.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.Constants
import com.pakarai.alarme.ui.challenge.ChallengeActivity

/**
 * Vigilância anti-fuga.
 *
 * Enquanto o alarme está tocando (estado Ringing), se o usuário tentar
 * sair da tela do desafio — fechar o app, abrir recents, ir pra home,
 * abrir outro app — este serviço REABRE o desafio em ~1s.
 *
 * Serviços de acessibilidade têm permissão de iniciar atividades em
 * background, então conseguem "puxar" o usuário de volta mesmo quando o
 * Android bloquearia um app comum. Nada é lido ou gravado da tela.
 */
class GuardService : AccessibilityService() {

    @Volatile
    private var lastRelaunch = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        lastRelaunch = 0L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // só vigia enquanto está de fato tocando
        if (!AppScope.stateManager.isRinging()) {
            lastRelaunch = 0L
            return
        }

        val topPackage = event.packageName?.toString() ?: return
        if (topPackage == packageName) return // continua no desafio: parado

        // fugiu (ou a tela piscou): relança o desafio com cooldown anti-loop
        val now = SystemClock.uptimeMillis()
        if (now - lastRelaunch < RELAUNCH_COOLDOWN_MS) return
        lastRelaunch = now

        val alarmId = AppScope.stateManager.currentAlarmId()
        if (alarmId < 0) return

        val intent = Intent(this, ChallengeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(Constants.EXTRA_ALARM_ID, alarmId)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    override fun onInterrupt() {
    }

    companion object {
        private const val RELAUNCH_COOLDOWN_MS = 1500L
    }
}