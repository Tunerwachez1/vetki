package dev.vetki.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VetkiDao {
    @Query("SELECT * FROM templates ORDER BY created DESC")
    fun templates(): Flow<List<BoardTemplate>>

    @Query("SELECT * FROM templates") suspend fun allTemplates(): List<BoardTemplate>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun template(id: String): BoardTemplate?

    @Query("SELECT * FROM boards WHERE id = :id") suspend fun board(id: String): Board?

    @Query("SELECT * FROM notes WHERE id = :id") suspend fun note(id: String): Note?

    @Upsert suspend fun save(template: BoardTemplate)

    @Query("DELETE FROM templates WHERE id = :id") suspend fun deleteTemplate(id: String)

    @Query("SELECT * FROM notes ORDER BY updated DESC") fun notes(): Flow<List<Note>>

    @Query("SELECT * FROM boards ORDER BY updated DESC") fun boards(): Flow<List<Board>>

    @Query("SELECT * FROM cards") fun cards(): Flow<List<Card>>

    @Query("SELECT * FROM reminders ORDER BY at") fun reminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM notes") suspend fun allNotes(): List<Note>

    @Query("SELECT * FROM boards") suspend fun allBoards(): List<Board>

    @Query("SELECT * FROM cards") suspend fun allCards(): List<Card>

    @Query("SELECT * FROM reminders") suspend fun allReminders(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun reminder(id: String): Reminder?

    @Query("SELECT * FROM cards WHERE id = :id") suspend fun card(id: String): Card?

    @Upsert suspend fun save(note: Note)

    @Upsert suspend fun save(board: Board)

    @Upsert suspend fun save(card: Card)

    @Upsert suspend fun save(reminder: Reminder)

    @Query("DELETE FROM notes WHERE id IN (:ids)") suspend fun deleteNotes(ids: List<String>)

    @Query("DELETE FROM cards WHERE id IN (:ids)") suspend fun deleteCards(ids: List<String>)

    @Query("DELETE FROM boards WHERE id = :id") suspend fun deleteBoard(id: String)

    @Query("DELETE FROM reminders WHERE id = :id") suspend fun deleteReminder(id: String)
}
