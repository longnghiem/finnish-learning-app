/**
 * Local development server on http://localhost:3001/mcp. It uses the same guards
 * ({@link rejectByMethodOrOrigin}, {@link rejectOversizedBody}) and the same request handler
 * ({@link handleMcpRequest}) as the Lambda, so it behaves like production.
 *
 * Usage: `npm run dev` (index from `build/index.json`) or `npm run dev -- --index <path>`.
 *
 * **Development only. It has no authentication**, so it listens on 127.0.0.1 only, which means
 * other machines can't reach it. Never deploy it. Production auth is the Lambda function URL's
 * AWS_IAM check, which doesn't exist here.
 */
import { createServer, type IncomingMessage, type ServerResponse } from 'node:http'
import { buffer } from 'node:stream/consumers'
import { parseArgs } from 'node:util'
import { loadIndex } from './loadIndex.ts'
import type { ProfileIndex } from './profile/schema.ts'
import { handleMcpRequest, rejectByMethodOrOrigin, rejectOversizedBody } from './profileServer.ts'

const HOST = '127.0.0.1'
const PORT = 3001
const MCP_PATH = '/mcp'

/**
 * Answers one HTTP request: path check, then {@link rejectByMethodOrOrigin},
 * {@link rejectOversizedBody} and {@link handleMcpRequest}.
 */
async function answer(index: ProfileIndex, req: IncomingMessage): Promise<Response> {
  const url = new URL(req.url ?? '/', `http://${HOST}:${PORT}`)
  if (url.pathname !== MCP_PATH) {
    return new Response('Not found. The MCP endpoint is /mcp.\n', { status: 404 })
  }

  const method = req.method ?? 'GET'
  const methodOrOriginRejection = rejectByMethodOrOrigin(method, req.headers.origin)
  if (methodOrOriginRejection !== undefined) {
    return methodOrOriginRejection
  }

  const body = await buffer(req)
  const sizeRejection = rejectOversizedBody(body.byteLength)
  if (sizeRejection !== undefined) {
    return sizeRejection
  }

  const headers = new Headers(
    Object.entries(req.headers).flatMap(([name, value]) =>
      value === undefined
        ? []
        : [[name, Array.isArray(value) ? value.join(', ') : value] as [string, string]],
    ),
  )
  return handleMcpRequest(index, new Request(url, { method, headers, body: new Uint8Array(body) }))
}

/** Copies a web `Response` onto Node's {@link ServerResponse}. */
async function send(response: Response, res: ServerResponse): Promise<void> {
  const body = Buffer.from(await response.arrayBuffer())
  res.writeHead(response.status, Object.fromEntries(response.headers))
  res.end(body)
}

const { values } = parseArgs({
  options: { index: { type: 'string', default: 'build/index.json' } },
  strict: true,
})
const index = await loadIndex(values.index)

createServer((req, res) => {
  const startedAt = Date.now()
  answer(index, req)
    .then((response) => {
      console.log(`${req.method} ${req.url} → ${response.status} (${Date.now() - startedAt} ms)`)
      return send(response, res)
    })
    .catch((error: unknown) => {
      console.error('Request failed:', error)
      res.writeHead(500, { 'content-type': 'text/plain' }).end('Internal error\n')
    })
}).listen(PORT, HOST, () => {
  console.log(
    `Profile MCP server (development, NO AUTH) on http://localhost:${PORT}${MCP_PATH} ` +
      `with ${index.entries.length} entries from ${values.index}`,
  )
})
