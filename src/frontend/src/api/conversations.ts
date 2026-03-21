import { apiClient } from './client'
import { API_BASE_URL } from '../constants'
import type { Page } from './types'

// Mirrors the backend SourceChunkReference record
export interface SourceChunkReference {
  chunkId: string
  chunkIndex: number
  preview: string
}

// Mirrors the backend SuggestedQuestion record
export interface SuggestedQuestion {
  text: string
  category: string
}

// Mirrors the backend ConversationResponse record
export interface Conversation {
  id: string
  documentId: string
  title: string | null
  messageCount: number
  createdAt: string
  lastMessageAt: string
}

// Mirrors the backend CreateConversationResponse record
export interface CreateConversationResult {
  id: string
  documentId: string
  suggestedQuestions: SuggestedQuestion[]
}

// Mirrors the backend MessageResponse record
export interface Message {
  id: string
  role: 'USER' | 'ASSISTANT'
  content: string
  sourceChunks: SourceChunkReference[] | null
  createdAt: string
}

// Mirrors the backend ChatStreamEvent record — sent via SSE during streaming
export interface ChatStreamEvent {
  type: 'TOKEN' | 'COMPLETE' | 'ERROR'
  content: string | null
  messageId: string | null
  sourceChunks: SourceChunkReference[] | null
}

/** Creates a new conversation for the given document. */
export function createConversation(documentId: string): Promise<CreateConversationResult> {
  return apiClient<CreateConversationResult>(`/documents/${documentId}/conversations`, {
    method: 'POST',
  })
}

/** Fetches all conversations for a document, ordered by most recent activity. */
export function fetchConversations(documentId: string): Promise<Conversation[]> {
  return apiClient<Conversation[]>(`/documents/${documentId}/conversations`)
}

/** Fetches a page of messages for a conversation (newest-first from the API). */
export function fetchMessages(conversationId: string, page: number = 0): Promise<Page<Message>> {
  const query = new URLSearchParams({
    page: String(page),
    size: '50',
  })

  return apiClient<Page<Message>>(`/conversations/${conversationId}/messages?${query}`)
}

/**
 * Sends a chat message and streams the response as Server-Sent Events.
 *
 * Uses raw fetch instead of apiClient because the response is text/event-stream,
 * not JSON. EventSource isn't an option either — it only supports GET requests,
 * but this endpoint is POST (we need to send the message content in the body).
 *
 * Returns an AsyncGenerator so the calling hook can pull events one at a time
 * with `for await...of`, which integrates naturally with React state updates.
 */
export async function* streamChat(
  conversationId: string,
  content: string,
): AsyncGenerator<ChatStreamEvent> {
  const response = await fetch(
    `${API_BASE_URL}/conversations/${conversationId}/messages`,
    {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content }),
    },
  )

  if (!response.ok) {
    throw new Error(`Chat error: ${response.status}`)
  }

  // ReadableStream reader gives us raw bytes as they arrive from the server.
  // TextDecoder with { stream: true } handles multi-byte characters that may
  // be split across chunks.
  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })

    // SSE format: each event is "data: {json}\n\n" — the double newline
    // separates events. Split on \n\n and keep the last (possibly incomplete)
    // fragment in the buffer for the next iteration.
    const events = buffer.split('\n\n')
    buffer = events.pop()!

    for (const event of events) {
      const dataLine = event.split('\n').find(line => line.startsWith('data:'))
      if (dataLine) {
        const json = dataLine.slice(5).trim()
        if (json) {
          yield JSON.parse(json) as ChatStreamEvent
        }
      }
    }
  }
}
