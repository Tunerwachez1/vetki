package dev.vetki.data

import androidx.room.withTransaction
import org.json.*

object Backup {
    suspend fun export(db: VetkiDatabase, attachments: AttachmentStore? = null): String =
        db.withTransaction {
            val dao = db.dao()
            JSONObject()
                .put("format", "vetki")
                .put("version", 2)
                .put("createdAt", System.currentTimeMillis())
                .put(
                    "notes",
                    JSONArray().apply {
                        dao.allNotes().forEach { n ->
                            put(
                                JSONObject()
                                    .put("id", n.id)
                                    .put("parentId", n.parentId)
                                    .put("title", n.title)
                                    .put("body", n.body)
                                    .put("folder", n.folder)
                                    .put("updated", n.updated)
                            )
                        }
                    },
                )
                .put(
                    "boards",
                    JSONArray().apply {
                        dao.allBoards().forEach { b ->
                            put(
                                JSONObject()
                                    .put("id", b.id)
                                    .put("title", b.title)
                                    .put("template", b.template)
                                    .put("updated", b.updated)
                                    .put("snapGrid", b.snapGrid)
                                    .put("snapObjects", b.snapObjects)
                                    .put("readOnly", b.readOnly)
                            )
                        }
                    },
                )
                .put("cards", CardJson.array(dao.allCards()))
                .put(
                    "templates",
                    JSONArray().apply {
                        dao.allTemplates().forEach { t ->
                            put(
                                JSONObject()
                                    .put("id", t.id)
                                    .put("title", t.title)
                                    .put("payload", t.payload)
                                    .put("created", t.created)
                            )
                        }
                    },
                )
                .put(
                    "assets",
                    JSONArray().apply {
                        if (attachments != null) {
                            val uris =
                                (dao.allCards() +
                                        dao.allTemplates().flatMap { CardJson.list(it.payload) })
                                    .mapNotNull { it.uri }
                                    .filter { it.startsWith("attachment://") }
                                    .toSet()
                            var total = 0L
                            uris.forEach { uri ->
                                val f = attachments.file(uri)
                                require(f.exists()) { "Вложение не найдено: $uri" }
                                total += f.length()
                                require(total <= 20 * 1024 * 1024) {
                                    "Для одной копии доступно до 20 МБ вложений"
                                }
                                put(
                                    JSONObject()
                                        .put("uri", uri)
                                        .put(
                                            "data",
                                            android.util.Base64.encodeToString(
                                                f.readBytes(),
                                                android.util.Base64.NO_WRAP,
                                            ),
                                        )
                                )
                            }
                        }
                    },
                )
                .put(
                    "reminders",
                    JSONArray().apply {
                        dao.allReminders().forEach { r ->
                            put(
                                JSONObject()
                                    .put("id", r.id)
                                    .put("title", r.title)
                                    .put("cardId", r.cardId)
                                    .put("boardId", r.boardId)
                                    .put("at", r.at)
                                    .put("alarm", r.alarm)
                                    .put("nag", r.nag)
                                    .put("repeat", r.repeat)
                                    .put("done", r.done)
                                    .put("delivered", r.delivered)
                                    .put("nextAt", r.nextAt)
                            )
                        }
                    },
                )
                .toString(2)
        }

