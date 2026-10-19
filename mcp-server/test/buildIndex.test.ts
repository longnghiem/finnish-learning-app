import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { buildIndex, ProfileValidationError } from '../src/profile/buildIndex.ts'
import { ENTRY_TYPES, SKILL_LEVELS } from '../src/profile/schema.ts'

const EXAMPLE_PROFILE_DIR = path.resolve(import.meta.dirname, '../profile.example')

const VALID_ABOUT = entryFile([
  'id: about',
  'type: about',
  'title: About Test',
  'tags: [kotlin]',
  'summary: A test person.',
])

/** Builds the text of an entry file from frontmatter lines and a body. */
function entryFile(frontmatterLines: readonly string[], body = 'Some body text.'): string {
  return ['---', ...frontmatterLines, '---', body, ''].join('\n')
}

/** Frontmatter lines for a minimal valid entry with the given id and type. */
function minimalFrontmatter(id: string, type: string): readonly string[] {
  return [
    `id: ${id}`,
    `type: ${type}`,
    `title: Title of ${id}`,
    'tags: [test]',
    `summary: Summary of ${id}.`,
  ]
}

/** Writes `files` (relative path → content) into `dir`, creating sub-folders as needed. */
async function writeProfile(dir: string, files: Readonly<Record<string, string>>): Promise<void> {
  await Promise.all(
    Object.entries(files).map(async ([relPath, content]) => {
      const filePath = path.join(dir, relPath)
      await mkdir(path.dirname(filePath), { recursive: true })
      await writeFile(filePath, content, 'utf8')
    }),
  )
}

/** Runs `buildIndex` and returns the validation error it throws; fails the test if it doesn't throw one. */
async function expectValidationError(dir: string): Promise<ProfileValidationError> {
  const error: unknown = await buildIndex(dir).then(
    () => undefined,
    (thrown: unknown) => thrown,
  )
  expect(error).toBeInstanceOf(ProfileValidationError)
  return error as ProfileValidationError
}

