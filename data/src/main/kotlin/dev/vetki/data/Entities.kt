package dev.vetki.data

import androidx.room.*
import java.util.UUID

@Entity(tableName = "notes", indices = [Index("parentId")])
data class Note(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val parentId: String? = null,
    val title: String,
    val body: String = "",
    val folder: Boolean = false,
    val updated: Long = System.currentTimeMillis(),
)

@Entity(tableName = "boards")
data class Board(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val template: String = "empty",
    val updated: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val snapGrid: Boolean = false,
    @ColumnInfo(defaultValue = "0") val snapObjects: Boolean = false,
    @ColumnInfo(defaultValue = "0") val readOnly: Boolean = false,
)

@Entity(
    tableName = "cards",
    foreignKeys =
        [
            ForeignKey(
                entity = Board::class,
                parentColumns = ["id"],
                childColumns = ["boardId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("boardId"), Index("parentId")],
)
data class Card(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val boardId: String,
    val parentId: String? = null,
    val title: String,
    val body: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val status: Int = 0,
    val done: Boolean = false,
    @ColumnInfo(defaultValue = "'text'") val kind: String = "text",
    val noteId: String? = null,
    val uri: String? = null,
    val mime: String? = null,
    val groupId: String? = null,
    @ColumnInfo(defaultValue = "190.0") val width: Float = 190f,
    @ColumnInfo(defaultValue = "112.0") val height: Float = 112f,
    @ColumnInfo(defaultValue = "'[]'") val connections: String = "[]",
)

@Entity(tableName = "reminders", indices = [Index("cardId"), Index("at")])
data class Reminder(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val cardId: String? = null,
    val boardId: String? = null,
    val at: Long,
    val alarm: Boolean = false,
    val nag: Boolean = false,
    val repeat: String = "none",
    val done: Boolean = false,
    val delivered: Boolean = false,
    val nextAt: Long? = null,
)

@Entity(tableName = "templates")
data class BoardTemplate(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val payload: String,
    val created: Long = System.currentTimeMillis(),
)
