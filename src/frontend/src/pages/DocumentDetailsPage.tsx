import { useCallback, useEffect, useRef, useState } from 'react'
import { EllipsisVertical, Sparkles, Trash2 } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import type { UserProfile } from '../api/auth'
import type { SuggestedQuestion } from '../api/conversations'
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
import ChatInterface from '../components/chat/ChatInterface'
import ConversationSidebar from '../components/chat/ConversationSidebar'
import { useConversations } from '../hooks/useConversations'
import { useCreateConversation } from '../hooks/useCreateConversation'
import { useDeleteDocument } from '../hooks/useDeleteDocument'
import { useDocument } from '../hooks/useDocument'
import { useDocumentStatus } from '../hooks/useDocumentStatus'
import { displayTitle, formatDate } from '../utils/display'

interface DocumentDetailsPageProps {
  user: UserProfile
  onLogout: () => void
}

const STATUS_COLOURS: Record<string, string> = {
  READY: 'bg-green-100 text-green-800',
  PROCESSING: 'bg-amber-100 text-amber-800',
  QUEUED: 'bg-sky-100 text-sky-800',
  PENDING_UPLOAD: 'bg-slate-100 text-slate-800',
  UPLOADING: 'bg-blue-100 text-blue-800',
  FAILED: 'bg-red-100 text-red-800',
}

function statusClasses(status: string): string {
  return STATUS_COLOURS[status] ?? 'bg-slate-100 text-slate-800'
}

