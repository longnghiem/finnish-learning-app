import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { parse as parseYaml, YAMLParseError } from 'yaml'
import { type Entry, type EntryType, EntrySchema, type ProfileIndex } from './schema.ts'

/**
 * Thrown by {@link buildIndex} when the profile has problems. `message` lists every problem on
 * its own line; `problems` holds the same lines for programmatic use (tests).
 */
export class ProfileValidationError extends Error {
  /** One human-readable line per problem, each starting with the file it concerns. */
  readonly problems: readonly string[]

  constructor(problems: readonly string[]) {
    super(
      `Profile validation failed (${problems.length} problem${problems.length === 1 ? '' : 's'}):\n` +
        problems.map((problem) => `  - ${problem}`).join('\n'),
    )
    this.name = 'ProfileValidationError'
    this.problems = problems
  }
}

/** The only top-level file; it holds the single `about` entry. */
const ABOUT_FILE = 'about.md'

/** Sub-folder name → the `type` its entries must have. Folders are plural, types singular. */
const FOLDER_TYPES: Readonly<Record<string, EntryType>> = {
  experience: 'experience',
  projects: 'project',
  stories: 'story',
}

/** Label used in problems that concern the profile as a whole rather than one file. */
const PROFILE_LABEL = '(profile)'

/** The required file extension, always lower-case. */
const MARKDOWN_EXTENSION = '.md'

/** Invisible character some editors put at the start of a UTF-8 file. Stripped before parsing. */
const BYTE_ORDER_MARK = '\uFEFF'

/**
 * The frontmatter starts on line 2 of the file (line 1 is the opening `---`), so a YAML error on
 * frontmatter line N is on file line N + 1.
 */
const FRONTMATTER_LINE_OFFSET = 1

/**
 * The leading `---` block and everything after it.
 * Group 1 = frontmatter YAML (may be absent when the block is empty), group 2 = body.
 */
const FRONTMATTER_PATTERN = /^---[ \t]*\r?\n(?:([\s\S]*?)\r?\n)?---[ \t]*(?:\r?\n|$)([\s\S]*)$/

/** What one file produced: the entry (when its frontmatter is valid) and its problems. */
type FileResult = {
  readonly relPath: string
  readonly entry: Entry | undefined
  readonly problems: readonly string[]
}

/**
 * Reads every `*.md` file under `profileDir`, validates it, and returns the profile index.
 *
 * Layout: `about.md` at the root, plus `experience/*.md`, `projects/*.md` and `stories/*.md`.
 * Each file starts with a YAML frontmatter block (see `EntrySchema`) followed by a markdown body.
 *
 * @param profileDir - path to the profile folder (absolute, or relative to the working directory)
 * @returns all entries, sorted by `type` then `id`, with the build time
 * @throws {ProfileValidationError} listing every problem found, with file paths, if any file
 *   or cross-file rule is broken
 * @throws the underlying file-system error if `profileDir` can't be read
 */
export async function buildIndex(profileDir: string): Promise<ProfileIndex> {
  const relPaths = await listMarkdownFiles(profileDir)
  const results = await Promise.all(relPaths.map((relPath) => readEntryFile(profileDir, relPath)))

  const parsedEntries = results.flatMap((result) =>
    result.entry === undefined ? [] : [result.entry],
  )
  const problems = [
    ...(relPaths.length === 0 ? [`${PROFILE_LABEL}: no .md files found in ${profileDir}`] : []),
    ...results.flatMap((result) => result.problems),
    ...findDuplicateIds(results),
    ...checkSingleAbout(results),
  ]

  if (problems.length > 0) {
    throw new ProfileValidationError(problems)
  }

  return {
    generatedAt: new Date().toISOString(),
    entries: parsedEntries.toSorted(
      (a, b) => compareStrings(a.type, b.type) || compareStrings(a.id, b.id),
    ),
  }
}

/**
 * Every markdown file under `profileDir`, as sorted paths relative to it with `/` separators.
 * The extension is matched in any case (`.md`, `.MD`) so a wrongly cased file is reported, not
 * silently skipped. Hidden files and folders (name starting with `.`, e.g. `.git/`, `.obsidian/`,
 * `.trash/`) are skipped.
 */
async function listMarkdownFiles(profileDir: string): Promise<readonly string[]> {
  const names = await readdir(profileDir, { recursive: true })
  return names
    .map((name) => name.split(path.sep).join('/'))
    .filter((relPath) => relPath.toLowerCase().endsWith(MARKDOWN_EXTENSION))
    .filter((relPath) => relPath.split('/').every((segment) => !segment.startsWith('.')))
    .toSorted(compareStrings)
}

