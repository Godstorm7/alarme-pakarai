package com.pakarai.alarme.core

import com.pakarai.alarme.data.AlarmEntity

private val SHAKE_PRESETS = listOf(5, 10, 15, 20, 30)
private val STEP_PRESETS = listOf(10, 20, 30, 50, 100)
private val SPIN_PRESETS = listOf(45, 90, 180, 360)

/**
 * Sobe a dificuldade de todos os desafios do alarme um degrau — usado pelo
 * "nudge" ("tá fácil demais? sobe o nível"). Puro e testável.
 */
fun bumpDifficulty(alarm: AlarmEntity): AlarmEntity = alarm.copy(
    mathDifficulty = (alarm.mathDifficulty + 1).coerceAtMost(2),
    memoryPairs = (alarm.memoryPairs + 1).coerceAtMost(8),
    memoryDifficulty = (alarm.memoryDifficulty + 1).coerceAtMost(7),
    shakeCount = nextPreset(alarm.shakeCount, SHAKE_PRESETS),
    stepCount = nextPreset(alarm.stepCount, STEP_PRESETS),
    spinCount = nextPreset(alarm.spinCount, SPIN_PRESETS),
)

private fun nextPreset(current: Int, presets: List<Int>): Int =
    presets.firstOrNull { it > current } ?: presets.last()
