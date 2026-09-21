package com.pakarai.alarme.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.core.AppJobs
import com.pakarai.alarme.ui.editor.EditorScreen
import com.pakarai.alarme.ui.home.HomeScreen
import com.pakarai.alarme.ui.theme.AlarmePakaraiTheme
import com.pakarai.alarme.ui.theme.PakaRaiMotion
import com.pakarai.alarme.ui.theme.rememberAnimationsEnabled
import com.pakarai.alarme.ui.wizard.SamsungWizardScreen

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val activityRecognitionPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // primeira execução: pedir notificação (regra Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // pedir atividade física pro desafio de passos (runtime desde API 29)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            activityRecognitionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        handleSpotifyDeepLink(intent)

        setContent {
            val accent by AppScope.settings.accentId.collectAsStateWithLifecycle()
            AlarmePakaraiTheme(accentId = accent) {
                AppNav()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // retorno do navegador do Spotify: activity é singleTask, o deep link
        // cai aqui quando o processo está vivo
        handleSpotifyDeepLink(intent)
    }

    /** Trabalha o retorno do login do Spotify (pakarai://spotify-callback): troca o
     *  authorization code por tokens ou reporta a negativa do usuário. */
    private fun handleSpotifyDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "pakarai" || data.host != "spotify-callback" || !AppScope.isManualDiReady()) return
        val error = data.getQueryParameter("error")
        if (error != null) {
            AppScope.spotifySession.reportAuthError(data.getQueryParameter("error_description"))
            return
        }
        val code = data.getQueryParameter("code")
        if (code != null) {
            AppJobs.launch("spotify-callback") {
                AppScope.spotifySession.exchangeCode(code)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun AppNav() {
        var screen by rememberSaveable { mutableStateOf("home") }
        var editId by rememberSaveable { mutableLongStateOf(-1L) }
        val animations = rememberAnimationsEnabled()

        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val forward = targetState != "home"
                val dir = if (forward) 1 else -1
                if (!animations) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    val enter = slideInHorizontally(tween(PakaRaiMotion.MEDIUM)) { full -> dir * full / 6 } +
                        fadeIn(tween(PakaRaiMotion.MEDIUM))
                    val exit = slideOutHorizontally(tween(PakaRaiMotion.MEDIUM)) { full -> -dir * full / 6 } +
                        fadeOut(tween(PakaRaiMotion.MEDIUM))
                    enter togetherWith exit
                }
            },
            label = "appNav"
        ) { target ->
            when (target) {
                "home" -> HomeScreen(
                    onNewAlarm = { editId = -1L; screen = "edit" },
                    onEditAlarm = { id ->
                        // 2ª barreira (a Home já bloqueia): alarme ativo não se edita
                        if (AppScope.stateManager.isFrozen(id)) {
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "Alarme ativo: não dá pra editar enquanto ele toca ou espera o \"AINDA ACORDADO?\".",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        } else {
                            editId = id
                            screen = "edit"
                        }
                    },
                    onOpenWizard = { screen = "wizard" },
                    onOpenGuide = { screen = "guide" },
                    onOpenGuard = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
                "edit" -> {
                    BackHandler { screen = "home" }
                    EditorScreen(
                        alarmId = editId,
                        onBack = { screen = "home" },
                        onDone = { screen = "home" }
                    )
                }
                "wizard" -> {
                    BackHandler { screen = "home" }
                    SamsungWizardScreen(onDone = { screen = "home" })
                }
                "guide" -> {
                    BackHandler { screen = "home" }
                    com.pakarai.alarme.ui.wizard.SettingsGuideScreen(onDone = { screen = "home" })
                }
            }
        }
    }
}