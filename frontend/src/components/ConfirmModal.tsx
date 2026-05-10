import {useEffect} from 'react'
import {useLang} from '../lang'

interface ConfirmModalProps {
  message: string
  onConfirm: () => void
  onCancel: () => void
  isLoading?: boolean
}

export function ConfirmModal({ message, onConfirm, onCancel, isLoading = false }: ConfirmModalProps) {
  const { L } = useLang()

  useEffect(() => {
    const fn = (e: KeyboardEvent) => { if (e.key === 'Escape') onCancel() }
    document.addEventListener('keydown', fn)
    return () => document.removeEventListener('keydown', fn)
  }, [onCancel])

  useEffect(() => {
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = '' }
  }, [])

  return (
    <div
      onClick={onCancel}
      className="fixed inset-0 z-50 flex items-center justify-center bg-overlay backdrop-blur-[2px]"
    >
      <div
        onClick={e => e.stopPropagation()}
        className="modal-enter bg-surface rounded-2xl p-7 max-w-95 w-full mx-4 shadow-modal border border-border"
      >
        <p className="text-text-primary mb-6 leading-[1.7] text-[0.95rem]">
          {message}
        </p>
        <div className="flex justify-end gap-2.5">
          <button
            type="button"
            onClick={onCancel}
            disabled={isLoading}
            className={`rounded-lg bg-surface-alt text-text-sub px-4.5 py-2.25 text-sm font-semibold border-none cursor-pointer font-[inherit] ${isLoading ? 'opacity-50' : ''}`}
          >
            {L.cancel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={isLoading}
            className={`rounded-lg bg-red text-white px-4.5 py-2.25 text-sm font-bold border-none font-[inherit] ${isLoading ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
          >
            {isLoading ? 'Deleting…' : L.delete}
          </button>
        </div>
      </div>
    </div>
  )
}