import type { LambdaFunctionURLEvent } from 'aws-lambda'
import { describe, expect, it, vi } from 'vitest'
import { createHandler, type FunctionUrlResult } from '../src/lambdaHandler.ts'
import { FAKE_INDEX, INITIALIZE_MESSAGE, MCP_HEADERS, toolCallMessage } from './fakeIndex.ts'

const DOMAIN = 'fakeurlid0000000000000000000000.lambda-url.eu-north-1.on.aws'

/**
 * A function URL event (payload format 2.0) like the ones Lambda sends; only the fields we use
 * vary.
 */
function functionUrlEvent(options: {
  method?: string
  headers?: Readonly<Record<string, string>>
  body?: string
  isBase64Encoded?: boolean
}): LambdaFunctionURLEvent {
  const method = options.method ?? 'POST'
  return {
    version: '2.0',
    routeKey: '$default',
    rawPath: '/mcp',
    rawQueryString: '',
    headers: { host: DOMAIN, ...MCP_HEADERS, ...options.headers },
    requestContext: {
      accountId: '000000000000',
      apiId: 'fakeurlid0000000000000000000000',
      domainName: DOMAIN,
      domainPrefix: 'fakeurlid0000000000000000000000',
      http: {
        method,
        path: '/mcp',
        protocol: 'HTTP/1.1',
        sourceIp: '192.0.2.10',
        userAgent: 'test',
      },
      requestId: 'test-request',
      routeKey: '$default',
      stage: '$default',
      time: '19/Oct/2026:12:00:00 +0000',
      timeEpoch: 1792411200000,
    },
    body: options.body,
    isBase64Encoded: options.isBase64Encoded ?? false,
  }
}

/** A POST event carrying one JSON-RPC message. */
const postEvent = (message: object) => functionUrlEvent({ body: JSON.stringify(message) })

/**
 * A handler from {@link createHandler} with {@link FAKE_INDEX}, plus a spy that counts index loads.
 */
function testHandler() {
  const loadIndex = vi.fn(async () => FAKE_INDEX)
  return { handler: createHandler(loadIndex), loadIndex }
}

const contentType = (result: FunctionUrlResult) => result.headers?.['content-type']

describe('Lambda handler', () => {
  it('testLambda_RejectsOrigin', async () => {
    const { handler, loadIndex } = testHandler()

    const result = await handler(
      functionUrlEvent({
        headers: { origin: 'https://evil.example' },
        body: JSON.stringify(INITIALIZE_MESSAGE),
      }),
    )

    expect(result.statusCode).toBe(403)
    expect(loadIndex).not.toHaveBeenCalled()
  })

  it('testLambda_RejectsGet', async () => {
    const { handler, loadIndex } = testHandler()

    const result = await handler(functionUrlEvent({ method: 'GET' }))

    expect(result.statusCode).toBe(405)
    expect(result.headers?.['allow']).toBe('POST')
    expect(loadIndex).not.toHaveBeenCalled()
  })

  it('testLambda_RejectsDelete', async () => {
    const { handler } = testHandler()

    const result = await handler(functionUrlEvent({ method: 'DELETE' }))

    expect(result.statusCode).toBe(405)
  })

  it('testLambda_RejectsLargeBody', async () => {
    const { handler, loadIndex } = testHandler()

    const result = await handler(functionUrlEvent({ body: 'x'.repeat(64 * 1024 + 1) }))

    expect(result.statusCode).toBe(413)
    expect(loadIndex).not.toHaveBeenCalled()
  })

  it('testLambda_Base64Body', async () => {
    const { handler } = testHandler()
    const message = {
      ...INITIALIZE_MESSAGE,
      params: { ...INITIALIZE_MESSAGE.params, clientInfo: { name: 'Hyvää päivää', version: '0' } },
    }

    const result = await handler(
      functionUrlEvent({
        body: Buffer.from(JSON.stringify(message), 'utf8').toString('base64'),
        isBase64Encoded: true,
      }),
    )

    expect(result.statusCode).toBe(200)
    expect(JSON.parse(result.body ?? '').result.protocolVersion).toBe('2025-11-25')
  })

  it('testLambda_AnswersJsonNotSse', async () => {
    const { handler } = testHandler()

    const results = await Promise.all([
      handler(postEvent(INITIALIZE_MESSAGE)),
      handler(postEvent({ jsonrpc: '2.0', id: 2, method: 'tools/list' })),
      handler(postEvent(toolCallMessage('list_entries', {}, 3))),
    ])

    expect(results.map((result) => result.statusCode)).toEqual([200, 200, 200])
    expect(results.map(contentType)).toEqual([
      'application/json',
      'application/json',
      'application/json',
    ])
    expect(results.every((result) => result.headers?.['mcp-session-id'] === undefined)).toBe(true)
    const entries = JSON.parse(JSON.parse(results[2]?.body ?? '').result.content[0].text)
    expect(entries.every((entry: object) => !('body' in entry))).toBe(true)
  })

  it('testLambda_NotificationReturns202', async () => {
    const { handler } = testHandler()

    const result = await handler(postEvent({ jsonrpc: '2.0', method: 'notifications/initialized' }))

    expect(result.statusCode).toBe(202)
    expect(result.body).toBe('')
  })

  it('testLambda_LoadsIndexOnce', async () => {
    const { handler, loadIndex } = testHandler()

    await handler(postEvent(INITIALIZE_MESSAGE))
    await handler(postEvent(toolCallMessage('get_entry', { id: 'about' })))

    expect(loadIndex).toHaveBeenCalledTimes(1)
  })

  it('testLambda_LogsNoBody', async () => {
    const { handler } = testHandler()
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})

    await handler(postEvent(toolCallMessage('get_entry', { id: 'experience/acme' })))

    expect(log).toHaveBeenCalledTimes(1)
    const line = JSON.parse(String(log.mock.calls[0]?.[0]))
    expect(Object.keys(line).sort()).toEqual(['latencyMs', 'method', 'tool'])
    expect(line).toMatchObject({ method: 'tools/call', tool: 'get_entry' })
    expect(String(log.mock.calls[0]?.[0])).not.toContain('experience/acme')
    log.mockRestore()
  })
})
