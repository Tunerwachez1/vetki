package dev.vetki.data

import android.content.Context
import androidx.room.*

@Database(
    entities = [Note::class, Board::class, Card::class, Reminder::class, BoardTemplate::class],
    version = 4,
    exportSchema = true,
)
abstract class VetkiDatabase : RoomDatabase() {
    abstract fun dao(): VetkiDao

    companion object {
        @Volatile private var instance: VetkiDatabase? = null

        fun get(context: Context): VetkiDatabase =
            instance
                ?: synchronized(this) {
                    instance
                        ?: Room.databaseBuilder(
                                context.applicationContext,
                                VetkiDatabase::class.java,
                                "vetki.db",
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            .build()
                            .also { instance = it }
                }
    }
}
