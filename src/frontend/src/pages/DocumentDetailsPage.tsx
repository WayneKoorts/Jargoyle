import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import type { UserProfile } from '../api/auth'
import {
  DOCUMENT_TYPE_LABELS,
  INPUT_TYPE_LABELS,
  STATUS_LABELS,
  parseKeyFacts,
  parseFlaggedTerms,
  type KeyFacts,
  type KeyFact,
  type FlaggedTerm,
} from '../api/documents'
import Layout from '../components/Layout'
import ChatPane from '../components/ChatPane'
import { useDeleteDocument } from '../hooks/useDeleteDocument'
import { useDocument } from '../hooks/useDocument'
import { useDocumentStatus } from '../hooks/useDocumentStatus'

interface DocumentDetailsPageProps {
  user: UserProfile
  onLogout: () => void
}

const STATUS_COLOURS: Record<string, string> = {
  READY: 'bg-green-100 text-green-800',
  PROCESSING: 'bg-amber-100 text-amber-800',
  UPLOADING: 'bg-blue-100 text-blue-800',
  FAILED: 'bg-red-100 text-red-800',
}

function statusClasses(status: string): string {
  return STATUS_COLOURS[status] ?? 'bg-slate-100 text-slate-800'
}

function formatDate(iso: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(iso))
}

