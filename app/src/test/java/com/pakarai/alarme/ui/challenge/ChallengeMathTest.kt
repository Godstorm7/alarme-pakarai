package com.pakarai.alarme.ui.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Os desafios de matemática precisam estar CERTOS sempre — um resultado errado
 * prende o usuário num alarme que não para. Aqui a conta é RECONFERIDA a partir
 * do próprio texto exibido, cobrindo os 4 ramos de cada um dos 3 níveis.
 */
class ChallengeMathTest {

    private val sum = Regex("^(\\d+) \\+ (\\d+)$")
    private val sub = Regex("^(\\d+) − (\\d+)$")
    private val mul = Regex("^(\\d+) × (\\d+)$")
    private val div = Regex("^(\\d+) ÷ (\\d+)$")

    /** Resolve a conta a partir do texto; falha se o formato for desconhecido. */
    private fun solve(text: String): Int = when {
        sum.matches(text) -> sum.matchEntire(text)!!.let {
            it.groupValues[1].toInt() + it.groupValues[2].toInt()
        }
        sub.matches(text) -> sub.matchEntire(text)!!.let {
            it.groupValues[1].toInt() - it.groupValues[2].toInt()
        }
        mul.matches(text) -> mul.matchEntire(text)!!.let {
            it.groupValues[1].toInt() * it.groupValues[2].toInt()
        }
        div.matches(text) -> div.matchEntire(text)!!.let {
            val dividend = it.groupValues[1].toInt()
            val divisor = it.groupValues[2].toInt()
            assertTrue("$text não é divisão exata", dividend % divisor == 0)
            dividend / divisor
        }
        else -> error("Formato inesperado: $text")
    }

    private fun operandos(text: String): Pair<Int, Int> {
        val m = listOf(sum, sub, mul, div).first { it.matches(text) }.matchEntire(text)!!
        return m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }

    /** Roda o nível muitas vezes conferindo resposta e coletando os formatos vistos. */
    private fun check(level: Int, rounds: Int = 900): Set<String> {
        val seen = mutableSetOf<String>()
        repeat(rounds) {
            val (text, answer) = generateMathQuestion(level)
            seen += when {
                sum.matches(text) -> "+"
                sub.matches(text) -> "−"
                mul.matches(text) -> "×"
                else -> "÷"
            }
            assertEquals("conta errada: $text", solve(text), answer)
            assertTrue("resultado negativo em: $text", answer >= 0)
            val (a, b) = operandos(text)
            assertTrue("operando negativo em: $text", a >= 0 && b >= 0)
        }
        return seen
    }

    @Test
    fun `facil tem as 4 operacoes com numeros de 1 digito`() {
        val seen = check(0)
        assertEquals("faltou ramo no fácil", setOf("+", "−", "×", "÷"), seen)
        repeat(900) {
            val (text, _) = generateMathQuestion(0)
            val (a, b) = operandos(text)
            // tudo de 1 casa: 1..9 (na divisão o dividendo também ≤ 9)
            assertTrue("número de 2 casas no fácil: $text", a in 0..9 && b in 0..9)
            if (sub.matches(text)) assertTrue("subtração negativa: $text", a >= b)
            if (div.matches(text)) assertTrue("dividendo > 9: $text", a <= 9)
        }
    }

    @Test
    fun `medio tem as 4 operacoes com no maximo um numero de 2 casas`() {
        val seen = check(1)
        assertEquals("faltou ramo no médio", setOf("+", "−", "×", "÷"), seen)
        repeat(900) {
            val (text, answer) = generateMathQuestion(1)
            val (a, b) = operandos(text)
            assertTrue("operação do médio estourou 2 casas: $text", a in 0..99 && b in 0..99)
            if (sub.matches(text)) assertTrue("subtração negativa: $text", a >= b)
            if (mul.matches(text)) {
                // no máximo UM número de 2 casas
                val dois = listOf(a, b).count { it >= 10 }
                assertTrue("dois números de 2 casas na multiplicação: $text", dois <= 1)
            }
            if (div.matches(text)) {
                val dois = listOf(a, b).count { it >= 10 }
                assertTrue("dois números de 2 casas na divisão: $text", dois <= 1)
                assertTrue("quociente não é inteiro: $text", answer * b == a)
            }
        }
    }

    @Test
    fun `dificil tem as 4 operacoes com ate 3 casas e sem parenteses`() {
        val seen = check(2)
        assertEquals("faltou ramo no difícil", setOf("+", "−", "×", "÷"), seen)
        repeat(900) {
            val (text, answer) = generateMathQuestion(2)
            assertTrue("não pode ter parênteses: $text", !text.contains("(") && !text.contains(")"))
            val (a, b) = operandos(text)
            assertTrue("operação do difícil estourou 3 casas: $text", a in 0..999 && b in 0..999)
            if (sub.matches(text)) assertTrue("subtração negativa: $text", a >= b)
            if (mul.matches(text)) {
                val tres = listOf(a, b).count { it >= 100 }
                assertTrue("dois números de 3 casas na multiplicação: $text", tres <= 1)
            }
            if (div.matches(text)) {
                val tres = listOf(a, b).count { it >= 100 }
                assertTrue("dois números de 3 casas na divisão: $text", tres <= 1)
                assertTrue("divisão com resto: $text", answer * b == a)
            }
        }
    }

    @Test
    fun `dificuldade fora do intervalo cai no dificil`() {
        val seen = check(9, rounds = 200)
        assertEquals(setOf("+", "−", "×", "÷"), seen)
    }
}
