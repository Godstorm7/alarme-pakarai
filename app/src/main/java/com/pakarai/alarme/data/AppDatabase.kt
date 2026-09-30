package com.pakarai.alarme.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AlarmEntity::class], version = 15, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 -> v2: camadas do sistema de desafios (modo + rodadas + segredo do QR). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN challengeMode TEXT NOT NULL DEFAULT 'math'")
                db.execSQL("ALTER TABLE alarms ADD COLUMN challengeRounds INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE alarms ADD COLUMN challengeQrSecret TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v2 -> v3: modo OBJETO com foto cadastrada (reconhecimento offline). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN objectRefPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE alarms ADD COLUMN objectRefLabel TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v3 -> v4: fila ordenada de desafios (vários modos por alarme). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN challengeModes TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4 -> v5: cadeado por alarme + confirmação "AINDA ACORDADO?". */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarms ADD COLUMN ackRequired INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v5 -> v6: tempo de espera configurável do "AINDA ACORDADO?". */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN ackSeconds INTEGER NOT NULL DEFAULT 30")
            }
        }

        /** v6 -> v7: intensidade dos desafios de movimento (agitar/passos/girar). */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN shakeCount INTEGER NOT NULL DEFAULT 20")
                db.execSQL("ALTER TABLE alarms ADD COLUMN stepCount INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE alarms ADD COLUMN spinCount INTEGER NOT NULL DEFAULT 180")
            }
        }

        /** v7 -> v8: valores de movimento mais razoáveis (só recalibra os que estão no default velho) +
         * "AINDA ACORDADO?" vira INTERVALO recorrente (janela fixa de 30s; default 5 min = 300s). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // presets velhos (20/100/180) → novos (10/20/90); escolha manual não é tocada
                db.execSQL("UPDATE alarms SET shakeCount = 10 WHERE shakeCount = 20")
                db.execSQL("UPDATE alarms SET stepCount = 20 WHERE stepCount = 100")
                db.execSQL("UPDATE alarms SET spinCount = 90 WHERE spinCount = 180")
                // ackSeconds era "janela de 30s..10min"; agora é intervalo → passa pro default
                db.execSQL("UPDATE alarms SET ackSeconds = 300 WHERE ackRequired = 1")
            }
        }

        /** v8 -> v9: fonte de som Spotify (URI + rótulo) no editor. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN spotifyUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE alarms ADD COLUMN spotifyLabel TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v9 -> v10: som local memorizado pro caso do Spotify falhar. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN fallbackKind TEXT NOT NULL DEFAULT 'siren'")
                db.execSQL("ALTER TABLE alarms ADD COLUMN fallbackUri TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v10 -> v11: desafio "tiles" (memória estilo Alarmy) — nº de tiles e tempo de memorize. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN memoryDifficulty INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE alarms ADD COLUMN memorySpeedMs INTEGER NOT NULL DEFAULT 5000")
            }
        }

        /** v11 -> v12: desafio "memory" (pares) — quantos pares no tabuleiro (2..8). */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN memoryPairs INTEGER NOT NULL DEFAULT 3")
            }
        }

        /** v12 -> v13: anti-preguiça/confiabilidade — tempo limite, mute, warmup, extra loud, prevent off. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN missionTimeLimitSec INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarms ADD COLUMN muteLimit INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE alarms ADD COLUMN warmupMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarms ADD COLUMN extraLoud INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarms ADD COLUMN preventOff INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v13 -> v14: "AINDA ACORDADO?" configurável — quantas checagens e a janela de resposta. */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN ackChecks INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE alarms ADD COLUMN ackWindowSec INTEGER NOT NULL DEFAULT 60")
            }
        }

        /**
         * v14 -> v15: sai o legado `challengeRounds` (a repetição vive em [AlarmEntity.challengeModes]) e
         * entram `tapCount` (modo taptap) e `preAlertMinutes` (heads-up antes do alarme).
         * SQLite só faz DROP COLUMN a partir da 3.35, então a coluna é removida reconstruindo a tabela.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS alarms_v15 (
                        -- NOT NULL obrigatório: o SQLite trata `INTEGER PRIMARY KEY`
                        -- como rowid anulável, e o Room valida a tabela recriada contra
                        -- a entidade — sem ele, quem viesse do v14 quebra no 1º acesso
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        label TEXT NOT NULL,
                        hour INTEGER NOT NULL,
                        minute INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        repeatDaysMask INTEGER NOT NULL,
                        vibrate INTEGER NOT NULL,
                        soundKind TEXT NOT NULL,
                        ringtoneUri TEXT NOT NULL,
                        spotifyUri TEXT NOT NULL,
                        spotifyLabel TEXT NOT NULL,
                        fallbackKind TEXT NOT NULL,
                        fallbackUri TEXT NOT NULL,
                        volumeInitial REAL NOT NULL,
                        volumePeak REAL NOT NULL,
                        rampMs INTEGER NOT NULL,
                        rampCurve TEXT NOT NULL,
                        policeVolume INTEGER NOT NULL,
                        snoozeLimit INTEGER NOT NULL,
                        snoozeMinutes INTEGER NOT NULL,
                        mathEnabled INTEGER NOT NULL,
                        mathDifficulty INTEGER NOT NULL,
                        challengeMode TEXT NOT NULL,
                        challengeModes TEXT NOT NULL,
                        challengeQrSecret TEXT NOT NULL,
                        objectRefPath TEXT NOT NULL,
                        objectRefLabel TEXT NOT NULL,
                        shakeCount INTEGER NOT NULL,
                        stepCount INTEGER NOT NULL,
                        tapCount INTEGER NOT NULL DEFAULT 100,
                        spinCount INTEGER NOT NULL,
                        memoryDifficulty INTEGER NOT NULL,
                        memorySpeedMs INTEGER NOT NULL,
                        memoryPairs INTEGER NOT NULL,
                        missionTimeLimitSec INTEGER NOT NULL,
                        muteLimit INTEGER NOT NULL,
                        warmupMinutes INTEGER NOT NULL,
                        preAlertMinutes INTEGER NOT NULL DEFAULT 0,
                        extraLoud INTEGER NOT NULL,
                        preventOff INTEGER NOT NULL,
                        locked INTEGER NOT NULL,
                        ackRequired INTEGER NOT NULL,
                        ackSeconds INTEGER NOT NULL,
                        ackChecks INTEGER NOT NULL,
                        ackWindowSec INTEGER NOT NULL,
                        screenPin INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO alarms_v15 (
                        id, label, hour, minute, enabled, repeatDaysMask, vibrate, soundKind,
                        ringtoneUri, spotifyUri, spotifyLabel, fallbackKind, fallbackUri,
                        volumeInitial, volumePeak, rampMs, rampCurve, policeVolume,
                        snoozeLimit, snoozeMinutes, mathEnabled, mathDifficulty, challengeMode,
                        challengeModes, challengeQrSecret, objectRefPath, objectRefLabel,
                        shakeCount, stepCount, spinCount, memoryDifficulty, memorySpeedMs,
                        memoryPairs, missionTimeLimitSec, muteLimit, warmupMinutes,
                        extraLoud, preventOff, locked, ackRequired, ackSeconds, ackChecks,
                        ackWindowSec, screenPin
                    )
                    SELECT
                        id, label, hour, minute, enabled, repeatDaysMask, vibrate, soundKind,
                        ringtoneUri, spotifyUri, spotifyLabel, fallbackKind, fallbackUri,
                        volumeInitial, volumePeak, rampMs, rampCurve, policeVolume,
                        snoozeLimit, snoozeMinutes, mathEnabled, mathDifficulty, challengeMode,
                        challengeModes, challengeQrSecret, objectRefPath, objectRefLabel,
                        shakeCount, stepCount, spinCount, memoryDifficulty, memorySpeedMs,
                        memoryPairs, missionTimeLimitSec, muteLimit, warmupMinutes,
                        extraLoud, preventOff, locked, ackRequired, ackSeconds, ackChecks,
                        ackWindowSec, screenPin
                    FROM alarms
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE alarms")
                db.execSQL("ALTER TABLE alarms_v15 RENAME TO alarms")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pakarai.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                        MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15
                    )
                    .build().also { instance = it }
            }
    }
}