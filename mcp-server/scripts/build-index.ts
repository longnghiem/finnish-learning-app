/**
 * CLI: validate a profile folder and write it as `index.json`.
 *
 * Usage: `npm run build:index -- --profile-dir <path> --out <file>`
 *
 * Exit code 0 = written; 1 = validation failed, bad arguments (including an `--out` path that
 * isn't a `.json` file outside the profile folder), or the folder couldn't be read or written.
 */
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { parseArgs } from 'node:util'
import { buildIndex, ProfileValidationError } from '../src/profile/buildIndex.ts'
import { checkOutputPath } from '../src/profile/checkOutputPath.ts'

const USAGE = 'Usage: npm run build:index -- --profile-dir <path> --out <file>'

async function main(): Promise<number> {
  const args = readArgs()
  if (args === undefined) {
    console.error(USAGE)
    return 1
  }

  const outputProblems = checkOutputPath(args.profileDir, args.out)
  if (outputProblems.length > 0) {
    outputProblems.forEach((problem) => console.error(problem))
    return 1
  }

  try {
    const index = await buildIndex(args.profileDir)
    await mkdir(path.dirname(args.out), { recursive: true })
    await writeFile(args.out, `${JSON.stringify(index, null, 2)}\n`, 'utf8')
    console.log(`Wrote ${index.entries.length} entries to ${args.out}`)
    return 0
  } catch (error) {
    if (error instanceof ProfileValidationError) {
      console.error(error.message)
      return 1
    }
    console.error('Could not build the index:', error)
    return 1
  }
}

/** The two required flags, or `undefined` if either is missing or an unknown flag is given. */
function readArgs(): { readonly profileDir: string; readonly out: string } | undefined {
  try {
    const { values } = parseArgs({
      options: {
        'profile-dir': { type: 'string' },
        out: { type: 'string' },
      },
      strict: true,
    })
    const profileDir = values['profile-dir']
    const out = values.out
    return profileDir !== undefined && out !== undefined ? { profileDir, out } : undefined
  } catch {
    return undefined
  }
}

process.exitCode = await main()
