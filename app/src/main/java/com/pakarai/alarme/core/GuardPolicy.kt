package com.pakarai.alarme.core

/**
 * Decisão do vigia anti-fuga, isolada da plataforma pra ser testável em JVM.
 *
 * O vigia NÃO usa serviço de acessibilidade: bancos (Nubank, Inter, Wise…)
 * marcam como "app nocivo" qualquer app instalado com acessibilidade ligada,
 * e a pessoa perdia o banco por causa do alarme. Aqui a detecção vem do
 * "Acesso ao uso" (UsageStats): o AlarmService olha qual pacote está na frente
 * a cada ~700ms e reabre o desafio se não for o nosso.
 */
object GuardPolicy {

    /**
     * Pacotes que NÃO contam como fuga:
     * - `com.android.systemui` — lockscreen, shade, tela de desligar;
     * - `android` — diálogos do próprio sistema;
     * - permission controllers — diálogo de permissão de câmera/microfone que
     *   o próprio desafio abre (sem isso o vigia brigaria com o popup do
     *   sistema que ele mesmo provocou);
     * - entrada vazia/desconhecida — sem evento recente não dá pra afirmar fuga.
     */
    private val NOT_A_ESCAPE = setOf(
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.samsung.android.permissioncontroller",
        "com.android.packageinstaller",
    )

    /**
     * @param foreground pacote atualmente na frente (null = nenhum evento recente)
     * @param ownPackage o pacote do Pakarai
     * @param active o ciclo está em toque/checagem (só então vale reabrir)
     * @param nowMs relógio monotônico (uptimeMillis)
     * @param lastRelaunchMs quando foi o último relançamento (0 = nunca)
     * @param cooldownMs intervalo mínimo entre relançamentos (anti-loop)
     */
    fun shouldRelaunch(
        foreground: String?,
        ownPackage: String,
        active: Boolean,
        nowMs: Long,
        lastRelaunchMs: Long,
        cooldownMs: Long = 1_500L,
    ): Boolean {
        if (!active) return false
        if (foreground.isNullOrBlank()) return false
        if (foreground == ownPackage) return false
        if (foreground in NOT_A_ESCAPE) return false
        // lastRelaunchMs == 0 significa "nunca": o primeiro relançamento é livre
        if (lastRelaunchMs != 0L && nowMs - lastRelaunchMs < cooldownMs) return false
        return true
    }
}
