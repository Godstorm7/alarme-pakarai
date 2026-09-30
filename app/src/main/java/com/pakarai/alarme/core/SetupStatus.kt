package com.pakarai.alarme.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.pakarai.alarme.AppScope
import com.pakarai.alarme.ui.wizard.PinningAppOp
import com.pakarai.alarme.ui.wizard.areNotificationsEnabled
import com.pakarai.alarme.ui.wizard.canUseFullScreenIntent
import com.pakarai.alarme.ui.wizard.isBatteryIgnored
import com.pakarai.alarme.ui.wizard.isBackgroundRestricted
import com.pakarai.alarme.ui.wizard.isDeviceAdminActive
import com.pakarai.alarme.ui.wizard.isOverlayAllowed
import com.pakarai.alarme.ui.wizard.pinningAppOpState

/**
 * Estado de um ajuste do sistema, SEM fingir certeza.
 *
 * A regra do app é: se não existe API pública que diga "está ligado", a gente
 * diz MANUAL (passo a passo) em vez de mentir com um ATIVO/PENDENTE inventado.
 * Foi exatamente esse "MODE_DEFAULT = ativo" que fazia o app dizer que a
 * Fixação de janelas estava ligada na OneUI.
 */
enum class SetupState {
    /** Confirmado ligado. */
    OK,

    /** Confirmado desligado e existe botão que resolve. */
    ACTION,

    /** Não dá pra ler o estado — a pessoa confere no passo a passo. */
    MANUAL,

    /** Sem informação (ex.: OEM escondeu, permissão não se aplica nesta versão). */
    UNKNOWN,

    /**
     * Não se aplica porque a pessoa DESLIGOU a opção no app. Não é pendência
     * e não é falha — é escolha dela, então não pode virar banner cobrando
     * permissão de algo que ela acabou de dizer que não quer.
     */
    SKIPPED,
}

/**
 * Cada proteção que o app depende pra não falhar de madrugada.
 * [severity] ordena o banner: quanto maior, mais dor de cabeça se faltar.
 */
enum class SetupItem(
    val title: String,
    val why: String,
    val path: String,
    val severity: Int,
) {
    EXACT_ALARM(
        title = "Alarmes exatos",
        why = "Sem isso o Android adia o alarme e ele toca na hora errada.",
        path = "Ajustes → Apps → Pakarai → Alarmes e lembretes → permitir",
        severity = 100,
    ),
    DND_ACCESS(
        title = "Furar o Não perturbe",
        why = "Com o DND ligado, o alarme só rompe a barreira se você permitir o acesso. " +
            "Desligando a opção lá em cima, o app para de pedir isto.",
        path = "Ajustes → Notificações → Não perturbe → acesso especial → permitir Pakarai",
        severity = 90,
    ),
    NOTIFICATIONS(
        title = "Notificações",
        why = "O aviso do próximo alarme e o heads-up do alarme dependem disto.",
        path = "Ajustes → Apps → Pakarai → Notificações → permitir",
        severity = 80,
    ),
    FULL_SCREEN(
        title = "Tela cheia (full-screen)",
        why = "Sem isso o Android pode deixar o alarme tocar sem abrir a tela.",
        path = "Ajustes → Apps → Acesso especial → Notificações em tela cheia → Pakarai",
        severity = 70,
    ),
    OVERLAY(
        title = "Aparecer por cima (popup)",
        why = "Sem isso o “AINDA ACORDADO?” pode não abrir com a tela ligada.",
        path = "Ajustes → Apps → Pakarai → Aparecer por cima → permitir",
        severity = 60,
    ),
    BATTERY(
        title = "Bateria sem restrições",
        why = "O sistema pode adiar o serviço do alarme se ele estiver em economia.",
        path = "Ajustes → Bateria → Limites de uso em 2º plano → Pakarai → Sem restrições",
        severity = 50,
    ),
    ACCESSIBILITY(
        title = "Acessibilidade (anti-fuga)",
        why = "Impede desligar o alarme no susto. O app não funciona sem esse serviço.",
        path = "Ajustes → Acessibilidade → Apps instalados → Pakarai → Ativar",
        severity = 40,
    ),
    SCREEN_PIN(
        title = "Fixar janelas (anti-fuga)",
        why = "Prende a tela do desafio: sem o botão de sair, o alarme só acaba resolvido.",
        path = "Ajustes → Segurança e privacidade → Outras configurações de segurança → Fixar janelas",
        severity = 35,
    ),
    BACKGROUND(
        title = "Auto start / Smart Manager",
        why = "A OneUI pode suspender o app e matar o alarme antes do toque.",
        path = "Ajustes → Bateria e cuidados → ⋮ → Config. avançadas → Iniciar automaticamente",
        severity = 30,
    ),
    ADMIN(
        title = "Impedir desinstalação",
        why = "Evita desinstalar o alarme sem querer, de madrugada.",
        path = "Ajustes → Segurança → Administradores do dispositivo → Pakarai",
        severity = 10,
    ),
    /**
     * Não existe API pública pra ler o Bloqueador Automático nem as
     * Restrições Máximas da OneUI. Não fingimos que sabemos: fica MANUAL e o
     * texto diz que é uma POSSÍVEL causa quando o alarme falha. Vale conferir
     * os dois: o Bloqueador Automático tem "Permissões não usadas" e as
     * Restrições Máximas moram no menu das Restrições.
     */
    AUTO_BLOCKER(
        title = "Bloqueador Automático / Restrições Máximas (conferir se falhar)",
        why = "O app não consegue ler esses ajustes. Se o alarme falhar, pode ser o Bloqueador " +
            "Automático barrando o app ou as Restrições Máximas travando ele em segundo plano.",
        path = "Ajustes → Segurança e privacidade → Segurança → Bloqueador Automático → " +
            "Permissões não usadas / Restrições",
        severity = 2,
    ),
    ACTIVITY_RECOGNITION(
        title = "Atividade física",
        why = "Usado só pra detectar que você foi mesmo dormir.",
        path = "Ajustes → Apps → Pakarai → Permissões → Atividade física",
        severity = 5,
    ),
    CAMERA(
        title = "Câmera (QR/objeto)",
        why = "Só é pedida quando você escolhe a missão com câmera.",
        path = "Ajustes → Apps → Pakarai → Permissões → Câmera",
        severity = 4,
    ),
}

