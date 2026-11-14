package net.wi7.quartettmediathek.tv.data.fake

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import net.wi7.quartettmediathek.model.Broadcaster
import net.wi7.quartettmediathek.model.DiscoverCategory
import net.wi7.quartettmediathek.model.DiscoverRow
import net.wi7.quartettmediathek.model.Episode
import net.wi7.quartettmediathek.model.EpisodeRef
import net.wi7.quartettmediathek.model.Show
import net.wi7.quartettmediathek.model.ShowRef
import net.wi7.quartettmediathek.model.SourceId
import net.wi7.quartettmediathek.model.Teaser
import net.wi7.quartettmediathek.model.Variant

/** Invented sample content mirroring docs/mockup.html. No images: tiles draw a placeholder gradient. */
object FakeData {
    /** Fixed so previews and screenshot tests stay reproducible (Saturday, 14 Nov 2026, 20:15 CET). */
    val now: Instant = Instant.parse("2026-11-14T19:15:00Z")

    private class Spec(
        val id: String,
        val title: String,
        val broadcaster: Broadcaster,
        val channel: String,
        val genre: String,
        val durationMinutes: Int,
        val everyDays: Int,
        val offsetDays: Int,
        val description: String,
        val episodes: List<String>,
    )

    private val specs = listOf(
        Spec("moorbauern", "Die Moorbauern", Broadcaster.ARD, "NDR", "Doku-Serie", 44, 7, 1,
            "Drei Familien bewirtschaften Höfe am Rand des Teufelsmoors. Ein Jahr zwischen Wiedervernässung, Torfgeschichte und Kranichzug.",
            listOf("Neue Pächter", "Torf und Tradition", "Der erste Frost", "Kraniche über dem Hof", "Wenn das Wasser zurückkommt")),
        Spec("hafenrevier", "Hafenrevier", Broadcaster.ARD, "NDR", "Krimiserie", 48, 7, 2,
            "Kommissarin Jara Wendt ermittelt zwischen Containerterminal und Fischmarkt. Jede Folge ein Fall, die Nachtschicht zieht sich durch die Staffel.",
            listOf("Ladung ohne Papiere", "Nebel über Pier 4", "Der stille Zeuge", "Schichtwechsel", "Letzte Fähre")),
        Spec("tiefsee", "Planet Tiefsee", Broadcaster.ZDF, "ZDF", "Dokumentation", 43, 14, 5,
            "Tauchroboter, Forschungsschiffe und Lebewesen, die nie Sonnenlicht sehen. Fünf Expeditionen in die Tiefsee.",
            listOf("Expedition Marianengraben", "Die Schlote des Pazifiks", "Riesen der Tiefe", "Leben ohne Licht", "Leuchten im Dunkeln")),
        Spec("abgruende", "Abgründe am See", Broadcaster.ZDF, "ZDFneo", "Krimiserie", 89, 7, 3,
            "Ein Dorf am Ammersee, ein verschwundener Bootsverleiher und eine Ermittlerin, die nicht zurückkommen wollte.",
            listOf("Das Bootshaus", "Eis auf dem Steg", "Die zweite Schwester", "Funkstille", "Unter der Oberfläche")),
        Spec("werkstatt", "Werkstatt Europa", Broadcaster.THREESAT, "3sat", "Reportage", 29, 7, 0,
            "Handwerk, das fast verschwunden ist: Menschen, die es weiterführen, und wie ihre Werkstätten heute wirtschaften.",
            listOf("Ziegel aus Friesland", "Messer aus Thiers", "Orgelbau im Erzgebirge", "Die letzte Seilerei", "Glasbläser in Murano")),
        Spec("fernweh", "Fernweh-Atlas", Broadcaster.ARTE, "arte", "Reisereportage", 26, 7, 4,
            "Eine Region, ein Tag, eine Karte. Reisereportagen über Orte abseits der bekannten Routen.",
            listOf("Inseln der Ägäis", "Patagonien im Herbst", "Entlang der Donau", "Georgiens Bergdörfer", "Die Färöer")),
        Spec("alpenlicht", "Alpenlicht", Broadcaster.THREESAT, "3sat", "Dokumentation", 52, 10, 6,
            "Ein Jahr in den Westalpen: Almwirtschaft, schmelzende Gletscher und alte Saumpfade.",
            listOf("Bergwald", "Wintervorrat", "Die Säumerpfade", "Gletscher im Wandel", "Sommer auf der Alm")),
        Spec("zweiufer", "Zwischen zwei Ufern", Broadcaster.ARTE, "arte", "Spielfilm", 104, 1, 2,
            "Eine Fährfrau an der Oder und ein Bauingenieur, der die Brücke bauen soll, die ihre Arbeit überflüssig macht.",
            listOf("Zwischen zwei Ufern")),
        Spec("kueche", "Stadt, Land, Küche", Broadcaster.ARD, "SWR", "Kochen", 29, 7, 3,
            "Regionale Küche aus dem Südwesten, gekocht mit den Leuten, die die Zutaten erzeugen.",
            listOf("Spätzle mit Linsen", "Wild aus dem Pfälzerwald", "Kürbis dreimal", "Maultaschen von Grund auf", "Zwiebelkuchen und Federweißer")),
        Spec("nachtzug", "Nachtzug nach Triest", Broadcaster.ZDF, "ZDF", "Thriller-Serie", 45, 7, 1,
            "Sechs Fahrgäste, ein Schlafwagen, eine Nacht von Wien an die Adria. Einer von ihnen kommt nicht an.",
            listOf("Ankunft", "Halt in Ljubljana", "Abteil 12", "Grenzkontrolle", "Abfahrt Wien")),
        Spec("sternwarte", "Die Sternwarte", Broadcaster.ARTE, "arte", "Wissenschaft", 27, 7, 5,
            "Astrophysik zum Mitdenken: jede Folge eine Frage, beantwortet an einem Observatorium in Europa.",
            listOf("Die dunkle Materie", "Schwarze Löcher hören", "Planeten anderer Sonnen", "Das Rauschen des Urknalls", "Wie schwer ist ein Stern?")),
        Spec("grenzland", "Grenzland", Broadcaster.ARD, "MDR", "Reportage", 30, 14, 8,
            "Geschichten von Menschen, die an der deutsch-polnischen und deutsch-tschechischen Grenze leben und arbeiten.",
            listOf("Die Brücke", "Markttag in Hof", "Doppelte Heimat", "Die Neiße im Sommer", "Pendler zwischen Görlitz und Zgorzelec")),
    )

