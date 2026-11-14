package net.wi7.quartettmediathek.feed

private val WHITESPACE = Regex("\\s+")

/**
 * Normalises free text for comparisons: lower case, umlauts folded to their `ae/oe/ue/ss` spelling, common
 * accents removed, whitespace collapsed. "Die Anstalt", "die  anstalt" and "Die Änstalt"/"Die Aenstalt" compare equal.
 */
fun normalizeText(input: String): String {
    val sb = StringBuilder(input.length + 4)
    for (ch in input.lowercase()) {
        when (ch) {
            'ä' -> sb.append("ae")
            'ö' -> sb.append("oe")
            'ü' -> sb.append("ue")
            'ß' -> sb.append("ss")
            'é', 'è', 'ê', 'ë' -> sb.append('e')
            'á', 'à', 'â' -> sb.append('a')
            'í', 'ì', 'î' -> sb.append('i')
            'ó', 'ò', 'ô' -> sb.append('o')
            'ú', 'ù', 'û' -> sb.append('u')
            'ç' -> sb.append('c')
            'ñ' -> sb.append('n')
            else -> sb.append(ch)
        }
    }
    return sb.toString().trim().replace(WHITESPACE, " ")
}
