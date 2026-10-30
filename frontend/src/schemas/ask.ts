import { z } from 'zod'

/*
 * Zod schemas of the recruiter chatbot's API (`/api/ask`). They mirror the backend's
 * `controller/dto/ask/AskSessionResponse.kt`, `AskStatsResponse.kt` and `AskSseEvents.kt`; a
 * change there needs the same change here. The TypeScript types are inferred from the schemas
 * (`z.infer`), so the two can't drift apart.
 */

/** Response of `POST /api/ask/session`, also what the browser keeps in `sessionStorage`. */
export const askSessionSchema = z.object({
  sessionId: z.string(),
  /** ISO 8601 instant, e.g. `2026-10-29T10:15:00Z`. */
  expiresAt: z.string(),
  /** Chat messages allowed in this session. */
  maxMessages: z.number().int(),
})
export type AskSession = z.infer<typeof askSessionSchema>

/** Response of `GET /api/ask/stats`: public totals since the chatbot started. */
export const askStatsSchema = z.object({
  answersTotal: z.number().int(),
  attacksBlockedTotal: z.number().int(),
  /** 0 to 100. */
  budgetUsedPercentThisMonth: z.number().int(),
})
export type AskStats = z.infer<typeof askStatsSchema>

/** The error body of a failed HTTP call; `code` only for the chatbot's own errors. */
export const askErrorBodySchema = z.object({
  message: z.string(),
  code: z.string().optional(),
})

/** One profile tool the model used, shown as "How I found this". */
export const askToolCallSchema = z.object({
  tool: z.string(),
  args: z.record(z.string(), z.unknown()),
  /** True if the tool answered with an error, e.g. an unknown entry id. */
  isError: z.boolean(),
})
export type AskToolCall = z.infer<typeof askToolCallSchema>

/**
 * A progress step. Only `TOOL_CALL` carries `tool` and `args`.
 * - `GUARDRAIL`: the input guardrail let the input through;
 * - `TOOL_CALL`: a profile tool runs, e.g. `get_entry` with `{ id: 'projects/x' }`;
 * - `COMPOSING`: the model is writing (in chat before every model call, in a fit check once);
 * - `VERIFYING`: fit check only, the report's quotes are being checked against the profile.
 */
export const askStepSchema = z.discriminatedUnion('kind', [
  z.object({ kind: z.literal('GUARDRAIL') }),
  z.object({
    kind: z.literal('TOOL_CALL'),
    tool: z.string(),
    args: z.record(z.string(), z.unknown()),
  }),
  z.object({ kind: z.literal('COMPOSING') }),
  z.object({ kind: z.literal('VERIFYING') }),
])
export type AskStep = z.infer<typeof askStepSchema>

/** The chat answer, plain text. */
export const askAnswerSchema = z.object({
  turnId: z.number().int(),
  text: z.string(),
  toolTrace: z.array(askToolCallSchema),
})
export type AskAnswer = z.infer<typeof askAnswerSchema>

/** How well the profile covers one requirement of a job description. */
export const fitVerdictSchema = z.enum(['STRONG', 'PARTIAL', 'GAP', 'NOT_IN_PROFILE'])
export type FitVerdict = z.infer<typeof fitVerdictSchema>

/** A quote from the profile, found word for word by the backend's check. */
export const fitEvidenceSchema = z.object({
  entryId: z.string(),
  quote: z.string(),
  /** The entry's title, e.g. "Backend developer, Acme Oy", shown under the quote. */
  entryTitle: z.string(),
})
export type FitEvidence = z.infer<typeof fitEvidenceSchema>

/** One requirement of the job description; `GAP` and `NOT_IN_PROFILE` rows have no evidence. */
export const fitRequirementSchema = z.object({
  requirement: z.string(),
  verdict: fitVerdictSchema,
  evidence: z.array(fitEvidenceSchema),
})
export type FitRequirement = z.infer<typeof fitRequirementSchema>

/**
 * A verified fit report. There is no score on purpose.
 * - `gaps` may name things that have no row in `requirements`: show it on its own;
 * - `unverifiedQuotesRemoved`: the model's quotes that weren't in the profile and were removed;
 * - `requirementsLowered`: rows that lost all their quotes. The backend has then already replaced
 *   the summary with one built from the checked rows, so it can be shown as it is.
 */
export const fitReportSchema = z.object({
  requirements: z.array(fitRequirementSchema),
  gaps: z.array(z.string()),
  summary: z.string(),
  unverifiedQuotesRemoved: z.number().int(),
  requirementsLowered: z.number().int(),
})
export type FitReport = z.infer<typeof fitReportSchema>

/** The fit check's result. */
export const askReportSchema = z.object({
  turnId: z.number().int(),
  report: fitReportSchema,
  toolTrace: z.array(askToolCallSchema),
})
export type AskReport = z.infer<typeof askReportSchema>

/** The error codes a stream's `error` event can carry. */
export const askStreamErrorCodeSchema = z.enum([
  'BLOCKED',
  'CAPPED',
  'CONVERSATION_LIMIT',
  'RATE_LIMITED',
  'UPSTREAM',
  'INTERNAL',
])

/** Why the stream ended without a result. `message` is written for the visitor. */
export const askStreamErrorSchema = z.object({
  code: askStreamErrorCodeSchema,
  message: z.string(),
  /** Which spending cap is full, only for `CAPPED`. */
  capScope: z.enum(['DAILY', 'MONTHLY']).optional(),
})
export type AskStreamError = z.infer<typeof askStreamErrorSchema>

/**
 * One event of a chat or fit-check stream: the SSE event name and its JSON data. A chat stream
 * sends `step`… then `answer` or `error`, then `done`; a fit-check stream sends `report` instead
 * of `answer`.
 */
export const askStreamEventSchema = z.discriminatedUnion('event', [
  z.object({ event: z.literal('step'), data: askStepSchema }),
  z.object({ event: z.literal('answer'), data: askAnswerSchema }),
  z.object({ event: z.literal('report'), data: askReportSchema }),
  z.object({ event: z.literal('error'), data: askStreamErrorSchema }),
  z.object({ event: z.literal('done'), data: z.object({}) }),
])
export type AskStreamEvent = z.infer<typeof askStreamEventSchema>

/*
 * The shapes below are the frontend's own: the chat as the page keeps it in `sessionStorage`, so a
 * reload shows the conversation again. They are checked when read back, like the stored session.
 */

/**
 * What became of a visitor's message: `sending` while the answer streams, then `answered`,
 * `blocked` by the guardrail, or `failed` for any other error. The backend counts `sending`,
 * `answered` and `blocked` messages toward the session's limit, `failed` ones not.
 */
export const chatMessageStatusSchema = z.enum(['sending', 'answered', 'blocked', 'failed'])
export type ChatMessageStatus = z.infer<typeof chatMessageStatusSchema>

/**
 * One message of the conversation, as the chat shows it. An assistant message carries the tools
 * the model used, for "How I found this".
 */
export const chatMessageSchema = z.discriminatedUnion('role', [
  z.object({
    id: z.string(),
    role: z.literal('user'),
    text: z.string(),
    status: chatMessageStatusSchema,
  }),
  z.object({
    id: z.string(),
    role: z.literal('assistant'),
    text: z.string(),
    turnId: z.number().int(),
    toolTrace: z.array(askToolCallSchema),
  }),
])
export type ChatMessage = z.infer<typeof chatMessageSchema>

/** The conversation kept in `sessionStorage`, with the session it belongs to. */
export const storedChatSchema = z.object({
  sessionId: z.string(),
  messages: z.array(chatMessageSchema),
})
