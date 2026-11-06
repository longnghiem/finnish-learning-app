import { describe, expect, it } from 'vitest'
import { handleMcpRequest, PROFILE_INDEX_URI, SERVER_INFO } from '../src/profileServer.ts'
import { FAKE_INDEX, INITIALIZE_MESSAGE, MCP_HEADERS, toolCallMessage } from './fakeIndex.ts'

/**
 * Sends one JSON-RPC message through {@link handleMcpRequest} (the real transport, in-process);
 * returns the status and parsed body.
 */
async function send(message: object): Promise<{ status: number; json: any }> {
  const response = await handleMcpRequest(
    FAKE_INDEX,
    new Request('http://localhost/mcp', {
      method: 'POST',
      headers: { ...MCP_HEADERS, 'mcp-protocol-version': '2025-11-25' },
      body: JSON.stringify(message),
    }),
  )
  const text = await response.text()
  return {
    status: response.status,
    json: text === '' ? undefined : JSON.parse(text),
  }
}

describe('profile MCP server', () => {
  it('testServer_InitializeStateless', async () => {
    // Every request below runs on a brand-new server, like separate Lambda invocations.
    const initialize = await send(INITIALIZE_MESSAGE)
    const toolsList = await send({ jsonrpc: '2.0', id: 2, method: 'tools/list' })
    const resource = await send({
      jsonrpc: '2.0',
      id: 3,
      method: 'resources/read',
      params: { uri: PROFILE_INDEX_URI },
    })

    expect(initialize.status).toBe(200)
    expect(initialize.json.result.protocolVersion).toBe('2025-11-25')
    expect(initialize.json.result.serverInfo).toEqual(SERVER_INFO)
    expect(toolsList.json.result.tools.map((tool: { name: string }) => tool.name)).toEqual([
      'list_entries',
      'get_entry',
    ])
    expect(resource.json.result.contents[0].text.split('\n')[0]).toBe(
      'about | about | About Test Person | kotlin | A fictional developer.',
    )
  })

  it('testServer_GetEntryReturnsFullEntry', async () => {
    const reply = await send(toolCallMessage('get_entry', { id: 'about' }))

    expect(reply.json.result.isError).toBeUndefined()
    expect(JSON.parse(reply.json.result.content[0].text)).toEqual(FAKE_INDEX.entries[0])
  })

  it('testGetEntry_InvalidInputIsToolError', async () => {
    const reply = await send(toolCallMessage('get_entry', { id: 'a'.repeat(200) }))

    expect(reply.status).toBe(200)
    expect(reply.json.error).toBeUndefined()
    expect(reply.json.result.isError).toBe(true)
    expect(reply.json.result.content[0].text).toMatch(/^Input validation error/)
  })

  it('testServer_UnknownIdIsToolError', async () => {
    const reply = await send(toolCallMessage('get_entry', { id: 'experience/nope' }))

    expect(reply.json.result.isError).toBe(true)
    expect(reply.json.result.content[0].text).toMatch(/^Unknown entry id/)
  })

  it('testServer_UnknownToolIsProtocolError', async () => {
    const reply = await send(toolCallMessage('nope', {}))

    expect(reply.json.result).toBeUndefined()
    expect(reply.json.error.message).toContain('nope')
  })
})