    fun showRef(id: String) = ShowRef(SourceId.MVW, id)

    val shows: List<Show> = specs.map { s ->
        Show(
            ref = showRef(s.id), title = s.title, broadcaster = s.broadcaster, channelLabel = s.channel,
            genre = s.genre, description = s.description, imageUrl = null,
            episodeCount = s.episodes.size,
        )
    }

    /** Episodes per show key, newest first. */
    val episodesByShow: Map<String, List<Episode>> = specs.associate { s ->
        s.id to s.episodes.mapIndexed { i, title ->
            val n = s.episodes.size - i
            val published = now - (s.offsetDays.days + (i * s.everyDays).days + (if (i == 0 && s.offsetDays == 0) 3.hours else 0.hours))
            Episode(
                ref = EpisodeRef(SourceId.MVW, "${s.id}-$n"),
                showRef = showRef(s.id),
                showTitle = s.title,
                title = title,
                broadcaster = s.broadcaster,
                channelLabel = s.channel,
                description = "$title: ${s.description}",
                imageUrl = null,
                durationSeconds = s.durationMinutes * 60 + (n * 137) % 540 - 200,
                publishedAt = published,
                availableUntil = published + 180.days,
                episodeNumber = if (s.episodes.size > 1) n else null,
                hasSubtitles = n % 2 == 1,
                variants = if (n % 3 == 0) setOf(Variant.STANDARD, Variant.AUDIO_DESCRIPTION) else setOf(Variant.STANDARD),
                maxHeight = if (n % 2 == 0) 720 else 1080,
            )
        }
    }

    val allEpisodes: List<Episode> = episodesByShow.values.flatten()

    fun show(id: String): Show = shows.first { it.ref.key == id }
    fun episode(key: String): Episode = allEpisodes.first { it.ref.key == key }

    private fun s(id: String): Teaser = Teaser.OfShow(show(id))
    private fun e(key: String): Teaser = Teaser.OfEpisode(episode(key))

    val discoverRows: List<DiscoverRow> = listOf(
        DiscoverRow(DiscoverCategory.HIGHLIGHTS, listOf(s("hafenrevier"), e("nachtzug-5"), e("zweiufer-1"), e("werkstatt-5"), s("tiefsee"), e("sternwarte-5"), e("kueche-5"))),
        DiscoverRow(DiscoverCategory.SERIES, listOf(s("hafenrevier"), s("abgruende"), s("nachtzug"), s("moorbauern"))),
        DiscoverRow(DiscoverCategory.DOCUMENTARIES, listOf(s("tiefsee"), s("alpenlicht"), s("grenzland"), e("fernweh-5"), s("werkstatt"), e("moorbauern-5"))),
        DiscoverRow(DiscoverCategory.KNOWLEDGE_CULTURE, listOf(s("sternwarte"), e("werkstatt-4"), e("tiefsee-4"), s("fernweh"), s("kueche"))),
        DiscoverRow(DiscoverCategory.MOVIES, listOf(e("zweiufer-1"), e("abgruende-5"))),
    )

    /** Subscription id = show key. "Seen until" offsets (days before now) as in the mockup. */
    val initialSubscriptionSeenDaysAgo: Map<String, Int> = mapOf(
        "moorbauern" to 5, "hafenrevier" to 3, "abgruende" to 4, "werkstatt" to 2, "tiefsee" to 1, "nachtzug" to 30,
    )

    val initialBookmarks: List<Teaser> = listOf(e("fernweh-5"), s("alpenlicht"), e("zweiufer-1"), e("sternwarte-4"), s("grenzland"))

    /** Episode key to position in seconds. */
    val initialProgress: Map<String, Long> = mapOf("abgruende-3" to 2210L, "tiefsee-4" to 860L, "nachtzug-3" to 1530L)
    val initialWatched: Set<String> = setOf("hafenrevier-3", "moorbauern-3")
}
