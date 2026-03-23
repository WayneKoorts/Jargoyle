import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { server } from '../test/msw-server'
import { createTestQueryClient } from '../test/test-utils'
import { useUpdateUser } from './useUpdateUser'
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

describe('useUpdateUser', () => {
  it('sends PUT with role, displayName, and email', async () => {
    let capturedBody: Record<string, string | boolean> | undefined

    server.use(
      http.put('/api/admin/users/:id', async ({ request }) => {
        capturedBody = await request.json() as Record<string, string | boolean>
        return HttpResponse.json({
          id: 'user-2',
          email: 'new@example.com',
          displayName: 'New Name',
          oauthProvider: 'google',
          role: 'ADMIN',
          enabled: true,
          createdAt: '2026-02-20T14:00:00Z',
          lastLoginAt: null,
        })
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useUpdateUser(), { wrapper: Wrapper })

    result.current.mutate({
      id: 'user-2',
      data: { role: 'ADMIN', displayName: 'New Name', email: 'new@example.com', enabled: true },
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(capturedBody).toEqual({
      role: 'ADMIN',
      displayName: 'New Name',
      email: 'new@example.com',
      enabled: true,
    })
  })

  it('invalidates admin user queries on success', async () => {
    const { Wrapper, queryClient } = createWrapper()

    // Seed queries so we can verify invalidation
    queryClient.setQueryData(['admin', 'users', {}], { content: [] })
    queryClient.setQueryData(['admin', 'user', 'user-2'], { id: 'user-2' })
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useUpdateUser(), { wrapper: Wrapper })

    result.current.mutate({ id: 'user-2', data: { role: 'ADMIN' } })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: ['admin', 'users'] }),
    )
    expect(invalidateSpy).toHaveBeenCalledWith(
      expect.objectContaining({ queryKey: ['admin', 'user', 'user-2'] }),
    )
  })

  it('reports error on API failure', async () => {
    server.use(
      http.put('/api/admin/users/:id', () => {
        return new HttpResponse('Cannot demote the last admin user', { status: 409 })
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useUpdateUser(), { wrapper: Wrapper })

    result.current.mutate({ id: 'user-1', data: { role: 'USER' } })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })
  })
})
