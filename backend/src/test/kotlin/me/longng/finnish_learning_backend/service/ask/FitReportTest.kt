package me.longng.finnish_learning_backend.service.ask

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class FitReportTest {

    private val input = mapOf(
        "requirements" to listOf(
            mapOf(
                "requirement" to "Kotlin",
                "verdict" to "STRONG",
                "evidence" to listOf(mapOf("entryId" to "experience/acme-backend", "quote" to "Built services in Kotlin")),
            ),
            mapOf("requirement" to "Kubernetes", "verdict" to "GAP", "evidence" to emptyList<Any>()),
        ),
        "gaps" to listOf("Kubernetes"),
        "summary" to "Good backend fit.",
    )

    @Test
    fun testFromToolInput_ReadsTheDraft() {
        val report = FitReport.fromToolInput(input)

        assertEquals(
            FitReport(
                requirements = listOf(
                    FitRequirement("Kotlin", FitVerdict.STRONG, listOf(FitEvidence("experience/acme-backend", "Built services in Kotlin"))),
                    FitRequirement("Kubernetes", FitVerdict.GAP, emptyList()),
                ),
                gaps = listOf("Kubernetes"),
                summary = "Good backend fit.",
                unverifiedQuotesRemoved = 0,
            ),
            report,
        )
    }

    @Test
    fun testFromToolInput_AppliesTheLimits() {
        fun row(name: String) = mapOf("requirement" to name, "verdict" to "GAP", "evidence" to emptyList<Any>())
        val many = (1..FitReport.MAX_REQUIREMENTS + 3).map { row("Skill $it") }

        val report = FitReport.fromToolInput(
            input + mapOf(
                "requirements" to listOf(row("   ")) + many,
                "gaps" to listOf(" Kubernetes ", "", "  ") + (1..FitReport.MAX_GAPS + 2).map { "Gap $it" },
            ),
        )

        assertEquals((1..FitReport.MAX_REQUIREMENTS).map { "Skill $it" }, report.requirements.map { it.requirement }, "blank row dropped, then the first 12")
        assertEquals(FitReport.MAX_GAPS, report.gaps.size)
        assertEquals("Kubernetes", report.gaps.first(), "trimmed; blank gaps dropped")
    }

    @Test
    fun testFromToolInput_WrongShapeFails() {
        assertThrows<IllegalArgumentException> { FitReport.fromToolInput(input - "summary") }
        assertThrows<IllegalArgumentException> { FitReport.fromToolInput(input + ("requirements" to "none")) }
        assertThrows<IllegalArgumentException> {
            FitReport.fromToolInput(input + ("requirements" to listOf(mapOf("requirement" to "X", "verdict" to "GREAT", "evidence" to emptyList<Any>()))))
        }
    }

    @Test
    fun testSchema_StrictRulesOnEveryObject() {
        // Strict tools need additionalProperties: false and every property required, on every object.
        fun objects(schema: Any?): List<Map<*, *>> = when (schema) {
            is Map<*, *> -> listOfNotNull(schema.takeIf { it["type"] == "object" }) + schema.values.flatMap { objects(it) }
            is List<*> -> schema.flatMap { objects(it) }
            else -> emptyList()
        }

        val found = objects(FitReportTool.SCHEMA)
        assertEquals(3, found.size)
        found.forEach { obj ->
            assertEquals(false, obj["additionalProperties"])
            assertEquals((obj["properties"] as Map<*, *>).keys, (obj["required"] as List<*>).toSet())
        }
    }
}