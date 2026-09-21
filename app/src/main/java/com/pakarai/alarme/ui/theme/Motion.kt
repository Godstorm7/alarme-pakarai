package com.pakarai.alarme.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * Tokens de movimento do PakaRai — estilo sutil/premium com acentos pontuais.
 * Use [rememberAnimationsEnabled] pra respeitar "reduzir movimento" do sistema.
 */
object PakaRaiMotion {
    const val FAST = 150
    const val MEDIUM = 250
    const val SLOW = 350

    /** Mola pro toque: responde rápido, sem tremer. */
    val press = spring<Float>(dampingRatio = 0.8f, stiffness = 700f)

    /** Mola com leve overshoot — só nos momentos-chave (FAB, ✓, missão concluída). */
    val accent = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
}

/** `false` quando o usuário desligou as animações (Configurações → Acessibilidade). */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) != 0f
        } catch (_: Exception) {
            true
        }
    }
}

/**
 * Feedback de toque: encolhe ~3% enquanto pressionado.
 * Passe o MESMO [interaction] pro `clickable`/`Button`/`Surface` pra o estado de press funcionar.
 */
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val enabled = rememberAnimationsEnabled()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = PakaRaiMotion.press,
        label = "pressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