data class SetupStatus(val item: SetupItem, val state: SetupState) {
    val isBlocking: Boolean
        get() = state == SetupState.ACTION || state == SetupState.MANUAL
}

/**
 * Lê o estado real de cada item agora. Chamar de novo a cada ON_RESUME: os
 * valores vêm do sistema e mudam fora do app.
 */
object SetupStatusReader {

    fun read(context: Context, guardEnabled: Boolean): List<SetupStatus> =
        SetupItem.entries.map { item ->
            SetupStatus(item, stateOf(context, item, guardEnabled))
        }

    private fun granted(context: Context, perm: String): Boolean =
        context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    private fun stateOf(context: Context, item: SetupItem, guardEnabled: Boolean): SetupState =
        when (item) {
            // canScheduleExact() só pode ser falso no Android 12+ (a permissão
            // não existe antes disso), então aqui é sempre PENDENTE de verdade —
            // nada de "sem informação" escondendo um alarme que não toca na hora
            SetupItem.EXACT_ALARM ->
                if (AppScope.scheduler.canScheduleExact()) SetupState.OK else SetupState.ACTION

            SetupItem.DND_ACCESS -> when {
                !AppScope.settings.dndBypass -> SetupState.SKIPPED // ela desligou no app
                DndBypass.hasPolicyAccess(context) -> SetupState.OK
                else -> SetupState.ACTION
            }

            SetupItem.NOTIFICATIONS ->
                if (areNotificationsEnabled(context)) SetupState.OK else SetupState.ACTION

            SetupItem.FULL_SCREEN ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) SetupState.OK
                else when (canUseFullScreenIntent(context)) {
                    true -> SetupState.OK
                    false -> SetupState.ACTION
                    null -> SetupState.UNKNOWN
                }

            SetupItem.OVERLAY ->
                if (isOverlayAllowed(context)) SetupState.OK else SetupState.ACTION

            SetupItem.BATTERY ->
                if (isBatteryIgnored(context)) SetupState.OK else SetupState.ACTION

            SetupItem.ACCESSIBILITY -> when {
                !guardEnabled -> SetupState.UNKNOWN // desligado de propósito pela pessoa
                else -> if (AppScope.settings.isGuardActuallyEnabled(context)) SetupState.OK
                else SetupState.ACTION
            }

            // Sem API: a OneUI esconde o estado real. Só declaramos OK quando o
            // AppOps responde ALLOWED de forma explícita — MODE_DEFAULT é HERDADO
            // (pode vir ligado do AOSP, desligado da OneUI), então vira MANUAL.
            SetupItem.SCREEN_PIN -> when (pinningAppOpState(context)) {
                PinningAppOp.ALLOWED -> SetupState.OK
                PinningAppOp.DENIED -> SetupState.ACTION
                PinningAppOp.INHERITED, PinningAppOp.UNKNOWN -> SetupState.MANUAL
            }

            SetupItem.BACKGROUND ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) SetupState.OK
                else when (isBackgroundRestricted(context)) {
                    true -> SetupState.ACTION
                    false -> SetupState.OK
                    null -> SetupState.UNKNOWN
                }

            SetupItem.ADMIN ->
                if (isDeviceAdminActive(context)) SetupState.OK else SetupState.ACTION

            SetupItem.AUTO_BLOCKER -> SetupState.MANUAL

            SetupItem.ACTIVITY_RECOGNITION ->
                if (granted(context, Manifest.permission.ACTIVITY_RECOGNITION)) SetupState.OK
                else SetupState.ACTION

            SetupItem.CAMERA ->
                if (granted(context, Manifest.permission.CAMERA)) SetupState.OK
                else SetupState.ACTION
        }
}
