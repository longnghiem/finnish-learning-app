export const inputCls = 'w-full rounded-lg border border-border-input bg-surface text-text-primary px-3.5 py-2.5 text-sm' +
  ' font-[inherit] outline-none transition-[border-color,box-shadow] duration-150 focus:border-accent focus:ring-2 focus:ring-accent/20'

export const labelCls = 'block text-[0.78rem] font-bold text-text-sub mb-[5px] tracking-[0.3px] uppercase'

export const submitButtonCls = 'mt-1 rounded-lg bg-amber text-nav-btn-text px-4.5 py-2.75 text-[0.9rem] font-extrabold ' +
  'border-none cursor-pointer font-[inherit] w-full transition-colors duration-150 hover:bg-amber-hover disabled:opacity-60 disabled:cursor-not-allowed'

// Flash cards
export const cardWrapperCls = 'flashcard-scene w-full max-w-75 h-115 mx-auto'
export const frontCardWrapperCls = 'flashcard-face border border-border bg-surface-alt shadow-flash'
export const backCardWrapperCls = 'flashcard-face back border-2 border-accent bg-surface-alt shadow-flash'
export const cardOverlayCls = 'absolute bottom-0 left-0 right-0 bg-flash-overlay-bg backdrop-blur-[6px] px-5 pt-3.5 pb-[18px] ' +
  'flex flex-col items-center gap-[5px]'
export const newBadgeCls = 'absolute top-2 right-2 z-10 rounded-full bg-accent text-white px-2.5 py-0.5 text-[0.65rem] ' +
  'font-bold uppercase tracking-[0.5px] shadow-card'
export const cardTranslationCls = 'text-[1.35rem] font-extrabold text-flash-front-text text-center m-0 leading-[1.3] ' +
  '[text-shadow:0_1px_2px_rgba(255,255,255,0.3)]'
export const flipPromptCls = 'text-[0.68rem] text-flash-front-hint m-0 tracking-[0.6px] font-semibold uppercase'
export const cardImgCls = 'w-full h-full object-cover object-top block'
export const cardNameCls = 'text-[1.55rem] font-extrabold text-flash-back-title text-center m-0 leading-[1.2]'
export const cardExampleCls = 'text-[0.82rem] italic text-flash-back-text text-center m-0 leading-[1.65]'

// Topic page / Quiz page
export const pageContainerCls = 'page-enter max-w-170 mx-auto px-6 pt-10 pb-16'
export const backNavigationBtnCls = 'bg-transparent border-none cursor-pointer text-text-muted text-sm font-[inherit] mb-4 ' +
  'flex items-center gap-1 p-0 font-semibold'
export const pageTitleCls = 'text-center text-2xl font-extrabold text-text-primary mb-7'

// Dashboard (Linear-style: tight spacing, 1px borders, mono numerals, no shadows)
export const dashPageCls = 'page-enter max-w-[1080px] mx-auto px-6 pt-9 pb-16'
export const dashHeaderCls = 'flex items-end justify-between mb-7 flex-wrap gap-3.5'
export const dashEyebrowCls = 'text-[11px] font-semibold text-text-muted tracking-[0.5px] uppercase m-0 mb-1.5'
export const dashTitleCls = 'text-[1.55rem] font-bold text-text-primary m-0 tracking-[-0.4px]'
export const dashSubtitleCls = 'text-[13.5px] text-text-sub mt-1.5 font-medium m-0'
export const dashPrimaryBtnCls = 'rounded-lg bg-text-primary text-bg px-4 py-[9px] text-[13px] font-semibold ' +
  'border-none cursor-pointer font-[inherit] inline-flex items-center gap-1.5 hover:opacity-90 transition-opacity'
export const dashCardCls = 'bg-surface border border-border rounded-[10px]'
export const dashSectionLabelCls = 'text-[11px] font-semibold text-text-muted tracking-[0.4px] uppercase'
export const dashMetaTextCls = 'text-[11px] text-text-muted font-mono tabular-nums'
export const dashStatGridCls = 'grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3 mb-3'
export const dashTableColsCls = 'grid grid-cols-[minmax(160px,2fr)_72px_minmax(120px,1fr)_56px_110px_120px] ' +
  'items-center gap-3.5'
export const dashTableHeaderCellCls = 'text-[10px] font-semibold text-text-muted tracking-[0.4px] uppercase'
export const dashRowBtnCls = 'bg-transparent border-0 cursor-pointer font-[inherit] text-left ' +
  'border-t border-border py-3.5 transition-opacity duration-150 hover:opacity-70'