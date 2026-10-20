import { z } from 'zod'
import {
  type Entry,
  EntryIdSchema,
  type EntryType,
  EntryTypeSchema,
  MAX_ID_LENGTH,
  type ProfileIndex,
} from './profile/schema.ts'

/**
 * Input of the `list_entries` tool ({@link listEntries}): an optional {@link EntryType} to
 * filter by.
 */
export const ListEntriesInputSchema = z.strictObject({
  type: EntryTypeSchema.optional().describe('Only list entries of this type. Omit to list all.'),
})

/**
 * Input of the `get_entry` tool ({@link getEntry}): the id of the entry to read, see
 * {@link EntryIdSchema}.
 */
export const GetEntryInputSchema = z.strictObject({
  id: EntryIdSchema.describe(
    'Entry id from list_entries or the profile index, e.g. "experience/acme-backend".',
  ),
})

/**
 * What {@link listEntries} returns per entry: the short fields of an {@link Entry}, without `body`,
 * `period`, `skills` and `notDone`.
 */
export type EntrySummary = Pick<Entry, 'id' | 'type' | 'title' | 'tags' | 'summary'>

/** Result of {@link getEntry}: the entry, or a message the model can read and act on. */
export type GetEntryResult =
  { readonly ok: true; readonly entry: Entry } | { readonly ok: false; readonly message: string }

/**
 * Lists entries without their bodies, optionally only one type.
 *
 * @param index - the loaded {@link ProfileIndex}
 * @param type - only entries of this type; `undefined` lists all
 * @returns one {@link EntrySummary} per entry, in index order
 */
export function listEntries(index: ProfileIndex, type?: EntryType): readonly EntrySummary[] {
  return index.entries
    .filter((entry) => type === undefined || entry.type === type)
    .map(({ id, type: entryType, title, tags, summary }) => ({
      id,
      type: entryType,
      title,
      tags,
      summary,
    }))
}

/**
 * Finds one complete entry (including `body`, `skills` and `notDone`) by id.
 *
 * Never throws for bad input: an id that fails {@link EntryIdSchema}, or that no entry has, gives
 * `{ ok: false, message }`. The MCP server returns that to the model as a tool error result, so
 * the model can correct its call.
 *
 * @param index - the loaded {@link ProfileIndex}
 * @param id - the entry id, e.g. `experience/acme-backend`
 * @returns the entry or the error message, see {@link GetEntryResult}
 */
export function getEntry(index: ProfileIndex, id: string): GetEntryResult {
  if (!EntryIdSchema.safeParse(id).success) {
    return {
      ok: false,
      message: `Invalid entry id. Ids are at most ${MAX_ID_LENGTH} characters of a-z, 0-9, "-" and "/".`,
    }
  }
  const entry = index.entries.find((candidate) => candidate.id === id)
  return entry === undefined
    ? {
        ok: false,
        message: `Unknown entry id "${id}". Call list_entries to see the available ids.`,
      }
    : { ok: true, entry }
}

/**
 * The compact text index served as the `profile://index` resource: one line per entry,
 * `id | type | title | tags | summary`. Inside a value, line breaks become spaces (so every entry
 * stays on exactly one line) and `|` becomes `/` (so `|` only ever separates fields).
 *
 * @param index - the loaded {@link ProfileIndex}
 */
export function formatIndex(index: ProfileIndex): string {
  const oneLine = (text: string): string => text.replace(/\s+/g, ' ').replaceAll('|', '/').trim()
  return index.entries
    .map((entry) =>
      [entry.id, entry.type, entry.title, entry.tags.join(', '), entry.summary]
        .map(oneLine)
        .join(' | '),
    )
    .join('\n')
}
