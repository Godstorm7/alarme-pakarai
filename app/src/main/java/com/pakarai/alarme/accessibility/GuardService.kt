package com.pakarai.alarme.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.Constants
import com.pakarai.alarme.service.AlarmService
import com.pakarai.alarme.ui.challenge.ChallengeActivity

/**
 * Vigilância anti-fuga.
 *
 * Enquanto o alarme está tocando (estado Ringing), se o usuário tentar
 * sair da tela do desafio — fechar o app, abrir recents, ir pra home,
 * abrir outro app — este serviço REABRE o desafio em ~1s.
 *
 * Durante o "AINDA ACORDADO?" (estado Checking), sair da tela = não respondeu:
 * em vez de relançar o check, o som volta a tocar na hora (re-toca o desafio).
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

        val stateManager = AppScope.stateManager
        val state = stateManager.state.value

        // "AINDA ACORDADO?": saiu da tela do check na janela de resposta = não respondeu → re-toca.
        // No intervalo EM ESPERA (check ainda não disparou) usar o telefone é normal: nada a fazer.
        if (state is com.pakarai.alarme.core.AlarmStateManager.State.Checking) {
            val topPackage = event.packageName?.toString() ?: return
            if (topPackage == packageName) return // continua no check: parado
            if (state.nextAtMs > System.currentTimeMillis()) return // check ainda não abriu
            val alarmId = state.alarmId
            if (alarmId < 0) return
            val now = SystemClock.uptimeMillis()
            if (now - lastRelaunch < RELAUNCH_COOLDOWN_MS) return
            lastRelaunch = now
            stateManager.finishChecking(alarmId)
            AppScope.scheduler.cancelCheck(alarmId)
            try {
                AlarmService.start(this, alarmId)
            } catch (_: Exception) {
            }
            return
        }

        // só vigia enquanto está de fato tocando
        if (!stateManager.isRinging()) {
            lastRelaunch = 0L
            return
        }

        val topPackage = event.packageName?.toString() ?: return
        if (topPackage == packageName) return // continua no desafio: parado

        // fugiu (ou a tela piscou): relança o desafio com cooldown anti-loop
        val now = SystemClock.uptimeMillis()
        if (now - lastRelaunch < RELAUNCH_COOLDOWN_MS) return
        lastRelaunch = now

        val alarmId = stateManager.currentAlarmId()
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