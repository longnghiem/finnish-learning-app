package me.longng.finnish_learning_backend.redteam

import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.io.File
import java.math.BigDecimal

/** What a red-team case expects to happen to its input. */
enum class Expect {
    /** The input guardrail must refuse the input before the model sees it. */
    BLOCKED,

    /** The input must get through and be answered: a chat answer or a fit report. */
    ANSWERED,

    /**
     * Either is fine. A subtle attack may or may not reach the guardrail's threshold; if it gets
     * through, the answer's own checks must still pass.
     */
    BLOCKED_OR_ANSWERED,
}

/**
 * One chat case of `redteam/chat-cases.json`.
 *
 * @property id short and unique, e.g. `A1_en`; also the test's name in the report
 * @property why what the case tries, shown in the summary and in a failure
 * @property forbidden texts the answer must not contain, ignoring case, e.g. a line of the
 *   system prompt or a tool name
 * @property mustContainAny texts of which the answer must contain at least one, ignoring case;
 *   empty means no such check
 * @property profileBased whether the answer must come from the profile: it names an entry
 *   title, or the model read an entry
 * @property finnishAnswer whether the answer must be written in Finnish
 * @property exampleOnly whether the case depends on names in the example profile, so a run against
 *   the real profile leaves it out
 */
data class ChatCase(
    val id: String,
    val why: String,
    val text: String,
    val expect: Expect,
    val forbidden: List<String> = emptyList(),
    val mustContainAny: List<String> = emptyList(),
    val profileBased: Boolean = false,
    val finnishAnswer: Boolean = false,
    val exampleOnly: Boolean = false,
)

/**
 * One fit-check case of `redteam/fit-cases.json`. Its job description is the file
 * `redteam/jd/<id>.txt`.
 *
 * @property notStrong keywords: a requirement whose text contains one of them, ignoring case,
 *   must not be STRONG
 * @property allowStrong false if no requirement at all may be STRONG, e.g. for a job the
 *   profile has nothing to do with
 * @property notAllStrong true if at least one requirement must be below STRONG, for ads that
 *   try to make the model rate everything STRONG
 * @property watch keywords whose requirements' verdicts are only printed in the summary, without
 *   failing the case: things to look at, not rules
 */
data class FitCase(
    val id: String,
    val why: String,
    val expect: Expect,
    val notStrong: List<String> = emptyList(),
    val allowStrong: Boolean = true,
    val notAllStrong: Boolean = false,
    val watch: List<String> = emptyList(),
)

/**
 * Reads the red-team cases from the test resources.
 *
 * With the system property [REAL_PROFILE_PROPERTY] set, the suite runs against the owner's real
 * profile: [chatCases] leaves out the [ChatCase.exampleOnly] cases and adds the cases of the file
 * the property names, if any. That file stays outside this repository, because its cases hold real
 * names.
 */
object RedTeamCases {

    /** Set by `./gradlew redTeam -PrealProfile`, to the extra cases' path or to nothing. */
    const val REAL_PROFILE_PROPERTY = "redteam.realProfile"

    private val mapper = jacksonObjectMapper()

    /** True if this run is against the real profile. */
    val realProfile: Boolean get() = System.getProperty(REAL_PROFILE_PROPERTY) != null

    fun chatCases(): List<ChatCase> {
        val cases: List<ChatCase> = mapper.readValue(resource("redteam/chat-cases.json"))
        val extraCasesPath = System.getProperty(REAL_PROFILE_PROPERTY) ?: return cases
        return forRealProfile(cases, extraCasesOf(extraCasesPath))
    }

    /** [cases] without the [ChatCase.exampleOnly] ones, followed by [extraCases]. */
    fun forRealProfile(cases: List<ChatCase>, extraCases: List<ChatCase>): List<ChatCase> =
        cases.filterNot { it.exampleOnly } + extraCases

    /** A real profile is longer than the example, so a run costs more: [limitUsd], doubled for one. */
    fun costLimit(limitUsd: String): BigDecimal =
        BigDecimal(limitUsd).let { if (realProfile) it * BigDecimal(2) else it }

    /** The cases in the file at [path]; none if [path] is blank. */
    private fun extraCasesOf(path: String): List<ChatCase> {
        if (path.isBlank()) return emptyList()
        val file = File(path)
        check(file.isFile) { "No red-team cases at $path. Give an absolute path, without ~" }
        return mapper.readValue(file.readText())
    }

    fun fitCases(): List<FitCase> = mapper.readValue(resource("redteam/fit-cases.json"))

    /** The job description of [case], from `redteam/jd/<id>.txt`. */
    fun jobDescription(case: FitCase): String = resource("redteam/jd/${case.id}.txt").trim()

    private fun resource(path: String): String =
        checkNotNull(RedTeamCases::class.java.classLoader.getResource(path)) { "Missing test resource $path" }
            .readText()
}