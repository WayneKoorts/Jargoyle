import { useEffect, useRef, useState } from 'react'
import { ArrowUp } from 'lucide-react'
import type { SuggestedQuestion, Message } from '../../api/conversations'
import { useMessages } from '../../hooks/useMessages'
import { useChatStream } from '../../hooks/useChatStream'
import MessageBubble from './MessageBubble'
import SuggestedQuestions from './SuggestedQuestions'

interface ChatInterfaceProps {
  conversationId: string
  suggestedQuestions: SuggestedQuestion[]
}

/**
 * The main chat panel: scrollable message list, streaming display,
 * suggested questions for empty conversations, and a text input.
 *
 * Messages are anchored to the bottom of the scroll container (like
 * iMessage/WhatsApp) using flexbox justify-end. New messages appear
 * at the bottom and older history is scrollable above.
 */
export default function ChatInterface({ conversationId, suggestedQuestions }: ChatInterfaceProps) {
  const { messages, isLoading, loadMore, hasMore, isLoadingMore } = useMessages(conversationId)
  const { sendMessage, streamingContent, isStreaming, error, optimisticMessage } = useChatStream(conversationId)
  const [inputValue, setInputValue] = useState('')
  const scrollContainerRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  // Auto-scroll to the bottom when new messages arrive or streaming content updates.
  // Uses scrollTop on the container rather than scrollIntoView on an anchor element,
  // because scrollIntoView can scroll *parent* containers (including the summary
  // panel), causing a visible gap below the page content.
  useEffect(() => {
    const el = scrollContainerRef.current
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  }, [messages.length, streamingContent, optimisticMessage])

  // Refocus the input when streaming finishes so the user can type immediately
  useEffect(() => {
    if (!isStreaming) {
      inputRef.current?.focus()
    }
  }, [isStreaming])

  function handleSubmit() {
    const trimmed = inputValue.trim()
    if (!trimmed || isStreaming) return

    setInputValue('')
    sendMessage(trimmed)
    inputRef.current?.focus()
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    // Enter submits, Shift+Enter inserts a newline
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit()
    }
  }

  function handleSelectSuggestion(questionText: string) {
    sendMessage(questionText)
  }

  const isEmpty = messages.length === 0 && !isStreaming && !optimisticMessage

  // Show the thinking indicator when streaming has started but no tokens
  // have arrived yet — the gap between the user hitting send and the first
  // TOKEN event from the server.
  const isThinking = isStreaming && !streamingContent

  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      {/* Message area — justify-end pushes content to the bottom like a real chat */}
      <div ref={scrollContainerRef} className="flex flex-1 flex-col overflow-y-auto px-4 py-4">
        {/* Load earlier messages */}
        {hasMore && (
          <div className="mb-4 text-centre">
            <button
              onClick={() => loadMore()}
              disabled={isLoadingMore}
              className="rounded-md border border-slate-300 px-3 py-1 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-50 disabled:opacity-50"
            >
              {isLoadingMore ? 'Loading…' : 'Load earlier messages'}
            </button>
          </div>
        )}

        {/* Loading state */}
        {isLoading && (
          <div className="flex flex-1 items-end justify-centre pb-4">
            <p className="text-sm text-slate-400">Loading messages…</p>
          </div>
        )}

        {/* Empty state: show suggested questions anchored to the bottom */}
        {!isLoading && isEmpty && suggestedQuestions.length > 0 && (
          <div className="flex flex-1 items-end justify-centre">
            <SuggestedQuestions questions={suggestedQuestions} onSelect={handleSelectSuggestion} />
          </div>
        )}

        {/* Empty with no suggestions */}
        {!isLoading && isEmpty && suggestedQuestions.length === 0 && (
          <div className="flex flex-1 items-end justify-centre pb-4">
            <p className="text-sm text-slate-400">
              Ask a question about this document to get started.
            </p>
          </div>
        )}

        {/* Messages — mt-auto pushes the list to the bottom of the container */}
        {!isLoading && !isEmpty && (
          <div className="mt-auto space-y-3">
            {messages.map((msg) => (
              <MessageBubble key={msg.id} message={msg} />
            ))}

            {/* Optimistic user message (shown before server confirms) */}
            {optimisticMessage && (
              <MessageBubble message={optimisticMessage} />
            )}

            {/* Thinking indicator — shown before the first token arrives */}
            {isThinking && (
              <MessageBubble message={makeThinkingMessage()} isThinking />
            )}

            {/* Streaming assistant ghost bubble — shown once tokens start */}
            {isStreaming && streamingContent && (
              <MessageBubble
                message={makeStreamingMessage(streamingContent)}
                isStreaming
              />
            )}
          </div>
        )}

      </div>

      {/* Error banner */}
      {error && (
        <div className="border-t border-red-200 bg-red-50 px-4 py-2">
          <p className="text-sm text-red-700">{error}</p>
        </div>
      )}

      {/* Input area */}
      <div className="border-t border-slate-200 bg-white px-4 py-3">
        <div className="flex gap-2">
          <textarea
            ref={inputRef}
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={isStreaming}
            placeholder="Ask a question about this document…"
            rows={3}
            className="flex-1 resize-none rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-800 placeholder:text-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 disabled:bg-slate-50 disabled:text-slate-400"
          />
          <button
            onClick={handleSubmit}
            disabled={isStreaming || !inputValue.trim()}
            title="Send"
            aria-label="Send"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-indigo-600 text-white transition-colors hover:bg-indigo-700 disabled:opacity-50"
          >
            <ArrowUp className="h-4 w-4" strokeWidth={2.5} aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>
  )
}

/**
 * Builds a temporary Message for the "thinking" state — the gap between
 * the user sending a message and the first TOKEN arriving from the server.
 */
function makeThinkingMessage(): Message {
  return {
    id: 'thinking',
    role: 'ASSISTANT',
    content: '',
    sourceChunks: null,
    createdAt: new Date().toISOString(),
  }
}

/**
 * Builds a temporary Message object for the streaming assistant response.
 * This is displayed as a "ghost" bubble with a pulsing cursor while tokens
 * arrive from the server.
 */
function makeStreamingMessage(content: string): Message {
  return {
    id: 'streaming',
    role: 'ASSISTANT',
    content,
    sourceChunks: null,
    createdAt: new Date().toISOString(),
  }
}
