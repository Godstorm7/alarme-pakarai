package com.pakarai.alarme.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vigia anti-fuga sem acessibilidade. Estes testes existem por causa de um
 * bug real: o app tinha um AccessibilityService só pra reabrir o desafio, e o
 * Nubank passou a acusar o Pakarai de "app nocivo" e mandar remover.
 */
class GuardPolicyTest {

    private val own = "com.pakarai.alarme"

    @Test
    fun `esta no nosso app - nao reabre`() {
        assertFalse(GuardPolicy.shouldRelaunch(own, own, active = true, nowMs = 10_000, lastRelaunchMs = 0))
    }

    @Test
    fun `fugiu pro launcher - reabre`() {
        assertTrue(
            GuardPolicy.shouldRelaunch(
                "com.sec.android.app.launcher", own, active = true, nowMs = 10_000, lastRelaunchMs = 0
            )
        )
    }

    @Test
    fun `abriu o nubank - reabre`() {
        assertTrue(
            GuardPolicy.shouldRelaunch("com.nu.production", own, active = true, nowMs = 10_000, lastRelaunchMs = 0)
        )
    }

    @Test
    fun `systemui e lockscreen nao contam como fuga`() {
        assertFalse(
            GuardPolicy.shouldRelaunch(
                "com.android.systemui", own, active = true, nowMs = 10_000, lastRelaunchMs = 0
            )
        )
    }

    @Test
    fun `dialogo de permissao do proprio desafio nao conta como fuga`() {
        assertFalse(
            GuardPolicy.shouldRelaunch(
                "com.google.android.permissioncontroller", own, active = true, nowMs = 10_000, lastRelaunchMs = 0
            )
        )
    }

    @Test
    fun `sem evento recente nao da pra afirmar fuga`() {
        assertFalse(GuardPolicy.shouldRelaunch(null, own, active = true, nowMs = 10_000, lastRelaunchMs = 0))
        assertFalse(GuardPolicy.shouldRelaunch("", own, active = true, nowMs = 10_000, lastRelaunchMs = 0))
    }

    @Test
    fun `fora do toque e da checagem o vigia nao opina`() {
        assertFalse(
            GuardPolicy.shouldRelaunch(
                "com.sec.android.app.launcher", own, active = false, nowMs = 10_000, lastRelaunchMs = 0
            )
        )
    }

    @Test
    fun `respeita o cooldown pra nao virar loop de activities`() {
        assertFalse(
            GuardPolicy.shouldRelaunch(
                "com.sec.android.app.launcher", own, active = true, nowMs = 10_000, lastRelaunchMs = 9_500
            )
        )
        assertTrue(
            GuardPolicy.shouldRelaunch(
                "com.sec.android.app.launcher", own, active = true, nowMs = 12_000, lastRelaunchMs = 9_500
            )
        )
    }
}
