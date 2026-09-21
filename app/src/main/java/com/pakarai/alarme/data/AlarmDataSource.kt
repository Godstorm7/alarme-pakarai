package com.pakarai.alarme.data

/**
 * Leitura de alarmes que o fluxo lógico precisa. O AlarmFlowCoordinator depende
 * SÓ desta porta — Room fica escondida no AlarmRepository pra o fluxo rodar em JVM.
 */
interface AlarmDataSource {
    suspend fun getAll(): List<AlarmEntity>
    suspend fun getById(id: Long): AlarmEntity?
}