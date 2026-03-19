import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { server } from '../test/msw-server'
import { createTestQueryClient } from '../test/test-utils'
import { useDeleteDocument } from './useDeleteDocument'
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

describe('useDeleteDocument', () => {
  it('calls DELETE endpoint with correct ID', async () => {
    let deletedId: string | undefined

    server.use(
      http.delete('/api/documents/:id', ({ params }) => {
        deletedId = params.id as string
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useDeleteDocument(), { wrapper: Wrapper })

    result.current.mutate('doc-42')

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(deletedId).toBe('doc-42')
  })

  it('invalidates documents queries on success', async () => {
    const { Wrapper, queryClient } = createWrapper()

    // Seed a documents query so we can check it gets invalidated
    queryClient.setQueryData(['documents', { page: 0 }], { content: [] })
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useDeleteDocument(), { wrapper: Wrapper })

    result.current.mutate('doc-1')

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: ['documents'] }),
    )
  })
})
