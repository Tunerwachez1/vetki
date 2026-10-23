package dev.vetki

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vetki.data.VetkiDatabase
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val db
        get() = VetkiDatabase.get(compose.activity)

    @Before
    fun before() {
        org.junit.Assume.assumeTrue(compose.activity.packageName == "dev.vetki.qa")
        runBlocking { db.clearAllTables() }
    }

    @After
    fun after() {
        if (compose.activity.packageName == "dev.vetki.qa") {
            runBlocking { db.clearAllTables() }
        }
    }

    @Test
    fun workCanvasContextMenuHelpReadOnlyAndOwnTemplate() {
        compose.onNodeWithContentDescription("Холсты").performClick()
        compose.onNodeWithContentDescription("Создать").performClick()
        compose.onNodeWithText("Работа").performScrollTo().performClick()
        compose.onNodeWithText("Создать холст").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { db.dao().allCards().size == 8 } }
        compose.onNodeWithContentDescription("Холст").performTouchInput { click(Offset(32f, 100f)) }
        compose.onNodeWithText("Добавить карточку").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Новая карточка").fetchSemanticsNodes().isNotEmpty()
        }
        compose
            .onNode(hasText("Новая карточка") and hasSetTextAction())
            .performTextReplacement("Идея")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.onNodeWithText("?").performClick()
        compose.onNodeWithText("Помощник мысли").assertIsDisplayed()
        compose.onNodeWithText("Вопросы").performClick()
        compose.onNodeWithText("Спроси конкретно").assertIsDisplayed()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("input keyevent KEYCODE_BACK")
            .close()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Меню").performClick()
        compose.onNodeWithText("Сохранить как шаблон").performClick()
        compose
            .onNode(hasText("Работа") and hasSetTextAction())
            .performTextReplacement("Мой процесс")
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(5000) { runBlocking { db.dao().allTemplates().size == 1 } }
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithContentDescription("Создать").performClick()
        compose.onNodeWithText("Свой шаблон").performScrollTo().performClick()
        compose.onNodeWithText("Мой процесс").performScrollTo().performClick()
        compose.onNodeWithText("Создать холст").performScrollTo().performClick()
        compose.waitUntil(5000) { runBlocking { db.dao().allBoards().size == 2 } }
        val boards = runBlocking { db.dao().allBoards() }
        val copy = boards.single { it.template.startsWith("saved:") }
        assertEquals(9, runBlocking { db.dao().allCards().count { it.boardId == copy.id } })
        compose.onNodeWithContentDescription("Холст").performTouchInput { click(Offset(32f, 100f)) }
        compose.onNodeWithText("Только просмотр").performClick()
        compose.waitUntil(5000) { runBlocking { db.dao().board(copy.id)?.readOnly == true } }
        compose.onNodeWithContentDescription("Добавить карточку").assertDoesNotExist()
    }
}
