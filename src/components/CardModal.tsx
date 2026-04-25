import type { CardResponse } from '../types'
import { useCreateCard, useUpdateCard } from '../hooks'
import { useEffect, useMemo, useRef, useState } from 'react'
import { getImageUrl } from '../api'
import { createCardSchema, editCardSchema } from '../schemas'
import { useTheme } from '../theme/index.tsx'
import { useLang } from '../lang/index.tsx'

interface CardModalProps {
  mode: 'create' | 'edit'
  topicId: number
  card?: CardResponse
  onClose: () => void
}

export function CardModal({ mode, topicId, card, onClose }: CardModalProps) {
  const { th } = useTheme()
  const { L } = useLang()
  const createCard = useCreateCard()
  const updateCard = useUpdateCard()

  const [name, setName] = useState(card?.name ?? '')
  const [exampleSentence, setExampleSentence] = useState(card?.exampleSentence ?? '')
  const [translation, setTranslation] = useState(card?.translation ?? '')
  const [imageFile, setImageFile] = useState<File | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitError, setSubmitError] = useState<string | null>(null)

  const fileInputRef = useRef<HTMLInputElement>(null)

  const previewUrl = useMemo(() => {
    if (imageFile) return URL.createObjectURL(imageFile)
    return card ? getImageUrl(card.imageUrl) : null
  }, [imageFile, card])

  useEffect(() => {
    if (!imageFile || !previewUrl) return
    return () => URL.revokeObjectURL(previewUrl)
  }, [imageFile, previewUrl])

  useEffect(() => {
    const fn = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', fn)
    return () => document.removeEventListener('keydown', fn)
  }, [onClose])

  useEffect(() => {
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = '' }
  }, [])

  const isPending = createCard.isPending || updateCard.isPending

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setFieldErrors({})
    setSubmitError(null)

    if (mode === 'create') {
      const result = createCardSchema.safeParse({ name, exampleSentence, translation, image: imageFile })
      if (!result.success) {
        const errors: Record<string, string> = {}
        for (const issue of result.error.issues) {
          const key = String(issue.path[0])
          if (!errors[key]) errors[key] = issue.message
        }
        setFieldErrors(errors)
        return
      }
      try {
        await createCard.mutateAsync({ name: result.data.name, exampleSentence: result.data.exampleSentence, translation: result.data.translation, topicId, image: result.data.image })
        onClose()
      } catch (err) {
        setSubmitError(err instanceof Error ? err.message : 'Failed to create card.')
      }
    } else {
      const result = editCardSchema.safeParse({ name, exampleSentence, translation, image: imageFile ?? undefined })
      if (!result.success) {
        const errors: Record<string, string> = {}
        for (const issue of result.error.issues) {
          const key = String(issue.path[0])
          if (!errors[key]) errors[key] = issue.message
        }
        setFieldErrors(errors)
        return
      }
      try {
        await updateCard.mutateAsync({ id: card!.id, data: { name: result.data.name, exampleSentence: result.data.exampleSentence, translation: result.data.translation, image: result.data.image } })
        onClose()
      } catch (err) {
        setSubmitError(err instanceof Error ? err.message : 'Failed to update card.')
      }
    }
  }

  const inp: React.CSSProperties = {
    width: '100%', borderRadius: '8px', border: `1px solid ${th.borderInput}`,
    background: th.surface, color: th.text,
    padding: '9px 12px', fontSize: '0.875rem', fontFamily: 'inherit',
    outline: 'none', boxSizing: 'border-box', transition: 'border-color 150ms, box-shadow 150ms',
  }
  const lbl: React.CSSProperties = {
    display: 'block', fontSize: '0.78rem', fontWeight: 700, color: th.textSub,
    marginBottom: '5px', letterSpacing: '0.3px', textTransform: 'uppercase',
  }
  const focFn = (e: React.FocusEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    e.target.style.borderColor = th.accent
    e.target.style.boxShadow = `0 0 0 2px ${th.accent}33`
  }
  const blrFn = (e: React.FocusEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    e.target.style.borderColor = th.borderInput
    e.target.style.boxShadow = 'none'
  }

  return (
    <div
      onClick={onClose}
      style={{ position: 'fixed', inset: 0, zIndex: 50, display: 'flex', alignItems: 'center', justifyContent: 'center', background: th.overlay, backdropFilter: 'blur(2px)' }}
    >
      <div
        onClick={e => e.stopPropagation()}
        className="modal-enter"
        style={{
          position: 'relative', background: th.surface, borderRadius: '16px',
          padding: '28px', width: '100%', maxWidth: '440px', margin: '0 16px',
          boxShadow: th.modalShadow, border: `1px solid ${th.border}`,
        }}
      >
        <button
          type="button"
          onClick={onClose}
          style={{ position: 'absolute', top: '14px', right: '14px', background: 'none', border: 'none', cursor: 'pointer', color: th.textMuted, fontSize: '16px', lineHeight: 1 }}
        >
          ✕
        </button>
        <h2 style={{ margin: '0 0 22px', fontSize: '1.1rem', fontWeight: 800, color: th.text }}>
          {mode === 'create' ? L.createCard : L.edit}
        </h2>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div>
            <label style={lbl}>{L.finnishWord}</label>
            <input style={inp} value={name} onChange={e => setName(e.target.value)} placeholder="syödä" onFocus={focFn} onBlur={blrFn} />
            {fieldErrors.name && <p style={{ marginTop: '4px', fontSize: '0.75rem', color: th.red }}>{fieldErrors.name}</p>}
          </div>

          <div>
            <label style={lbl}>{L.exSentence}</label>
            <textarea style={{ ...inp, resize: 'vertical' } as React.CSSProperties} rows={2} value={exampleSentence} onChange={e => setExampleSentence(e.target.value)} placeholder="Minä syön aamiaista" onFocus={focFn} onBlur={blrFn} />
            {fieldErrors.exampleSentence && <p style={{ marginTop: '4px', fontSize: '0.75rem', color: th.red }}>{fieldErrors.exampleSentence}</p>}
          </div>

          <div>
            <label style={lbl}>{L.engTranslation}</label>
            <input style={inp} value={translation} onChange={e => setTranslation(e.target.value)} placeholder="to eat" onFocus={focFn} onBlur={blrFn} />
            {fieldErrors.translation && <p style={{ marginTop: '4px', fontSize: '0.75rem', color: th.red }}>{fieldErrors.translation}</p>}
          </div>

          <div>
            <label style={lbl}>Image{mode === 'edit' ? <span style={{ fontWeight: 400, opacity: 0.6, textTransform: 'none', letterSpacing: 0 }}> (optional)</span> : ''}</label>
            {/* Hidden native file input */}
            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp"
              onChange={e => setImageFile(e.target.files?.[0] ?? null)}
              style={{ display: 'none' }}
            />
            {/* Styled trigger button */}
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              style={{
                display: 'flex', alignItems: 'center', gap: '8px',
                borderRadius: '8px', border: `1px dashed ${th.borderInput}`,
                background: th.surfaceAlt, color: th.textSub,
                padding: '9px 14px', fontSize: '0.875rem', fontWeight: 600,
                cursor: 'pointer', fontFamily: 'inherit', width: '100%',
              }}
            >
              <span style={{ fontSize: '1rem' }}>📎</span>
              {imageFile ? imageFile.name : 'Choose image…'}
            </button>
            {fieldErrors.image && <p style={{ marginTop: '4px', fontSize: '0.75rem', color: th.red }}>{fieldErrors.image}</p>}
            {previewUrl && (
              <div style={{ marginTop: '10px', borderRadius: '10px', overflow: 'hidden', border: `1px solid ${th.border}`, background: th.surface, display: 'flex', justifyContent: 'center' }}>
                <img src={previewUrl} alt="preview" style={{ display: 'block', width: 'auto', height: 'auto', maxWidth: '100%', maxHeight: '360px' }} />
              </div>
            )}
          </div>

          {submitError && (
            <div style={{ background: '#fef2f2', borderRadius: '8px', padding: '9px 12px', fontSize: '0.8rem', color: th.red }}>{submitError}</div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', paddingTop: '4px' }}>
            <button type="button" onClick={onClose} style={{ borderRadius: '8px', background: th.surfaceAlt, color: th.textSub, padding: '9px 18px', fontSize: '0.875rem', fontWeight: 600, border: 'none', cursor: 'pointer', fontFamily: 'inherit' }}>
              {L.cancel}
            </button>
            <button type="submit" disabled={isPending} style={{ borderRadius: '8px', background: th.amber, color: '#2f3d56', padding: '9px 18px', fontSize: '0.875rem', fontWeight: 800, border: 'none', cursor: 'pointer', fontFamily: 'inherit', opacity: isPending ? 0.6 : 1 }}>
              {isPending ? 'Saving…' : mode === 'create' ? L.create : L.save}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
