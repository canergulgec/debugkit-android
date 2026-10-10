package dev.debugkit.network.internal

/** One occurrence of the search text: the body line it is on and where it starts. */
internal data class SearchMatch(val line: Int, val start: Int)

/** Case-insensitive search over the body lines as displayed, i.e. after pretty-printing. */
internal object BodySearch {

    /** Every non-overlapping occurrence of [query], in reading order. Blank queries match nothing. */
    fun find(lines: List<String>, query: String): List<SearchMatch> {
        if (query.isBlank()) return emptyList()
        val matches = ArrayList<SearchMatch>()
        lines.forEachIndexed { lineIndex, line ->
            var from = 0
            while (true) {
                val start = line.indexOf(query, from, ignoreCase = true)
                if (start < 0) break
                matches += SearchMatch(lineIndex, start)
                from = start + query.length
            }
        }
        return matches
    }
}
