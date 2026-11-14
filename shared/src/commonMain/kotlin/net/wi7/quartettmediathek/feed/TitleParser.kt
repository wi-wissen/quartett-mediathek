package net.wi7.quartettmediathek.feed

import net.wi7.quartettmediathek.model.Variant

/** Result of [TitleParser.parse]: the displayed title without markers, plus what was found. */
data class ParsedTitle(
    val title: String,
    val variant: Variant = Variant.STANDARD,
    val season: Int? = null,
    val episode: Int? = null,
)

/** Pure parsing of the titles as delivered by MediathekViewWeb and the broadcasters. */
object TitleParser {
    private val seasonEpisode =
        Regex("""\(?(?<![\p{L}\p{N}])S(\d{1,4})\s*/\s*E(\d{1,4})(?![\p{L}\p{N}])\)?""", RegexOption.IGNORE_CASE)

    private fun bracket(alternatives: String) =
        Regex("""\(\s*(?:mit\s+)?(?:$alternatives)\s*\)""", RegexOption.IGNORE_CASE)

    private fun bare(alternatives: String) =
        Regex("""(?<![\p{L}\p{N}])(?:$alternatives)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

    /** Variant markers in priority order; the first variant found wins. */
    private val variantPatterns: List<Pair<Variant, List<Regex>>> = listOf(
        Variant.AUDIO_DESCRIPTION to listOf(
            bracket("audiodeskription|hörfassung"),
            bare("(?:mit\\s+)?(?:audiodeskription|hörfassung)"),
        ),
        Variant.SIGN_LANGUAGE to listOf(
            bracket("gebärdensprache|dgs"),
            bare("mit\\s+gebärdensprache"),
        ),
        // "klare Sprache" (plain language) has no variant of its own; it is treated as an alternative version.
        Variant.ORIGINAL_VERSION to listOf(
            // Language versions: "(Englisch)", "(Originalfassung)", "(OmU)", "(englische Originalfassung)" ...
            bracket(
                "originalversion|originalfassung|ov|omu|klare sprache|" +
                    "(?:englische|französische|italienische|spanische)?\\s*(?:originalfassung|fassung)|" +
                    "englisch|französisch|italienisch|spanisch|polnisch|türkisch|english|version originale|" +
                    "original mit untertitel|originalton",
            ),
        ),
    )

    private val leadingSeparators = Regex("""^[\s\-–—:|,·]+""")
    private val trailingSeparators = Regex("""[\s\-–—:|,·]+$""")
    private val emptyBrackets = Regex("""\(\s*\)""")
    private val spaces = Regex("""\s{2,}""")

    fun parse(raw: String): ParsedTitle {
        var text = raw
        var season: Int? = null
        var episode: Int? = null
        seasonEpisode.find(text)?.let {
            season = it.groupValues[1].toInt()
            episode = it.groupValues[2].toInt()
        }
        text = text.replace(seasonEpisode, " ")

        var variant = Variant.STANDARD
        for ((v, patterns) in variantPatterns) {
            for (pattern in patterns) {
                if (pattern.containsMatchIn(text)) {
                    if (variant == Variant.STANDARD) variant = v
                    text = text.replace(pattern, " ")
                }
            }
        }
        return ParsedTitle(tidy(text).ifEmpty { raw.trim() }, variant, season, episode)
    }

    /**
     * Show name usable as MediathekViewWeb topic query. "Die Friesenjunkies | Impro-Dramedy-Serie" becomes
     * "Die Friesenjunkies"; the part after `|` or `·` and season markers are removed.
     */
    fun cleanShowName(raw: String): String {
        val cut = raw.substringBefore('|').substringBefore('·')
        val cleaned = tidy(cut.replace(seasonEpisode, " "))
        return cleaned.ifEmpty { raw.trim() }
    }

    private fun tidy(text: String): String =
        text.replace(emptyBrackets, " ")
            .replace(spaces, " ")
            .trim()
            .replace(leadingSeparators, "")
            .replace(trailingSeparators, "")
            .trim()
}