    suspend fun restore(db: VetkiDatabase, text: String, attachments: AttachmentStore? = null) {
        require(text.length < 40_000_000) { "Копия слишком большая" }
        val root = JSONObject(text)
        require(root.getString("format") == "vetki" && root.getInt("version") in 1..2) {
            "Неподдерживаемый формат копии"
        }
        fun objects(name: String): List<JSONObject> =
            (root.optJSONArray(name) ?: JSONArray()).let { array ->
                (0 until array.length()).map { array.getJSONObject(it) }
            }
        fun JSONObject.nullable(key: String) =
            if (isNull(key) || !has(key)) null else getString(key)
        val notes =
            objects("notes").map {
                Note(
                    it.getString("id"),
                    it.nullable("parentId"),
                    it.getString("title"),
                    it.getString("body"),
                    it.getBoolean("folder"),
                    it.getLong("updated"),
                )
            }
        val boards =
            objects("boards").map {
                Board(
                    it.getString("id"),
                    it.getString("title"),
                    it.getString("template"),
                    it.getLong("updated"),
                    it.optBoolean("snapGrid"),
                    it.optBoolean("snapObjects"),
                    it.optBoolean("readOnly"),
                )
            }
        val cards = objects("cards").map(CardJson::decode)
        val templates =
            objects("templates").map {
                BoardTemplate(
                    it.getString("id"),
                    it.getString("title"),
                    it.getString("payload"),
                    it.getLong("created"),
                )
            }
        val reminders =
            objects("reminders").map {
                Reminder(
                    it.getString("id"),
                    it.getString("title"),
                    it.nullable("cardId"),
                    it.nullable("boardId"),
                    it.getLong("at"),
                    it.getBoolean("alarm"),
                    it.getBoolean("nag"),
                    it.getString("repeat"),
                    it.getBoolean("done"),
                    it.getBoolean("delivered"),
                    if (it.isNull("nextAt") || !it.has("nextAt")) null else it.getLong("nextAt"),
                )
            }
        val byNote = notes.associateBy { it.id }
        val byCard = cards.associateBy { it.id }
        val boardIds = boards.map { it.id }.toSet()
        require(
            byNote.size == notes.size && byCard.size == cards.size && boardIds.size == boards.size
        ) {
            "Повторяющиеся идентификаторы"
        }
        notes.forEach { note ->
            val seen = mutableSetOf<String>()
            var n: Note? = note
            while (n != null) {
                require(seen.add(n.id)) { "Цикл в дереве папок" }
                val parent = n.parentId
                n = parent?.let { requireNotNull(byNote[it]) { "Папка не найдена" } }
                require(n == null || n.folder) { "Некорректная папка" }
            }
        }
        cards.forEach { c ->
            require(
                c.boardId in boardIds &&
                    c.x.isFinite() &&
                    c.y.isFinite() &&
                    c.width.isFinite() &&
                    c.height.isFinite() &&
                    c.width in 100f..5000f &&
                    c.height in 80f..5000f
            ) {
                "Некорректная карточка"
            }
            CardJson.connections(c.connections).forEach { link ->
                require(
                    link.targetId != c.id &&
                        byCard[link.targetId]?.let {
                            it.boardId == c.boardId && it.kind != "group"
                        } == true
                ) {
                    "Некорректная связь карточек"
                }
            }
            require(c.kind in setOf("text", "note", "image", "file", "link", "group"))
            require(
                c.groupId == null ||
                    byCard[c.groupId]?.let {
                        it.kind == "group" && it.boardId == c.boardId && it.id != c.id
                    } == true
            )
            val seen = mutableSetOf<String>()
            var n: Card? = c
            while (n != null) {
                require(seen.add(n.id)) { "Цикл в дереве карточек" }
                val parent = n.parentId
                n = parent?.let { requireNotNull(byCard[it]) }
                require(n == null || n.boardId == c.boardId)
            }
        }
        reminders.forEach {
            require(it.cardId == null || it.cardId in byCard)
            require(it.boardId == null || it.boardId in boardIds)
        }
        templates.forEach { t ->
            require(
                CardJson.list(t.payload).all {
                    it.x.isFinite() &&
                        it.y.isFinite() &&
                        it.width.isFinite() &&
                        it.height.isFinite()
                }
            )
        }
        val decodedAssets =
            objects("assets").map { asset ->
                require(attachments != null) { "Для восстановления вложений требуется хранилище" }
                val target = attachments.file(asset.getString("uri"))
                val data =
                    android.util.Base64.decode(asset.getString("data"), android.util.Base64.DEFAULT)
                require(data.size <= AttachmentStore.MAX_FILE) { "Вложение слишком большое" }
                require(!target.exists() || target.readBytes().contentEquals(data)) {
                    "Вложение с таким именем уже существует"
                }
                target to data
            }
        require(decodedAssets.sumOf { it.second.size.toLong() } <= 20 * 1024 * 1024)
        val created = mutableListOf<java.io.File>()
        try {
            decodedAssets.forEach { (file, data) ->
                if (!file.exists()) {
                    created += file
                    file.writeBytes(data)
                }
            }
            db.withTransaction {
                val dao = db.dao()
                boards.forEach { dao.save(it) }
                notes.forEach { dao.save(it) }
                cards.forEach { dao.save(it) }
                templates.forEach { dao.save(it) }
                reminders.forEach { dao.save(it) }
            }
        } catch (e: Exception) {
            created.forEach { it.delete() }
            throw e
        }
    }
}
