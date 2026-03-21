import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { server } from '../test/msw-server'
import { createTestQueryClient } from '../test/test-utils'
import { useConversations } from './useConversations'
import type { ReactNode } from 'react'

function createWrapper() {
  const queryClient = createTestQueryClient()

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    )
  }

  return { Wrapper, queryClient }
}

describe('useConversations', () => {
  it('returns empty array while loading', () => {
    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useConversations('doc-1'), { wrapper: Wrapper })

    expect(result.current.conversations).toEqual([])
    expect(result.current.isLoading).toBe(true)
  })

  it('returns conversations from the API', async () => {
    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useConversations('doc-1'), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.conversations).toHaveLength(1)
    expect(result.current.conversations[0].id).toBe('conv-1')
    expect(result.current.isError).toBe(false)
  })

  it('fetches conversations for the correct document', async () => {
    let capturedDocumentId: string | undefined

    server.use(
      http.get('/api/documents/:documentId/conversations', ({ params }) => {
        capturedDocumentId = params.documentId as string
        return HttpResponse.json([])
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useConversations('doc-99'), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(capturedDocumentId).toBe('doc-99')
  })

  it('reports error state on failure', async () => {
    server.use(
      http.get('/api/documents/:documentId/conversations', () => {
        return new HttpResponse(null, { status: 500 })
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useConversations('doc-1'), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.conversations).toEqual([])
  })
})
