import type { SourceChunkReference } from '../../api/conversations'

interface SourceChunkDisplayProps {
  sourceChunks: SourceChunkReference[]
}

/**
 * Renders source attribution tags for an assistant message.
 *
 * Each tag is a numbered pill ("Section 1", "Section 4") showing which
 * document chunks the response drew from. Hovering reveals a preview
 * of the chunk text via a native tooltip.
 *
 * The numbering is human-friendly: chunkIndex + 1 (the backend uses
 * zero-based indices).
 */
export default function SourceChunkDisplay({ sourceChunks }: SourceChunkDisplayProps) {
  if (sourceChunks.length === 0) return null

  return (
    <div className="mt-2 flex flex-wrap gap-1.5">
      {sourceChunks.map((chunk) => (
        <span
          key={chunk.chunkId}
          title={chunk.preview}
          className="cursor-help rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600 transition-colors hover:bg-slate-200"
        >
          Section {chunk.chunkIndex + 1}
        </span>
      ))}
    </div>
  )
}
