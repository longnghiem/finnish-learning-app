export const T = {
  light: {
    bg: '#f9fafb', surface: '#ffffff', surfaceAlt: '#f3f4f6',
    border: '#e5e7eb', borderInput: '#d1d5db',
    text: '#1f2937', textSub: '#4b5563', textMuted: '#9ca3af',
    navBg: '#2f3d56', navText: '#ffbd59',
    accent: '#3b82f6', amber: '#ffbd59', amberHover: '#f5a623',
    red: '#ef4444', redHover: '#dc2626',
    green: '#22c55e', greenHover: '#16a34a',
    cardShadow: '0 1px 3px rgb(0 0 0/.08)',
    cardShadowHover: '0 8px 24px rgb(0 0 0/.12)',
    modalShadow: '0 24px 48px rgb(0 0 0/.16)',
    flashShadow: '0 8px 32px rgb(0 0 0/.1)',
    overlay: 'rgba(0,0,0,0.5)',
  },
  dark: {
    bg: '#0f172a', surface: '#1e293b', surfaceAlt: '#263044',
    border: '#334155', borderInput: '#475569',
    text: '#f1f5f9', textSub: '#94a3b8', textMuted: '#475569',
    navBg: '#0a1628', navText: '#ffbd59',
    accent: '#60a5fa', amber: '#ffbd59', amberHover: '#f5a623',
    red: '#f87171', redHover: '#ef4444',
    green: '#4ade80', greenHover: '#22c55e',
    cardShadow: '0 1px 3px rgb(0 0 0/.4)',
    cardShadowHover: '0 8px 24px rgb(0 0 0/.5)',
    modalShadow: '0 24px 48px rgb(0 0 0/.6)',
    flashShadow: '0 8px 32px rgb(0 0 0/.4)',
    overlay: 'rgba(0,0,0,0.7)',
  },
}

export type ThemeTokens = typeof T.light

