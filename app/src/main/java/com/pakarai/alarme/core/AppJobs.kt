package com.pakarai.alarme.core

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Jobs de aplicação nomeados — substituto do `CoroutineScope(Dispatchers.IO).launch`
 * cru espalhado no startup. SupervisorJob garante que falha num job não derruba
 * os outros; o mesmo dispatcher IO de sempre; o tag vira o nome da corrotina
 * (visível em debugging/tracing). Vive com o processo inteiro.
 */
object AppJobs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launch(tag: String, block: suspend CoroutineScope.() -> Unit): Job =
        scope.launch(CoroutineName(tag)) { block() }
}