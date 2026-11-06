import type { Lang } from '../../lang'

/*
 * Formatting shared by the admin page's chatbot views. Times are shown in Europe/Helsinki time,
 * because the backend's date filters and daily totals use Helsinki days too.
 */

const HELSINKI = 'Europe/Helsinki'

/** The locale for numbers and dates in the chosen language. */
export function localeOf(lang: Lang): string {
  return lang === 'fi' ? 'fi-FI' : 'en-GB'
}

/** An ISO instant as a Helsinki date and time, e.g. `29/10/2026, 10:15:02`. */
export function formatHelsinkiTime(iso: string, lang: Lang): string {
  return new Date(iso).toLocaleString(localeOf(lang), {
    timeZone: HELSINKI,
    dateStyle: 'short',
    timeStyle: 'medium',
  })
}

/** A cost in USD with 5 decimals, e.g. `$0.00275`: one chat turn costs fractions of a cent. */
export function formatUsd(amount: number): string {
  return `$${amount.toFixed(5)}`
}

/** A text on one line, cut to `max` characters with `…` if it was longer. */
export function preview(text: string, max: number): string {
  const oneLine = text.replace(/\s+/g, ' ').trim()
  return oneLine.length > max ? `${oneLine.slice(0, max - 1)}…` : oneLine
}
