package dev.vetki.core

data class TreeEntry(val id: String, val parentId: String?, val title: String, val folder: Boolean)

data class TreeRow(val entry: TreeEntry, val depth: Int)

fun visibleTree(
    entries: List<TreeEntry>,
    expanded: Set<String>,
    query: String = "",
): List<TreeRow> {
    val byId = entries.associateBy { it.id }
    val included: Set<String>? =
        if (query.isBlank()) null
        else
            buildSet<String> {
                entries
                    .filter { it.title.contains(query, ignoreCase = true) }
                    .forEach { entry ->
                        var current: TreeEntry? = entry
                        val seen = mutableSetOf<String>()
                        while (current != null && seen.add(current.id)) {
                            add(current.id)
                            current = byId[current.parentId]
                        }
                    }
            }
    val children = entries.groupBy { it.parentId }
    val result = mutableListOf<TreeRow>()
    val visited = mutableSetOf<String>()
    fun walk(parent: String?, depth: Int) {
        children[parent]
            .orEmpty()
            .sortedWith(compareBy<TreeEntry> { !it.folder }.thenBy { it.title.lowercase() })
            .forEach {
                if (visited.add(it.id) && (included == null || it.id in included)) {
                    result += TreeRow(it, depth)
                    if (it.folder && (it.id in expanded || included != null)) walk(it.id, depth + 1)
                }
            }
    }
    walk(null, 0)
    return result
}

fun descendants(entries: List<TreeEntry>, id: String): Set<String> {
    val children = entries.groupBy { it.parentId }
    val result = mutableSetOf<String>()
    fun visit(key: String) {
        if (result.add(key)) children[key].orEmpty().forEach { visit(it.id) }
    }
    visit(id)
    return result
}