describe('buildIndex', () => {
  let tempDir = ''

  beforeEach(async () => {
    tempDir = await mkdtemp(path.join(tmpdir(), 'profile-test-'))
  })

  afterEach(async () => {
    await rm(tempDir, { recursive: true, force: true })
  })

  it('testBuildIndex_ExampleProfileValid', async () => {
    const index = await buildIndex(EXAMPLE_PROFILE_DIR)

    expect(index.entries.map((entry) => entry.id)).toEqual([
      'about',
      'experience/acme-backend',
      'experience/globex-fullstack',
      'projects/finnish-learning-app',
      'stories/kafka-read-model',
    ])
    expect(new Set(index.entries.map((entry) => entry.type))).toEqual(new Set(ENTRY_TYPES))
    const levels = new Set(
      index.entries.flatMap((entry) => entry.skills ?? []).map((skill) => skill.level),
    )
    expect(levels).toEqual(new Set(SKILL_LEVELS))
    expect(index.entries.find((entry) => entry.type === 'about')?.notDone?.length).toBeGreaterThan(
      0,
    )
    expect(index.entries.every((entry) => entry.body.length > 0)).toBe(true)
    expect(Number.isNaN(Date.parse(index.generatedAt))).toBe(false)
  })

  it('testBuildIndex_EmptyProfile', async () => {
    const error = await expectValidationError(tempDir)

    expect(error.problems).toEqual([
      `(profile): no .md files found in ${tempDir}`,
      '(profile): expected exactly one "about" entry (in about.md), found none',
    ])
  })

  it('testBuildIndex_DuplicateId', async () => {
    await writeProfile(tempDir, {
      'about.md': VALID_ABOUT,
      'projects/first.md': entryFile(minimalFrontmatter('projects/first', 'project')),
      // A copied file whose id was not updated.
      'projects/second.md': entryFile(minimalFrontmatter('projects/first', 'project')),
    })

    const error = await expectValidationError(tempDir)

    expect(error.problems).toContain(
      'projects/first.md, projects/second.md: duplicate id "projects/first"',
    )
  })

  it('testBuildIndex_TypeFolderMismatch', async () => {
    await writeProfile(tempDir, {
      'about.md': VALID_ABOUT,
      'projects/misfiled.md': entryFile(minimalFrontmatter('projects/misfiled', 'story')),
    })

    const error = await expectValidationError(tempDir)

    expect(error.problems).toEqual([
      'projects/misfiled.md: type "story" doesn\'t match its folder (expected "project")',
    ])
  })

  it('testBuildIndex_MissingAbout', async () => {
    await writeProfile(tempDir, {
      'projects/only.md': entryFile(minimalFrontmatter('projects/only', 'project')),
    })

    const error = await expectValidationError(tempDir)

    expect(error.problems).toEqual([
      '(profile): expected exactly one "about" entry (in about.md), found none',
    ])
  })

  it('testBuildIndex_ReportsAllErrors', async () => {
    await writeProfile(tempDir, {
      'about.md': VALID_ABOUT,
      'experience/wrong-id.md': entryFile(
        minimalFrontmatter('experience/other-name', 'experience'),
      ),
      'projects/long-summary.md': entryFile([
        'id: projects/long-summary',
        'type: project',
        'title: Long summary',
        'tags: []',
        `summary: ${'x'.repeat(301)}`,
      ]),
      'stories/empty-body.md': entryFile(minimalFrontmatter('stories/empty-body', 'story'), ''),
      'stories/no-frontmatter.md': 'Just text, no frontmatter.\n',
      'notes.md': entryFile(minimalFrontmatter('notes', 'story')),
    })

    const error = await expectValidationError(tempDir)

    const problemFiles = [
      'experience/wrong-id.md',
      'projects/long-summary.md',
      'stories/empty-body.md',
      'stories/no-frontmatter.md',
      'notes.md',
    ]
    expect(
      problemFiles.every((file) =>
        error.problems.some((problem) => problem.startsWith(`${file}:`)),
      ),
    ).toBe(true)
    expect(problemFiles.every((file) => error.message.includes(file))).toBe(true)
    expect(
      error.problems.some((problem) => problem.startsWith('projects/long-summary.md: summary:')),
    ).toBe(true)
  })
  it('testBuildIndex_YamlErrorFileLine', async () => {
    await writeProfile(tempDir, {
      // File line 1 is "---". The "[" on file line 4 is never closed; YAML notices on the next line (5).
      'about.md': entryFile([
        'id: about',
        'type: about',
        'title: [unclosed',
        'tags: []',
        'summary: S.',
      ]),
    })

    const error = await expectValidationError(tempDir)

    expect(error.problems).toHaveLength(1)
    expect(error.problems[0]).toMatch(
      /^about\.md: invalid YAML in frontmatter \(line \d+, column \d+\): /,
    )
    expect(error.problems[0]).toContain('(line 5, column 1)')
    expect(error.problems[0]).not.toMatch(/:$/)
  })

  it('testBuildIndex_InvalidAboutNoMissingAboutProblem', async () => {
    await writeProfile(tempDir, {
      'about.md': entryFile(['id: about', 'type: about', 'title: ""', 'tags: []', 'summary: S.']),
    })

    const error = await expectValidationError(tempDir)

    expect(error.problems).toEqual(['about.md: title: must not be empty'])
  })

  it('testBuildIndex_UpperCaseExtension', async () => {
    await writeProfile(tempDir, {
      'about.md': VALID_ABOUT,
      'projects/app.MD': entryFile(minimalFrontmatter('projects/app', 'project')),
    })

    const error = await expectValidationError(tempDir)

    expect(error.problems).toEqual(['projects/app.MD: use a lower-case ".md" file extension'])
  })

  it('testBuildIndex_SkipsHiddenFilesAndFolders', async () => {
    await writeProfile(tempDir, {
      'about.md': VALID_ABOUT,
      '.trash/old.md': 'Deleted note without frontmatter.\n',
      '.obsidian/notes.md': 'Editor settings.\n',
      'projects/.draft.md': 'Unfinished.\n',
    })

    const index = await buildIndex(tempDir)

    expect(index.entries.map((entry) => entry.id)).toEqual(['about'])
  })

  it('testBuildIndex_ByteOrderMark', async () => {
    await writeProfile(tempDir, { 'about.md': `\uFEFF${VALID_ABOUT}` })

    const index = await buildIndex(tempDir)

    expect(index.entries.map((entry) => entry.id)).toEqual(['about'])
  })
})