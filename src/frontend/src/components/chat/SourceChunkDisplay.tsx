import { useRef, useState } from 'react'
import { ChevronRight } from 'lucide-react'
import { createPortal } from 'react-dom'
import type { SourceChunkReference } from '../../api/conversations'

interface SourceChunkDisplayProps {
  sourceChunks: SourceChunkReference[]
}

/**
 * Number of section pills shown inline before the rest are collapsed
 * behind a toggle. Keeps the chat tidy when the RAG pipeline retrieves
 * many chunks (e.g. broad questions against long documents).
 */
const COLLAPSE_THRESHOLD = 5

/**
 * Renders source attribution tags for an assistant message.
 *
 * Each tag is a numbered pill ("Section 1", "Section 4") showing which
 * document chunks the response drew from. Hovering reveals a styled
 * tooltip with a preview of the chunk text.
 *
 * When more than {@link COLLAPSE_THRESHOLD} chunks are referenced, the
 * pills are hidden behind a compact toggle to avoid visual noise.
 *
 * The numbering is human-friendly: chunkIndex + 1 (the backend uses
 * zero-based indices).
 */
export default function SourceChunkDisplay({ sourceChunks }: SourceChunkDisplayProps) {
  const [expanded, setExpanded] = useState(false)

  if (sourceChunks.length === 0) return null

  const shouldCollapse = sourceChunks.length > COLLAPSE_THRESHOLD

  return (
    <div className="mt-2">
      {/* Collapsed summary — shown when there are many references */}
      {shouldCollapse && !expanded && (
        <button
          type="button"
          onClick={() => setExpanded(true)}
          className="flex items-center gap-1 text-xs text-slate-500 transition-colors hover:text-slate-700"
        >
          <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
          Referenced {sourceChunks.length} sections
        </button>
      )}

      {/* Pill list — always visible when ≤ threshold, toggled when above */}
      {(!shouldCollapse || expanded) && (
        <>
          {shouldCollapse && (
            <button
              type="button"
              onClick={() => setExpanded(false)}
              className="mb-1.5 flex items-center gap-1 text-xs text-slate-500 transition-colors hover:text-slate-700"
            >
              <ChevronRight className="h-3.5 w-3.5 rotate-90" aria-hidden="true" />
              Referenced {sourceChunks.length} sections
            </button>
          )}
          <div className="flex flex-wrap gap-1.5">
            {sourceChunks.map((chunk) => (
              <ChunkPill key={chunk.chunkId} chunk={chunk} />
            ))}
          </div>
        </>
      )}
    </div>
  )
}

/**
 * Single section pill with a custom dark tooltip rendered via a React portal.
 *
 * The tooltip is portalled into `document.body` and positioned with
 * `position: fixed` using the pill's viewport coordinates. This ensures
 * it is never clipped by `overflow` on ancestor containers (e.g. the
 * scrollable chat pane).
 */
function ChunkPill({ chunk }: { chunk: SourceChunkReference }) {
  const [hovered, setHovered] = useState(false)
  const pillRef = useRef<HTMLSpanElement>(null)
  const [position, setPosition] = useState({ top: 0, left: 0 })

  const handleMouseEnter = () => {
    if (pillRef.current) {
      const rect = pillRef.current.getBoundingClientRect()
      setPosition({
        top: rect.top - 8,          // 8px gap above the pill
        left: rect.left + rect.width / 2,  // centred horizontally
      })
    }
    setHovered(true)
  }

  const tooltipId = `tooltip-${chunk.chunkId}`

  return (
    <>
      <span
        ref={pillRef}
        aria-describedby={hovered ? tooltipId : undefined}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={() => setHovered(false)}
        className="cursor-help rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600 transition-colors hover:bg-slate-200"
      >
        Section {chunk.chunkIndex + 1}
      </span>
      {hovered && chunk.preview && createPortal(
        <span
          id={tooltipId}
          role="tooltip"
          style={{ top: position.top, left: position.left }}
          className="pointer-events-none fixed z-50 w-64 -translate-x-1/2 -translate-y-full rounded-lg bg-slate-800 px-3 py-2 text-xs leading-relaxed text-slate-100 shadow-lg animate-fade-in"
        >
          {chunk.preview}
          {/* CSS border triangle pointing down toward the pill */}
          <span
            className="absolute left-1/2 top-full -translate-x-1/2 border-4 border-transparent border-t-slate-800"
            aria-hidden="true"
          />
        </span>,
        document.body,
      )}
    </>
  )
}

