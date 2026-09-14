package com.pakarai.alarme.ui.challenge

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.AlarmActions
import com.pakarai.alarme.core.Constants
import com.pakarai.alarme.data.AlarmEntity
import com.pakarai.alarme.ui.MainActivity
import com.pakarai.alarme.ui.theme.AlarmePakaraiTheme

/**
 * Tela de bloqueio do alarme. Roda POR CIMA da lockscreen
 * (manifest: showWhenLocked + turnScreenOn), respondível SEM desbloquear.
 * O desafio certo desliga. Se você sair da tela, o GuardService reabre.
 */
class ChallengeActivity : ComponentActivity() {

    private var alarmId = -1L

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
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        alarmId = intent.getLongExtra(Constants.EXTRA_ALARM_ID, -1L)

        setContent {
            val accent by AppScope.settings.accentId.collectAsStateWithLifecycle()
            AlarmePakaraiTheme(accentId = accent) {
                ChallengeScreen(alarmId = alarmId)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val state = AppScope.stateManager.state.value
        if (state !is com.pakarai.alarme.core.AlarmStateManager.State.Ringing) {
            finish()
            return
        }
    }

    companion object {
        /** Resolve o desafio com sucesso: para tudo, agenda o check e volta pra lista. */
        fun resolve(context: Activity, alarm: AlarmEntity) {
            AlarmActions.resolve(alarm)
            openHome(context)
        }

        /** Aplica soneca: silencia, agenda o retorno e volta pra lista. */
        fun snooze(context: Activity, alarm: AlarmEntity) {
            AlarmActions.snooze(alarm)
            openHome(context)
        }

        /** Volta pra lista de alarmes, o ponto de referência depois de qualquer ação. */
        private fun openHome(context: Activity) {
            try {
                val intent = Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
            } catch (_: Exception) {
            }
            try {
                context.finish()
            } catch (_: Exception) {
            }
        }
    }
}