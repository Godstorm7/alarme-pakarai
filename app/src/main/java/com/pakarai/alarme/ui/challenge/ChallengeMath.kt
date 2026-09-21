package com.pakarai.alarme.ui.challenge

import kotlin.random.Random

/**
 * Gera a conta conforme a dificuldade. Retorna (texto, resposta).
 *
 * 0 = Fácil   → números de 1 casa (1..9); as 4 operações
 * 1 = Médio   → até 2 casas; em × e ÷, no máximo UM número de 2 casas
 * 2 = Difícil → até 3 casas, SEM parênteses; em × e ÷, no máximo UM de 3 casas
 *
 * Invariantes (cobertos por teste): resultado SEMPRE inteiro, NUNCA negativo,
 * nunca com vírgula/resto — uma conta impossível prenderia o alarme pra sempre.
 */
fun generateMathQuestion(difficulty: Int): Pair<String, Int> {
    val rnd = Random.Default
    return when (difficulty.coerceIn(0, 2)) {
        0 -> easyQuestion(rnd)
        1 -> mediumQuestion(rnd)
        else -> hardQuestion(rnd)
    }
}

//── FÁCIL: tudo com 1 dígito ────────────────────────────────────────────────

private fun easyQuestion(rnd: Random): Pair<String, Int> = when (rnd.nextInt(4)) {
    0 -> {
        val a = rnd.nextInt(1, 10)
        val b = rnd.nextInt(1, 10)
        "$a + $b" to a + b
    }
    1 -> {
        val a = rnd.nextInt(1, 10)
        val b = rnd.nextInt(1, a + 1) // a ≥ b: nunca negativo
        "$a − $b" to a - b
    }
    2 -> {
        val a = rnd.nextInt(2, 10)
        val b = rnd.nextInt(2, 10)
        "$a × $b" to a * b
    }
    // divisão exata com TUDO de 1 dígito: o dividendo também (≤ 9)
    else -> exactDivision(rnd, divisors = 2..9, quotients = 2..9, maxDividend = 9)
}

//── MÉDIO: até 2 casas ──────────────────────────────────────────────────────

private fun mediumQuestion(rnd: Random): Pair<String, Int> = when (rnd.nextInt(4)) {
    0 -> {
        val a = rnd.nextInt(2, 100)
        val b = rnd.nextInt(2, 100)
        "$a + $b" to a + b
    }
    1 -> {
        val a = rnd.nextInt(2, 100)
        val b = rnd.nextInt(2, a + 1)
        "$a − $b" to a - b
    }
    2 -> {
        // no máximo UM número de 2 casas
        val a = rnd.nextInt(2, 10)
        val b = rnd.nextInt(10, 100)
        if (rnd.nextBoolean()) "$a × $b" to a * b else "$b × $a" to b * a
    }
    else -> exactDivision(rnd, divisors = 2..9, quotients = 2..9, maxDividend = 99)
}

//── DIFÍCIL: até 3 casas, sem parênteses ────────────────────────────────────

private fun hardQuestion(rnd: Random): Pair<String, Int> = when (rnd.nextInt(4)) {
    0 -> {
        val a = rnd.nextInt(10, 1000)
        val b = rnd.nextInt(10, 1000)
        "$a + $b" to a + b
    }
    1 -> {
        val a = rnd.nextInt(10, 1000)
        val b = rnd.nextInt(10, a + 1)
        "$a − $b" to a - b
    }
    2 -> {
        // no máximo UM número de 3 casas
        val a = rnd.nextInt(2, 10)
        val b = rnd.nextInt(100, 1000)
        if (rnd.nextBoolean()) "$a × $b" to a * b else "$b × $a" to b * a
    }
    // divisor de 1 dígito e quociente de 2 → dividendo de até 3 casas
    else -> exactDivision(rnd, divisors = 2..9, quotients = 10..99, maxDividend = 999)
}

/**
 * Divisão SEMPRE exata: sorteia um par (divisor, quociente) válido e monta
 * o dividendo como o produto. Assim nunca sobra resto nem vírgula.
 */
private fun exactDivision(
    rnd: Random,
    divisors: IntRange,
    quotients: IntRange,
    maxDividend: Int,
): Pair<String, Int> {
    val pairs = ArrayList<Pair<Int, Int>>()
    for (d in divisors) {
        for (q in quotients) {
            if (d * q <= maxDividend) pairs += d to q
        }
    }
    val (d, q) = pairs[rnd.nextInt(pairs.size)]
    return "${d * q} ÷ $d" to q
}
