/**
 * Builds the Lambda deployment folder `dist/lambda/`:
 * - `index.mjs`: `src/lambdaHandler.ts` and every library it uses (MCP SDK, Zod), bundled by esbuild into
 *   one ES module file, so no `node_modules` folder has to be uploaded;
 * - `index.json`: the profile index, copied next to the bundle, where `lambdaHandler.ts` looks for it.
 *
 * Usage: `npm run bundle` (index from `build/index.json`) or `npm run bundle -- --index <path>`.
 * Build the index first with `npm run build:index`.
 */
import { build } from 'esbuild'
import { copyFile, mkdir, rm } from 'node:fs/promises'
import path from 'node:path'
import { parseArgs } from 'node:util'

const PROJECT_DIR = path.resolve(import.meta.dirname, '..')
const OUT_DIR = path.join(PROJECT_DIR, 'dist', 'lambda')

const { values } = parseArgs({
    options: { index: { type: 'string', default: 'build/index.json' } },
    strict: true,
})
const indexPath = path.resolve(PROJECT_DIR, values.index)

// Start from an empty folder, so a file left over from an older build can never be uploaded.
await rm(OUT_DIR, { recursive: true, force: true })
await mkdir(OUT_DIR, { recursive: true })

await build({
    entryPoints: [path.join(PROJECT_DIR, 'src', 'lambdaHandler.ts')],
    outfile: path.join(OUT_DIR, 'index.mjs'),
    bundle: true,
    platform: 'node',
    format: 'esm',
    // The Lambda runtime is nodejs22.x.
    target: 'node22',
    // Not minified: stack traces in the Lambda logs stay readable, and the file is small anyway.
    minify: false,
    logLevel: 'info',
})

await copyFile(indexPath, path.join(OUT_DIR, 'index.json'))
console.log(`Copied ${path.relative(PROJECT_DIR, indexPath)} → dist/lambda/index.json`)