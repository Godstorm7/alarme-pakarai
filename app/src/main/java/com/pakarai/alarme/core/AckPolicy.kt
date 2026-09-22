package com.pakarai.alarme.core

/**
 * Teto ABSOLUTO de checagens do "AINDA ACORDADO?" — existe pra NUNCA dar beco
 * sem saída: mesmo um valor antigo/estranho (ex.: -1 = "sempre") termina sozinho.
 */
const val ACK_MAX_CHECKS = 5

/**
 * Política do "AINDA ACORDADO?" — pura e testável.
 *
 * [total] é quantas checagens o ciclo tem; **negativo ("sempre") é tratado como
 * [ACK_MAX_CHECKS]**, porque repetir sem fim deixava o alarme congelado pra sempre.
 * Devolve o índice da PRÓXIMA checagem depois de um SIM, ou null quando o ciclo
 * termina (o alarme para de verificar).
 */
fun nextCheckIndex(current: Int, total: Int): Int? {
    val cur = current.coerceAtLeast(1)
    val max = if (total < 0) ACK_MAX_CHECKS else total.coerceAtMost(ACK_MAX_CHECKS)
    return if (cur < max) cur + 1 else null
}
