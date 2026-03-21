import { useCallback, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { streamChat } from '../api/conversations'
import type { Message } from '../api/conversations'

/**
 * Manages the lifecycle of streaming a chat response from the backend.
 *
 * This is a custom hook rather than a React Query mutation because streaming
 * doesn't fit the request/response model — we need to update state incrementally
 * as TOKEN events arrive, not just once when the request completes.
 *
 * The hook provides:
 * - `sendMessage(content)` — kicks off the stream
 * - `streamingContent` — accumulates TOKEN payloads for live display
 * - `optimisticMessage` — the user's message, shown immediately before the
 *   server confirms it (cleared on COMPLETE when queries are invalidated)
 * - `isStreaming` / `error` — status flags
 *
 * The consuming component appends `optimisticMessage` (if present) to the
 * messages from useMessages, and shows `streamingContent` as a "ghost"
 * assistant message while streaming.
 */
export function useChatStream(conversationId: string) {
  const [streamingContent, setStreamingContent] = useState('')
  const [isStreaming, setIsStreaming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [optimisticMessage, setOptimisticMessage] = useState<Message | null>(null)
  const queryClient = useQueryClient()

  const sendMessage = useCallback(async (content: string) => {
    setIsStreaming(true)
    setStreamingContent('')
    setError(null)

    // Show the user's message immediately — the consuming component appends
    // this after the messages from useMessages for instant feedback
    setOptimisticMessage({
      id: `optimistic-${Date.now()}`,
      role: 'USER',
      content,
      sourceChunks: null,
      createdAt: new Date().toISOString(),
    })

    try {
      for await (const event of streamChat(conversationId, content)) {
        switch (event.type) {
          case 'TOKEN':
            setStreamingContent(prev => prev + (event.content ?? ''))
            break
          case 'COMPLETE':
            // The server has persisted both the user message and the assistant
            // response. Invalidate queries so useMessages refetches the real
            // data, and useConversations picks up the updated lastMessageAt.
            setStreamingContent('')
            setOptimisticMessage(null)
            queryClient.invalidateQueries({ queryKey: ['messages', conversationId] })
            queryClient.invalidateQueries({ queryKey: ['conversations'] })
            break
          case 'ERROR':
            setError(event.content ?? 'Something went wrong.')
            break
        }
      }
    } catch {
      setError('Failed to send message. Please try again.')
    } finally {
      setIsStreaming(false)
    }
  }, [conversationId, queryClient])

  return {
    sendMessage,
    streamingContent,
    isStreaming,
    error,
    optimisticMessage,
  }
}
