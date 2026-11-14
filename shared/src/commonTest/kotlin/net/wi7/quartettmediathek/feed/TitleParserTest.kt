package net.wi7.quartettmediathek.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import net.wi7.quartettmediathek.model.Variant

class TitleParserTest {
    @Test
    fun parsesSeasonAndEpisode() {
        val p = TitleParser.parse("Die Anstalt (S2026/E145)")
        assertEquals(ParsedTitle("Die Anstalt", Variant.STANDARD, 2026, 145), p)
        val q = TitleParser.parse("Krank Berlin (S01/E08) - Folge 8")
        assertEquals("Krank Berlin - Folge 8", q.title)
        assertEquals(1, q.season)
        assertEquals(8, q.episode)
    }

    @Test
    fun detectsVariantSuffixes() {
        val cases = mapOf(
            "Game of Drones (Audiodeskription)" to Variant.AUDIO_DESCRIPTION,
            "Game of Drones (mit Audiodeskription)" to Variant.AUDIO_DESCRIPTION,
            "Game of Drones - Hörfassung" to Variant.AUDIO_DESCRIPTION,
            "Game of Drones (Hörfassung)" to Variant.AUDIO_DESCRIPTION,
            "Game of Drones (mit Gebärdensprache)" to Variant.SIGN_LANGUAGE,
            "Game of Drones (Gebärdensprache)" to Variant.SIGN_LANGUAGE,
            "Game of Drones (DGS)" to Variant.SIGN_LANGUAGE,
            "Game of Drones (Originalversion)" to Variant.ORIGINAL_VERSION,
            "Game of Drones (OV)" to Variant.ORIGINAL_VERSION,
            "Game of Drones (OmU)" to Variant.ORIGINAL_VERSION,
            "Game of Drones (klare Sprache)" to Variant.ORIGINAL_VERSION,
            "Game of Drones (AUDIODESKRIPTION)" to Variant.AUDIO_DESCRIPTION,
        )
        for ((raw, variant) in cases) {
            val p = TitleParser.parse(raw)
            assertEquals("Game of Drones", p.title, raw)
            assertEquals(variant, p.variant, raw)
        }
    }

    @Test
    fun combinesMarkersAndLeavesOtherTitlesAlone() {
        val p = TitleParser.parse("Tatort: Borowski (S2026/E3) (Audiodeskription)")
        assertEquals("Tatort: Borowski", p.title)
        assertEquals(Variant.AUDIO_DESCRIPTION, p.variant)
        assertEquals(TitleParser.parse("Ein Film (2020)").title, "Ein Film (2020)")
        assertEquals("Dovjenko", TitleParser.parse("Dovjenko").title)
    }

    @Test
    fun cleansShowNames() {
        assertEquals("Die Friesenjunkies", TitleParser.cleanShowName("Die Friesenjunkies | Impro-Dramedy-Serie"))
        assertEquals("Krank Berlin", TitleParser.cleanShowName("Krank Berlin · Serie"))
        assertEquals("Die Anstalt", TitleParser.cleanShowName("Die Anstalt (S2026/E145)"))
        assertEquals("nano", TitleParser.cleanShowName("nano"))
    }

    @Test
    fun normalizesUmlautsAndCase() {
        assertEquals(normalizeText("Käse  Ärger"), normalizeText("kaese aerger"))
        assertEquals("strasse", normalizeText("Straße"))
    }

    @Test
    fun languageVersionsAreVariants() {
        // Real MediathekViewWeb titles (ZDFneo, "Die Ringe der Macht").
        val en = TitleParser.parse("Schatten und Flammen (S02/E08) (Englisch)")
        assertEquals("Schatten und Flammen", en.title)
        assertEquals(Variant.ORIGINAL_VERSION, en.variant)
        assertEquals(2, en.season)
        assertEquals(8, en.episode)
        assertEquals(Variant.AUDIO_DESCRIPTION, TitleParser.parse("Schatten und Flammen (S02/E08) (Audiodeskription)").variant)
        assertEquals(Variant.STANDARD, TitleParser.parse("Schatten und Flammen (S02/E08)").variant)
        assertEquals(Variant.ORIGINAL_VERSION, TitleParser.parse("Le Mépris (Originalfassung)").variant)
        assertEquals(Variant.ORIGINAL_VERSION, TitleParser.parse("Film (englische Originalfassung)").variant)
    }
}