export default function DocumentDetailsPage({ user, onLogout }: DocumentDetailsPageProps) {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { document, isLoading, isError, refetch } = useDocument(id)

  // Chat drawer state — hidden by default
  const [isChatOpen, setIsChatOpen] = useState(false)

  // Active conversation and suggested questions for the chat panel
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null)
  const [suggestedQuestions, setSuggestedQuestions] = useState<SuggestedQuestion[]>([])

  // Kebab dropdown menu
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

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
  const isProcessing = document != null && document.status !== 'READY' && document.status !== 'FAILED'
  const { step, isComplete, isFailed, errorMessage: sseError } = useDocumentStatus(
    isProcessing ? document.id : null,
  )

  // Refetch the document when SSE signals processing is complete (or failed)
  useEffect(() => {
    if (isComplete || isFailed) {
      refetch()
    }
  }, [isComplete, isFailed, refetch])

  // Conversation management — only active when the document is READY
  const isReady = document?.status === 'READY'
  const { conversations, isLoading: conversationsLoading } = useConversations(isReady ? document!.id : '')
  const createConversation = useCreateConversation(document?.id ?? '')

  // Guard ref to prevent double-creation in React StrictMode
  const autoCreatedRef = useRef(false)

  // Auto-create a conversation on first visit to a READY document with none
  useEffect(() => {
    if (
      isReady
      && !conversationsLoading
      && conversations.length === 0
      && activeConversationId === null
      && !createConversation.isPending
      && !autoCreatedRef.current
    ) {
      autoCreatedRef.current = true
      createConversation.mutateAsync().then((result) => {
        setActiveConversationId(result.id)
        setSuggestedQuestions(result.suggestedQuestions)
      })
    }
  }, [isReady, conversationsLoading, conversations.length, activeConversationId, createConversation])

  // Derive the effective conversation ID: use the explicit selection if set,
  // otherwise fall back to the most recent conversation.
  const effectiveConversationId = activeConversationId ?? conversations[0]?.id ?? null

  function handleSelectConversation(conversationId: string) {
    setActiveConversationId(conversationId)
    setSuggestedQuestions([])
  }

  function handleNewConversation() {
    createConversation.mutateAsync().then((result) => {
      setActiveConversationId(result.id)
      setSuggestedQuestions(result.suggestedQuestions)
    })
  }

  // --- Early returns for loading/error states ---

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
  const title = displayTitle(document)
  const flaggedTerms: FlaggedTerm[] = summary
    ? parseFlaggedTerms(summary.flaggedTerms).sort((a, b) =>
        a.term.localeCompare(b.term, undefined, { sensitivity: 'base' }),
      )
    : []
  const keyFacts: KeyFacts = summary ? parseKeyFacts(summary.keyFacts) : { amounts: [], dates: [], parties: [] }
  const hasKeyFacts = keyFacts.amounts.length > 0 || keyFacts.dates.length > 0 || keyFacts.parties.length > 0

  // --- Non-READY states: single-column layout ---

  if (!isReady) {
    return (
      <Layout user={user} onLogout={onLogout}>
        <div className="mx-auto max-w-4xl px-6 py-8">
          <TopBar
            title={title}
            document={document}
            isMenuOpen={isMenuOpen}
            setIsMenuOpen={setIsMenuOpen}
            menuRef={menuRef}
            deleteDialogRef={deleteDialogRef}
          />

          {isProcessing && (
            <div className="mt-6 rounded-lg border border-amber-200 bg-amber-50 p-4">
              <div className="flex items-start gap-3">
                <div
                  role="status"
                  aria-label="Document processing"
                  className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center"
                >
                  <span className="sr-only">Document processing</span>
                  <span className="h-5 w-5 animate-spin rounded-full border-2 border-amber-300 border-t-amber-700" />
                </div>
                <div>
                  <p className="font-medium text-amber-800">{STATUS_LABELS[document.status] ?? document.status}</p>
                  {step && <p className="mt-1 text-sm text-amber-700">{step}</p>}
                </div>
              </div>
              {sseError && <p className="mt-1 text-sm text-red-600">{sseError}</p>}
            </div>
          )}

          {document.status === 'FAILED' && (
            <div className="mt-6 rounded-lg border border-red-200 bg-red-50 p-4">
              <p className="font-medium text-red-800">Processing failed</p>
              {document.errorMessage && (
                <p className="mt-1 text-sm text-red-700">{document.errorMessage}</p>
              )}
            </div>
          )}
        </div>

        <DeleteDialog
          title={title}
          documentId={document.id}
          deleteDialogRef={deleteDialogRef}
          deleteMutation={deleteMutation}
          navigate={navigate}
        />
      </Layout>
    )
  }

  // --- READY state: full-width summary with collapsible chat drawer ---

  return (
    <Layout user={user} onLogout={onLogout} fullHeight>
      {/* Top bar */}
      <div className="shrink-0 border-b border-slate-200 px-6 py-3">
        <TopBar
          title={title}
          document={document}
          isMenuOpen={isMenuOpen}
          setIsMenuOpen={setIsMenuOpen}
          menuRef={menuRef}
          deleteDialogRef={deleteDialogRef}
          chatToggle={
            <button
              onClick={() => setIsChatOpen((prev) => !prev)}
              className={`flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                isChatOpen
                  ? 'border border-slate-300 bg-white text-slate-600 hover:bg-slate-50'
                  : 'bg-indigo-600 text-white hover:bg-indigo-700'
              }`}
            >
              <Sparkles className="h-4 w-4" aria-hidden="true" />
              {isChatOpen ? 'Hide chat' : 'Ask AI about this document'}
            </button>
          }
        />
      </div>

      {/* Main content area */}
      <div className="flex flex-1 overflow-hidden">
        {/* Summary panel — takes remaining space */}
        <div className={`flex-1 overflow-y-auto ${isChatOpen ? 'hidden md:block' : ''}`}>
          <div className="mx-auto max-w-4xl px-6 py-6">
            {summary && (
              <div className="space-y-8">
                {/* Flagged Terms */}
                <section className="rounded-lg border border-indigo-100 bg-indigo-50/50 p-6">
                  <h3 className="text-lg font-semibold text-slate-900">Jargon Explained</h3>
                  <p className="mt-1 text-sm text-slate-500">
                    Plain-English definitions for the technical terms in your document.
                  </p>

                  {flaggedTerms.length > 0 ? (
                    <div className="mt-4 space-y-4">
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
        </div>

        {/* Chat panel — on the right */}
        {/* Chat panel — on the right */}
        {isChatOpen && (
          <div className="flex w-full flex-col border-l border-slate-200 bg-slate-50 md:w-[clamp(450px,30%,600px)]">
            {/* Chat header with conversation selector */}
            <div className="shrink-0 border-b border-slate-200 bg-white px-3 py-2">
              <ConversationSidebar
                documentId={document.id}
                activeConversationId={effectiveConversationId}
                onSelect={handleSelectConversation}
                onNewConversation={handleNewConversation}
              />
            </div>

            {/* Chat content */}
            <div className="flex flex-1 flex-col overflow-hidden">
              {effectiveConversationId ? (
                <ChatInterface
                  key={effectiveConversationId}
                  conversationId={effectiveConversationId}
                  suggestedQuestions={suggestedQuestions}
                />
              ) : (
                <div className="flex flex-1 items-center justify-center">
                  <p className="text-sm text-slate-400">
                    {createConversation.isPending ? 'Starting conversation…' : 'Select a conversation'}
                  </p>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      <DeleteDialog
        title={title}
        documentId={document.id}
        deleteDialogRef={deleteDialogRef}
        deleteMutation={deleteMutation}
        navigate={navigate}
      />
    </Layout>
  )
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

/** Top bar with back link, title, metadata, status badge, and kebab menu. */
function TopBar({
  title,
  document,
  isMenuOpen,
  setIsMenuOpen,
  menuRef,
  deleteDialogRef,
  chatToggle,
}: {
  title: string
  document: { title: string | null; documentType: string; inputType: string; originalFilename: string | null; status: string; createdAt: string }
  isMenuOpen: boolean
  setIsMenuOpen: (open: boolean | ((prev: boolean) => boolean)) => void
  menuRef: React.RefObject<HTMLDivElement | null>
  deleteDialogRef: React.RefObject<HTMLDialogElement | null>
  chatToggle?: React.ReactNode
}) {
  return (
    <>
      <div className="mb-4 flex items-center justify-between">
        <Link to="/" className="text-sm font-medium text-indigo-600 hover:text-indigo-500">
          &larr; Back to documents
        </Link>
        <div className="flex items-center gap-2">
          {/* Kebab menu */}
          <div ref={menuRef} className="relative">
            <button
              onClick={() => setIsMenuOpen((prev: boolean) => !prev)}
              className="rounded-md p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600"
              aria-label="More actions"
            >
              <EllipsisVertical className="h-5 w-5" aria-hidden="true" />
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
                  <Trash2 className="h-4 w-4" aria-hidden="true" />
                  Delete
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Title */}
      <h2 className={`text-2xl font-bold ${document.title ? 'text-slate-900' : 'italic text-slate-500'}`}>
        {title}
      </h2>

      {/* Metadata + status badge — all on one line */}
      <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-slate-500">
        <span>{DOCUMENT_TYPE_LABELS[document.documentType] ?? 'Unknown'}</span>
        <span className="text-slate-300">&middot;</span>
        <span>{INPUT_TYPE_LABELS[document.inputType] ?? document.inputType}</span>
        {document.originalFilename && (
          <>
            <span className="text-slate-300">&middot;</span>
            <span className="max-w-[200px] truncate">{document.originalFilename}</span>
          </>
        )}
        <span className="text-slate-300">&middot;</span>
        <span>{formatDate(document.createdAt)}</span>
        <span className="text-slate-300">&middot;</span>
        <span className={`rounded px-2 py-0.5 text-xs font-medium ${statusClasses(document.status)}`}>
          {STATUS_LABELS[document.status] ?? document.status}
        </span>
        {/* Chat toggle — pushed to the right edge */}
        {chatToggle && (
          <>
            <span className="flex-1" />
            {chatToggle}
          </>
        )}
      </div>
    </>
  )
}

/** Delete confirmation dialog. */
function DeleteDialog({
  title,
  documentId,
  deleteDialogRef,
  deleteMutation,
  navigate,
}: {
  title: string
  documentId: string
  deleteDialogRef: React.RefObject<HTMLDialogElement | null>
  deleteMutation: ReturnType<typeof useDeleteDocument>
  navigate: ReturnType<typeof useNavigate>
}) {
  return (
    <dialog
      ref={deleteDialogRef}
      onClick={(e) => { if (e.target === deleteDialogRef.current) deleteDialogRef.current.close() }}
      className="fixed left-1/2 top-1/2 w-full max-w-sm -translate-x-1/2 -translate-y-1/2 rounded-xl border-0 p-0 shadow-xl backdrop:bg-black/50"
    >
      <div className="p-6">
        <h3 className="text-lg font-semibold text-slate-900">Delete document</h3>
        <p className="mt-2 text-sm text-slate-600">
          Are you sure you want to delete <span className="font-medium text-slate-900">{title}</span>? This action cannot be undone.
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
              deleteMutation.mutate(documentId, {
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
