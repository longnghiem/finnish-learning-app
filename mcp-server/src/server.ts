import { McpServer, WebStandardStreamableHTTPServerTransport } from '@modelcontextprotocol/server'
import type { ProfileIndex } from './profile/schema.ts'
import {
  formatIndex,
  getEntry,
  GetEntryInputSchema,
  listEntries,
  ListEntriesInputSchema,
} from './tools.ts'

// Protocol version: nothing is pinned. The backend's MCP client (the Java SDK) speaks the
// 2025-era protocol, which starts with `initialize`. This SDK serves that protocol by default, and
// both sides settle on version 2025-11-25.

/** Name and version that {@link createProfileServer} reports in its `initialize` answer. */
export const SERVER_INFO = { name: 'opisuomea-profile', version: '0.1.0' } as const

/** Address of the compact index resource registered by {@link createProfileServer}. */
export const PROFILE_INDEX_URI = 'profile://index'

/** Largest request body accepted, in bytes. A real MCP message here is well under 1 KB. */
export const MAX_BODY_BYTES = 64 * 1024

const LIST_ENTRIES_DESCRIPTION =
  'List the entries of the developer profile (about, education, experience, faq, project, story) ' +
  'with their id, title, tags and a short summary, but without the full text. faq entries are ' +
  "the developer's own answers to common recruiter questions, such as why they are changing " +
  'jobs. Use list_entries to discover entries; then use get_entry to read one before quoting it.'

const GET_ENTRY_DESCRIPTION =
  'Read one profile entry in full: its text (body), skills with proficiency levels, and things ' +
  'not done. Use get_entry to read an entry before quoting it or relying on its details.'

/** A successful tool result carrying one text block. */
const textResult = (text: string) => ({ content: [{ type: 'text' as const, text }] })

/** A tool error result: the model sees `text` and can correct its call. */
const toolErrorResult = (text: string) => ({ ...textResult(text), isError: true })

/**
 * Creates the read-only profile MCP server:
 * - tool `list_entries`, answered by {@link listEntries};
 * - tool `get_entry`, answered by {@link getEntry};
 * - resource {@link PROFILE_INDEX_URI}, whose text comes from {@link formatIndex}.
 *
 * The SDK validates tool inputs against {@link ListEntriesInputSchema} and
 * {@link GetEntryInputSchema} before the handlers run; invalid input comes back to the model as a
 * tool error result.
 *
 * @param index - the loaded {@link ProfileIndex}; the server never reads anything else
 */
export function createProfileServer(index: ProfileIndex): McpServer {
  const server = new McpServer(SERVER_INFO)

  server.registerTool(
    'list_entries',
    { description: LIST_ENTRIES_DESCRIPTION, inputSchema: ListEntriesInputSchema },
    async ({ type }) => textResult(JSON.stringify(listEntries(index, type))),
  )

  server.registerTool(
    'get_entry',
    { description: GET_ENTRY_DESCRIPTION, inputSchema: GetEntryInputSchema },
    async ({ id }) => {
      const result = getEntry(index, id)
      return result.ok ? textResult(JSON.stringify(result.entry)) : toolErrorResult(result.message)
    },
  )

  server.registerResource(
    'profile-index',
    PROFILE_INDEX_URI,
    {
      description: 'Compact profile index, one line per entry: id | type | title | tags | summary.',
      mimeType: 'text/plain',
    },
    async (uri) => ({
      contents: [{ uri: uri.href, mimeType: 'text/plain', text: formatIndex(index) }],
    }),
  )

  return server
}

/**
 * Answers one MCP HTTP request statelessly with a plain JSON response (never an SSE stream).
 *
 * A fresh server ({@link createProfileServer}) and {@link WebStandardStreamableHTTPServerTransport}
 * are created for every request (< 1 ms), so nothing can leak between requests. The response body
 * is read completely before the server is closed, so closing can't cut it short. Call
 * {@link rejectByMethodOrOrigin} and {@link rejectOversizedBody} first.
 *
 * @param index - the loaded {@link ProfileIndex}
 * @param request - a web-standard request (POST with a JSON-RPC message)
 */
export async function handleMcpRequest(index: ProfileIndex, request: Request): Promise<Response> {
  const server = createProfileServer(index)
  const transport = new WebStandardStreamableHTTPServerTransport({
    sessionIdGenerator: undefined,
    enableJsonResponse: true,
    maxRequestBodySize: MAX_BODY_BYTES,
  })
  await server.connect(transport)
  try {
    const response = await transport.handleRequest(request)
    const body = await response.arrayBuffer()
    return new Response(body.byteLength > 0 ? body : null, {
      status: response.status,
      headers: response.headers,
    })
  } finally {
    await server.close()
  }
}

/**
 * Guard that must run **before** the SDK sees a request. Rejects:
 * - any method but POST with 405. Essential: in stateless JSON mode the SDK holds a GET open
 *   forever, and the backend's Java client sends one GET per connection;
 * - any request with an `Origin` header with 403. Only browsers send `Origin`, our only caller
 *   (the backend) sends none, and the SDK doesn't validate it.
 *
 * @param method - the HTTP method
 * @param origin - the `Origin` header, or `undefined` if absent
 * @returns the rejection response, or `undefined` if the request may continue
 */
export function rejectByMethodOrOrigin(
  method: string,
  origin: string | undefined,
): Response | undefined {
  if (method !== 'POST') {
    return jsonRpcErrorResponse(405, 'Method not allowed.', { allow: 'POST' })
  }
  return origin === undefined ? undefined : jsonRpcErrorResponse(403, 'Forbidden.')
}

/**
 * Guard for the decoded request body: more than {@link MAX_BODY_BYTES} bytes gets 413.
 *
 * @param byteLength - size of the decoded body in bytes
 * @returns the rejection response, or `undefined` if the size is fine
 */
export function rejectOversizedBody(byteLength: number): Response | undefined {
  return byteLength > MAX_BODY_BYTES ? jsonRpcErrorResponse(413, 'Payload too large.') : undefined
}

/**
 * A well-formed JSON-RPC error reply, so requests rejected by {@link rejectByMethodOrOrigin} or
 * {@link rejectOversizedBody} still get an answer MCP clients can parse.
 */
function jsonRpcErrorResponse(
  status: number,
  message: string,
  extraHeaders: Readonly<Record<string, string>> = {},
): Response {
  return new Response(
    JSON.stringify({ jsonrpc: '2.0', error: { code: -32000, message }, id: null }),
    {
      status,
      headers: { 'content-type': 'application/json', ...extraHeaders },
    },
  )
}
