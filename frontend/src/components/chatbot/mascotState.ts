import type { AskErrorCode } from '../../api'
import type { AskStreamStatus } from '../../hooks'

/** Himmel's moods, from calm to busy. Each one has its own animation. */
export type MascotState = 'idle' | 'listening' | 'thinking' | 'talking' | 'sleeping'

/** What one tab of the Ask page is doing, as far as Himmel is concerned. */
export interface TabActivity {
  /** The tab's stream: `streaming` while an answer or a report is being made. */
  status: AskStreamStatus
  /** The code of the tab's last error, or null. */
  errorCode: AskErrorCode | null
  /** True while the tab's input holds text that hasn't been sent yet. */
  typing: boolean
  /** True when the tab's newest result is an answer or a report. */
  answered: boolean
}

/**
 * Himmel's state from what the two tabs are doing. The first rule that matches wins:
 *
 * 1. either tab is streaming → `thinking` (also the hidden tab: Himmel is still busy with it);
 * 2. either tab's last error is `CAPPED` → `sleeping` (the budget is shared by both tabs);
 * 3. the shown tab's input holds text not sent yet → `listening`;
 * 4. the shown tab's newest result is an answer or a report → `talking`;
 * 5. otherwise → `idle`.
 *
 * A pure function of what the page already has, so the page works it out while rendering, with
 * no state or effect of its own.
 */
export function mascotStateOf(shown: TabActivity, hidden: TabActivity): MascotState {
  if (shown.status === 'streaming' || hidden.status === 'streaming') return 'thinking'
  if (shown.errorCode === 'CAPPED' || hidden.errorCode === 'CAPPED') return 'sleeping'
  if (shown.typing) return 'listening'
  if (shown.answered && shown.status === 'idle') return 'talking'
  return 'idle'
}
