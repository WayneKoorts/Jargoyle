import { useEffect, useMemo, useRef, useState } from 'react'
import { API_BASE_URL } from '../constants'
import type { ProcessingStatusEvent } from '../api/documents'

interface DocumentStatusState {
  status: ProcessingStatusEvent['status'] | null
  step: string | null
  errorMessage: string | null
  isComplete: boolean
  isFailed: boolean
}

const INITIAL_STATE: DocumentStatusState = {
  status: null,
  step: null,
  errorMessage: null,
  isComplete: false,
  isFailed: false,
}

// Internal state includes the documentId it belongs to, so we can
// detect stale data without a synchronous setState in the effect
interface InternalState extends DocumentStatusState {
  forDocumentId: string | null
}

const INITIAL_INTERNAL: InternalState = { ...INITIAL_STATE, forDocumentId: null }

/**
 * Subscribes to Server-Sent Events for a document's processing status.
 *
 * Pass a documentId to start listening; pass null to disconnect.
 * The hook cleans up the EventSource on unmount or when the ID changes.
 *
 * EventSource is a browser-native API (like WebSocket but simpler,
 * one-directional). The server sends newline-delimited events and the
 * browser reconnects automatically on transient failures. We set
 * withCredentials so the session cookie is sent with the request —
 * Spring Security needs it to identify the user.
 */
export function useDocumentStatus(documentId: string | null): DocumentStatusState {
  const [internal, setInternal] = useState<InternalState>(INITIAL_INTERNAL)
  const eventSourceRef = useRef<EventSource | null>(null)

  useEffect(() => {
    if (!documentId) return

    const url = `${API_BASE_URL}/documents/${documentId}/status`
    const eventSource = new EventSource(url, { withCredentials: true })
    eventSourceRef.current = eventSource

    eventSource.onmessage = (event) => {
      try {
        const data: ProcessingStatusEvent = JSON.parse(event.data)
        setInternal({
          forDocumentId: documentId,
          status: data.status,
          step: data.step,
          errorMessage: data.errorMessage,
          isComplete: data.status === 'READY',
          isFailed: data.status === 'FAILED',
        })

        // Server closes the stream after READY or FAILED, but close our
        // end too so the browser doesn't try to reconnect
        if (data.status === 'READY' || data.status === 'FAILED') {
          eventSource.close()
        }
      } catch {
        // Ignore malformed events — the next one will likely be fine
      }
    }

    eventSource.onerror = () => {
      // EventSource auto-reconnects on transient errors, but if the
      // readyState is CLOSED the connection is gone for good
      if (eventSource.readyState === EventSource.CLOSED) {
        setInternal((prev) => ({
          ...prev,
          errorMessage: prev.errorMessage ?? 'Lost connection to server.',
          isFailed: true,
        }))
      }
    }

    return () => {
      eventSource.close()
      eventSourceRef.current = null
    }
  }, [documentId])

  // Return initial state when there's no documentId or when internal
  // state belongs to a different document (stale from previous subscription)
  return useMemo(() => {
    if (!documentId || internal.forDocumentId !== documentId) {
      return INITIAL_STATE
    }
    return internal
  }, [documentId, internal])
}
