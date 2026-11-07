package com.pakarai.alarme.core

import android.app.NotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tradução do `currentInterruptionFilter` para "o DND está ligado?".
 *
 * O bug que existiu: `INTERRUPTION_FILTER_ALL` (valor 1) é o estado **normal**
 * do aparelho — DND DESLIGADO. Ele estava listado como "DND ativo", então o
 * AlarmService chamava `setInterruptionFilter(ALARMS)` mesmo com o DND
 * desligado, e o efeito visível era o app **ligando o Não Perturbe** durante o
 * toque. Estes testes travam a tradução nos cinco valores possíveis.
 */
class DndFilterTest {

    @Test
    fun allSignificaDndDesligadoENaoSeMexe() {
        // regressão: era este valor que fazia o app ligar o Não Perturbe
        assertFalse(DndBypass.dndOnFor(NotificationManager.INTERRUPTION_FILTER_ALL))
    }

    @Test
    fun desconhecidoNaoAutorizaMexer() {
        // UNKNOWN (0): sem saber o estado, mexer seria chutar a preferência
        assertFalse(DndBypass.dndOnFor(NotificationManager.INTERRUPTION_FILTER_UNKNOWN))
    }

    @Test
    fun osTresModosDeSilencioContamComoDndLigado() {
        assertTrue(DndBypass.dndOnFor(NotificationManager.INTERRUPTION_FILTER_PRIORITY))
        assertTrue(DndBypass.dndOnFor(NotificationManager.INTERRUPTION_FILTER_NONE))
        assertTrue(DndBypass.dndOnFor(NotificationManager.INTERRUPTION_FILTER_ALARMS))
    }

    @Test
    fun valorForaDoEnumNaoAutorizaMexer() {
        // robustez: um OEM pode devolver algo inesperado; na dúvida, não toca
        assertFalse(DndBypass.dndOnFor(-1))
        assertFalse(DndBypass.dndOnFor(99))
    }

    @Test
    fun osValoresDoEnumSaoOsDoAndroid() {
        // se alguém "arrumar" esses números, a tradução acima vira mentira
        assertTrue(NotificationManager.INTERRUPTION_FILTER_ALL == 1)
        assertTrue(NotificationManager.INTERRUPTION_FILTER_PRIORITY == 2)
        assertTrue(NotificationManager.INTERRUPTION_FILTER_NONE == 3)
        assertTrue(NotificationManager.INTERRUPTION_FILTER_ALARMS == 4)
        assertTrue(NotificationManager.INTERRUPTION_FILTER_UNKNOWN == 0)
    }
}
