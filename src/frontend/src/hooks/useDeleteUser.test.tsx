import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { server } from '../test/msw-server'
import { createTestQueryClient } from '../test/test-utils'
import { useDeleteUser } from './useDeleteUser'
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

describe('useDeleteUser', () => {
  it('calls DELETE endpoint with correct ID', async () => {
    let deletedId: string | undefined

    server.use(
      http.delete('/api/admin/users/:id', ({ params }) => {
        deletedId = params.id as string
        return new HttpResponse(null, { status: 204 })
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useDeleteUser(), { wrapper: Wrapper })

    result.current.mutate('user-42')

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(deletedId).toBe('user-42')
  })

  it('invalidates admin users queries on success', async () => {
    const { Wrapper, queryClient } = createWrapper()

    queryClient.setQueryData(['admin', 'users', {}], { content: [] })
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useDeleteUser(), { wrapper: Wrapper })

    result.current.mutate('user-2')

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: ['admin', 'users'] }),
    )
  })

  it('reports error on API failure', async () => {
    server.use(
      http.delete('/api/admin/users/:id', () => {
        return new HttpResponse('Cannot delete your own account', { status: 409 })
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useDeleteUser(), { wrapper: Wrapper })

    result.current.mutate('user-1')

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })
  })
})
