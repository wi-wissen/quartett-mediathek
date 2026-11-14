package net.wi7.quartettmediathek.source.mvw

/**
 * Parser for the website's selector syntax: `!channel #topic +title *description >min <min`, no selector =
 * topic+title. Commas inside a value and bare words are AND-combined (one clause per word). A repeated
 * selector is OR, which MVW cannot express in one request, so the result is one [MvwQuery] per combination.
 */
object MvwSelectorParser {
    private enum class Sel(val prefix: Char?, val fields: List<MvwField>) {
        CHANNEL('!', listOf(MvwField.CHANNEL)),
        TOPIC('#', listOf(MvwField.TOPIC)),
        TITLE('+', listOf(MvwField.TITLE)),
        DESCRIPTION('*', listOf(MvwField.DESCRIPTION)),
        BARE(null, listOf(MvwField.TOPIC, MvwField.TITLE)),
    }

    fun parse(input: String, size: Int = 50): List<MvwQuery> {
        val groups = Sel.entries.associateWith { mutableListOf<List<String>>() }
        val bare = mutableListOf<String>()
        var min: Int? = null
        var max: Int? = null
        for (token in input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val head = token[0]
            val body = token.drop(1)
            when {
                head == '>' || head == '<' -> {
                    val minutes = body.toIntOrNull() ?: continue
                    if (head == '>') min = maxOf(min ?: 0, minutes * 60) else max = minOf(max ?: Int.MAX_VALUE, minutes * 60)
                }
                Sel.entries.any { it.prefix == head } -> {
                    val sel = Sel.entries.first { it.prefix == head }
                    val words = words(body)
                    if (words.isNotEmpty()) groups.getValue(sel).add(words)
                }
                else -> bare += words(token)
            }
        }
        if (bare.isNotEmpty()) groups.getValue(Sel.BARE).add(bare)

        var combos: List<List<MvwClause>> = listOf(emptyList())
        for (sel in Sel.entries) {
            val alternatives = groups.getValue(sel)
            if (alternatives.isEmpty()) continue
            combos = combos.flatMap { prefix ->
                alternatives.map { words -> prefix + words.map { MvwClause(sel.fields, it) } }
            }
        }
        if (combos.all { it.isEmpty() }) return emptyList()
        return combos.map { MvwQuery(it, size = size, durationMinSeconds = min, durationMaxSeconds = max) }
    }

    private fun words(value: String) = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/** Reverse direction of [MvwSelectorParser]: renders queries as selector text, e.g. `!3sat #nano >20`. */
object MvwSelectorFormatter {
    fun format(queries: List<MvwQuery>): String {
        fun key(c: MvwClause) = when (c.fields) {
            listOf(MvwField.CHANNEL) -> '!'
            listOf(MvwField.TOPIC) -> '#'
            listOf(MvwField.TITLE) -> '+'
            listOf(MvwField.DESCRIPTION) -> '*'
            else -> ' '
        }
        val perSelector = linkedMapOf('!' to mutableListOf<List<String>>(), '#' to mutableListOf(), '+' to mutableListOf(), '*' to mutableListOf(), ' ' to mutableListOf())
        for (q in queries) {
            for (sel in perSelector.keys) {
                val words = q.clauses.filter { key(it) == sel }.map { it.query }
                if (words.isNotEmpty() && words !in perSelector.getValue(sel)) perSelector.getValue(sel).add(words)
            }
        }
        val parts = mutableListOf<String>()
        for ((sel, groups) in perSelector) {
            for (words in groups) {
                parts += if (sel == ' ') words.joinToString(" ") else sel + words.joinToString(",")
            }
        }
        queries.mapNotNull { it.durationMinSeconds }.maxOrNull()?.let { parts += ">${it / 60}" }
        queries.mapNotNull { it.durationMaxSeconds }.minOrNull()?.let { parts += "<${it / 60}" }
        return parts.joinToString(" ")
    }
}
