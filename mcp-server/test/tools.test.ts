import { describe, expect, it } from 'vitest'
import { formatIndex, getEntry, listEntries } from '../src/tools.ts'
import { FAKE_INDEX } from './fakeIndex.ts'

describe('listEntries', () => {
  it('testListEntries_NoBodies', () => {
    const summaries = listEntries(FAKE_INDEX)

    expect(summaries.map((summary) => summary.id)).toEqual([
      'about',
      'experience/acme',
      'stories/fast-dashboard',
    ])
    expect(
      summaries.every(
        (summary) => Object.keys(summary).sort().join() === 'id,summary,tags,title,type',
      ),
    ).toBe(true)
  })

  it('testListEntries_FilterByType', () => {
    expect(listEntries(FAKE_INDEX, 'story').map((summary) => summary.id)).toEqual([
      'stories/fast-dashboard',
    ])
    expect(listEntries(FAKE_INDEX, 'project')).toEqual([])
  })
})

describe('getEntry', () => {
  it('testGetEntry_Found', () => {
    const result = getEntry(FAKE_INDEX, 'about')

    expect(result).toEqual({ ok: true, entry: FAKE_INDEX.entries[0] })
    expect(result.ok && result.entry.body).toBe('About body. Hyvää päivää!')
    expect(result.ok && result.entry.notDone).toEqual(['Kubernetes in production'])
  })

  it('testGetEntry_UnknownId', () => {
    expect(getEntry(FAKE_INDEX, 'experience/nope')).toEqual({
      ok: false,
      message: 'Unknown entry id "experience/nope". Call list_entries to see the available ids.',
    })
  })

  it('testGetEntry_InvalidId', () => {
    const tooLong = getEntry(FAKE_INDEX, 'a'.repeat(101))
    const badCharacters = getEntry(FAKE_INDEX, 'About Me!')

    expect(tooLong.ok).toBe(false)
    expect(badCharacters.ok).toBe(false)
    expect(!badCharacters.ok && badCharacters.message).toMatch(/^Invalid entry id/)
  })
})

describe('formatIndex', () => {
  it('testFormatIndex_OneLinePerEntry', () => {
    expect(formatIndex(FAKE_INDEX).split('\n')).toEqual([
      'about | about | About Test Person | kotlin | A fictional developer.',
      'experience/acme | experience | Backend developer, Acme | kotlin, kafka | Built a Kafka pipeline.',
      'stories/fast-dashboard | story | Fast dashboard | performance | Made the dashboard fast. Second line.',
    ])
  })

  it('testFormatIndex_PipeInValue', () => {
    const [about] = FAKE_INDEX.entries
    const index = {
      ...FAKE_INDEX,
      entries: about === undefined ? [] : [{ ...about, title: 'Kotlin | Spring' }],
    }

    expect(formatIndex(index)).toBe(
      'about | about | Kotlin / Spring | kotlin | A fictional developer.',
    )
  })
})