export default function DocumentDetailsPage({ user, onLogout }: DocumentDetailsPageProps) {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { document, isLoading, isError, refetch } = useDocument(id)
  const [isChatOpen, setIsChatOpen] = useState(false)

  // Kebab dropdown menu
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  // Close the menu when clicking outside
  const handleClickOutside = useCallback((e: MouseEvent) => {
    if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
      setIsMenuOpen(false)
    }
  }, [])

  useEffect(() => {
    if (isMenuOpen) {
      window.document.addEventListener('mousedown', handleClickOutside)
      return () => window.document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [isMenuOpen, handleClickOutside])

  // Delete confirmation dialog
  const deleteDialogRef = useRef<HTMLDialogElement>(null)
  const deleteMutation = useDeleteDocument()

  // Subscribe to SSE only while the document is still processing
  const isProcessing = document?.status === 'PROCESSING'
  const { step, isComplete, isFailed, errorMessage: sseError } = useDocumentStatus(
    isProcessing ? document.id : null,
  )

  // Refetch the document when SSE signals processing is complete (or failed)
  useEffect(() => {
    if (isComplete || isFailed) {
      refetch()
    }
  }, [isComplete, isFailed, refetch])

  if (isLoading) {
    return (
      <Layout user={user} onLogout={onLogout}>
        <div className="mx-auto max-w-4xl px-6 py-8">
          <div className="py-12 text-center text-slate-400">Loading…</div>
        </div>
      </Layout>
    )
  }

  if (isError || !document) {
    return (
      <Layout user={user} onLogout={onLogout}>
        <div className="mx-auto max-w-4xl px-6 py-8">
          <div className="py-12 text-center">
            <p className="text-slate-500">Could not load this document.</p>
            <Link
              to="/"
              className="mt-3 inline-block rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
            >
              Back to documents
            </Link>
          </div>
        </div>
      </Layout>
    )
  }

  const summary = document.summary
  const flaggedTerms: FlaggedTerm[] = summary ? parseFlaggedTerms(summary.flaggedTerms) : []
  const keyFacts: KeyFacts = summary ? parseKeyFacts(summary.keyFacts) : { amounts: [], dates: [], parties: [] }
  const hasKeyFacts = keyFacts.amounts.length > 0 || keyFacts.dates.length > 0 || keyFacts.parties.length > 0

  return (
    <Layout user={user} onLogout={onLogout}>
      <div className="mx-auto max-w-4xl px-6 py-8">
        {/* Top bar: back link + actions */}
        <div className="mb-6 flex items-center justify-between">
          <Link to="/" className="text-sm font-medium text-indigo-600 hover:text-indigo-500">
            &larr; Back to documents
          </Link>
          <div className="flex items-center gap-2">
            {document.status === 'READY' && (
              <button
                onClick={() => setIsChatOpen(true)}
                className="rounded-md border border-indigo-600 px-3 py-1.5 text-sm font-medium text-indigo-600 transition-colors hover:bg-indigo-50"
              >
                Ask a question
              </button>
            )}
            {/* Kebab menu */}
            <div ref={menuRef} className="relative">
              <button
                onClick={() => setIsMenuOpen((prev) => !prev)}
                className="rounded-md p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
                aria-label="More actions"
              >
                <svg className="h-5 w-5" fill="currentColor" viewBox="0 0 20 20">
                  <path d="M10 6a2 2 0 110-4 2 2 0 010 4zM10 12a2 2 0 110-4 2 2 0 010 4zM10 18a2 2 0 110-4 2 2 0 010 4z" />
                </svg>
              </button>
              {isMenuOpen && (
                <div className="absolute right-0 z-10 mt-1 w-40 rounded-lg border border-slate-200 bg-white py-1 shadow-lg">
                  <button
                    onClick={() => {
                      setIsMenuOpen(false)
                      deleteDialogRef.current?.showModal()
                    }}
                    className="flex w-full items-center gap-2 px-3 py-2 text-sm text-red-600 hover:bg-red-50"
                  >
                    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
                    </svg>
                    Delete
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Title + metadata */}
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-2xl font-bold text-slate-900">{document.title}</h2>
            <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-slate-500">
              <span>{DOCUMENT_TYPE_LABELS[document.documentType] ?? 'Unknown'}</span>
              <span className="text-slate-300">&middot;</span>
              <span>{INPUT_TYPE_LABELS[document.inputType] ?? document.inputType}</span>
              {document.originalFilename && (
                <>
                  <span className="text-slate-300">&middot;</span>
                  <span className="truncate max-w-[200px]">{document.originalFilename}</span>
                </>
              )}
              <span className="text-slate-300">&middot;</span>
              <span>{formatDate(document.createdAt)}</span>
            </div>
          </div>
          <span className={`shrink-0 rounded px-2 py-0.5 text-xs font-medium ${statusClasses(document.status)}`}>
            {STATUS_LABELS[document.status] ?? document.status}
          </span>
        </div>

        {/* Processing state */}
        {document.status === 'PROCESSING' && (
          <div className="mt-6 rounded-lg border border-amber-200 bg-amber-50 p-4">
            <p className="font-medium text-amber-800">Processing your document…</p>
            {step && <p className="mt-1 text-sm text-amber-700">{step}</p>}
            {sseError && <p className="mt-1 text-sm text-red-600">{sseError}</p>}
          </div>
        )}

        {/* Failed state */}
        {document.status === 'FAILED' && (
          <div className="mt-6 rounded-lg border border-red-200 bg-red-50 p-4">
            <p className="font-medium text-red-800">Processing failed</p>
            {document.errorMessage && (
              <p className="mt-1 text-sm text-red-700">{document.errorMessage}</p>
            )}
          </div>
        )}

        {/* Ready state — full results */}
        {document.status === 'READY' && summary && (
          <div className="mt-8 space-y-8">
            {/* Flagged Terms — the visual centrepiece */}
            <section className="rounded-lg border border-indigo-100 bg-indigo-50/50 p-6">
              <h3 className="text-lg font-semibold text-slate-900">Jargon Explained</h3>
              <p className="mt-1 text-sm text-slate-500">
                Plain-English definitions for the technical terms in your document.
              </p>

              {flaggedTerms.length > 0 ? (
                <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
                  {flaggedTerms.map((item, i) => (
                    <div
                      key={i}
                      className="rounded-md border-l-4 border-indigo-400 bg-white p-4 shadow-sm"
                    >
                      <p className="font-semibold text-slate-900">{item.term}</p>
                      <p className="mt-1 text-sm text-slate-600">{item.definition}</p>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="mt-4 text-sm text-slate-400">
                  No technical jargon was detected in this document.
                </p>
              )}
            </section>

            {/* Summary */}
            <section className="rounded-lg border border-slate-200 bg-white p-6">
              <h3 className="text-lg font-semibold text-slate-900">Summary</h3>
              <p className="mt-3 whitespace-pre-line text-sm leading-relaxed text-slate-700">
                {summary.plainSummary}
              </p>
            </section>

            {/* Key Facts */}
            {hasKeyFacts && (
              <section className="rounded-lg border border-slate-200 bg-white p-6">
                <h3 className="text-lg font-semibold text-slate-900">Key Facts</h3>
                <div className="mt-4 space-y-5">
                  <KeyFactGroup label="Amounts" facts={keyFacts.amounts} />
                  <KeyFactGroup label="Dates" facts={keyFacts.dates} />
                  <KeyFactGroup label="Parties" facts={keyFacts.parties} />
                </div>
              </section>
            )}
          </div>
        )}
      </div>

      {/* Delete confirmation dialog */}
      <dialog
        ref={deleteDialogRef}
        onClick={(e) => { if (e.target === deleteDialogRef.current) deleteDialogRef.current.close() }}
        className="fixed top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full max-w-sm rounded-xl border-0 p-0 shadow-xl backdrop:bg-black/50"
      >
        <div className="p-6">
          <h3 className="text-lg font-semibold text-slate-900">Delete document</h3>
          <p className="mt-2 text-sm text-slate-600">
            Are you sure you want to delete <span className="font-medium text-slate-900">{document.title}</span>? This action cannot be undone.
          </p>
          {deleteMutation.error && (
            <p className="mt-3 text-sm text-red-600">{deleteMutation.error.message}</p>
          )}
          <div className="mt-6 flex justify-end gap-3">
            <button
              onClick={() => { deleteMutation.reset(); deleteDialogRef.current?.close() }}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
            >
              Cancel
            </button>
            <button
              disabled={deleteMutation.isPending}
              onClick={() => {
                deleteMutation.mutate(document.id, {
                  onSuccess: () => {
                    deleteDialogRef.current?.close()
                    navigate('/')
                  },
                })
              }}
              className="rounded-md bg-red-600 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-red-700 disabled:opacity-50"
            >
              {deleteMutation.isPending ? 'Deleting\u2026' : 'Delete'}
            </button>
          </div>
        </div>
      </dialog>

      <ChatPane open={isChatOpen} onClose={() => setIsChatOpen(false)} />
    </Layout>
  )
}

/** Renders a labelled group of key facts. Hidden when the list is empty. */
function KeyFactGroup({ label, facts }: { label: string; facts: KeyFact[] }) {
  if (facts.length === 0) return null

  return (
    <div>
      <h4 className="text-sm font-semibold uppercase tracking-wide text-slate-500">{label}</h4>
      <ul className="mt-2 space-y-2">
        {facts.map((fact, i) => (
          <li key={i} className="text-sm text-slate-700">
            <span className="font-medium">{fact.label}</span>
            <span className="mx-1 text-slate-400">&rarr;</span>
            <span>{fact.value}</span>
            {fact.context && (
              <span className="ml-2 text-slate-400">({fact.context})</span>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
