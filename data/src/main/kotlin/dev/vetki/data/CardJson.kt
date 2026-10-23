package dev.vetki.data

import org.json.*

object CardJson {
    fun connections(payload: String): List<dev.vetki.core.CardConnection> =
        JSONArray(payload).let { array ->
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val side = o.getString("side")
                require(side in setOf("top", "bottom", "left", "right"))
                dev.vetki.core.CardConnection(o.getString("targetId"), side)
            }
        }

    fun connectionsJson(links: List<dev.vetki.core.CardConnection>): String =
        JSONArray()
            .apply {
                links.forEach {
                    put(JSONObject().put("targetId", it.targetId).put("side", it.side))
                }
            }
            .toString()

    fun encode(c: Card): JSONObject =
        JSONObject()
            .put("id", c.id)
            .put("boardId", c.boardId)
            .put("parentId", c.parentId)
            .put("title", c.title)
            .put("body", c.body)
            .put("x", c.x)
            .put("y", c.y)
            .put("status", c.status)
            .put("done", c.done)
            .put("kind", c.kind)
            .put("noteId", c.noteId)
            .put("uri", c.uri)
            .put("mime", c.mime)
            .put("connections", JSONArray(c.connections))
            .put("groupId", c.groupId)
            .put("width", c.width)
            .put("height", c.height)

    fun decode(o: JSONObject): Card {
        fun optional(key: String) = if (o.isNull(key) || !o.has(key)) null else o.getString(key)
        return Card(
            id = o.getString("id"),
            boardId = o.getString("boardId"),
            parentId = optional("parentId"),
            title = o.getString("title"),
            body = o.optString("body"),
            x = o.getDouble("x").toFloat(),
            y = o.getDouble("y").toFloat(),
            status = o.optInt("status"),
            done = o.optBoolean("done"),
            kind = o.optString("kind", "text"),
            noteId = optional("noteId"),
            uri = optional("uri"),
            mime = optional("mime"),
            groupId = optional("groupId"),
            width = o.optDouble("width", 190.0).toFloat(),
            height = o.optDouble("height", 112.0).toFloat(),
            connections =
                (o.optJSONArray("connections") ?: JSONArray()).toString().also { connections(it) },
        )
    }

    fun array(cards: List<Card>) = JSONArray().apply { cards.forEach { put(encode(it)) } }

    fun list(payload: String): List<Card> =
        JSONArray(payload).let { a -> (0 until a.length()).map { decode(a.getJSONObject(it)) } }
}
