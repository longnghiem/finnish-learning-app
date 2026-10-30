/**
 * One Server-Sent Events message: the event name and its data, both still text.
 *
 * @see SseParser
 */
export interface SseMessage {
  /** The `event:` field, or `message` when the block has none (the SSE default). */
  event: string
  /** The `data:` lines, joined with `\n`. */
  data: string
}

/**
 * Turns the text chunks of an SSE response into {@link SseMessage}s, as they arrive.
 *
 * The format, as the backend's `SseEmitter` writes it:
 * ```
 * event:step
 * data:{"kind":"GUARDRAIL"}
 *
 * :keepalive
 *
 * ```
 * A message is a block of `field:value` lines ended by an empty line. A line starting with `:` is
 * a comment and is ignored, so a block of only comments (the fit check's `:keepalive`) gives no
 * message. One space after the colon is optional and removed: Spring writes none, the standard
 * allows one.
 *
 * Chunks can end anywhere, even inside a line, so the parser keeps the unfinished end of the text
 * until the next chunk completes it.
 */
export class SseParser {
  private pending = ''

  /**
   * Adds the next chunk of text and returns the messages it completes, oldest first; often none.
   */
  push(chunk: string): SseMessage[] {
    const blocks = (this.pending + chunk).split(BLOCK_END)
    this.pending = blocks.at(-1) ?? ''
    return blocks.slice(0, -1).flatMap((block) => {
      const message = parseBlock(block)
      return message === null ? [] : [message]
    })
  }
}

/** An empty line ends a block. The standard allows `\n` and `\r\n` line ends. */
const BLOCK_END = /\r?\n\r?\n/

/** The message in one block, or null if the block has only comments or no data. */
function parseBlock(block: string): SseMessage | null {
  const fields = block
    .split(/\r?\n/)
    .filter((line) => line !== '' && !line.startsWith(':'))
    .map((line) => {
      const colon = line.indexOf(':')
      if (colon === -1) return { name: line, value: '' }
      const value = line.slice(colon + 1)
      return { name: line.slice(0, colon), value: value.startsWith(' ') ? value.slice(1) : value }
    })
  const dataLines = fields.filter((field) => field.name === 'data').map((field) => field.value)
  if (dataLines.length === 0) return null
  const event = fields.findLast((field) => field.name === 'event')?.value ?? 'message'
  return { event, data: dataLines.join('\n') }
}
