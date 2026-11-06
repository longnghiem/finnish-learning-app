import { readFile } from 'node:fs/promises'
import { z } from 'zod'
import type { buildIndex } from './profile/buildIndex.ts'
import { EntrySchema, MAX_BODY_LENGTH, type ProfileIndex } from './profile/schema.ts'

/**
 * Shape of `index.json` before each entry is checked against {@link EntrySchema}. `looseObject`
 * keeps all fields, so each entry can then be split into its frontmatter part and its body.
 */
const IndexFileSchema = z.object({
  generatedAt: z.string(),
  entries: z.array(z.looseObject({ body: z.string().min(1).max(MAX_BODY_LENGTH) })),
})

/**
 * Reads `index.json` (the output of {@link buildIndex}, written by `npm run build:index`) and checks
 * every entry against {@link EntrySchema}, the schema the build used. A wrong or damaged file
 * therefore fails loudly at start-up instead of producing odd answers later.
 *
 * @param indexPath - path or `file:` URL of the index file
 * @returns the validated {@link ProfileIndex}
 * @throws if the file can't be read, isn't JSON, or doesn't match the profile schema
 */
export async function loadIndex(indexPath: string | URL): Promise<ProfileIndex> {
  const file = IndexFileSchema.parse(JSON.parse(await readFile(indexPath, 'utf8')))
  return {
    generatedAt: file.generatedAt,
    entries: file.entries.map(({ body, ...frontmatter }) => ({
      ...EntrySchema.parse(frontmatter),
      body,
    })),
  }
}
