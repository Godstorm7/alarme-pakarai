package com.pakarai.alarme.ui.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Os desafios de matemática precisam estar CERTOS sempre — um resultado errado
 * prende o usuário num alarme que não para. Valida a conta gerada em 3 níveis.
 */
class ChallengeMathTest {

    private fun parseSum(text: String): Pair<Int, Int> {
        val parts = text.split(" + ")
        assertEquals(2, parts.size)
        return parts[0].toInt() to parts[1].toInt()
    }

    @Test
    fun `facil gera soma correta com numeros pequenos`() {
        repeat(200) {
            val (text, answer) = generateMathQuestion(0)
            val (a, b) = parseSum(text)
            assertEquals(a + b, answer)
            assertTrue(a in 2..99 && b in 2..99)
        }
    }

    @Test
    fun `medio gera soma correta com numeros maiores`() {
        repeat(200) {
            val (text, answer) = generateMathQuestion(1)
            val (a, b) = parseSum(text)
            assertEquals(a + b, answer)
            assertTrue(a in 10..999 && b in 10..999)
        }
    }

    @Test
    fun `dificil nunca gera negativos e a conta bate`() {
        repeat(500) {
            val (text, answer) = generateMathQuestion(2)
            assertTrue("resultado deve ser positivo: $answer", answer > 0)
            val q = Regex("^\\((\\d+) \\+ (\\d+)\\) × (\\d+)$")
            val m = Regex("^(\\d+) \\+ \\((\\d+) × (\\d+)\\)$")
            val p = Regex("^\\((\\d+) × (\\d+)\\) \\+ (\\d+)$")
            val expected = when {
                q.matches(text) -> q.matchEntire(text)!!.let {
                    (it.groupValues[1].toInt() + it.groupValues[2].toInt()) * it.groupValues[3].toInt()
                }
                m.matches(text) -> m.matchEntire(text)!!.let { it.groupValues[1].toInt() + it.groupValues[2].toInt() * it.groupValues[3].toInt() }
                p.matches(text) -> p.matchEntire(text)!!.let { it.groupValues[1].toInt() * it.groupValues[2].toInt() + it.groupValues[3].toInt() }
                else -> error("Formato inesperado: $text")
            }
            assertEquals("$text", expected, answer)
        }
    }
}