import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { loadIndex } from '../src/loadIndex.ts'
import { buildIndex } from '../src/profile/buildIndex.ts'

const EXAMPLE_PROFILE_DIR = path.resolve(import.meta.dirname, '../profile.example')

describe('loadIndex', () => {
  let tempDir = ''

  beforeEach(async () => {
    tempDir = await mkdtemp(path.join(tmpdir(), 'load-index-test-'))
  })

  afterEach(async () => {
    await rm(tempDir, { recursive: true, force: true })
  })

  it('testLoadIndex_RoundTrip', async () => {
    const built = await buildIndex(EXAMPLE_PROFILE_DIR)
    const file = path.join(tempDir, 'index.json')
    await writeFile(file, JSON.stringify(built), 'utf8')

    expect(await loadIndex(file)).toEqual(built)
  })

  it('testLoadIndex_RejectsDamagedFile', async () => {
    const file = path.join(tempDir, 'index.json')
    await writeFile(
      file,
      JSON.stringify({ generatedAt: 'x', entries: [{ id: 'about', body: 'text' }] }),
      'utf8',
    )

    await expect(loadIndex(file)).rejects.toThrow()
  })
})