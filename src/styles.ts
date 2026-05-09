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