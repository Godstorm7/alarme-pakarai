package com.pakarai.alarme.ui.challenge

import kotlin.random.Random

/**
 * Gera conta conforme dificuldade. Retorna (texto, resposta). 0=fácil 1=médio 2=difícil.
 * Nunca gera números negativos: nada de subtração, tudo positivo.
 */
fun generateMathQuestion(difficulty: Int): Pair<String, Int> {
    val rnd = Random.Default
    return when (difficulty) {
        // fácil: só soma, parcelas de 1 a 2 dígitos
        0 -> {
            val a = rnd.nextInt(2, 100)
            val b = rnd.nextInt(2, 100)
            "$a + $b" to a + b
        }
        // médio: só soma, parcelas de 2 a 3 dígitos
        1 -> {
            val a = rnd.nextInt(10, 1000)
            val b = rnd.nextInt(10, 1000)
            "$a + $b" to a + b
        }
        // difícil: parênteses + multiplicação por 1 dígito
        else -> {
            val c = rnd.nextInt(2, 10)
            val a = rnd.nextInt(2, 100)
            val b = rnd.nextInt(2, 100)
            when (rnd.nextInt(3)) {
                0 -> "($a + $b) × $c" to (a + b) * c
                1 -> "$a + ($b × $c)" to a + b * c
                else -> "($a × $c) + $b" to a * c + b
            }
        }
    }
}