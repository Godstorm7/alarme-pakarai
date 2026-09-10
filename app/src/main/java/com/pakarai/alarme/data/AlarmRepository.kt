package com.pakarai.alarme.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class AlarmRepository(private val dao: AlarmDao) {

    fun observeAll(): Flow<List<AlarmEntity>> = dao.observeAll()

    suspend fun getAll(): List<AlarmEntity> = dao.getAll()

    suspend fun getById(id: Long): AlarmEntity? = dao.getById(id)

    suspend fun upsert(alarm: AlarmEntity): Long = dao.upsert(alarm)

    suspend fun update(alarm: AlarmEntity) = dao.update(alarm)

    suspend fun delete(id: Long) {
        dao.getById(id)?.let { dao.delete(it) }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    companion object {
        fun create(context: Context): AlarmRepository =
            AlarmRepository(AppDatabase.get(context).alarmDao())
    }
}