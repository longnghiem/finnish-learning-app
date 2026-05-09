import type {CardResponse} from '../types'
import {useCreateCard, useUpdateCard} from '../hooks'
import {useEffect, useMemo, useRef, useState, type SubmitEvent} from 'react'
import {getImageUrl} from '../api'
import {createCardSchema, editCardSchema} from '../schemas'
import {useLang} from '../lang'
import {inputCls, labelCls} from '../styles'

interface CardModalProps {
  mode: 'create' | 'edit'
  topicId: number
  card?: CardResponse
  onClose: () => void
}

export function CardModal({ mode, topicId, card, onClose }: CardModalProps) {
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

  const handleSubmit = async (e: SubmitEvent<HTMLFormElement>) => {
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
        await createCard.mutateAsync({
          name: result.data.name,
          exampleSentence: result.data.exampleSentence,
          translation: result.data.translation,
          topicId,
          image: result.data.image
        })
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
        await updateCard.mutateAsync({
          id: card!.id,
          data: {
            name: result.data.name,
            exampleSentence: result.data.exampleSentence,
            translation: result.data.translation,
            image: result.data.image
          }
        })
        onClose()
      } catch (err) {
        setSubmitError(err instanceof Error ? err.message : 'Failed to update card.')
      }
    }
  }

  return (
    <div
      onClick={onClose}
      className="fixed inset-0 z-50 flex items-center justify-center bg-overlay backdrop-blur-[2px]"
    >
      <div
        onClick={e => e.stopPropagation()}
        className="modal-enter relative bg-surface rounded-2xl p-7 w-full max-w-[440px] mx-4 shadow-modal border border-border"
      >
        <button
          type="button"
          onClick={onClose}
          className="absolute top-3.5 right-3.5 bg-transparent border-none cursor-pointer text-text-muted text-base leading-none"
        >
          ✕
        </button>
        <h2 className="m-0 mb-[22px] text-[1.1rem] font-extrabold text-text-primary">
          {mode === 'create' ? L.createCard : L.edit}
        </h2>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div>
            <label className={labelCls}>{L.finnishWord}</label>
            <input className={inputCls} value={name} onChange={e => setName(e.target.value)} placeholder="syödä" />
            {fieldErrors.name && <p className="mt-1 text-xs text-red">{fieldErrors.name}</p>}
          </div>

          <div>
            <label className={labelCls}>{L.exSentence}</label>
            <textarea className={`${inputCls} resize-y`} rows={2} value={exampleSentence} onChange={e => setExampleSentence(e.target.value)} placeholder="Minä syön aamiaista" />
            {fieldErrors.exampleSentence && <p className="mt-1 text-xs text-red">{fieldErrors.exampleSentence}</p>}
          </div>

          <div>
            <label className={labelCls}>{L.engTranslation}</label>
            <input className={inputCls} value={translation} onChange={e => setTranslation(e.target.value)} placeholder="to eat" />
            {fieldErrors.translation && <p className="mt-1 text-xs text-red">{fieldErrors.translation}</p>}
          </div>

          <div>
            <label className={labelCls}>
              Image{mode === 'edit' ? <span className="font-normal opacity-60 normal-case tracking-normal"> (optional)</span> : ''}
            </label>
            {/* Hidden native file input */}
            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp"
              onChange={e => setImageFile(e.target.files?.[0] ?? null)}
              className="hidden"
            />
            {/* Styled trigger button */}
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="flex items-center gap-2 rounded-lg border border-dashed border-border-input bg-surface-alt text-text-sub px-3.5 py-[9px] text-sm font-semibold cursor-pointer font-[inherit] w-full"
            >
              <span className="text-base">📎</span>
              {imageFile ? imageFile.name : 'Choose image…'}
            </button>
            {fieldErrors.image && <p className="mt-1 text-xs text-red">{fieldErrors.image}</p>}
            {previewUrl && (
              <div className="mt-2.5 rounded-[10px] overflow-hidden border border-border bg-surface flex justify-center">
                <img src={previewUrl} alt="preview" className="block w-auto h-auto max-w-full max-h-[360px]" />
              </div>
            )}
          </div>

          {submitError && (
            <div className="bg-error-bg rounded-lg px-3 py-[9px] text-[0.8rem] text-red">{submitError}</div>
          )}

          <div className="flex justify-end gap-2.5 pt-1">
            <button type="button" onClick={onClose} className="rounded-lg bg-surface-alt text-text-sub px-[18px] py-[9px] text-sm font-semibold border-none cursor-pointer font-[inherit]">
              {L.cancel}
            </button>
            <button type="submit" disabled={isPending} className={`rounded-lg bg-amber text-nav-btn-text px-[18px] py-[9px] text-sm font-extrabold border-none cursor-pointer font-[inherit] ${isPending ? 'opacity-60' : ''}`}>
              {isPending ? 'Saving…' : mode === 'create' ? L.create : L.save}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}