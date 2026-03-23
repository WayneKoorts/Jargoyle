import { useState } from 'react'
import { AlertCircle, ChevronDown, ExternalLink, Loader2 } from 'lucide-react'
import { useDocumentContent } from '../hooks/useDocumentContent'
import { API_BASE_URL } from '../constants'

interface DocumentViewerProps {
  documentId: string
  originalFilename: string | null
}

/**
 * Inline viewer for the original document content, displayed as a collapsible
 * accordion. Content is lazy-loaded: the backend is only called when the user
 * first expands the section. Subsequent collapses and expansions use cached data.
 *
 * Renders PDFs in an iframe (with a mobile link fallback), images in an img tag,
 * and text in a pre block.
 */
export default function DocumentViewer({ documentId, originalFilename }: DocumentViewerProps) {
  const [isExpanded, setIsExpanded] = useState(false)
  const { content, isLoading, isError, error, isFetched, load } = useDocumentContent(documentId)

  function handleToggle() {
    const expanding = !isExpanded
    setIsExpanded(expanding)

    // Trigger fetch on first expansion
    if (expanding && !isFetched && !isLoading) {
      load()
    }
  }

  // The URL to use for "Open in new tab" — either the presigned/backend URL
  // from the response, or the streaming endpoint as a fallback for text.
  const externalUrl = content?.url ?? (content?.text != null
    ? `${API_BASE_URL}/documents/${documentId}/original/stream`
    : null)

  return (
    <section className="overflow-hidden rounded-lg border border-slate-200 bg-white">
      {/* Accordion header — always visible */}
      {/* Header row — button and link are siblings to avoid nesting interactive elements */}
      <div className="flex items-center px-5 py-4">
        <button
          type="button"
          onClick={handleToggle}
          className="flex flex-1 cursor-pointer select-none items-center gap-2 text-left"
          aria-expanded={isExpanded}
        >
          <h3 className="min-w-0 truncate text-lg font-semibold text-slate-900">
            Original Document
          </h3>

          {/* Inline loading spinner in header */}
          {isLoading && (
            <Loader2
              className="h-4 w-4 shrink-0 animate-spin text-slate-400"
              aria-label="Loading"
            />
          )}

          <span className="flex-1" />

          <ChevronDown
            className={`h-5 w-5 shrink-0 text-slate-400 transition-transform duration-150 ${
              isExpanded ? 'rotate-180' : ''
            }`}
            aria-hidden="true"
          />
        </button>

        {/* Open in new tab — shown after content loads, outside the button */}
        {isFetched && !isError && externalUrl && (
          <a
            href={externalUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="ml-2 flex shrink-0 items-center rounded p-1 text-slate-400 transition-colors hover:text-slate-600"
            title="Open original in new tab"
            aria-label="Open original in new tab"
          >
            <ExternalLink className="h-4 w-4" aria-hidden="true" />
          </a>
        )}
      </div>

      {/* Accordion body — conditionally rendered */}
      {isExpanded && (
        <div className="border-t border-slate-100 px-5 py-4">
          {isLoading && (
            <div className="flex items-center gap-3 text-slate-500">
              <Loader2 className="h-5 w-5 animate-spin" aria-hidden="true" />
              <span className="text-sm">Loading document…</span>
            </div>
          )}

          {isError && (
            <div>
              <div className="flex items-center gap-3">
                <AlertCircle className="h-5 w-5 shrink-0 text-red-500" aria-hidden="true" />
                <span className="text-sm text-red-700">
                  {error instanceof Error ? error.message : 'Failed to load document content.'}
                </span>
              </div>
              <button
                onClick={(e) => { e.stopPropagation(); load() }}
                className="mt-3 rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
              >
                Try again
              </button>
            </div>
          )}

          {isFetched && !isError && content && (
            <div className="overflow-hidden rounded-md border border-slate-100 bg-slate-50">
              {content.inputType === 'PDF' && content.url && (
                <PdfViewer url={content.url} filename={originalFilename} />
              )}
              {content.inputType === 'IMAGE' && content.url && (
                <ImageViewer url={content.url} filename={originalFilename} />
              )}
              {content.inputType === 'TEXT' && content.text != null && (
                <TextViewer text={content.text} />
              )}
            </div>
          )}
        </div>
      )}
    </section>
  )
}

/** Renders a PDF via an iframe on md+ screens, with a link fallback for mobile. */
function PdfViewer({ url, filename }: { url: string; filename: string | null }) {
  return (
    <>
      {/* Desktop: inline iframe */}
      <iframe
        src={url}
        title={filename ?? 'PDF document'}
        className="hidden min-h-[400px] w-full max-h-[60vh] md:block"
      />
      {/* Mobile: link to open in browser's PDF viewer */}
      <div className="p-4 md:hidden">
        <a
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-2 rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
        >
          <ExternalLink className="h-4 w-4" aria-hidden="true" />
          Open PDF in viewer
        </a>
      </div>
    </>
  )
}

/** Renders an image with responsive scaling. */
function ImageViewer({ url, filename }: { url: string; filename: string | null }) {
  return (
    <img
      src={url}
      alt={filename ?? 'Document image'}
      className="max-h-[60vh] w-full bg-slate-100 object-contain"
    />
  )
}

/** Renders plain text preserving whitespace and formatting. */
function TextViewer({ text }: { text: string }) {
  return (
    <pre className="max-h-[60vh] w-full overflow-auto whitespace-pre-wrap p-4 font-mono text-sm leading-relaxed text-slate-700">
      {text}
    </pre>
  )
}
