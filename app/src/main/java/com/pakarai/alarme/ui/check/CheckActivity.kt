package com.pakarai.alarme.ui.check

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.Constants
import com.pakarai.alarme.service.AlarmService
import com.pakarai.alarme.ui.MainActivity
import com.pakarai.alarme.ui.theme.AlarmePakaraiTheme

/**
 * "AINDA ACORDADO?" — tela cheia, por cima da lockscreen, com SIM e NÃO
 * em posições aleatórias (o usuário precisa LER a tela pra desligar).
 *
 * - SIM → encerra de vez: cancela o check e volta ao estado Idle.
 * - NÃO / timeout / sair da tela → re-toca o alarme completo
 *   (AlarmService.start) e o desafio recomeça.
 */
class CheckActivity : ComponentActivity() {

    private var alarmId = -1L

    /** false enquanto o check segue aberto; true depois de SIM/NÃO/timeout. */
    private var resolved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        alarmId = intent.getLongExtra(Constants.EXTRA_ALARM_ID, -1L)

        onBackPressedDispatcher.addCallback {
            reRing()
        }

        setContent {
            val accent by AppScope.settings.accentId.collectAsStateWithLifecycle()
            AlarmePakaraiTheme(accentId = accent) {
                CheckScreen(
                    onYes = ::confirmAwake,
                    onNo = ::reRing,
                    onTimeout = ::reRing
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // check já foi respondido/desativado em outro lugar → não mostra e não re-toca
        if (!AppScope.stateManager.isCheckingFor(alarmId)) {
            resolved = true
            finish()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // apertou Home/recents = não respondeu → re-toca
        reRing()
    }

    /** Respondeu SIM: acordou de verdade → tudo encerra e volta pra lista. */
    private fun confirmAwake() {
        if (resolved) return
        resolved = true
        AppScope.scheduler.cancelCheck(alarmId)
        AppScope.stateManager.clear()
        openHome()
        finish()
    }

    /** NÃO respondeu / saiu / sem tempo: volta a tocar o desafio inteiro. */
    private fun reRing() {
        if (resolved) return
        resolved = true
        AppScope.scheduler.cancelCheck(alarmId)
        try {
            AlarmService.start(this, alarmId)
        } catch (_: Exception) {
        }
        finish()
    }

    private fun openHome() {
        try {
            val i = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(i)
        } catch (_: Exception) {
        }
    }
}