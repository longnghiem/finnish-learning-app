import { z } from 'zod'
import { askToolCallSchema, fitReportSchema } from './ask.ts'

/*
 * Zod schemas of the chatbot's admin API (`/api/admin/ask`, `ROLE_ADMIN` only). They mirror the
 * backend's `controller/dto/ask/AdminAskResponses.kt`; a change there needs the same change here.
 * The tool trace and the fit report reuse the visitor-side schemas: the backend stores and sends
 * exactly the JSON the visitor got.
 */

/** The kind of request a turn was: a chat message or a fit check. */
export const askModeSchema = z.enum(['CHAT', 'FIT_CHECK'])
export type AskMode = z.infer<typeof askModeSchema>

/** Where a turn stands. `PENDING` only while it is being answered (or after a restart cut it off). */
export const turnStatusSchema = z.enum(['PENDING', 'ANSWERED', 'BLOCKED', 'CAPPED', 'ERROR'])
export type TurnStatus = z.infer<typeof turnStatusSchema>

/** The input guardrail's result. `ERROR`: the check itself failed, so the turn was refused. */
export const guardrailOutcomeSchema = z.enum(['PASSED', 'BLOCKED', 'ERROR'])
export type GuardrailOutcome = z.infer<typeof guardrailOutcomeSchema>

/** Tokens of all the turn's model calls together. */
export const tokenUsageSchema = z.object({
  inputTokens: z.number().int(),
  outputTokens: z.number().int(),
  cacheReadTokens: z.number().int(),
  cacheWriteTokens: z.number().int(),
})
export type TokenUsage = z.infer<typeof tokenUsageSchema>

/** One stored turn with everything recorded about it. */
export const adminTurnSchema = z.object({
  id: z.number().int(),
  sessionId: z.string(),
  /** The first 8 characters of the session's IP hash; null if the session was just deleted. */
  ipHashPrefix: z.string().nullable(),
  mode: askModeSchema,
  status: turnStatusSchema,
  /** The chat message or the job description, as the visitor sent it. */
  userText: z.string(),
  /** The chat answer; null for fit checks and for turns without an answer. */
  answerText: z.string().nullable(),
  /** The fit report; null for chat turns and for fit checks without a report. */
  fitReport: fitReportSchema.nullable(),
  toolTrace: z.array(askToolCallSchema),
  /** Null while the turn is pending, before the check ran. */
  guardrailOutcome: guardrailOutcomeSchema.nullable(),
  /** Score per category, 0.0 to 1.0; null if the check didn't run or failed. */
  guardrailScores: z.record(z.string(), z.number()).nullable(),
  /** For a `BLOCKED` turn, the categories at or above today's thresholds; otherwise empty. */
  guardrailBlockedBy: z.array(z.string()),
  tokens: tokenUsageSchema,
  guardrailTextUnits: z.number().int(),
  /** What the turn cost in USD: the guardrail plus every model call. */
  costUsd: z.number(),
  /** Null while the turn is pending. */
  latencyMs: z.number().int().nullable(),
  /** ISO 8601 instant. */
  createdAt: z.string(),
})
export type AdminTurn = z.infer<typeof adminTurnSchema>

/** One page of turns, newest first. A page with fewer than `limit` turns is the last one. */
export const adminTurnPageSchema = z.object({
  turns: z.array(adminTurnSchema),
  limit: z.number().int(),
  offset: z.number().int(),
})
export type AdminTurnPage = z.infer<typeof adminTurnPageSchema>

/** One chat session with all its turns, oldest first. */
export const adminSessionSchema = z.object({
  id: z.string(),
  ipHashPrefix: z.string(),
  /** ISO 8601 instants. */
  createdAt: z.string(),
  expiresAt: z.string(),
  turns: z.array(adminTurnSchema),
})
export type AdminSession = z.infer<typeof adminSessionSchema>

/** The totals of one Europe/Helsinki day. Days without activity are missing. */
export const adminDailyStatsSchema = z.object({
  /** `YYYY-MM-DD`. */
  day: z.string(),
  answers: z.number().int(),
  fitChecks: z.number().int(),
  blocked: z.number().int(),
  spentUsd: z.number(),
  /** Money set aside by requests that were still running; normally 0. */
  reservedUsd: z.number(),
})
export type AdminDailyStats = z.infer<typeof adminDailyStatsSchema>