/** Reads, parses and checks one file. Never throws for content problems; it reports them. */
async function readEntryFile(profileDir: string, relPath: string): Promise<FileResult> {
  if (!relPath.endsWith(MARKDOWN_EXTENSION)) {
    return {
      relPath,
      entry: undefined,
      problems: [`${relPath}: use a lower-case "${MARKDOWN_EXTENSION}" file extension`],
    }
  }
  const rawText = await readFile(path.join(profileDir, relPath), 'utf8')
  const text = rawText.startsWith(BYTE_ORDER_MARK) ? rawText.slice(BYTE_ORDER_MARK.length) : rawText
  const locationProblems = checkLocation(relPath)

  const match = FRONTMATTER_PATTERN.exec(text)
  if (match === null) {
    return {
      relPath,
      entry: undefined,
      problems: [
        ...locationProblems,
        `${relPath}: missing frontmatter (the file must start with a "---" line, then YAML, then another "---" line)`,
      ],
    }
  }
  const frontmatterText = match[1] ?? ''
  const body = (match[2] ?? '').trim()

  const yamlResult = parseFrontmatterYaml(frontmatterText)
  if (!yamlResult.ok) {
    return {
      relPath,
      entry: undefined,
      problems: [...locationProblems, `${relPath}: ${yamlResult.message}`],
    }
  }

  const schemaResult = EntrySchema.safeParse(yamlResult.data)
  if (!schemaResult.success) {
    return {
      relPath,
      entry: undefined,
      problems: [
        ...locationProblems,
        ...schemaResult.error.issues.map(
          (issue) =>
            `${relPath}: ${issue.path.length > 0 ? issue.path.join('.') : 'frontmatter'}: ${issue.message}`,
        ),
      ],
    }
  }

  const frontmatter = schemaResult.data
  const entry: Entry = { ...frontmatter, body }
  const expectedId = relPath.slice(0, -MARKDOWN_EXTENSION.length)
  const expectedType = expectedTypeFor(relPath)

  return {
    relPath,
    entry,
    problems: [
      ...locationProblems,
      ...(frontmatter.id !== expectedId
        ? [
            `${relPath}: id "${frontmatter.id}" must equal the file path without .md ("${expectedId}")`,
          ]
        : []),
      ...(expectedType !== undefined && frontmatter.type !== expectedType
        ? [
            `${relPath}: type "${frontmatter.type}" doesn't match its folder (expected "${expectedType}")`,
          ]
        : []),
      ...(body.length === 0
        ? [`${relPath}: body is empty (write the entry text below the frontmatter)`]
        : []),
    ],
  }
}

/**
 * Parses YAML without throwing; a syntax error becomes a one-line message. The YAML library
 * counts lines from the start of the frontmatter, so its position is converted to the line
 * number you see in the editor.
 */
function parseFrontmatterYaml(
  text: string,
):
  | { readonly ok: true; readonly data: unknown }
  | { readonly ok: false; readonly message: string } {
  try {
    return { ok: true, data: parseYaml(text) }
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    // The first line reads "<reason> at line N, column M:"; the rest is a code excerpt.
    const reason = (message.split('\n')[0] ?? '').replace(/ at line \d+, column \d+:?$/, '')
    const position = error instanceof YAMLParseError ? error.linePos?.[0] : undefined
    const where =
      position === undefined
        ? ''
        : ` (line ${position.line + FRONTMATTER_LINE_OFFSET}, column ${position.col})`
    return { ok: false, message: `invalid YAML in frontmatter${where}: ${reason}` }
  }
}

/** The `type` a file must have, based on where it is; `undefined` if the location isn't allowed. */
function expectedTypeFor(relPath: string): EntryType | undefined {
  if (relPath === ABOUT_FILE) {
    return 'about'
  }
  const segments = relPath.split('/')
  const folder = segments[0]
  return segments.length === 2 && folder !== undefined && Object.hasOwn(FOLDER_TYPES, folder)
    ? FOLDER_TYPES[folder]
    : undefined
}

/** A problem if the file sits somewhere entries aren't allowed (e.g. `notes.md`, `projects/a/b.md`). */
function checkLocation(relPath: string): readonly string[] {
  return expectedTypeFor(relPath) === undefined
    ? [
        `${relPath}: not an allowed location (use ${ABOUT_FILE}, or one level inside ${Object.keys(
          FOLDER_TYPES,
        )
          .map((folder) => `${folder}/`)
          .join(', ')})`,
      ]
    : []
}

/**
 * Ids declared by more than one file. Usually the id = path rule already catches this, but a
 * copied file whose id wasn't updated also gets this clearer message naming both files.
 */
function findDuplicateIds(results: readonly FileResult[]): readonly string[] {
  const declared = results.flatMap((result) =>
    result.entry === undefined ? [] : [{ id: result.entry.id, relPath: result.relPath }],
  )
  const ids = [...new Set(declared.map((item) => item.id))]
  return ids
    .map((id) => ({
      id,
      relPaths: declared.filter((item) => item.id === id).map((item) => item.relPath),
    }))
    .filter(({ relPaths }) => relPaths.length > 1)
    .map(({ id, relPaths }) => `${relPaths.join(', ')}: duplicate id "${id}"`)
}

/**
 * A problem unless exactly one entry has `type: about`. An `about.md` whose frontmatter failed
 * validation still counts as present: its own problems are already reported, and adding
 * "found none" would be misleading.
 */
function checkSingleAbout(results: readonly FileResult[]): readonly string[] {
  const aboutFiles = results
    .filter(
      (result) =>
        result.entry?.type === 'about' ||
        (result.relPath === ABOUT_FILE && result.entry === undefined),
    )
    .map((result) => result.relPath)
  if (aboutFiles.length === 1) {
    return []
  }
  return aboutFiles.length === 0
    ? [`${PROFILE_LABEL}: expected exactly one "about" entry (in ${ABOUT_FILE}), found none`]
    : [
        `${PROFILE_LABEL}: expected exactly one "about" entry, found ${aboutFiles.length}: ${aboutFiles.join(', ')}`,
      ]
}

/** Plain code-point comparison, so the order is the same on every machine and locale. */
function compareStrings(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0
}
