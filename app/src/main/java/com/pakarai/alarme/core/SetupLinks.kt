package com.pakarai.alarme.core

import android.content.Context
import android.content.Intent
import android.os.Build
import com.pakarai.alarme.admin.PakaraiDeviceAdmin
import com.pakarai.alarme.ui.wizard.copyToClipboard
import com.pakarai.alarme.ui.wizard.openAccessibilitySettings
import com.pakarai.alarme.ui.wizard.openAppDetails
import com.pakarai.alarme.ui.wizard.openBatteryExemption
import com.pakarai.alarme.ui.wizard.openDndSettings
import com.pakarai.alarme.ui.wizard.openExactAlarmSettings
import com.pakarai.alarme.ui.wizard.openFullScreenIntentSettings
import com.pakarai.alarme.ui.wizard.openNotificationSettings
import com.pakarai.alarme.ui.wizard.openOverlaySettings
import com.pakarai.alarme.ui.wizard.openPinningSettings
import com.pakarai.alarme.ui.wizard.openSmartManager
import com.pakarai.alarme.ui.wizard.tryStart

/**
 * Abre (ou copia o caminho de) um item de [SetupItem]. Um lugar só, pra Guia,
 * wizard, editor e banner discordarem nunca do mesmo jeito.
 *
 * Os deep links em si moram em `ui/wizard/SettingsLinks.kt` (com o
 * `resolveActivity()` e o fallback); aqui só mora a decisão de QUAL ajuste
 * cada item do [SetupItem] é.
 *
 * Quando não existe deep link confiável — pinning e Bloqueador Automático na
 * OneUI — a gente não inventa componente: mostra o caminho escrito e deixa a
 * pessoa copiar.
 */
object SetupLinks {

    /** Verdadeiro quando existe um botão que resolve (ou pelo menos leva perto). */
    fun hasDirectAction(item: SetupItem): Boolean = item != SetupItem.AUTO_BLOCKER

    /** Abre a tela do ajuste. [item] precisa passar por [hasDirectAction]. */
    fun open(context: Context, item: SetupItem): Boolean = when (item) {
        SetupItem.EXACT_ALARM -> {
            openExactAlarmSettings(context)
            true
        }
        SetupItem.DND_ACCESS -> {
            openDndSettings(context)
            true
        }
        SetupItem.NOTIFICATIONS -> {
            openNotificationSettings(context)
            true
        }
        // Antes do Android 14 não existe acesso especial: full-screen é
        // liberado junto com as notificações, então caímos na tela do app.
        SetupItem.FULL_SCREEN -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                openFullScreenIntentSettings(context)
            } else {
                openNotificationSettings(context)
            }
            true
        }
        SetupItem.OVERLAY -> {
            openOverlaySettings(context)
            true
        }
        SetupItem.BATTERY -> {
            openBatteryExemption(context)
            true
        }
        SetupItem.ACCESSIBILITY -> {
            openAccessibilitySettings(context)
            true
        }
        SetupItem.SCREEN_PIN -> {
            openPinningSettings(context)
            true
        }
        SetupItem.BACKGROUND -> {
            openSmartManager(context)
            true
        }
        SetupItem.ADMIN -> requestAdmin(context)
        SetupItem.ACTIVITY_RECOGNITION, SetupItem.CAMERA -> {
            openAppDetails(context)
            true
        }
        SetupItem.AUTO_BLOCKER -> false
    }

    /** Fallback de texto: copia o caminho pra pessoa colar onde precisar. */
    fun copyPath(context: Context, item: SetupItem): Boolean =
        copyToClipboard(context, item.title, item.path)

    // ── intents ──────────────────────────────────────────────────────────────

    /**
     * Device Admin não tem tela pública separada de "gerenciar": a mesma
     * ACTION_ADD_DEVICE_ADMIN abre a lista com o nosso admin destacado, e é
     * lá que fica o botão de DESATIVAR quando ele já está ligado. O texto muda
     * conforme o estado, porque "ativar" e "remover" são coisas diferentes.
     */
    private fun requestAdmin(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
            as? android.app.admin.DevicePolicyManager ?: return false
        val cn = android.content.ComponentName(context, PakaraiDeviceAdmin::class.java)
        val already = try {
            dpm.isAdminActive(cn)
        } catch (_: Exception) {
            false
        }
        val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
            .putExtra(
                android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                if (already) {
                    "O Pakarai está ativo. É daqui que você desativa se um dia quiser remover."
                } else {
                    "Dificulta desinstalar o alarme por engano. Pra remover depois, desative em Segurança → Apps de administração."
                }
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return tryStart(context, intent)
    }
}
