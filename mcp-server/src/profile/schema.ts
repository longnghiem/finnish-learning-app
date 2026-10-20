import { z } from 'zod'

/**
 * The kinds of profile entries:
 * - `about`: the one overview entry (location, languages, overall skills, gaps);
 * - `education`: a degree, certificate or substantial course;
 * - `experience`: a job;
 * - `faq`: the owner's own answer to a question recruiters often ask (e.g. why they are leaving);
 * - `project`: something built, including open source, talks and articles;
 * - `story`: a concrete anecdote that shows a skill (situation, task, action, result).
 */
export const ENTRY_TYPES = ['about', 'education', 'experience', 'faq', 'project', 'story'] as const
export const EntryTypeSchema = z.enum(ENTRY_TYPES)
export type EntryType = z.infer<typeof EntryTypeSchema>

/** The {@link EntryType}s that may have a `period`: the ones that cover a span of time. */
export const PERIOD_TYPES: readonly EntryType[] = ['education', 'experience']

/**
 * How well the owner knows a skill. The fit check relies on these to avoid overselling:
 * - `strong`: used a lot in real work, could lead others;
 * - `working`: used for real, productive without help;
 * - `familiar`: tried it or knows the basics.
 */
export const SKILL_LEVELS = ['strong', 'working', 'familiar'] as const
export const SkillLevelSchema = z.enum(SKILL_LEVELS)
export type SkillLevel = z.infer<typeof SkillLevelSchema>

/** Longest allowed `summary`. Summaries go into every prompt, so they must stay short. */
export const MAX_SUMMARY_LENGTH = 300

/**
 * Longest allowed body, in characters. The backend passes at most 8,000 characters of a `get_entry`
 * result to the model and drops the rest. The result is the whole entry as JSON (frontmatter fields,
 * quotes, escaped line breaks), so the body must stay well below that or its end is never read.
 */
export const MAX_BODY_LENGTH = 6000

/**
 * Longest allowed `id`. Same limit as the `get_entry(id)` tool input in TASK_05, so every
 * entry can actually be requested.
 */
export const MAX_ID_LENGTH = 100

/**
 * Allowed characters in an `id`: lower-case letters, digits, `-` and `/`. Same pattern as the
 * `get_entry(id)` tool input.
 */
export const ID_PATTERN = /^[a-z0-9/-]+$/

/**
 * Zod schema for an entry id: at most {@link MAX_ID_LENGTH} characters matching
 * {@link ID_PATTERN}. Used for the `id` field of {@link EntrySchema} and for the input of the MCP
 * tool `get_entry`, so both apply exactly the same rules.
 */
export const EntryIdSchema = z
  .string()
  .max(MAX_ID_LENGTH)
  .regex(ID_PATTERN, 'may only contain a-z, 0-9, "-" and "/"')

const nonBlankString = z.string().trim().min(1, 'must not be empty')

/** Zod schema for one item of `skills`: `{ name: Kotlin, level: strong }`. */
export const SkillSchema = z.strictObject({
  name: nonBlankString,
  level: SkillLevelSchema,
})
/** A skill with its proficiency level. */
export type Skill = z.infer<typeof SkillSchema>

/**
 * Zod schema for the YAML frontmatter of a profile entry.
 *
 * It checks what can be judged from one file alone. Rules that need the file path or the
 * other files (id = path, type = folder, unique ids, exactly one `about`, non-empty body) are
 * checked in `buildIndex.ts`.
 *
 * `strictObject` rejects unknown keys, so a typo such as `sumary:` fails the build instead of
 * being silently ignored.
 */
export const EntrySchema = z
  .strictObject({
    id: EntryIdSchema,
    type: EntryTypeSchema,
    title: nonBlankString,
    tags: z.array(nonBlankString).readonly(),
    summary: nonBlankString.max(MAX_SUMMARY_LENGTH),
    period: nonBlankString.optional(),
    skills: z.array(SkillSchema).readonly().optional(),
    notDone: z.array(nonBlankString).readonly().optional(),
  })
  .readonly()
  .refine((entry) => entry.period === undefined || PERIOD_TYPES.includes(entry.type), {
    message: `is only allowed on ${PERIOD_TYPES.join(' and ')} entries`,
    path: ['period'],
  })
export type EntryFrontmatter = z.infer<typeof EntrySchema>

/** One profile entry: its frontmatter plus the markdown body (the text after the frontmatter). */
export type Entry = EntryFrontmatter & { readonly body: string }

/** The build output written to `index.json` and bundled into the Lambda. */
export type ProfileIndex = {
  /** When the index was built, as an ISO 8601 timestamp (UTC). */
  readonly generatedAt: string
  /** All entries, sorted by `type`, then `id`. */
  readonly entries: readonly Entry[]
}
