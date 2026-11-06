import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { checkOutputPath } from '../src/profile/checkOutputPath.ts'

describe('checkOutputPath', () => {
  it('testCheckOutputPath_ValidPath', () => {
    expect(checkOutputPath('profile.example', 'build/index.json')).toEqual([])
  })

  it('testCheckOutputPath_SiblingFolderWithSamePrefix', () => {
    // "profile.example-copy" starts with the same letters but is a different folder.
    expect(checkOutputPath('profile.example', 'profile.example-copy/index.json')).toEqual([])
  })

  it('testCheckOutputPath_NotJson', () => {
    expect(checkOutputPath('profile.example', 'build/index')).toEqual([
      '--out must be a .json file (got "build/index")',
    ])
  })

  it('testCheckOutputPath_Empty', () => {
    expect(checkOutputPath('profile.example', '')).toContain('--out must be a .json file (got "")')
  })

  it('testCheckOutputPath_InsideProfile', () => {
    expect(checkOutputPath('profile.example', 'profile.example/stories/index.json')).toEqual([
      '--out must be outside the profile folder (got "profile.example/stories/index.json" inside "profile.example")',
    ])
  })

  it('testCheckOutputPath_InsideProfileAbsolutePath', () => {
    const profileDir = path.resolve('profile.example')

    expect(checkOutputPath(profileDir, path.join(profileDir, 'index.json'))).toHaveLength(1)
  })

  it('testCheckOutputPath_ReportsBothProblems', () => {
    expect(checkOutputPath('profile.example', 'profile.example/notes.md')).toEqual([
      '--out must be a .json file (got "profile.example/notes.md")',
      '--out must be outside the profile folder (got "profile.example/notes.md" inside "profile.example")',
    ])
  })
})
