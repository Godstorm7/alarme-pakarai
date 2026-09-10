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
import com.pakarai.alarme.ui.editor.EditorScreen
import com.pakarai.alarme.ui.home.HomeScreen
import com.pakarai.alarme.ui.theme.AlarmePakaraiTheme
import com.pakarai.alarme.ui.wizard.SamsungWizardScreen

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // primeira execução: pedir notificação (regra Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val accent by AppScope.settings.accentId.collectAsStateWithLifecycle()
            AlarmePakaraiTheme(accentId = accent) {
                AppNav()
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