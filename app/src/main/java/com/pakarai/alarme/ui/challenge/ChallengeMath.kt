package com.pakarai.alarme.ui.challenge

import kotlin.random.Random

/** Gera conta conforme dificuldade. Retorna (texto, resposta). 0=fácil 1=médio 2=difícil. */
fun generateMathQuestion(difficulty: Int): Pair<String, Int> {
    val rnd = Random.Default
    return when (difficulty) {
        0 -> {
            val a = rnd.nextInt(5, 25)
            val b = rnd.nextInt(1, 15)
            if (rnd.nextBoolean()) "$a + $b" to a + b else "$a - $b" to a - b
        }
        1 -> {
            val a = rnd.nextInt(12, 95)
            val b = rnd.nextInt(2, 9)
            "$a × $b" to a * b
        }
        else -> {
            val a = rnd.nextInt(10, 60)
            val b = rnd.nextInt(4, 9)
            val c = rnd.nextInt(4, 9)
            "$a + $b × $c" to a + b * c
        }
    }
}