import { renderHook, act } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { server } from '../test/msw-server'
import { createTestQueryClient } from '../test/test-utils'
import { useCreateConversation } from './useCreateConversation'
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

describe('useCreateConversation', () => {
  it('creates a conversation and returns the result', async () => {
    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useCreateConversation('doc-1'), { wrapper: Wrapper })

    await act(async () => {
      const data = await result.current.mutateAsync()
      expect(data.id).toBe('conv-1')
      expect(data.documentId).toBe('doc-1')
      expect(data.suggestedQuestions).toHaveLength(2)
    })
  })

  it('sends the request to the correct document endpoint', async () => {
    let capturedDocumentId: string | undefined

    server.use(
      http.post('/api/documents/:documentId/conversations', ({ params }) => {
        capturedDocumentId = params.documentId as string
        return HttpResponse.json(
          { id: 'conv-99', documentId: params.documentId, suggestedQuestions: [] },
          { status: 201 },
        )
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useCreateConversation('doc-42'), { wrapper: Wrapper })

    await act(async () => {
      await result.current.mutateAsync()
    })

    expect(capturedDocumentId).toBe('doc-42')
  })

  it('invalidates conversation queries on success', async () => {
    const { Wrapper, queryClient } = createWrapper()

    // Seed the conversation list cache so we can verify invalidation
    queryClient.setQueryData(['conversations', 'doc-1'], [])

    const { result } = renderHook(() => useCreateConversation('doc-1'), { wrapper: Wrapper })

    await act(async () => {
      await result.current.mutateAsync()
    })

    // After invalidation the cached data should be marked stale
    const state = queryClient.getQueryState(['conversations', 'doc-1'])
    expect(state?.isInvalidated).toBe(true)
  })

  it('reports error state on API failure', async () => {
    server.use(
      http.post('/api/documents/:documentId/conversations', () => {
        return new HttpResponse(null, { status: 500 })
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useCreateConversation('doc-1'), { wrapper: Wrapper })

    await act(async () => {
      try {
        await result.current.mutateAsync()
      } catch {
        // Expected — mutateAsync throws on error
      }
    })

    expect(result.current.isError).toBe(true)
  })
})
