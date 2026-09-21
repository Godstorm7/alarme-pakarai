package com.pakarai.alarme.core

/**
 * Política do "AINDA ACORDADO?" — pura e testável.
 *
 * [total] é quantas checagens o ciclo tem; **negativo = "até confirmar"**.
 * Devolve o índice da PRÓXIMA checagem depois de um SIM, ou null quando o
 * ciclo termina (o alarme para de verificar).
 */
fun nextCheckIndex(current: Int, total: Int): Int? {
    val cur = current.coerceAtLeast(1)
    if (total < 0) return cur + 1
    return if (cur < total) cur + 1 else null
}
