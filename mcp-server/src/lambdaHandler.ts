import type { APIGatewayProxyStructuredResultV2, LambdaFunctionURLEvent } from 'aws-lambda'
import { loadIndex } from './loadIndex.ts'
import type { ProfileIndex } from './profile/schema.ts'
import { handleMcpRequest, rejectByMethodOrOrigin, rejectOversizedBody } from './profileServer.ts'

/**
 * The object form of a function URL result. (Lambda also accepts a bare string; we never use it,
 * so callers and tests can rely on `statusCode`, `headers` and `body`.)
 */
export type FunctionUrlResult = APIGatewayProxyStructuredResultV2

/** A Lambda function URL handler. */
export type FunctionUrlHandler = (event: LambdaFunctionURLEvent) => Promise<FunctionUrlResult>

/**
 * Where the bundled index lives: `index.json` in the same folder as this module's file. The deploy
 * bundles this module into `dist/lambda/index.mjs` and copies `index.json` beside it.
 */
const BUNDLED_INDEX_URL = new URL('./index.json', import.meta.url)

/** Longest JSON-RPC method or tool name written to the log. */
const MAX_LOGGED_NAME_LENGTH = 64

/**
 * Builds the function URL handler. Separate from {@link handler} so tests can pass a fake index.
 *
 * Per request, in this order and before the SDK sees anything:
 * 1. only POST, and no `Origin` header ({@link rejectByMethodOrOrigin}: 405 / 403 otherwise);
 * 2. body decoded ({@link decodeBody}) and at most 64 KB ({@link rejectOversizedBody}: 413 otherwise).
 *
 * Then {@link toWebRequest} turns the event into a web `Request`, {@link handleMcpRequest} answers
 * it, and {@link toFunctionUrlResult} turns the `Response` into the function URL result. Auth is
 * done by the function URL (`AWS_IAM`) before this code runs.
 *
 * The index is loaded on the first request that passes the guards and then reused for the life of
 * this Lambda instance (once per cold start).
 *
 * @param loadProfileIndex - loads the {@link ProfileIndex}; called at most once
 */
export function createHandler(loadProfileIndex: () => Promise<ProfileIndex>): FunctionUrlHandler {
  let indexPromise: Promise<ProfileIndex> | undefined

  return async (event) => {
    const startedAt = Date.now()
    const method = event.requestContext.http.method

    // Function URL header names arrive lower-case.
    const methodOrOriginRejection = rejectByMethodOrOrigin(method, event.headers['origin'])
    if (methodOrOriginRejection !== undefined) {
      logRequest(method, undefined, startedAt)
      return toFunctionUrlResult(methodOrOriginRejection)
    }

    const body = decodeBody(event)
    const sizeRejection = rejectOversizedBody(body.byteLength)
    if (sizeRejection !== undefined) {
      logRequest(method, undefined, startedAt)
      return toFunctionUrlResult(sizeRejection)
    }

    indexPromise ??= loadProfileIndex()
    const response = await handleMcpRequest(await indexPromise, toWebRequest(event, body))
    logRequest(method, body, startedAt)
    return toFunctionUrlResult(response)
  }
}

/**
 * The Lambda entry point, built by {@link createHandler} with the index from {@link BUNDLED_INDEX_URL}.
 * The function's handler setting is `index.handler`: export `handler` of the bundle file `index.mjs`.
 */
export const handler: FunctionUrlHandler = createHandler(() => loadIndex(BUNDLED_INDEX_URL))

/** The raw request body: base64-decoded when Lambda flagged it, otherwise UTF-8. */
function decodeBody(event: LambdaFunctionURLEvent): Buffer {
  if (event.body === undefined) {
    return Buffer.alloc(0)
  }
  return Buffer.from(event.body, event.isBase64Encoded ? 'base64' : 'utf8')
}

/** Turns the function URL event into the web-standard `Request` the MCP SDK understands. */
function toWebRequest(event: LambdaFunctionURLEvent, body: Buffer): Request {
  const query = event.rawQueryString ? `?${event.rawQueryString}` : ''
  const headers = new Headers(
    Object.entries(event.headers).flatMap(([name, value]) =>
      value === undefined ? [] : [[name, value] as [string, string]],
    ),
  )
  return new Request(`https://${event.requestContext.domainName}${event.rawPath}${query}`, {
    method: event.requestContext.http.method,
    headers,
    body: body.byteLength > 0 ? new Uint8Array(body) : null,
  })
}

/**
 * Turns the web `Response` back into a {@link FunctionUrlResult}. A 202 (notification) has an empty
 * body.
 */
async function toFunctionUrlResult(response: Response): Promise<FunctionUrlResult> {
  return {
    statusCode: response.status,
    headers: Object.fromEntries(response.headers),
    body: await response.text(),
    isBase64Encoded: false,
  }
}

/**
 * Logs one line per request: the JSON-RPC method and the tool name for `tools/call` (both from
 * {@link readRpcMethod}), and the latency. Never the request body: it can carry recruiter text
 * passed through by the backend.
 */
function logRequest(httpMethod: string, body: Buffer | undefined, startedAt: number): void {
  const message = body === undefined ? undefined : readRpcMethod(body)
  console.log(
    JSON.stringify({
      method: message?.method ?? httpMethod,
      tool: message?.tool,
      latencyMs: Date.now() - startedAt,
    }),
  )
}

/** Extracts only `method` and, for `tools/call`, `params.name` from a JSON-RPC message. */
function readRpcMethod(
  body: Buffer,
): { readonly method?: string; readonly tool?: string } | undefined {
  try {
    const message: unknown = JSON.parse(body.toString('utf8'))
    if (typeof message !== 'object' || message === null || !('method' in message)) {
      return undefined
    }
    const method = typeof message.method === 'string' ? message.method : undefined
    const params = 'params' in message ? message.params : undefined
    const tool =
      method === 'tools/call' &&
      typeof params === 'object' &&
      params !== null &&
      'name' in params &&
      typeof params.name === 'string'
        ? params.name
        : undefined
    // Both values come from the caller; cap them so a log line can't grow without limit.
    return {
      method: method?.slice(0, MAX_LOGGED_NAME_LENGTH),
      tool: tool?.slice(0, MAX_LOGGED_NAME_LENGTH),
    }
  } catch {
    return undefined
  }
}
