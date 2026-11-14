package net.wi7.quartettmediathek.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.wi7.quartettmediathek.source.mvw.MvwClause
import net.wi7.quartettmediathek.source.mvw.MvwField
import net.wi7.quartettmediathek.source.mvw.MvwQuery
import net.wi7.quartettmediathek.source.mvw.MvwSelectorFormatter
import net.wi7.quartettmediathek.source.mvw.MvwSelectorParser

class MvwSelectorTest {
    @Test
    fun parsesOrOverRepeatedSelectorAndAndOverCommas() {
        val queries = MvwSelectorParser.parse("!ard !ndr #sturm,der,liebe >20")
        assertEquals(2, queries.size)
        val first = queries[0]
        assertEquals(
            listOf(
                MvwClause(MvwField.CHANNEL, "ard"),
                MvwClause(MvwField.TOPIC, "sturm"),
                MvwClause(MvwField.TOPIC, "der"),
                MvwClause(MvwField.TOPIC, "liebe"),
            ),
            first.clauses,
        )
        assertEquals(MvwClause(MvwField.CHANNEL, "ndr"), queries[1].clauses.first())
        assertEquals(1200, first.durationMinSeconds)
        assertEquals(null, first.durationMaxSeconds)
    }

    @Test
    fun roundTrips() {
        for (text in listOf("!ard !ndr #sturm,der,liebe >20", "!3sat #nano >20", "+tatort *mord <90", "die anstalt", "#olympia,tokio")) {
            assertEquals(text, MvwSelectorFormatter.format(MvwSelectorParser.parse(text)), text)
        }
    }

    @Test
    fun bareWordsSearchTopicAndTitle() {
        val q = MvwSelectorParser.parse("anstalt").single()
        assertEquals(listOf(MvwField.TOPIC, MvwField.TITLE), q.clauses.single().fields)
    }

    @Test
    fun emptyInputGivesNoQueries() {
        assertTrue(MvwSelectorParser.parse("   ").isEmpty())
    }

    @Test
    fun repeatedTopicsMultiplyQueries() {
        assertEquals(4, MvwSelectorParser.parse("!ard !zdf #a #b").size)
    }

    @Test
    fun queryJsonMatchesWireFormat() {
        val json = MvwQuery(
            listOf(MvwClause(MvwField.TOPIC, "die anstalt"), MvwClause(MvwField.CHANNEL, "zdf")),
            durationMinSeconds = 1200,
        ).toJson()
        assertEquals(
            """{"queries":[{"fields":["topic"],"query":"die anstalt"},{"fields":["channel"],"query":"zdf"}],""" +
                """"sortBy":"timestamp","sortOrder":"desc","future":false,"offset":0,"size":50,"duration_min":1200}""",
            json,
        )
    }
}
