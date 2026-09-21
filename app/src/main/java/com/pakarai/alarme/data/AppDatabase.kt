package com.pakarai.alarme.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AlarmEntity::class], version = 10, exportSchema = false)
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
                        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10
                    )
                    .build().also { instance = it }
            }
    }
}