import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { server } from '../test/msw-server'
import { createTestQueryClient } from '../test/test-utils'
import { useAdminUsers } from './useAdminUsers'
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

const defaultParams = { page: 0, size: 20, sortField: 'displayName', sortDirection: 'asc' as const }

describe('useAdminUsers', () => {
  it('returns user list from the API', async () => {
    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useAdminUsers(defaultParams), { wrapper: Wrapper })

    // Starts loading
    expect(result.current.isLoading).toBe(true)

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.users).toHaveLength(2)
    expect(result.current.users[0].displayName).toBe('Admin User')
    expect(result.current.users[1].displayName).toBe('Regular User')
    expect(result.current.totalElements).toBe(2)
    expect(result.current.isEmpty).toBe(false)
  })

  it('passes sort params to the API', async () => {
    let capturedSort = ''

    server.use(
      http.get('/api/admin/users', ({ request }) => {
        const url = new URL(request.url)
        capturedSort = url.searchParams.get('sort') ?? ''
        return HttpResponse.json({
          content: [],
          totalElements: 0,
          totalPages: 0,
          numberOfElements: 0,
          first: true,
          last: true,
          empty: true,
        })
      }),
    )

    const { Wrapper } = createWrapper()
    renderHook(
      () => useAdminUsers({ page: 0, size: 10, sortField: 'email', sortDirection: 'desc' }),
      { wrapper: Wrapper },
    )

    await waitFor(() => {
      expect(capturedSort).toBe('email,desc')
    })
  })

  it('returns empty defaults when there are no users', async () => {
    server.use(
      http.get('/api/admin/users', () => {
        return HttpResponse.json({
          content: [],
          totalElements: 0,
          totalPages: 0,
          numberOfElements: 0,
          first: true,
          last: true,
          empty: true,
        })
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useAdminUsers(defaultParams), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.users).toEqual([])
    expect(result.current.isEmpty).toBe(true)
    expect(result.current.totalElements).toBe(0)
  })

  it('sets isError on API failure', async () => {
    server.use(
      http.get('/api/admin/users', () => {
        return new HttpResponse(null, { status: 500 })
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useAdminUsers(defaultParams), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })
  })
})
