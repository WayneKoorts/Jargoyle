import { useCallback, useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useUploadDocument } from '../hooks/useUploadDocument'
import { useDocumentStatus } from '../hooks/useDocumentStatus'
import { ACCEPTED_FILE_TYPES, MAX_FILE_SIZE_BYTES, MAX_TEXT_LENGTH } from '../api/documents'

type Tab = 'file' | 'text'
type Phase = 'input' | 'processing' | 'complete'

interface UploadDialogProps {
  open: boolean
  onClose: () => void
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export default function UploadDialog({ open, onClose }: UploadDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  // Form state
  const [tab, setTab] = useState<Tab>('file')
  const [file, setFile] = useState<File | null>(null)
  const [text, setText] = useState('')
  const [dragOver, setDragOver] = useState(false)
  const [validationError, setValidationError] = useState<string | null>(null)

  // Processing state
  const [phase, setPhase] = useState<Phase>('input')
  const [documentId, setDocumentId] = useState<string | null>(null)

  const queryClient = useQueryClient()
  const upload = useUploadDocument()
  const status = useDocumentStatus(phase === 'processing' ? documentId : null)

  // Derive effective phase: when SSE reports READY, show complete phase
  // without needing a synchronous setState inside an effect
  const effectivePhase: Phase = phase === 'processing' && status.isComplete ? 'complete' : phase

  // --- Dialog open/close ---

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return

    if (open && !dialog.open) {
      dialog.showModal()
    } else if (!open && dialog.open) {
      dialog.close()
    }
  }, [open])

  // Reset state when dialog closes
  const handleClose = useCallback(() => {
    setTab('file')
    setFile(null)
    setText('')
    setDragOver(false)
    setValidationError(null)
    setPhase('input')
    setDocumentId(null)
    upload.reset()
    onClose()
  }, [onClose, upload])

  // Handle native dialog close (Escape key, backdrop click)
  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    const onDialogClose = () => handleClose()
    dialog.addEventListener('close', onDialogClose)
    return () => dialog.removeEventListener('close', onDialogClose)
  }, [handleClose])

  // Close on backdrop click
  const handleBackdropClick = (e: React.MouseEvent<HTMLDialogElement>) => {
    if (e.target === dialogRef.current) {
      handleClose()
    }
  }

  // Close the complete phase — refreshes the document list then closes
  function handleCompleteClose() {
    queryClient.invalidateQueries({ queryKey: ['documents'] })
    handleClose()
  }

  // --- Validation ---

  function validateFile(f: File): string | null {
    if (!f.name.toLowerCase().endsWith('.pdf')) {
      return 'Only PDF files are accepted.'
    }
    if (f.size > MAX_FILE_SIZE_BYTES) {
      return `File is too large (${formatFileSize(f.size)}). Maximum size is ${formatFileSize(MAX_FILE_SIZE_BYTES)}.`
    }
    return null
  }

  function validateText(t: string): string | null {
    if (t.trim().length === 0) return 'Please enter some text.'
    if (t.length > MAX_TEXT_LENGTH) return `Text is too long (${t.length.toLocaleString()} characters). Maximum is ${MAX_TEXT_LENGTH.toLocaleString()}.`
    return null
  }

  // --- File handling ---

  function handleFileSelect(f: File) {
    const error = validateFile(f)
    setValidationError(error)
    setFile(error ? null : f)
  }

  function handleDrop(e: React.DragEvent) {
    e.preventDefault()
    setDragOver(false)
    const droppedFile = e.dataTransfer.files[0]
    if (droppedFile) handleFileSelect(droppedFile)
  }

  function handleFileInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const selected = e.target.files?.[0]
    if (selected) handleFileSelect(selected)
  }

  // --- Submit ---

  const canSubmit =
    effectivePhase === 'input' &&
    !upload.isPending &&
    ((tab === 'file' && file !== null) || (tab === 'text' && text.trim().length > 0)) &&
    !validationError

  async function handleSubmit() {
    // Final validation
    if (tab === 'text') {
      const error = validateText(text)
      if (error) {
        setValidationError(error)
        return
      }
    }

    try {
      const result = await upload.mutateAsync(
        tab === 'file' ? { file: file! } : { text },
      )
      setDocumentId(result.id)
      setPhase('processing')
    } catch {
      // Error is available via upload.error — stays on input phase
    }
  }

  // --- Render helpers ---

  const tabButtonClass = (t: Tab) =>
    `flex-1 rounded-md px-4 py-2 text-sm font-medium transition-colors ${
      tab === t
        ? 'bg-indigo-600 text-white'
        : 'text-slate-600 hover:bg-slate-100'
    }`

  return (
    <dialog
      ref={dialogRef}
      onClick={handleBackdropClick}
      className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full max-w-lg rounded-xl border-0 p-0 shadow-xl backdrop:bg-black/50"
    >
      <div className="p-6">
        {/* Header */}
        <div className="mb-6 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-900">
            {effectivePhase === 'input' && 'Upload Document'}
            {effectivePhase === 'processing' && 'Processing Document'}
            {effectivePhase === 'complete' && 'Upload Complete'}
          </h2>
          {effectivePhase !== 'processing' && (
            <button
              onClick={handleClose}
              className="rounded-md p-1 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
              aria-label="Close"
            >
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          )}
        </div>

        {/* Phase: Input */}
        {effectivePhase === 'input' && (
          <>
            {/* Tab switcher */}
            <div className="mb-4 flex gap-1 rounded-lg bg-slate-100 p-1">
              <button className={tabButtonClass('file')} onClick={() => { setTab('file'); setValidationError(null) }}>
                Upload File
              </button>
              <button className={tabButtonClass('text')} onClick={() => { setTab('text'); setValidationError(null) }}>
                Paste Text
              </button>
            </div>

            {/* File tab */}
            {tab === 'file' && (
              <div
                onDragOver={(e) => { e.preventDefault(); setDragOver(true) }}
                onDragLeave={() => setDragOver(false)}
                onDrop={handleDrop}
                className={`flex flex-col items-center justify-center rounded-lg border-2 border-dashed p-8 text-center transition-colors ${
                  dragOver
                    ? 'border-indigo-400 bg-indigo-50'
                    : 'border-slate-300 bg-slate-50'
                }`}
              >
                {file ? (
                  <div className="space-y-1">
                    <p className="text-sm font-medium text-slate-700">{file.name}</p>
                    <p className="text-xs text-slate-500">{formatFileSize(file.size)}</p>
                    <button
                      onClick={() => { setFile(null); setValidationError(null) }}
                      className="mt-2 text-xs text-indigo-600 hover:text-indigo-800"
                    >
                      Remove
                    </button>
                  </div>
                ) : (
                  <>
                    <svg className="mb-3 h-10 w-10 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M12 16.5V9.75m0 0l3 3m-3-3l-3 3M6.75 19.5a4.5 4.5 0 01-1.41-8.775 5.25 5.25 0 0110.233-2.33 3 3 0 013.758 3.848A3.752 3.752 0 0118 19.5H6.75z" />
                    </svg>
                    <p className="text-sm text-slate-600">
                      Drag and drop a PDF here, or{' '}
                      <label className="cursor-pointer font-medium text-indigo-600 hover:text-indigo-800">
                        browse
                        <input
                          type="file"
                          accept={ACCEPTED_FILE_TYPES}
                          onChange={handleFileInputChange}
                          className="hidden"
                        />
                      </label>
                    </p>
                    <p className="mt-1 text-xs text-slate-400">PDF only, up to 10 MB</p>
                  </>
                )}
              </div>
            )}

            {/* Text tab */}
            {tab === 'text' && (
              <div>
                <textarea
                  value={text}
                  onChange={(e) => { setText(e.target.value); setValidationError(null) }}
                  placeholder="Paste your document text here..."
                  rows={8}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-700 placeholder:text-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                />
                <p className="mt-1 text-right text-xs text-slate-400">
                  {text.length.toLocaleString()} / {MAX_TEXT_LENGTH.toLocaleString()} characters
                </p>
              </div>
            )}

            {/* Validation / server error */}
            {(validationError || upload.error) && (
              <p className="mt-3 text-sm text-red-600">
                {validationError ?? upload.error?.message}
              </p>
            )}

            {/* Submit button */}
            <button
              onClick={handleSubmit}
              disabled={!canSubmit}
              className="mt-4 w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {upload.isPending ? 'Uploading…' : 'Upload'}
            </button>
          </>
        )}

        {/* Phase: Processing */}
        {effectivePhase === 'processing' && (
          <div className="flex flex-col items-center py-6">
            {status.isFailed ? (
              <>
                <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-red-100">
                  <svg className="h-6 w-6 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </div>
                <p className="text-sm font-medium text-slate-900">Processing failed</p>
                <p className="mt-1 text-sm text-slate-500">{status.errorMessage ?? 'An unexpected error occurred.'}</p>
                <button
                  onClick={handleClose}
                  className="mt-4 rounded-lg border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
                >
                  Close
                </button>
              </>
            ) : (
              <>
                {/* Spinner */}
                <div className="mb-4 h-10 w-10 animate-spin rounded-full border-4 border-slate-200 border-t-indigo-600" />
                <p className="text-sm font-medium text-slate-900">
                  {status.step ?? 'Starting…'}
                </p>
                <p className="mt-1 text-xs text-slate-400">
                  This may take a moment
                </p>
              </>
            )}
          </div>
        )}

        {/* Phase: Complete */}
        {effectivePhase === 'complete' && (
          <div className="flex flex-col items-center py-6">
            <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-green-100">
              <svg className="h-6 w-6 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M4.5 12.75l6 6 9-13.5" />
              </svg>
            </div>
            <p className="text-sm font-medium text-slate-900">Document processed successfully</p>
            <p className="mt-1 text-sm text-slate-500">Your document is ready to view.</p>
            <button
              onClick={handleCompleteClose}
              className="mt-4 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-indigo-700"
            >
              Done
            </button>
          </div>
        )}
      </div>
    </dialog>
  )
}
