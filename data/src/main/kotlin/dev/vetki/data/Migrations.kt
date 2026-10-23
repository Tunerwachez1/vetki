package dev.vetki.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE reminders ADD COLUMN nextAt INTEGER")
        }
    }

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE boards ADD COLUMN snapGrid INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE boards ADD COLUMN snapObjects INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE boards ADD COLUMN readOnly INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE cards ADD COLUMN kind TEXT NOT NULL DEFAULT 'text'")
            db.execSQL("ALTER TABLE cards ADD COLUMN noteId TEXT")
            db.execSQL("ALTER TABLE cards ADD COLUMN uri TEXT")
            db.execSQL("ALTER TABLE cards ADD COLUMN mime TEXT")
            db.execSQL("ALTER TABLE cards ADD COLUMN groupId TEXT")
            db.execSQL("ALTER TABLE cards ADD COLUMN width REAL NOT NULL DEFAULT 190.0")
            db.execSQL("ALTER TABLE cards ADD COLUMN height REAL NOT NULL DEFAULT 112.0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS templates (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, payload TEXT NOT NULL, created INTEGER NOT NULL)"
            )
        }
    }
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE cards ADD COLUMN connections TEXT NOT NULL DEFAULT '[]'")
        }
    }
