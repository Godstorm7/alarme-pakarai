package com.pakarai.alarme.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v14 -> v15 é a migração mais delicada do app: reconstrói a tabela INTEIRA
 * (o SQLite só faz DROP COLUMN a partir da 3.35) e é justamente a hora em que
 * o alarme da pessoa pode sumir se uma coluna ficar de fora do INSERT.
 *
 * O banco v14 é criado aqui na mão, com o `challengeRounds` legado, e então
 * aberto pelo Room com a migração — sem depender de schema exportado.
 */
@RunWith(AndroidJUnit4::class)
class Migration14To15Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    /** Colunas do alarms na v14 (igual ao que o app gravava antes da v15). */
    private val v14Columns = listOf(
        "id", "label", "hour", "minute", "enabled", "repeatDaysMask", "vibrate",
        "soundKind", "ringtoneUri", "spotifyUri", "spotifyLabel", "fallbackKind",
        "fallbackUri", "volumeInitial", "volumePeak", "rampMs", "rampCurve",
        "policeVolume", "snoozeLimit", "snoozeMinutes", "mathEnabled",
        "mathDifficulty", "challengeMode", "challengeModes", "challengeQrSecret",
        "objectRefPath", "objectRefLabel", "shakeCount", "stepCount",
        "challengeRounds", "spinCount", "memoryDifficulty", "memorySpeedMs",
        "memoryPairs", "missionTimeLimitSec", "muteLimit", "warmupMinutes",
        "extraLoud", "preventOff", "locked", "ackRequired", "ackSeconds",
        "ackChecks", "ackWindowSec", "screenPin"
    )

    private fun typeOf(col: String): String = when (col) {
        // o Room gerava `id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT` — sem o
        // NOT NULL o SQLite trata a coluna como anulável (rowid) e a validação
        // de schema acusa divergência que não existe em nenhum aparelho real
        "id" -> "INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT"
        "label", "soundKind", "ringtoneUri", "spotifyUri", "spotifyLabel",
        "fallbackKind", "fallbackUri", "rampCurve", "challengeMode",
        "challengeModes", "challengeQrSecret", "objectRefPath", "objectRefLabel",
        "challengeRounds" -> "TEXT NOT NULL"
        "volumeInitial", "volumePeak" -> "REAL NOT NULL"
        else -> "INTEGER NOT NULL"
    }

    @Before
    fun createLegacyDatabase() {
        context.deleteDatabase(dbName)
        // SQLiteOpenHelper do framework (e não o do Room): a gente só quer
        // gravar o banco v14 "como o app antigo gravava", sem passar pela
        // validação de schema do Room. Ele também deixa o user_version = 14,
        // que é o que o Room lê pra saber de onde migrar.
        val helper = object : SQLiteOpenHelper(context, dbName, null, 14) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE alarms (" +
                        v14Columns.joinToString(", ") { "$it ${typeOf(it)}" } +
                        ")"
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
        }
        val db = helper.writableDatabase
        db.execSQL(
            """
            INSERT INTO alarms (
                id, label, hour, minute, enabled, repeatDaysMask, vibrate, soundKind,
                ringtoneUri, spotifyUri, spotifyLabel, fallbackKind, fallbackUri,
                volumeInitial, volumePeak, rampMs, rampCurve, policeVolume,
                snoozeLimit, snoozeMinutes, mathEnabled, mathDifficulty, challengeMode,
                challengeModes, challengeQrSecret, objectRefPath, objectRefLabel,
                shakeCount, stepCount, challengeRounds, spinCount, memoryDifficulty,
                memorySpeedMs, memoryPairs, missionTimeLimitSec, muteLimit, warmupMinutes,
                extraLoud, preventOff, locked, ackRequired, ackSeconds, ackChecks,
                ackWindowSec, screenPin
            ) VALUES (
                7, 'Acordar de verdade', 6, 30, 1, 62, 1, 'builtin',
                '', '', '', 'builtin', '',
                0.15, 0.95, 6000, 'linear', 1,
                3, 9, 1, 2, 'taptap',
                'taptap', 'SEGREDO', '', 'garrafa',
                30, 50, '1,2,3', 90, 4, 900, 3, 120, 2, 5, 1, 1, 0, 1, 30, 2, 45, 1
            )
            """.trimIndent()
        )
        helper.close()
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(dbName)
    }

    private fun openMigrated(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_14_15)
            .build()

    private fun columnsOf(db: AppDatabase): List<String> = db.openHelper.readableDatabase.query(
        "PRAGMA table_info(alarms)"
    ).use { cursor ->
        buildList {
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) add(cursor.getString(nameIdx))
        }
    }

    @Test
    fun migratesWithoutLosingTheAlarm() = runBlocking {
        val db = openMigrated()
        val alarm = db.alarmDao().getById(7)
        assertEquals("Acordar de verdade", alarm?.label)
        assertEquals(6, alarm?.hour)
        assertEquals(30, alarm?.minute)
        assertEquals("taptap", alarm?.challengeModes)
        assertEquals("SEGREDO", alarm?.challengeQrSecret)
        // tudo que já existia continua igual
        assertEquals(3, alarm?.snoozeLimit)
        assertEquals(9, alarm?.snoozeMinutes)
        assertEquals(5, alarm?.warmupMinutes)
        assertEquals(30, alarm?.ackSeconds)
        assertTrue("screenPin antigo tem que sobreviver", alarm?.screenPin == true)
        db.close()
    }

    @Test
    fun newColumnsGetSaneDefaults() = runBlocking {
        val db = openMigrated()
        val alarm = db.alarmDao().getById(7)!!
        // taptap começa em 100 e aviso prévio desligado — um alarme antigo não
        // pode acordar com 0 toques exigidos nem com notificação surpresa
        assertEquals(100, alarm.tapCount)
        assertEquals(0, alarm.preAlertMinutes)
        db.close()
    }

    @Test
    fun legacyColumnIsGone() {
        val db = openMigrated()
        val columns = columnsOf(db)
        assertFalse("challengeRounds não pode sobreviver à v15", columns.contains("challengeRounds"))
        assertTrue(columns.contains("tapCount"))
        assertTrue(columns.contains("preAlertMinutes"))
        // e a tabela nova tem exatamente uma cópia (sem lixo alarms_v15)
        assertFalse(columns.contains("id_v15"))
        db.close()
    }
}
