package com.pakarai.alarme.core

import com.pakarai.alarme.data.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class NudgeTest {

    @Test
    fun `sobe a dificuldade um degrau`() {
        val a = AlarmEntity(mathDifficulty = 0, memoryPairs = 3, memoryDifficulty = 4)
        val b = bumpDifficulty(a)
        assertEquals(1, b.mathDifficulty)
        assertEquals(4, b.memoryPairs)
        assertEquals(5, b.memoryDifficulty)
    }

    @Test
    fun `nao passa do teto`() {
        val a = AlarmEntity(mathDifficulty = 2, memoryPairs = 8, memoryDifficulty = 7)
        val b = bumpDifficulty(a)
        assertEquals(2, b.mathDifficulty)
        assertEquals(8, b.memoryPairs)
        assertEquals(7, b.memoryDifficulty)
    }

    @Test
    fun `contagens sobem pro proximo preset`() {
        val a = AlarmEntity(shakeCount = 10, stepCount = 20, spinCount = 90)
        val b = bumpDifficulty(a)
        assertEquals(15, b.shakeCount)
        assertEquals(30, b.stepCount)
        assertEquals(180, b.spinCount)
    }

    @Test
    fun `valores fora de preset caem no extremo mais proximo`() {
        val a = AlarmEntity(shakeCount = 999, stepCount = 0, spinCount = 1000)
        val b = bumpDifficulty(a)
        // acima do teto → fica no último preset; abaixo do piso → sobe pro primeiro
        assertEquals(30, b.shakeCount)
        assertEquals(10, b.stepCount)
        assertEquals(360, b.spinCount)
    }
}
