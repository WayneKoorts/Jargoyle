import type { Message } from '../../api/conversations'
import FormattedContent from './FormattedContent'
import SourceChunkDisplay from './SourceChunkDisplay'

interface MessageBubbleProps {
  message: Message
  isStreaming?: boolean
  isThinking?: boolean
}

/**
 * Renders a single chat message as a styled bubble.
 *
 * User messages are right-aligned with an accent background and plain text.
 * Assistant messages are left-aligned with a light background and markdown
 * formatting (bold, italic, code, lists). Supports three transient states:
 * - `isThinking` — animated dots before the first token arrives
 * - `isStreaming` — pulsing cursor appended to partial content
 * - default — completed message with optional source attribution
 */
export default function MessageBubble({ message, isStreaming = false, isThinking = false }: MessageBubbleProps) {
  const isUser = message.role === 'USER'

  if (isUser) {
    return (
      <div className="flex justify-end">
        <div className="max-w-[90%] rounded-2xl rounded-br-sm bg-indigo-600 px-4 py-2.5 text-sm text-white">
          <p className="whitespace-pre-line">{message.content}</p>
        </div>
      </div>
    )
  }

  // Assistant message — thinking state
  if (isThinking) {
    return (
      <div className="flex justify-start">
        <div className="rounded-2xl rounded-bl-sm border border-slate-200 bg-white px-4 py-3">
          <div className="flex items-center gap-1" role="status" aria-label="AI is thinking">
            <span className="sr-only">AI is thinking</span>
            <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-slate-400" style={{ animationDelay: '0ms' }} />
            <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-slate-400" style={{ animationDelay: '150ms' }} />
            <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-slate-400" style={{ animationDelay: '300ms' }} />
          </div>
        </div>
      </div>
    )
  }

  // Assistant message — streaming or completed
  const showSources = !isStreaming
    && message.sourceChunks != null
    && message.sourceChunks.length > 0

  return (
    <div className="flex justify-start">
      <div className="max-w-[90%] rounded-2xl rounded-bl-sm border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800">
        <FormattedContent text={message.content} />
        {isStreaming && <span className="ml-0.5 inline-block animate-pulse">▊</span>}
        {showSources && <SourceChunkDisplay sourceChunks={message.sourceChunks!} />}
      </div>
    </div>
  )
}
