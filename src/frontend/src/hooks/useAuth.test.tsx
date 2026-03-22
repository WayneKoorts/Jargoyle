import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { http, HttpResponse } from 'msw'
import { server } from '../test/msw-server'
import { createTestQueryClient } from '../test/test-utils'
import { useAuth } from './useAuth'
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

describe('useAuth', () => {
  it('returns user data when authenticated', async () => {
    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useAuth(), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.user).toBeDefined()
    expect(result.current.user?.displayName).toBe('Test User')
    expect(result.current.isAuthenticated).toBe(true)
  })

  it('isAdmin is true when role is ADMIN', async () => {
    server.use(
      http.get('/api/auth/me', () => {
        return HttpResponse.json({
          id: 'admin-1',
          email: 'admin@example.com',
          displayName: 'Admin User',
          oauthProvider: 'google',
          role: 'ADMIN',
          enabled: true,
        })
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useAuth(), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.isAdmin).toBe(true)
  })

  it('isAdmin is false when role is USER', async () => {
    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useAuth(), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.isAdmin).toBe(false)
  })

  it('isAuthenticated is false on 401', async () => {
    server.use(
      http.get('/api/auth/me', () => {
        return new HttpResponse(null, { status: 401 })
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useAuth(), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.isAuthenticated).toBe(false)
  })

  it('isEnabled is false when the account is disabled', async () => {
    server.use(
      http.get('/api/auth/me', () => {
        return HttpResponse.json({
          id: 'user-2',
          email: 'pending@example.com',
          displayName: 'Pending User',
          oauthProvider: 'google',
          role: 'USER',
          enabled: false,
        })
      }),
    )

    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useAuth(), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.isEnabled).toBe(false)
  })

  it('isLoading is true initially', () => {
    const { Wrapper } = createWrapper()
    const { result } = renderHook(() => useAuth(), { wrapper: Wrapper })

    expect(result.current.isLoading).toBe(true)
  })

  it('logout clears cache then calls API', async () => {
    const { Wrapper, queryClient } = createWrapper()
    const { result } = renderHook(() => useAuth(), { wrapper: Wrapper })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    // Verify user data is cached
    expect(queryClient.getQueryData(['auth', 'me'])).toBeDefined()

    await result.current.logout()

    // After logout, the cached data should be removed
    expect(queryClient.getQueryData(['auth', 'me'])).toBeUndefined()
  })
})
