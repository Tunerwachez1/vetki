package dev.vetki.core

data class CanvasCard(
    val id: String,
    val parentId: String?,
    val title: String,
    val body: String,
    val x: Float,
    val y: Float,
    val status: Int,
    val done: Boolean,
    val due: Long?,
    val kind: String = "text",
    val width: Float = 190f,
    val height: Float = 112f,
    val groupId: String? = null,
    val uri: String? = null,
    val connections: List<CardConnection> = emptyList(),
)

/** Independent arrows do not change the folder/tree hierarchy. */
data class CardConnection(val targetId: String, val side: String)
