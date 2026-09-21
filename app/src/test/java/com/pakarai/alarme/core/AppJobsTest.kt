package com.pakarai.alarme.core

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AppJobsTest {

    @Test
    fun `launch executa a tarefa com o tag como nome da corrotina`() = runBlocking {
        var rodou = false
        var nomeDaCorrotina = ""

        AppJobs.launch("startup") {
            rodou = true
            nomeDaCorrotina = currentCoroutineContext()[CoroutineName]?.name ?: ""
        }.join()

        assertEquals(true, rodou)
        assertEquals("startup", nomeDaCorrotina)
    }
}