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

        when (screen) {
            "home" -> HomeScreen(
                onNewAlarm = { editId = -1L; screen = "edit" },
                onEditAlarm = { editId = it; screen = "edit" },
                onOpenWizard = { screen = "wizard" },
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
        }
    }
}