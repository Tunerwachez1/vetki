package dev.vetki.data

fun Card.asCanvas(due: Long? = null) =
    dev.vetki.core.CanvasCard(
        id,
        parentId,
        title,
        body,
        x,
        y,
        status,
        done,
        due,
        kind,
        width,
        height,
        groupId,
        uri,
        CardJson.connections(connections),
    )
