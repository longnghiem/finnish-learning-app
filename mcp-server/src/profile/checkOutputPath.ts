import path from 'node:path'

/** The only extension the index file may have. */
const OUTPUT_EXTENSION = '.json'

/**
 * Checks the `--out` path of the index build before anything is built or written.
 *
 * Rules:
 * - it must end in `.json` (catches typos such as `--out build/index` or `--out notes.md`, and
 *   an empty value);
 * - it must not be inside the profile folder, where the next build would trip over it.
 *
 * Overwriting an existing file is allowed, because rebuilding `build/index.json` is the normal
 * case. Symbolic links aren't resolved; the check compares the paths as written.
 *
 * @param profileDir - the `--profile-dir` value
 * @param outPath - the `--out` value
 * @returns one human-readable line per broken rule; empty if the path is fine
 */
export function checkOutputPath(profileDir: string, outPath: string): readonly string[] {
  const relativeToProfile = path.relative(path.resolve(profileDir), path.resolve(outPath))
  const isInsideProfile =
    relativeToProfile === '' ||
    (!relativeToProfile.startsWith(`..${path.sep}`) &&
      relativeToProfile !== '..' &&
      !path.isAbsolute(relativeToProfile))

  return [
    ...(path.extname(outPath) === OUTPUT_EXTENSION
      ? []
      : [`--out must be a ${OUTPUT_EXTENSION} file (got "${outPath}")`]),
    ...(isInsideProfile
      ? [`--out must be outside the profile folder (got "${outPath}" inside "${profileDir}")`]
      : []),
  ]
}
