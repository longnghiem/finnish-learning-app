package me.longng.finnish_learning_backend.redteam

import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

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
 */
data class ChatCase(
    val id: String,
    val why: String,
    val text: String,
    val expect: Expect,
    val forbidden: List<String> = emptyList(),
    val mustContainAny: List<String> = emptyList(),
    val profileBased: Boolean = false,
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

/** Reads the red-team cases from the test resources. */
object RedTeamCases {

    private val mapper = jacksonObjectMapper()

    fun chatCases(): List<ChatCase> = mapper.readValue(resource("redteam/chat-cases.json"))

    fun fitCases(): List<FitCase> = mapper.readValue(resource("redteam/fit-cases.json"))

    /** The job description of [case], from `redteam/jd/<id>.txt`. */
    fun jobDescription(case: FitCase): String = resource("redteam/jd/${case.id}.txt").trim()

    private fun resource(path: String): String =
        checkNotNull(RedTeamCases::class.java.classLoader.getResource(path)) { "Missing test resource $path" }
            .readText()
}