package com.pakarai.alarme.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O token de devolução do DND é a única coisa que impede o aparelho de ficar
 * preso em "só alarme" quando o processo morre no meio do toque — e também a
 * única barreira que impede o app de sobrescrever uma escolha do usuário feita
 * depois. A regra de validade mora isolada em [DndBypass.Token] justamente pra
 * ser testada sem aparelho.
 */
class DndTokenTest {

    @Test
    fun tokenRecemGravadoAindaVale() {
        assertTrue(DndBypass.Token.isUsable(0))
        assertTrue(DndBypass.Token.isUsable(1_000))
    }

    @Test
    fun tokenVivoDuranteTodoOCicloDoAlarme() {
        // janela do alarme é 30 min: mesmo bem depois de começa a tocar, o
        // token ainda precisa devolver o filtro
        assertTrue(DndBypass.Token.isUsable(29 * 60 * 1000L))
        assertTrue(DndBypass.Token.isUsable(DndBypass.Token.MAX_AGE_MS))
    }

    @Test
    fun tokenPassadoDoPrazoNaoVoltaMais() {
        assertFalse(DndBypass.Token.isUsable(DndBypass.Token.MAX_AGE_MS + 1))
        assertFalse(DndBypass.Token.isUsable(24 * 60 * 60 * 1000L))
    }

    @Test
    fun prazoCobreOTetoDeCicloMaisUmaFolga() {
        // 30 min de janela + folga: se o token durasse menos que isso, um
        // alarme longo redeliverado perderia a devolução do DND
        val ringWindow = 30 * 60 * 1000L
        assertTrue(DndBypass.Token.MAX_AGE_MS > ringWindow)
    }
}
