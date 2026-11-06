import type { ProfileIndex } from '../src/profile/schema.ts'

/** A small, fake {@link ProfileIndex} shared by the tests. */
export const FAKE_INDEX: ProfileIndex = {
  generatedAt: '2026-10-19T12:00:00.000Z',
  entries: [
    {
      id: 'about',
      type: 'about',
      title: 'About Test Person',
      tags: ['kotlin'],
      summary: 'A fictional developer.',
      skills: [{ name: 'Kotlin', level: 'strong' }],
      notDone: ['Kubernetes in production'],
      body: 'About body. Hyvää päivää!',
    },
    {
      id: 'experience/acme',
      type: 'experience',
      title: 'Backend developer, Acme',
      tags: ['kotlin', 'kafka'],
      summary: 'Built a Kafka pipeline.',
      period: '2022 – 2024',
      body: 'Acme body.',
    },
    {
      id: 'stories/fast-dashboard',
      type: 'story',
      title: 'Fast dashboard',
      tags: ['performance'],
      summary: 'Made the dashboard fast.\nSecond line.',
      body: 'Story body.',
    },
  ],
}

/** Headers every MCP request needs: JSON body, and both answer formats accepted. */
export const MCP_HEADERS = {
  'content-type': 'application/json',
  accept: 'application/json, text/event-stream',
} as const

/** The `initialize` message the backend's Java client sends (protocol 2025-11-25). */
export const INITIALIZE_MESSAGE = {
  jsonrpc: '2.0',
  id: 1,
  method: 'initialize',
  params: {
    protocolVersion: '2025-11-25',
    capabilities: {},
    clientInfo: { name: 'test', version: '0' },
  },
} as const

/** A `tools/call` message for the tool `name` with the arguments `args`. */
export function toolCallMessage(name: string, args: Readonly<Record<string, unknown>>, id = 2) {
  return { jsonrpc: '2.0', id, method: 'tools/call', params: { name, arguments: args } } as const
}
