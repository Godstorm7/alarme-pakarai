package com.pakarai.alarme.ui.check

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pakarai.alarme.core.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private val SLOTS = listOf(
    Slot(0.06f, 0.44f),
    Slot(0.55f, 0.44f),
    Slot(0.06f, 0.62f),
    Slot(0.55f, 0.62f),
    Slot(0.06f, 0.80f),
    Slot(0.55f, 0.80f),
)

private data class Slot(val x: Float, val y: Float)

/**
 * Prompt "AINDA ACORDADO?" com countdown de 30s e dois botões IDÊNTICOS
 * (SIM / NÃO) em posições aleatórias da metade de baixo da tela — só dá pra
 * desligar lendo qual é qual.
 */
@Composable
internal fun CheckScreen(
    onYes: () -> Unit,
    onNo: () -> Unit,
    onTimeout: () -> Unit,
) {
    // sorteia onde cada botão fica pra ESTA janela; muda a cada check
    val (simSlot, naoSlot) = remember { pairOfDistinctSlots() }
    val windowSeconds = (Constants.CHECK_WINDOW_MS / 1000).toInt().coerceAtLeast(1)
    var left by remember { mutableIntStateOf(windowSeconds) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1_000)
            left -= 1
        }
        onTimeout()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(MaterialTheme.colorScheme.background)
    ) {
        val w = maxWidth
        val h = maxHeight
        val btnW = 148.dp
        val btnH = 58.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 70.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AINDA ACORDADO?",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Essa pergunta volta enquanto você não responder SIM.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(22.dp))
            Text(
                text = "$left",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(horizontal = 28.dp)
            ) {
                Text(
                    text = "Sem tocar em SIM, o som volta",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        RandomButton(
            text = "SIM",
            x = slotDp(w, simSlot.x, btnW),
            y = slotDp(h, simSlot.y, btnH),
            width = btnW,
            height = btnH,
            show = left > 0,
            onClick = {
                scope.launch { onYes() }
            }
        )
        RandomButton(
            text = "NÃO",
            x = slotDp(w, naoSlot.x, btnW),
            y = slotDp(h, naoSlot.y, btnH),
            width = btnW,
            height = btnH,
            show = left > 0,
            onClick = {
                scope.launch { onNo() }
            }
        )
    }
}

@Composable
private fun RandomButton(
    text: String,
    x: androidx.compose.ui.unit.Dp,
    y: androidx.compose.ui.unit.Dp,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    show: Boolean,
    onClick: () -> Unit,
) {
    if (!show) return
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .offset(x = x, y = y)
            .size(width, height)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

/** Distância em dp do canto superior-esquerdo, centralizando o botão no slot. */
private fun slotDp(
    total: androidx.compose.ui.unit.Dp,
    fraction: Float,
    buttonSize: androidx.compose.ui.unit.Dp,
): androidx.compose.ui.unit.Dp {
    val px = total.value * fraction
    val buttonPx = buttonSize.value
    return (px - buttonPx / 2f).coerceAtLeast(0f).dp
}

private fun pairOfDistinctSlots(): Pair<Slot, Slot> {
    val shuffled = SLOTS.shuffled(Random.Default)
    return Pair(shuffled[0], shuffled[1])
}